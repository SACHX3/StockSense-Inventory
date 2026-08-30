package com.stocksense.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())

            .authorizeHttpRequests(auth -> auth
                // Public resources
                .requestMatchers(
                    "/css/**", "/js/**", "/images/**", "/vendor/**",
                    "/static/**", "/webjars/**", "/favicon.ico",
                    "/uploads/**"
                ).permitAll()
                .requestMatchers("/login", "/error", "/access-denied").permitAll()

                // Admin only pages
                .requestMatchers("/users/**", "/audit/**").hasRole("ADMIN")

                // Admin + Inventory Manager
                .requestMatchers(
                    "/categories/**", "/suppliers/**",
                    "/inventory/**", "/reports/**", "/forecasting/**"
                ).hasAnyRole("ADMIN", "INVENTORY_MANAGER")
                // Staff can view products list and low-stock page (read-only)
                .requestMatchers("/products/low-stock", "/products", "/products/view/**").hasAnyRole("ADMIN", "INVENTORY_MANAGER", "STAFF")
                // Product create/edit/delete - Admin + Manager only
                .requestMatchers("/products/**").hasAnyRole("ADMIN", "INVENTORY_MANAGER")
                // OCR delete - Admin only; rest of OCR - Admin + Manager
                .requestMatchers("/ocr/delete/**").hasRole("ADMIN")
                .requestMatchers("/ocr/**").hasAnyRole("ADMIN", "INVENTORY_MANAGER")

                // All logged-in users (Admin + Manager + Staff)
                .requestMatchers("/sales/**", "/dashboard/**", "/profile/**").hasAnyRole("ADMIN", "INVENTORY_MANAGER", "STAFF")

                // API endpoints
                .requestMatchers("/api/**").hasAnyRole("ADMIN", "INVENTORY_MANAGER", "STAFF")

                // Everything else needs authentication
                .anyRequest().authenticated()
            )

            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/login")
                .usernameParameter("username")
                .passwordParameter("password")
                .defaultSuccessUrl("/dashboard", true)
                .failureUrl("/login?error=true")
                .permitAll()
            )

            .exceptionHandling(exceptions -> exceptions
                .accessDeniedPage("/access-denied")
            )

            .logout(logout -> logout
                .logoutRequestMatcher(new AntPathRequestMatcher("/logout", "GET"))
                .logoutSuccessUrl("/login?logout=true")
                .invalidateHttpSession(true)
                .clearAuthentication(true)
                .deleteCookies("JSESSIONID")
                .permitAll()
            )

            .sessionManagement(session -> session
                .invalidSessionUrl("/login?expired=true")
                .maximumSessions(5)
                .expiredUrl("/login?expired=true")
            );

        return http.build();
    }
}
