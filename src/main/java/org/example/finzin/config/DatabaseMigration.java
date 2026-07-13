package org.example.finzin.config;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Runs idempotent DDL fixes that Hibernate ddl-auto=update won't apply automatically
 * (e.g. dropping NOT NULL constraints on existing columns).
 */
@Component
public class DatabaseMigration {

    private final DataSource dataSource;

    public DatabaseMigration(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @PostConstruct
    public void migrate() {
        runSilently("ALTER TABLE transactions ALTER COLUMN category_id DROP NOT NULL");
    }

    private void runSilently(String sql) {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            // If the constraint doesn't exist or column is already nullable, ignore
        }
    }
}
