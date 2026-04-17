package com.ncf.demo.config;

import com.ncf.demo.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.util.StreamUtils;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Configuration
public class DataInitializer implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final UserRepository userRepository;
    private final DataSource dataSource;
    private final AppProperties appProperties;

    public DataInitializer(UserRepository userRepository, DataSource dataSource, AppProperties appProperties) {
        this.userRepository = userRepository;
        this.dataSource = dataSource;
        this.appProperties = appProperties;
    }

    @Override
    public void run(String... args) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        try {
            jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS=0");
            try {
                jdbcTemplate.execute("ALTER TABLE sys_user MODIFY id BIGINT AUTO_INCREMENT");
                jdbcTemplate.execute("ALTER TABLE sys_user AUTO_INCREMENT = 2000");
                log.info("Successfully altered sys_user table to auto_increment");
            } finally {
                jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS=1");
            }
        } catch (Exception e) {
            log.warn("Could not alter table to auto_increment: {}", e.getMessage());
        }

        try {
            jdbcTemplate.execute("ALTER TABLE device DROP COLUMN sn_code");
            log.info("Dropped device.sn_code column");
        } catch (Exception e) {
            log.warn("Could not drop device.sn_code column: {}", e.getMessage());
        }

        ensureGuardianForeignKey(jdbcTemplate);
        migrateAlarmRuleType(jdbcTemplate);

        try {
            if (userRepository.count() == 0) {
                maybeImportMockData();
            }
            ensureDefaultAdminUser(jdbcTemplate);
        } catch (Exception e) {
            log.error("Failed during data initialization: {}", e.getMessage(), e);
        }
    }

    private void maybeImportMockData() {
        if (!appProperties.isMockDataEnabled()) {
            log.info("Mock data import is disabled (app.mock-data-enabled=false).");
            return;
        }

        ClassPathResource script = new ClassPathResource("mock_data.sql");
        if (!hasUsableScript(script)) {
            log.warn("mock_data.sql is missing or empty, skipping mock data import.");
            return;
        }

        log.info("Database appears empty. Initializing with mock data...");
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(script);
        populator.execute(dataSource);
        log.info("Mock data initialization completed.");
    }

    private boolean hasUsableScript(ClassPathResource script) {
        try {
            if (!script.exists() || script.contentLength() <= 0) {
                return false;
            }
            try (var in = script.getInputStream()) {
                String content = StreamUtils.copyToString(in, StandardCharsets.UTF_8);
                return content != null && !content.trim().isEmpty();
            }
        } catch (Exception e) {
            log.warn("Unable to read mock_data.sql: {}", e.getMessage());
            return false;
        }
    }

    private void ensureDefaultAdminUser(JdbcTemplate jdbcTemplate) {
        if (userRepository.existsById(1000L) || userRepository.existsByUsername("admin")) {
            return;
        }

        log.info("Admin user not found. Creating default admin user...");
        String passwordHash = appProperties.getAdminPasswordHash();
        if (passwordHash == null || passwordHash.isBlank()) {
            log.error("app.admin-password-hash is not configured, skip creating admin user.");
            return;
        }

        jdbcTemplate.update(
                "INSERT INTO sys_user (id, username, password_hash, role, region, phone, status, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, NOW(), NOW())",
                1000L, "admin", passwordHash, "ADMIN", "HQ", "13800138000", "ENABLED"
        );
        log.info("Default admin user created.");
    }

    private void migrateAlarmRuleType(JdbcTemplate jdbcTemplate) {
        try {
            jdbcTemplate.execute("ALTER TABLE alarm_rule MODIFY alarm_type VARCHAR(32) NOT NULL");
            log.info("alarm_rule.alarm_type column migrated to VARCHAR(32)");
        } catch (Exception e) {
            log.warn("Could not migrate alarm_rule.alarm_type column: {}", e.getMessage());
        }
    }

    private void ensureGuardianForeignKey(JdbcTemplate jdbcTemplate) {
        try {
            List<GuardianFkInfo> foreignKeys = jdbcTemplate.query("""
                    SELECT CONSTRAINT_NAME, REFERENCED_TABLE_NAME
                    FROM information_schema.KEY_COLUMN_USAGE
                    WHERE TABLE_SCHEMA = DATABASE()
                      AND TABLE_NAME = 'device'
                      AND COLUMN_NAME = 'guardian_id'
                      AND REFERENCED_TABLE_NAME IS NOT NULL
                    """, (rs, rowNum) -> new GuardianFkInfo(
                    rs.getString("CONSTRAINT_NAME"),
                    rs.getString("REFERENCED_TABLE_NAME")
            ));

            boolean hasClientUserFk = false;
            for (GuardianFkInfo fk : foreignKeys) {
                if ("client_user".equalsIgnoreCase(fk.referencedTable())) {
                    hasClientUserFk = true;
                    continue;
                }
                if (fk.constraintName() != null && !fk.constraintName().isBlank()) {
                    jdbcTemplate.execute("ALTER TABLE device DROP FOREIGN KEY `" + fk.constraintName() + "`");
                }
            }

            jdbcTemplate.execute("""
                    UPDATE device d
                    LEFT JOIN client_user c ON d.guardian_id = c.id
                    SET d.guardian_id = NULL
                    WHERE d.guardian_id IS NOT NULL AND c.id IS NULL
                    """);

            if (!hasClientUserFk) {
                jdbcTemplate.execute("""
                        ALTER TABLE device
                        ADD CONSTRAINT fk_device_guardian_client_user
                        FOREIGN KEY (guardian_id) REFERENCES client_user(id)
                        """);
                log.info("Aligned device.guardian_id foreign key to client_user(id)");
            } else {
                log.info("device.guardian_id foreign key already points to client_user(id)");
            }
        } catch (Exception e) {
            log.warn("Could not align device.guardian_id foreign key: {}", e.getMessage());
        }
    }

    private record GuardianFkInfo(String constraintName, String referencedTable) {
    }
}
