package com.stocksense.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Security Configuration Tests")
class SecurityConfigTest {

    @Test
    @DisplayName("TC95 - security: password encoder matches the original password")
    void passwordEncoder_matchesEncodedPassword() {
        PasswordEncoder encoder = new SecurityConfig().passwordEncoder();

        String encoded = encoder.encode("admin123");

        assertThat(encoded).isNotEqualTo("admin123");
        assertThat(encoder.matches("admin123", encoded)).isTrue();
        assertThat(encoder.matches("wrong-password", encoded)).isFalse();
    }
}
