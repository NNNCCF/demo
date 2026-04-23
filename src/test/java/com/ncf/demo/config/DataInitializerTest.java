package com.ncf.demo.config;

import com.ncf.demo.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class DataInitializerTest {
    @Test
    void resolveInitialAdminPasswordHashFallsBackToDefaultPassword() {
        DataInitializer initializer = new DataInitializer(
                mock(UserRepository.class),
                mock(DataSource.class),
                new AppProperties(),
                mock(MiniAppDemoDataSeeder.class),
                new BCryptPasswordEncoder()
        );

        String passwordHash = initializer.resolveInitialAdminPasswordHash();

        assertThat(new BCryptPasswordEncoder().matches("200502", passwordHash)).isTrue();
    }

    @Test
    void resolveInitialAdminPasswordHashUsesConfiguredHash() {
        AppProperties appProperties = new AppProperties();
        appProperties.setAdminPasswordHash("configured-hash");
        DataInitializer initializer = new DataInitializer(
                mock(UserRepository.class),
                mock(DataSource.class),
                appProperties,
                mock(MiniAppDemoDataSeeder.class),
                new BCryptPasswordEncoder()
        );

        assertThat(initializer.resolveInitialAdminPasswordHash()).isEqualTo("configured-hash");
    }
}
