package com.stocksense.service;

import com.stocksense.dto.request.UserRequest;
import com.stocksense.entity.Role;
import com.stocksense.entity.User;
import com.stocksense.repository.RoleRepository;
import com.stocksense.repository.UserRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserService Tests")
class UserServiceTest {

    @Mock UserRepository userRepository;
    @Mock RoleRepository roleRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock AuditLogService auditLogService;
    @InjectMocks UserService userService;

    private UserRequest request;
    private Role role;
    private User user;

    @BeforeEach
    void setUp() {
        role = new Role();
        role.setId(1L);
        role.setName("STAFF");

        request = new UserRequest();
        request.setUsername("cashier01");
        request.setEmail("cashier01@example.com");
        request.setPassword("secret123");
        request.setFullName("Test Cashier");
        request.setPhone("0712345678");
        request.setRoleId(1L);

        user = new User();
        user.setId(5L);
        user.setUsername("cashier01");
        user.setEmail("cashier01@example.com");
        user.setPassword("encoded-old-password");
        user.setFullName("Test Cashier");
        user.setIsActive(true);
        user.setRole(role);
    }

    @Test
    @DisplayName("TC21 - create: encodes password, assigns role and writes audit log")
    void create_assignsRoleAndEncodesPassword() {
        when(userRepository.existsByUsername(request.getUsername())).thenReturn(false);
        when(userRepository.existsByEmail(request.getEmail())).thenReturn(false);
        when(passwordEncoder.encode("secret123")).thenReturn("encoded-secret");
        when(roleRepository.findById(1L)).thenReturn(Optional.of(role));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(5L);
            return saved;
        });

        User result = userService.create(request);

        assertThat(result.getPassword()).isEqualTo("encoded-secret");
        assertThat(result.getRole()).isSameAs(role);
        verify(auditLogService).log(eq("USER_CREATED"), eq("User"), eq(5L), contains("cashier01"));
    }

    @Test
    @DisplayName("TC22 - create: rejects duplicate username before saving")
    void create_duplicateUsername_rejectsWithoutSaving() {
        when(userRepository.existsByUsername(request.getUsername())).thenReturn(true);

        assertThatThrownBy(() -> userService.create(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Username already exists");
        verify(userRepository, never()).save(any());
        verifyNoInteractions(passwordEncoder, roleRepository);
    }

    @Test
    @DisplayName("TC23 - create: rejects duplicate email before saving")
    void create_duplicateEmail_rejectsWithoutSaving() {
        when(userRepository.existsByUsername(request.getUsername())).thenReturn(false);
        when(userRepository.existsByEmail(request.getEmail())).thenReturn(true);

        assertThatThrownBy(() -> userService.create(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Email already exists");
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("TC24 - create: rejects a missing role")
    void create_missingRole_rejectsWithoutSaving() {
        when(userRepository.existsByUsername(request.getUsername())).thenReturn(false);
        when(userRepository.existsByEmail(request.getEmail())).thenReturn(false);
        when(passwordEncoder.encode("secret123")).thenReturn("encoded-secret");
        when(roleRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.create(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Role not found");
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("TC25 - update: keeps existing password when no new password is supplied")
    void update_blankPassword_keepsExistingPassword() {
        request.setUsername("cashier01");
        request.setEmail("cashier01@example.com");
        request.setPassword("");
        when(userRepository.findById(5L)).thenReturn(Optional.of(user));
        when(roleRepository.findById(1L)).thenReturn(Optional.of(role));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User result = userService.update(5L, request);

        assertThat(result.getPassword()).isEqualTo("encoded-old-password");
        verify(passwordEncoder, never()).encode(anyString());
        verify(auditLogService).log(eq("USER_UPDATED"), eq("User"), eq(5L), anyString());
    }

    @Test
    @DisplayName("TC26 - update: rejects a changed username already used by another user")
    void update_duplicateUsername_rejectsWithoutSaving() {
        request.setUsername("existing-user");
        when(userRepository.findById(5L)).thenReturn(Optional.of(user));
        when(userRepository.existsByUsername("existing-user")).thenReturn(true);

        assertThatThrownBy(() -> userService.update(5L, request))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Username already exists");
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("TC27 - toggleStatus: switches active user to inactive")
    void toggleStatus_activeUser_becomesInactive() {
        when(userRepository.findById(5L)).thenReturn(Optional.of(user));

        userService.toggleStatus(5L);

        assertThat(user.getIsActive()).isFalse();
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("TC28 - findAllRoles: delegates to role repository")
    void findAllRoles_returnsRepositoryResults() {
        when(roleRepository.findAll()).thenReturn(List.of(role));

        assertThat(userService.findAllRoles()).containsExactly(role);
    }
}
