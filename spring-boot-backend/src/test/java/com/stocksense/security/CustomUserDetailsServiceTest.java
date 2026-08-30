package com.stocksense.security;

import com.stocksense.entity.Role;
import com.stocksense.entity.User;
import com.stocksense.repository.UserRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Authentication and User Details Tests")
class CustomUserDetailsServiceTest {

    @Mock UserRepository userRepository;
    @InjectMocks CustomUserDetailsService userDetailsService;

    @Test
    @DisplayName("TC66 - authentication: active user receives normalized role authority")
    void loadUserByUsername_activeManager_normalizesAuthority() {
        User user = user("manager", true, "MANAGER");
        when(userRepository.findByUsername("manager")).thenReturn(Optional.of(user));

        UserDetails details = userDetailsService.loadUserByUsername("manager");

        assertThat(details.getUsername()).isEqualTo("manager");
        assertThat(details.getPassword()).isEqualTo("encoded");
        assertThat(details.getAuthorities()).extracting(Object::toString)
                .containsExactly("ROLE_INVENTORY_MANAGER");
    }

    @Test
    @DisplayName("TC67 - authentication: inactive user cannot sign in")
    void loadUserByUsername_inactiveUser_isRejected() {
        when(userRepository.findByUsername("disabled")).thenReturn(Optional.of(user("disabled", false, "STAFF")));

        assertThatThrownBy(() -> userDetailsService.loadUserByUsername("disabled"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("disabled");
    }

    @Test
    @DisplayName("TC68 - authentication: missing user is rejected")
    void loadUserByUsername_unknownUser_isRejected() {
        when(userRepository.findByUsername("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userDetailsService.loadUserByUsername("unknown"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("unknown");
    }

    @Test
    @DisplayName("TC69 - authentication: administrator role is normalized")
    void loadUserByUsername_administratorRole_normalizesAuthority() {
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user("admin", true, "ROLE_ADMINISTRATOR")));

        UserDetails details = userDetailsService.loadUserByUsername("admin");

        assertThat(details.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_ADMIN");
    }

    private User user(String username, boolean active, String roleName) {
        Role role = new Role();
        role.setName(roleName);
        User user = new User();
        user.setUsername(username);
        user.setPassword("encoded");
        user.setIsActive(active);
        user.setRole(role);
        return user;
    }
}
