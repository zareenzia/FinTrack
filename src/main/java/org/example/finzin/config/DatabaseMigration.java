package org.example.finzin.config;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Runs idempotent DDL fixes that Hibernate ddl-auto=validate won't apply automatically
 * (new tables/columns, constraint tweaks). Implemented as a BeanPostProcessor — rather than
 * a plain @PostConstruct bean — so it runs the moment the DataSource bean itself finishes
 * initializing, which is guaranteed to happen before JPA's EntityManagerFactory validates
 * the schema against that same DataSource.
 */
@Component
public class DatabaseMigration implements BeanPostProcessor {

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (bean instanceof DataSource dataSource) {
            migrate(dataSource);
        }
        return bean;
    }

    private void migrate(DataSource dataSource) {
        runSilently(dataSource, "ALTER TABLE transactions ALTER COLUMN category_id DROP NOT NULL");

        runSilently(dataSource, "ALTER TABLE transactions ADD COLUMN IF NOT EXISTS is_auto_generated BOOLEAN DEFAULT FALSE");
        runSilently(dataSource, "ALTER TABLE transactions ADD COLUMN IF NOT EXISTS recurring_transaction_id BIGINT");
        runSilently(dataSource, "ALTER TABLE transactions ADD COLUMN IF NOT EXISTS from_savings BOOLEAN NOT NULL DEFAULT FALSE");

        // Stale leftover from before CategoryEntity's uniqueness was properly scoped to
        // (userId, name): a Hibernate-auto-named global unique constraint on categories.name alone
        // is still live in the DB (ddl-auto=validate never drops/alters existing constraints), so
        // ANY user creating a category name any other user has ever used gets an unhandled 500.
        // The correctly-scoped uk_user_category_name constraint already covers per-user uniqueness.
        runSilently(dataSource, "ALTER TABLE categories DROP CONSTRAINT IF EXISTS ukt8o6pivur7nn124jehx7cygw5");

        runSilently(dataSource, "CREATE TABLE IF NOT EXISTS recurring_transactions (" +
                "id BIGSERIAL PRIMARY KEY, " +
                "user_id BIGINT NOT NULL, " +
                "transaction_name VARCHAR(255) NOT NULL, " +
                "description TEXT, " +
                "transaction_type VARCHAR(20) NOT NULL, " +
                "category_id BIGINT, " +
                "amount DOUBLE PRECISION NOT NULL, " +
                "source_account_id BIGINT, " +
                "destination_account_id BIGINT, " +
                "frequency VARCHAR(20) NOT NULL, " +
                "interval_value INTEGER NOT NULL DEFAULT 1, " +
                "start_date DATE NOT NULL, " +
                "end_date DATE, " +
                "next_execution_date DATE NOT NULL, " +
                "last_execution_date DATE, " +
                "status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE', " +
                "created_at TIMESTAMP NOT NULL DEFAULT NOW(), " +
                "updated_at TIMESTAMP NOT NULL DEFAULT NOW()" +
                ")");

        runSilently(dataSource, "CREATE TABLE IF NOT EXISTS category_budgets (" +
                "id BIGSERIAL PRIMARY KEY, " +
                "user_id BIGINT NOT NULL, " +
                "category_id BIGINT NOT NULL, " +
                "period VARCHAR(7) NOT NULL, " +
                "budget_amount DOUBLE PRECISION NOT NULL, " +
                "created_at TIMESTAMP NOT NULL DEFAULT NOW(), " +
                "updated_at TIMESTAMP NOT NULL DEFAULT NOW(), " +
                "CONSTRAINT uk_budget_user_category_period UNIQUE (user_id, category_id, period)" +
                ")");

        runSilently(dataSource, "CREATE TABLE IF NOT EXISTS notifications (" +
                "id BIGSERIAL PRIMARY KEY, " +
                "user_id BIGINT NOT NULL, " +
                "type VARCHAR(50) NOT NULL, " +
                "title VARCHAR(255) NOT NULL, " +
                "message TEXT NOT NULL, " +
                "related_entity_type VARCHAR(50), " +
                "related_entity_id BIGINT, " +
                "is_read BOOLEAN NOT NULL DEFAULT FALSE, " +
                "created_at TIMESTAMP NOT NULL DEFAULT NOW()" +
                ")");

        // ============== Complete Budget Planner ==============
        runSilently(dataSource, "CREATE TABLE IF NOT EXISTS budget_plans (" +
                "id BIGSERIAL PRIMARY KEY, " +
                "user_id BIGINT NOT NULL, " +
                "name VARCHAR(255) NOT NULL, " +
                "period_type VARCHAR(10) NOT NULL, " +
                "period VARCHAR(10) NOT NULL, " +
                "start_date DATE NOT NULL, " +
                "end_date DATE NOT NULL, " +
                "planned_income DOUBLE PRECISION NOT NULL DEFAULT 0, " +
                "planned_savings DOUBLE PRECISION NOT NULL DEFAULT 0, " +
                "notes TEXT, " +
                "status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE', " +
                "created_at TIMESTAMP NOT NULL DEFAULT NOW(), " +
                "updated_at TIMESTAMP NOT NULL DEFAULT NOW()" +
                ")");

        // Extend category_budgets with a link to its parent plan, and re-scope uniqueness to (plan, category)
        // instead of (user, category, period) — multiple named plans can now cover the same period.
        runSilently(dataSource, "ALTER TABLE category_budgets ADD COLUMN IF NOT EXISTS budget_plan_id BIGINT");
        runSilently(dataSource, "ALTER TABLE category_budgets DROP CONSTRAINT IF EXISTS uk_budget_user_category_period");
        runSilently(dataSource, "ALTER TABLE category_budgets ADD CONSTRAINT uk_budget_plan_category UNIQUE (budget_plan_id, category_id)");

        runSilently(dataSource, "CREATE TABLE IF NOT EXISTS savings_budgets (" +
                "id BIGSERIAL PRIMARY KEY, " +
                "budget_plan_id BIGINT NOT NULL, " +
                "category_id BIGINT NOT NULL, " +
                "target_amount DOUBLE PRECISION NOT NULL, " +
                "created_at TIMESTAMP NOT NULL DEFAULT NOW(), " +
                "updated_at TIMESTAMP NOT NULL DEFAULT NOW(), " +
                "CONSTRAINT uk_savings_budget_plan_category UNIQUE (budget_plan_id, category_id)" +
                ")");
        // Savings goal enhancements: a starting balance not backed by an in-app transaction (money already
        // saved before this goal was created), plus optional account references for "where it's stored" /
        // "where it came from".
        runSilently(dataSource, "ALTER TABLE savings_budgets ADD COLUMN IF NOT EXISTS initial_amount DOUBLE PRECISION NOT NULL DEFAULT 0");
        runSilently(dataSource, "ALTER TABLE savings_budgets ADD COLUMN IF NOT EXISTS storage_account_id BIGINT");
        runSilently(dataSource, "ALTER TABLE savings_budgets ADD COLUMN IF NOT EXISTS source_account_id BIGINT");
        // External funding source (bonus, gift, etc.) when the money didn't come from a tracked account.
        runSilently(dataSource, "ALTER TABLE savings_budgets ADD COLUMN IF NOT EXISTS source_description VARCHAR(255)");

        runSilently(dataSource, "CREATE TABLE IF NOT EXISTS budget_templates (" +
                "id BIGSERIAL PRIMARY KEY, " +
                "user_id BIGINT NOT NULL, " +
                "name VARCHAR(255) NOT NULL, " +
                "planned_income DOUBLE PRECISION NOT NULL DEFAULT 0, " +
                "planned_savings DOUBLE PRECISION NOT NULL DEFAULT 0, " +
                "notes TEXT, " +
                "created_at TIMESTAMP NOT NULL DEFAULT NOW(), " +
                "updated_at TIMESTAMP NOT NULL DEFAULT NOW()" +
                ")");

        runSilently(dataSource, "CREATE TABLE IF NOT EXISTS budget_template_categories (" +
                "id BIGSERIAL PRIMARY KEY, " +
                "template_id BIGINT NOT NULL, " +
                "category_id BIGINT NOT NULL, " +
                "planned_amount DOUBLE PRECISION NOT NULL, " +
                "is_savings BOOLEAN NOT NULL DEFAULT FALSE" +
                ")");

        // ============== AI Financial Assistant ==============
        runSilently(dataSource, "CREATE TABLE IF NOT EXISTS ai_conversations (" +
                "id BIGSERIAL PRIMARY KEY, " +
                "user_id BIGINT NOT NULL, " +
                "title VARCHAR(255), " +
                "created_at TIMESTAMP NOT NULL DEFAULT NOW(), " +
                "updated_at TIMESTAMP NOT NULL DEFAULT NOW()" +
                ")");

        runSilently(dataSource, "CREATE TABLE IF NOT EXISTS ai_messages (" +
                "id BIGSERIAL PRIMARY KEY, " +
                "conversation_id BIGINT NOT NULL, " +
                "user_id BIGINT NOT NULL, " +
                "role VARCHAR(20) NOT NULL, " +
                "content TEXT NOT NULL, " +
                "tool_name VARCHAR(100), " +
                "token_count INTEGER, " +
                "created_at TIMESTAMP NOT NULL DEFAULT NOW()" +
                ")");
        runSilently(dataSource, "CREATE INDEX IF NOT EXISTS idx_ai_messages_conv_created ON ai_messages (conversation_id, created_at)");
        runSilently(dataSource, "CREATE INDEX IF NOT EXISTS idx_ai_messages_user ON ai_messages (user_id)");

        runSilently(dataSource, "CREATE TABLE IF NOT EXISTS ai_settings (" +
                "id BIGSERIAL PRIMARY KEY, " +
                "user_id BIGINT NOT NULL, " +
                "provider VARCHAR(30) NOT NULL DEFAULT 'openai', " +
                "model VARCHAR(60) NOT NULL DEFAULT 'gpt-5', " +
                "max_tokens INTEGER NOT NULL DEFAULT 800, " +
                "temperature DOUBLE PRECISION NOT NULL DEFAULT 0.3, " +
                "enabled BOOLEAN NOT NULL DEFAULT TRUE, " +
                "created_at TIMESTAMP NOT NULL DEFAULT NOW(), " +
                "updated_at TIMESTAMP NOT NULL DEFAULT NOW(), " +
                "CONSTRAINT uk_ai_settings_user UNIQUE (user_id)" +
                ")");
        runSilently(dataSource, "ALTER TABLE ai_settings ADD COLUMN IF NOT EXISTS developer_mode BOOLEAN NOT NULL DEFAULT FALSE");

        // ============== RAG: Semantic Retrieval Infrastructure (Phase 2A) ==============
        runSilently(dataSource, "CREATE EXTENSION IF NOT EXISTS vector");

        runSilently(dataSource, "CREATE TABLE IF NOT EXISTS ai_document_embeddings (" +
                "id BIGSERIAL PRIMARY KEY, " +
                "user_id BIGINT NOT NULL, " +
                "entity_type VARCHAR(30) NOT NULL, " +
                "entity_id BIGINT NOT NULL, " +
                "title VARCHAR(255), " +
                "content TEXT NOT NULL, " +
                "content_hash VARCHAR(64) NOT NULL, " +
                "metadata TEXT, " +
                "embedding vector(1536), " +
                "created_at TIMESTAMP NOT NULL DEFAULT NOW(), " +
                "updated_at TIMESTAMP NOT NULL DEFAULT NOW(), " +
                "CONSTRAINT uk_ai_doc_embed_entity UNIQUE (user_id, entity_type, entity_id)" +
                ")");
        runSilently(dataSource, "CREATE INDEX IF NOT EXISTS idx_ai_doc_embed_user ON ai_document_embeddings (user_id)");
        runSilently(dataSource, "CREATE INDEX IF NOT EXISTS idx_ai_doc_embed_vector ON ai_document_embeddings USING hnsw (embedding vector_cosine_ops)");

        // ============== AI Financial Coach (Phase 2C) ==============
        runSilently(dataSource, "CREATE TABLE IF NOT EXISTS net_worth_snapshots (" +
                "id BIGSERIAL PRIMARY KEY, " +
                "user_id BIGINT NOT NULL, " +
                "snapshot_month VARCHAR(7) NOT NULL, " +
                "net_worth DOUBLE PRECISION NOT NULL, " +
                "total_assets DOUBLE PRECISION NOT NULL, " +
                "balance DOUBLE PRECISION NOT NULL, " +
                "total_savings_contributed DOUBLE PRECISION NOT NULL, " +
                "created_at TIMESTAMP NOT NULL DEFAULT NOW(), " +
                "CONSTRAINT uk_networth_snapshot_user_month UNIQUE (user_id, snapshot_month)" +
                ")");

        runSilently(dataSource, "ALTER TABLE ai_settings ADD COLUMN IF NOT EXISTS enable_proactive_insights BOOLEAN NOT NULL DEFAULT TRUE");
        runSilently(dataSource, "ALTER TABLE ai_settings ADD COLUMN IF NOT EXISTS enable_budget_coaching BOOLEAN NOT NULL DEFAULT TRUE");
        runSilently(dataSource, "ALTER TABLE ai_settings ADD COLUMN IF NOT EXISTS enable_savings_coaching BOOLEAN NOT NULL DEFAULT TRUE");
        runSilently(dataSource, "ALTER TABLE ai_settings ADD COLUMN IF NOT EXISTS enable_monthly_reports BOOLEAN NOT NULL DEFAULT TRUE");
        runSilently(dataSource, "ALTER TABLE ai_settings ADD COLUMN IF NOT EXISTS enable_dashboard_summary BOOLEAN NOT NULL DEFAULT TRUE");

        // ============== Financial Planner ==============
        runSilently(dataSource, "CREATE TABLE IF NOT EXISTS investments (" +
                "id BIGSERIAL PRIMARY KEY, " +
                "user_id BIGINT NOT NULL, " +
                "name VARCHAR(255) NOT NULL, " +
                "investment_type VARCHAR(50) NOT NULL, " +
                "platform VARCHAR(255), " +
                "purchase_date DATE NOT NULL, " +
                "quantity DOUBLE PRECISION NOT NULL, " +
                "purchase_price DOUBLE PRECISION NOT NULL, " +
                "current_price DOUBLE PRECISION NOT NULL, " +
                "notes TEXT, " +
                "created_at TIMESTAMP NOT NULL DEFAULT NOW(), " +
                "updated_at TIMESTAMP NOT NULL DEFAULT NOW()" +
                ")");

        runSilently(dataSource, "CREATE TABLE IF NOT EXISTS loans (" +
                "id BIGSERIAL PRIMARY KEY, " +
                "user_id BIGINT NOT NULL, " +
                "loan_name VARCHAR(255) NOT NULL, " +
                "loan_type VARCHAR(50) NOT NULL, " +
                "lender_borrower VARCHAR(255), " +
                "principal_amount DOUBLE PRECISION NOT NULL, " +
                "interest_rate DOUBLE PRECISION, " +
                "emi_amount DOUBLE PRECISION, " +
                "loan_start_date DATE NOT NULL, " +
                "loan_end_date DATE, " +
                "remaining_balance DOUBLE PRECISION NOT NULL, " +
                "payment_frequency VARCHAR(20), " +
                "status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE', " +
                "notes TEXT, " +
                "created_at TIMESTAMP NOT NULL DEFAULT NOW(), " +
                "updated_at TIMESTAMP NOT NULL DEFAULT NOW()" +
                ")");

        runSilently(dataSource, "CREATE TABLE IF NOT EXISTS subscriptions (" +
                "id BIGSERIAL PRIMARY KEY, " +
                "user_id BIGINT NOT NULL, " +
                "name VARCHAR(255) NOT NULL, " +
                "category VARCHAR(100), " +
                "billing_cycle VARCHAR(20) NOT NULL, " +
                "cost DOUBLE PRECISION NOT NULL, " +
                "renewal_date DATE, " +
                "payment_method VARCHAR(100), " +
                "payment_account VARCHAR(255), " +
                "auto_renewal BOOLEAN NOT NULL DEFAULT TRUE, " +
                "status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE', " +
                "notes TEXT, " +
                "created_at TIMESTAMP NOT NULL DEFAULT NOW(), " +
                "updated_at TIMESTAMP NOT NULL DEFAULT NOW()" +
                ")");

        runSilently(dataSource, "CREATE TABLE IF NOT EXISTS wishlist_goals (" +
                "id BIGSERIAL PRIMARY KEY, " +
                "user_id BIGINT NOT NULL, " +
                "goal_name VARCHAR(255) NOT NULL, " +
                "category VARCHAR(100), " +
                "target_amount DOUBLE PRECISION NOT NULL, " +
                "saved_amount DOUBLE PRECISION NOT NULL DEFAULT 0, " +
                "target_date DATE, " +
                "priority VARCHAR(20) NOT NULL DEFAULT 'MEDIUM', " +
                "status VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS', " +
                "icon VARCHAR(100), " +
                "notes TEXT, " +
                "created_at TIMESTAMP NOT NULL DEFAULT NOW(), " +
                "updated_at TIMESTAMP NOT NULL DEFAULT NOW()" +
                ")");

        runSilently(dataSource, "CREATE TABLE IF NOT EXISTS sidebar_preferences (" +
                "id BIGSERIAL PRIMARY KEY, " +
                "user_id BIGINT NOT NULL UNIQUE, " +
                "preferences_json TEXT, " +
                "created_at TIMESTAMP NOT NULL DEFAULT NOW(), " +
                "updated_at TIMESTAMP NOT NULL DEFAULT NOW()" +
                ")");

        // ============== Notes: "done" state ==============
        runSilently(dataSource, "ALTER TABLE notes ADD COLUMN IF NOT EXISTS done BOOLEAN NOT NULL DEFAULT FALSE");

        // Repair pre-existing checklist markup: the editor used to emit data-list="check", a value
        // Quill's own checkbox click handler (formats/list.js) never recognizes as toggleable —
        // only "checked"/"unchecked" are. Rewriting to "unchecked" makes existing checklist items
        // clickable without altering any note's visible content or deleting anything.
        runSilently(dataSource, "UPDATE notes SET content = REPLACE(content, 'data-list=\"check\"', 'data-list=\"unchecked\"') " +
                "WHERE content LIKE '%data-list=\"check\"%'");

        // ============== Credit card accounting ==============
        runSilently(dataSource, "ALTER TABLE accounts ADD COLUMN IF NOT EXISTS credit_limit_behavior VARCHAR(10) NOT NULL DEFAULT 'WARN'");

        // Outstanding balance is a derived value, not an independently-trusted stored number: on every
        // startup, recompute each credit card's current_balance from opening_balance plus the signed
        // effect of every transaction that references it (source: expense/savings/transfer-out increase
        // what's owed, income decreases it as a refund; destination: transfer-in is a payment and
        // decreases it). Idempotent and safe to run every time — it only ever re-derives the same
        // number the live application logic would already be maintaining.
        runSilently(dataSource, "UPDATE accounts a SET current_balance = a.opening_balance + COALESCE((" +
                "SELECT SUM(CASE " +
                "WHEN t.source_account_id = a.id AND t.transaction_type IN ('expense','savings','transfer') THEN t.amount " +
                "WHEN t.source_account_id = a.id AND t.transaction_type = 'income' THEN -t.amount " +
                "WHEN t.destination_account_id = a.id AND t.transaction_type = 'transfer' THEN -t.amount " +
                "ELSE 0 END) " +
                "FROM transactions t WHERE t.source_account_id = a.id OR t.destination_account_id = a.id" +
                "), 0) " +
                "WHERE a.account_type = 'CREDIT_CARD'");

        // ============== Voice Assistant ==============
        runSilently(dataSource, "CREATE TABLE IF NOT EXISTS voice_command_history (" +
                "id BIGSERIAL PRIMARY KEY, " +
                "user_id BIGINT NOT NULL, " +
                "audio_path VARCHAR(500), " +
                "original_transcript TEXT NOT NULL, " +
                "corrected_text TEXT, " +
                "intent VARCHAR(20) NOT NULL, " +
                "parsed_json TEXT, " +
                "source VARCHAR(10) NOT NULL DEFAULT 'AI', " +
                "status VARCHAR(20) NOT NULL DEFAULT 'pending', " +
                "resolved_entity_type VARCHAR(20), " +
                "resolved_entity_id BIGINT, " +
                "created_at TIMESTAMP NOT NULL DEFAULT NOW()" +
                ")");
        runSilently(dataSource, "CREATE INDEX IF NOT EXISTS idx_voice_history_user_created ON voice_command_history (user_id, created_at DESC)");

        runSilently(dataSource, "CREATE TABLE IF NOT EXISTS voice_settings (" +
                "id BIGSERIAL PRIMARY KEY, " +
                "user_id BIGINT NOT NULL, " +
                "enabled BOOLEAN NOT NULL DEFAULT TRUE, " +
                "language VARCHAR(10) NOT NULL DEFAULT 'en-US', " +
                "speech_provider VARCHAR(30) NOT NULL DEFAULT 'browser', " +
                "auto_stop_silence_seconds INTEGER NOT NULL DEFAULT 3, " +
                "noise_reduction BOOLEAN NOT NULL DEFAULT FALSE, " +
                "save_audio_recordings BOOLEAN NOT NULL DEFAULT FALSE, " +
                "max_recording_length_seconds INTEGER NOT NULL DEFAULT 60, " +
                "speech_speed DOUBLE PRECISION NOT NULL DEFAULT 1.0, " +
                "created_at TIMESTAMP NOT NULL DEFAULT NOW(), " +
                "updated_at TIMESTAMP NOT NULL DEFAULT NOW(), " +
                "CONSTRAINT uk_voice_settings_user UNIQUE (user_id)" +
                ")");

        // ============== Gamification ==============
        runSilently(dataSource, "CREATE TABLE IF NOT EXISTS gamification_settings (" +
                "id BIGSERIAL PRIMARY KEY, " +
                "user_id BIGINT NOT NULL, " +
                "enabled BOOLEAN NOT NULL DEFAULT TRUE, " +
                "enable_notifications BOOLEAN NOT NULL DEFAULT TRUE, " +
                "show_dashboard_widget BOOLEAN NOT NULL DEFAULT TRUE, " +
                "enable_celebrations BOOLEAN NOT NULL DEFAULT TRUE, " +
                "enable_challenges BOOLEAN NOT NULL DEFAULT TRUE, " +
                "enable_streak_tracking BOOLEAN NOT NULL DEFAULT TRUE, " +
                "show_xp BOOLEAN NOT NULL DEFAULT TRUE, " +
                "created_at TIMESTAMP NOT NULL DEFAULT NOW(), " +
                "updated_at TIMESTAMP NOT NULL DEFAULT NOW(), " +
                "CONSTRAINT uk_gamification_settings_user UNIQUE (user_id)" +
                ")");

        runSilently(dataSource, "CREATE TABLE IF NOT EXISTS user_xp (" +
                "id BIGSERIAL PRIMARY KEY, " +
                "user_id BIGINT NOT NULL, " +
                "total_xp BIGINT NOT NULL DEFAULT 0, " +
                "current_level INTEGER NOT NULL DEFAULT 1, " +
                "updated_at TIMESTAMP NOT NULL DEFAULT NOW(), " +
                "CONSTRAINT uk_user_xp_user UNIQUE (user_id)" +
                ")");

        runSilently(dataSource, "CREATE TABLE IF NOT EXISTS xp_history (" +
                "id BIGSERIAL PRIMARY KEY, " +
                "user_id BIGINT NOT NULL, " +
                "amount INTEGER NOT NULL, " +
                "reason VARCHAR(60) NOT NULL, " +
                "source_type VARCHAR(30) NOT NULL, " +
                "source_id VARCHAR(60) NOT NULL, " +
                "created_at TIMESTAMP NOT NULL DEFAULT NOW(), " +
                "CONSTRAINT uk_xp_history_source UNIQUE (user_id, source_type, source_id, reason)" +
                ")");
        runSilently(dataSource, "CREATE INDEX IF NOT EXISTS idx_xp_history_user_created ON xp_history (user_id, created_at DESC)");

        runSilently(dataSource, "CREATE TABLE IF NOT EXISTS achievement_definitions (" +
                "id BIGSERIAL PRIMARY KEY, " +
                "code VARCHAR(60) NOT NULL, " +
                "category VARCHAR(30) NOT NULL, " +
                "name VARCHAR(100) NOT NULL, " +
                "description VARCHAR(255) NOT NULL, " +
                "icon VARCHAR(60) NOT NULL, " +
                "tier_color VARCHAR(20) NOT NULL DEFAULT 'bronze', " +
                "criteria_type VARCHAR(30) NOT NULL, " +
                "metric_key VARCHAR(60) NOT NULL, " +
                "threshold DOUBLE PRECISION NOT NULL, " +
                "window_days INTEGER, " +
                "xp_reward INTEGER NOT NULL DEFAULT 0, " +
                "is_milestone BOOLEAN NOT NULL DEFAULT FALSE, " +
                "active BOOLEAN NOT NULL DEFAULT TRUE, " +
                "created_at TIMESTAMP NOT NULL DEFAULT NOW(), " +
                "CONSTRAINT uk_achievement_code UNIQUE (code)" +
                ")");

        runSilently(dataSource, "CREATE TABLE IF NOT EXISTS user_achievements (" +
                "id BIGSERIAL PRIMARY KEY, " +
                "user_id BIGINT NOT NULL, " +
                "achievement_id BIGINT NOT NULL, " +
                "status VARCHAR(20) NOT NULL DEFAULT 'LOCKED', " +
                "progress_current DOUBLE PRECISION NOT NULL DEFAULT 0, " +
                "progress_target DOUBLE PRECISION NOT NULL DEFAULT 0, " +
                "unlocked_at TIMESTAMP, " +
                "updated_at TIMESTAMP NOT NULL DEFAULT NOW(), " +
                "CONSTRAINT uk_user_achievement UNIQUE (user_id, achievement_id)" +
                ")");
        runSilently(dataSource, "CREATE INDEX IF NOT EXISTS idx_user_achievements_user ON user_achievements (user_id)");

        runSilently(dataSource, "CREATE TABLE IF NOT EXISTS user_stat_counters (" +
                "id BIGSERIAL PRIMARY KEY, " +
                "user_id BIGINT NOT NULL, " +
                "counter_key VARCHAR(60) NOT NULL, " +
                "counter_value DOUBLE PRECISION NOT NULL DEFAULT 0, " +
                "updated_at TIMESTAMP NOT NULL DEFAULT NOW(), " +
                "CONSTRAINT uk_user_stat_counter UNIQUE (user_id, counter_key)" +
                ")");

        runSilently(dataSource, "CREATE TABLE IF NOT EXISTS streaks (" +
                "id BIGSERIAL PRIMARY KEY, " +
                "user_id BIGINT NOT NULL, " +
                "streak_type VARCHAR(30) NOT NULL, " +
                "current_streak INTEGER NOT NULL DEFAULT 0, " +
                "longest_streak INTEGER NOT NULL DEFAULT 0, " +
                "last_activity_date DATE, " +
                "updated_at TIMESTAMP NOT NULL DEFAULT NOW(), " +
                "CONSTRAINT uk_streaks_user_type UNIQUE (user_id, streak_type)" +
                ")");

        // Seed data — a curated, illustrative starting set; more can be added later via pure
        // inserts at existing metrics/thresholds, no code changes required. ON CONFLICT DO NOTHING
        // (raw SQL against this JDBC-migration file only, not a new pattern for application code)
        // keeps this idempotent across every restart.
        runSilently(dataSource, "INSERT INTO achievement_definitions " +
                "(code, category, name, description, icon, tier_color, criteria_type, metric_key, threshold, xp_reward, is_milestone) VALUES " +
                "('TXN_FIRST','Transactions','First Transaction','Log your very first transaction','fa-receipt','bronze','COUNT','transactions.count',1,10,TRUE)," +
                "('TXN_100','Transactions','Century Logger','Log 100 transactions','fa-receipt','bronze','COUNT','transactions.count',100,20,FALSE)," +
                "('TXN_500','Transactions','Transaction Veteran','Log 500 transactions','fa-receipt','silver','COUNT','transactions.count',500,40,FALSE)," +
                "('TXN_1000','Transactions','Transaction Master','Log 1000 transactions','fa-receipt','gold','COUNT','transactions.count',1000,80,FALSE)," +
                "('SAV_10K','Savings','Bronze Saver','Save ৳ 10,000 in total','fa-piggy-bank','bronze','CUMULATIVE_SUM','savings.total',10000,30,FALSE)," +
                "('SAV_50K','Savings','Silver Saver','Save ৳ 50,000 in total','fa-piggy-bank','silver','CUMULATIVE_SUM','savings.total',50000,60,FALSE)," +
                "('SAV_100K','Savings','Gold Saver','Save ৳ 1,00,000 in total','fa-piggy-bank','gold','CUMULATIVE_SUM','savings.total',100000,100,FALSE)," +
                "('BUDGET_FIRST','Budget','Budget Beginner','Create your first budget','fa-wallet','bronze','COUNT','budget.plans_created',1,20,TRUE)," +
                "('BUDGET_3MO','Budget','Budget Disciplined','Stay within budget for 3 consecutive months','fa-wallet','silver','CONSECUTIVE_PERIODS','budget.consecutive_months_within',3,100,FALSE)," +
                "('BUDGET_6MO','Budget','Budget Master','Stay within budget for 6 consecutive months','fa-wallet','gold','CONSECUTIVE_PERIODS','budget.consecutive_months_within',6,150,FALSE)," +
                "('BUDGET_12MO','Budget','Budget Legend','Stay within budget for 12 consecutive months','fa-wallet','diamond','CONSECUTIVE_PERIODS','budget.consecutive_months_within',12,300,FALSE)," +
                "('NW_1L','Net Worth','Six Figures','Reach ৳ 1,00,000 net worth','fa-chart-line','bronze','SINGLE_VALUE_REACHED','networth.value',100000,40,FALSE)," +
                "('NW_5L','Net Worth','Half Millionaire','Reach ৳ 5,00,000 net worth','fa-chart-line','silver','SINGLE_VALUE_REACHED','networth.value',500000,80,FALSE)," +
                "('NW_10L','Net Worth','Millionaire','Reach ৳ 10,00,000 net worth','fa-chart-line','gold','SINGLE_VALUE_REACHED','networth.value',1000000,150,TRUE)," +
                "('NW_GROWTH25','Net Worth','Rising Star','Grow your net worth by 25%','fa-arrow-trend-up','silver','SINGLE_VALUE_REACHED','networth.growth_percent',25,60,FALSE)," +
                "('INV_FIRST','Investments','First Investment','Log your first investment','fa-coins','bronze','COUNT','investments.count',1,30,TRUE)," +
                "('INV_50K','Investments','Growing Portfolio','Reach ৳ 50,000 portfolio value','fa-coins','silver','SINGLE_VALUE_REACHED','investments.portfolio_value',50000,60,FALSE)," +
                "('INV_100K','Investments','Serious Investor','Reach ৳ 1,00,000 portfolio value','fa-coins','gold','SINGLE_VALUE_REACHED','investments.portfolio_value',100000,100,FALSE)," +
                "('INV_DIVERSIFIED','Investments','Diversified Investor','Invest in 3 or more different asset types','fa-layer-group','gold','COUNT','investments.distinct_types',3,50,FALSE)," +
                "('LOAN_FIRST_PAID','Loans','Debt Payer','Pay off your first loan','fa-hand-holding-dollar','bronze','COUNT','loans.paid_off_count',1,40,TRUE)," +
                "('LOAN_50PCT','Loans','Halfway There','Reduce total debt by 50%','fa-hand-holding-dollar','silver','SINGLE_VALUE_REACHED','loans.debt_reduced_percent',50,60,FALSE)," +
                "('LOAN_DEBT_FREE','Loans','Debt Free','Pay off all your loans','fa-hand-holding-dollar','diamond','SINGLE_VALUE_REACHED','loans.debt_free',1,200,TRUE)," +
                "('CC_UTIL_30','Credit Cards','Credit Conscious','Keep credit utilization below 30%','fa-credit-card','silver','SINGLE_VALUE_REACHED','creditcard.headroom_percent',70,50,FALSE)," +
                "('ASSET_FIRST_GOLD','Assets','Gold Beginner','Purchase your first gold asset','fa-gem','bronze','COUNT','assets.count',1,20,TRUE)," +
                "('ASSET_50K','Assets','Asset Builder','Reach ৳ 50,000 in total asset value','fa-gem','silver','SINGLE_VALUE_REACHED','assets.total_value',50000,50,FALSE)," +
                "('GOAL_FIRST','Goals','Goal Getter','Complete your first goal','fa-bullseye','bronze','COUNT','goals.completed_count',1,50,TRUE)," +
                "('GOAL_5','Goals','Goal Crusher','Complete 5 goals','fa-bullseye','gold','COUNT','goals.completed_count',5,150,FALSE)," +
                "('AI_10','AI Usage','AI Curious','Use the AI Assistant 10 times','fa-robot','bronze','COUNT','ai.conversations_count',10,20,FALSE)," +
                "('AI_100','AI Usage','AI Power User','Use the AI Assistant 100 times','fa-robot','gold','COUNT','ai.conversations_count',100,80,FALSE)," +
                "('NOTE_10','Notes','Note Taker','Create 10 notes','fa-sticky-note','bronze','COUNT','notes.count',10,15,FALSE)," +
                "('NOTE_100','Notes','Prolific Writer','Create 100 notes','fa-sticky-note','gold','COUNT','notes.count',100,60,FALSE)," +
                "('TODO_10','Productivity','Getting Things Done','Complete 10 to-dos','fa-list-check','bronze','COUNT','todos.completed_count',10,15,FALSE)," +
                "('TODO_100','Productivity','Productivity Pro','Complete 100 to-dos','fa-list-check','gold','COUNT','todos.completed_count',100,60,FALSE)," +
                "('RECEIPT_10','Productivity','Receipt Rookie','Scan 10 receipts','fa-camera','bronze','COUNT','receipts.scanned_count',10,15,FALSE)," +
                "('RECEIPT_100','Productivity','Receipt Master','Scan 100 receipts','fa-camera','gold','COUNT','receipts.scanned_count',100,60,FALSE)," +
                "('STREAK_7','Consistency','Week Warrior','Stay active 7 days in a row','fa-fire','bronze','STREAK_DAYS','streak.daily_active',7,30,FALSE)," +
                "('STREAK_30','Consistency','Monthly Habit','Stay active 30 days in a row','fa-fire','silver','STREAK_DAYS','streak.daily_active',30,80,FALSE)," +
                "('STREAK_100','Consistency','Centurion','Stay active 100 days in a row','fa-fire','gold','STREAK_DAYS','streak.daily_active',100,200,FALSE) " +
                "ON CONFLICT (code) DO NOTHING");

        // ============== Gamification: Monthly Challenges (Stage 2) ==============
        runSilently(dataSource, "CREATE TABLE IF NOT EXISTS challenge_definitions (" +
                "id BIGSERIAL PRIMARY KEY, " +
                "code VARCHAR(40) NOT NULL, " +
                "scope VARCHAR(10) NOT NULL DEFAULT 'MONTHLY', " +
                "name VARCHAR(100) NOT NULL, " +
                "description VARCHAR(255) NOT NULL, " +
                "metric_key VARCHAR(60) NOT NULL, " +
                "target_value DOUBLE PRECISION NOT NULL, " +
                "xp_reward INTEGER NOT NULL, " +
                "active BOOLEAN NOT NULL DEFAULT TRUE, " +
                "created_at TIMESTAMP NOT NULL DEFAULT NOW(), " +
                "CONSTRAINT uk_challenge_code UNIQUE (code)" +
                ")");

        runSilently(dataSource, "CREATE TABLE IF NOT EXISTS user_challenges (" +
                "id BIGSERIAL PRIMARY KEY, " +
                "user_id BIGINT NOT NULL, " +
                "challenge_id BIGINT NOT NULL, " +
                "period_key VARCHAR(10) NOT NULL, " +
                "progress_current DOUBLE PRECISION NOT NULL DEFAULT 0, " +
                "target_value DOUBLE PRECISION NOT NULL, " +
                "status VARCHAR(15) NOT NULL DEFAULT 'IN_PROGRESS', " +
                "completed_at TIMESTAMP, " +
                "created_at TIMESTAMP NOT NULL DEFAULT NOW(), " +
                "updated_at TIMESTAMP NOT NULL DEFAULT NOW(), " +
                "CONSTRAINT uk_user_challenge_period UNIQUE (user_id, challenge_id, period_key)" +
                ")");

        runSilently(dataSource, "INSERT INTO challenge_definitions " +
                "(code, name, description, metric_key, target_value, xp_reward) VALUES " +
                "('CH_TXN_20','Transaction Tracker','Log 20 transactions this month','transactions.count',20,30)," +
                "('CH_SAVE_2K','Savings Sprint','Save ৳ 2,000 this month','savings.total',2000,40)," +
                "('CH_TODO_10','Todo Finisher','Complete 10 to-dos this month','todos.completed_count',10,25)," +
                "('CH_RECEIPT_3','Receipt Ready','Scan 3 receipts this month','receipts.scanned_count',3,20)," +
                "('CH_NOTE_5','Note Taker','Create 5 notes this month','notes.count',5,15) " +
                "ON CONFLICT (code) DO NOTHING");
    }

    private void runSilently(DataSource dataSource, String sql) {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            // If the constraint doesn't exist, column is already nullable, or table already exists, ignore
        }
    }
}
