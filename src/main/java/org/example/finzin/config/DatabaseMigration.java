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
