package com.stocksense.service;

import com.stocksense.dto.request.UserRequest;
import com.stocksense.entity.Role;
import com.stocksense.entity.User;
import com.stocksense.repository.RoleRepository;
import com.stocksense.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;

    public List<User> findAll() {
        return userRepository.findAll();
    }

    public User findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found: " + id));
    }

    public User findByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));
    }

    @Transactional
    public User create(UserRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new RuntimeException("Username already exists");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already exists");
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setFullName(request.getFullName());
        user.setPhone(request.getPhone());

        Role role = roleRepository.findById(request.getRoleId())
                .orElseThrow(() -> new RuntimeException("Role not found"));
        user.setRole(role);

        User saved = userRepository.save(user);
        auditLogService.log("USER_CREATED", "User", saved.getId(), "Created user: " + saved.getUsername());
        return saved;
    }

    @Transactional
    public User update(Long id, UserRequest request) {
        User user = findById(id);

        if (!user.getUsername().equals(request.getUsername()) && userRepository.existsByUsername(request.getUsername())) {
            throw new RuntimeException("Username already exists");
        }
        if (!user.getEmail().equals(request.getEmail()) && userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already exists");
        }

        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setFullName(request.getFullName());
        user.setPhone(request.getPhone());

        if (request.getPassword() != null && !request.getPassword().isEmpty()) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }

        Role role = roleRepository.findById(request.getRoleId())
                .orElseThrow(() -> new RuntimeException("Role not found"));
        user.setRole(role);

        User saved = userRepository.save(user);
        auditLogService.log("USER_UPDATED", "User", saved.getId(), "Updated user: " + saved.getUsername());
        return saved;
    }

    @Transactional
    public void toggleStatus(Long id) {
        User user = findById(id);
        user.setIsActive(!user.getIsActive());
        userRepository.save(user);
    }

    /**
     * Permanently delete a user account.
     *
     * Three guards, in order:
     *  1. You cannot delete yourself - that would log you out of an account you
     *     can no longer sign back into.
     *  2. You cannot delete the last remaining active admin, which would lock
     *     every administrator out of the system with no way back in.
     *  3. Users are referenced by sales, inventory logs, invoices and audit logs.
     *     Deleting one that owns any history hits a foreign-key constraint, so we
     *     translate that into a message telling the operator to deactivate instead
     *     of showing them a raw SQL error.
     */
    @Transactional
    public void delete(Long id, String currentUsername) {
        User user = findById(id);

        if (currentUsername != null && currentUsername.equals(user.getUsername())) {
            throw new IllegalStateException("You cannot delete your own account.");
        }

        if (isAdmin(user) && Boolean.TRUE.equals(user.getIsActive()) && countActiveAdmins() <= 1) {
            throw new IllegalStateException(
                    "This is the last active administrator - deleting it would lock everyone out.");
        }

        String username = user.getUsername();
        try {
            userRepository.delete(user);
            userRepository.flush();   // force the FK check now, inside this try
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            throw new IllegalStateException(
                    "\"" + username + "\" has sales, stock or invoice history recorded against them "
                    + "and cannot be deleted. Deactivate the account instead - it keeps the history "
                    + "intact and blocks any further sign-in.");
        }
        auditLogService.log("USER_DELETED", "User", id, "Deleted user: " + username);
    }

    private boolean isAdmin(User user) {
        return user.getRole() != null && "ROLE_ADMIN".equals(user.getRole().getName());
    }

    private long countActiveAdmins() {
        return userRepository.findAll().stream()
                .filter(u -> isAdmin(u) && Boolean.TRUE.equals(u.getIsActive()))
                .count();
    }

    public List<Role> findAllRoles() {
        return roleRepository.findAll();
    }
}
