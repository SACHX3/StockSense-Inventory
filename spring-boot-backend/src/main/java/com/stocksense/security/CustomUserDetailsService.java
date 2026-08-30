package com.stocksense.security;

import com.stocksense.entity.User;
import com.stocksense.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        if (!user.getIsActive()) {
            throw new UsernameNotFoundException("User account is disabled: " + username);
        }

        String authority = normaliseAuthority(user.getRole().getName());

        return new org.springframework.security.core.userdetails.User(
                user.getUsername(),
                user.getPassword(),
                List.of(new SimpleGrantedAuthority(authority))
        );
    }

    private String normaliseAuthority(String roleName) {
        if (roleName == null || roleName.isBlank()) {
            throw new UsernameNotFoundException("User has no assigned role");
        }

        String role = roleName.trim().toUpperCase();
        if (!role.startsWith("ROLE_")) {
            role = "ROLE_" + role;
        }

        return switch (role) {
            case "ROLE_MANAGER", "ROLE_INVENTORYMANAGER" -> "ROLE_INVENTORY_MANAGER";
            case "ROLE_ADMINISTRATOR" -> "ROLE_ADMIN";
            default -> role;
        };
    }
}
