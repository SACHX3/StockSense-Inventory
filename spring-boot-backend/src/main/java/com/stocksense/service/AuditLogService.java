package com.stocksense.service;

import com.stocksense.entity.AuditLog;
import com.stocksense.entity.User;
import com.stocksense.repository.AuditLogRepository;
import com.stocksense.repository.UserRepository;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    public void log(String action, String entityType, Long entityId, String details) {
        try {
            String username = SecurityContextHolder.getContext().getAuthentication() != null
                    ? SecurityContextHolder.getContext().getAuthentication().getName() : "system";

            AuditLog log = new AuditLog();
            log.setAction(action);
            log.setEntityType(entityType);
            log.setEntityId(entityId);
            log.setNewValues(details);
            log.setUsername(username);

            Optional<User> user = userRepository.findByUsername(username);
            user.ifPresent(log::setUser);

            auditLogRepository.save(log);
        } catch (Exception e) {
            // Don't fail business operations due to audit log issues
        }
    }

    public void log(String action, String entityType, Long entityId, String oldValues, String newValues, HttpServletRequest request) {
        try {
            String username = SecurityContextHolder.getContext().getAuthentication() != null
                    ? SecurityContextHolder.getContext().getAuthentication().getName() : "system";

            AuditLog log = new AuditLog();
            log.setAction(action);
            log.setEntityType(entityType);
            log.setEntityId(entityId);
            log.setOldValues(oldValues);
            log.setNewValues(newValues);
            log.setUsername(username);

            if (request != null) {
                log.setIpAddress(getClientIP(request));
                log.setUserAgent(request.getHeader("User-Agent"));
            }

            Optional<User> user = userRepository.findByUsername(username);
            user.ifPresent(log::setUser);

            auditLogRepository.save(log);
        } catch (Exception e) {
            // Silent fail
        }
    }

    public Page<AuditLog> findAll(Pageable pageable) {
        return auditLogRepository.findAllByOrderByCreatedAtDesc(pageable);
    }

    private String getClientIP(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null) return request.getRemoteAddr();
        return xfHeader.split(",")[0];
    }
}
