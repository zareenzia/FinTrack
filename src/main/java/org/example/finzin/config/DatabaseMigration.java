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
