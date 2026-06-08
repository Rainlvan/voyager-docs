package com.voyager.docs.service;

import com.voyager.docs.domain.AppUser;
import com.voyager.docs.domain.UserRole;
import com.voyager.docs.dto.AuthDtos;
import com.voyager.docs.dto.UserDtos;
import com.voyager.docs.repository.AppUserRepository;
import com.voyager.docs.security.JwtService;
import jakarta.persistence.EntityNotFoundException;
import java.io.InputStream;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AccountService {
    private static final long MAX_AVATAR_BYTES = 5L * 1024L * 1024L;
    private static final List<String> AVATAR_CONTENT_TYPES = List.of("image/png", "image/jpeg", "image/webp");

    private final AppUserRepository users;
    private final CurrentUserService currentUserService;
    private final AuthService authService;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final StorageService storage;
    private final DocumentService documentService;
    private final MaintenanceService maintenanceService;
    private final AuditService auditService;

    public AccountService(
            AppUserRepository users,
            CurrentUserService currentUserService,
            AuthService authService,
            JwtService jwtService,
            PasswordEncoder passwordEncoder,
            StorageService storage,
            DocumentService documentService,
            MaintenanceService maintenanceService,
            AuditService auditService) {
        this.users = users;
        this.currentUserService = currentUserService;
        this.authService = authService;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
        this.storage = storage;
        this.documentService = documentService;
        this.maintenanceService = maintenanceService;
        this.auditService = auditService;
    }

    @Transactional
    public AuthDtos.UserResponse uploadAvatar(MultipartFile file) {
        maintenanceService.requireAvailableForWrites();
        AppUser user = currentUserService.requireCurrentUser();
        validateAvatar(file);
        String oldObjectKey = user.getAvatarObjectKey();
        String objectKey = "avatars/" + user.getId() + "/" + UUID.randomUUID() + extensionFor(file.getContentType());
        try {
            storage.put(objectKey, file.getInputStream(), file.getSize(), file.getContentType());
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to upload avatar", exception);
        }
        user.setAvatarObjectKey(objectKey);
        user.setAvatarContentType(file.getContentType());
        AppUser saved = users.save(user);
        if (StringUtils.hasText(oldObjectKey)) {
            removeObjectQuietly(oldObjectKey);
        }
        auditService.recordForUser(saved, "ACCOUNT_AVATAR_UPDATE", "USER", saved.getId().toString(), true,
                "Updated avatar");
        return authService.toUserResponse(saved);
    }

    @Transactional(readOnly = true)
    public AvatarObject readAvatar(Long userId) {
        currentUserService.requireCurrentUser();
        AppUser user = users.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));
        if (!StringUtils.hasText(user.getAvatarObjectKey())) {
            throw new EntityNotFoundException("Avatar not found");
        }
        InputStream stream = storage.get(user.getAvatarObjectKey());
        return new AvatarObject(stream, user.getAvatarContentType());
    }

    @Transactional
    public AuthDtos.LoginResponse updateAdminProfile(AuthDtos.UpdateAdminProfileRequest request) {
        maintenanceService.requireAvailableForWrites();
        AppUser admin = requireAdmin();
        String username = request.username().trim();
        String displayName = request.displayName().trim();
        if (users.existsByUsernameAndIdNot(username, admin.getId())) {
            throw new IllegalArgumentException("Username already exists");
        }
        if (StringUtils.hasText(request.newPassword())) {
            if (!StringUtils.hasText(request.currentPassword())
                    || !passwordEncoder.matches(request.currentPassword(), admin.getPasswordHash())) {
                throw new BadCredentialsException("Current password is incorrect");
            }
            admin.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        }
        admin.setUsername(username);
        admin.setDisplayName(displayName);
        AppUser saved = users.save(admin);
        auditService.recordForUser(saved, "ADMIN_PROFILE_UPDATE", "USER", saved.getId().toString(), true,
                "Updated admin profile");
        return new AuthDtos.LoginResponse(jwtService.issue(saved), authService.toUserResponse(saved));
    }

    @Transactional(readOnly = true)
    public List<UserDtos.ManagedUserResponse> listEmployees() {
        requireAdmin();
        return users.findByRoleOrderByCreatedAtDesc(UserRole.USER).stream()
                .map(this::toManagedUserResponse)
                .toList();
    }

    @Transactional
    public UserDtos.ManagedUserResponse createEmployee(UserDtos.CreateUserRequest request) {
        maintenanceService.requireAvailableForWrites();
        requireAdmin();
        String username = request.username().trim();
        if (users.existsByUsername(username)) {
            throw new IllegalArgumentException("Username already exists");
        }
        AppUser user = new AppUser();
        user.setUsername(username);
        user.setDisplayName(request.displayName().trim());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(UserRole.USER);
        user.setEnabled(true);
        AppUser saved = users.save(user);
        auditService.recordCurrent("USER_CREATE", "USER", saved.getId().toString(), true,
                "Created employee " + saved.getUsername());
        return toManagedUserResponse(saved);
    }

    @Transactional
    public UserDtos.ManagedUserResponse updateEmployee(Long id, UserDtos.UpdateUserRequest request) {
        maintenanceService.requireAvailableForWrites();
        requireAdmin();
        AppUser user = requireActiveEmployee(id);
        String username = request.username().trim();
        if (users.existsByUsernameAndIdNot(username, user.getId())) {
            throw new IllegalArgumentException("Username already exists");
        }
        user.setUsername(username);
        user.setDisplayName(request.displayName().trim());
        user.setEnabled(request.enabled());
        if (StringUtils.hasText(request.password())) {
            user.setPasswordHash(passwordEncoder.encode(request.password()));
        }
        AppUser saved = users.save(user);
        auditService.recordCurrent("USER_UPDATE", "USER", saved.getId().toString(), true,
                "Updated employee " + saved.getUsername());
        return toManagedUserResponse(saved);
    }

    @Transactional
    public UserDtos.ManagedUserResponse setEmployeeEnabled(Long id, UserDtos.EnabledRequest request) {
        maintenanceService.requireAvailableForWrites();
        requireAdmin();
        AppUser user = requireActiveEmployee(id);
        user.setEnabled(request.enabled());
        AppUser saved = users.save(user);
        auditService.recordCurrent(request.enabled() ? "USER_ENABLE" : "USER_DISABLE", "USER", saved.getId().toString(), true,
                "Changed employee enabled state");
        return toManagedUserResponse(saved);
    }

    @Transactional
    public void deleteEmployee(Long id) {
        maintenanceService.requireAvailableForWrites();
        AppUser admin = requireAdmin();
        AppUser user = requireActiveEmployee(id);
        user.setEnabled(false);
        user.setDeletedAt(Instant.now());
        users.save(user);
        documentService.recycleDocumentsByUser(user, admin);
        auditService.recordForUser(admin, "USER_DELETE", "USER", user.getId().toString(), true,
                "Soft deleted employee " + user.getUsername());
    }

    private void validateAvatar(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Avatar file must not be empty");
        }
        if (file.getSize() > MAX_AVATAR_BYTES) {
            throw new IllegalArgumentException("Avatar file must be 5MB or smaller");
        }
        if (!AVATAR_CONTENT_TYPES.contains(file.getContentType())) {
            throw new IllegalArgumentException("Avatar must be PNG, JPG, or WebP");
        }
    }

    private AppUser requireAdmin() {
        AppUser user = currentUserService.requireCurrentUser();
        if (user.getRole() != UserRole.ADMIN) {
            throw new AccessDeniedException("Admin permission required");
        }
        return user;
    }

    private AppUser requireActiveEmployee(Long id) {
        AppUser user = users.findById(id).orElseThrow(() -> new EntityNotFoundException("Employee not found"));
        if (user.getRole() != UserRole.USER || user.getDeletedAt() != null) {
            throw new EntityNotFoundException("Employee not found");
        }
        return user;
    }

    private UserDtos.ManagedUserResponse toManagedUserResponse(AppUser user) {
        return new UserDtos.ManagedUserResponse(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getRole().name(),
                user.isEnabled(),
                user.getDeletedAt() != null,
                user.getAvatarObjectKey() == null ? null : "/api/account/avatar/" + user.getId(),
                DateTimeFormatter.ISO_INSTANT.format(user.getCreatedAt()),
                DateTimeFormatter.ISO_INSTANT.format(user.getUpdatedAt()),
                user.getDeletedAt() == null ? null : DateTimeFormatter.ISO_INSTANT.format(user.getDeletedAt()));
    }

    private String extensionFor(String contentType) {
        return switch (contentType) {
            case "image/png" -> ".png";
            case "image/jpeg" -> ".jpg";
            case "image/webp" -> ".webp";
            default -> "";
        };
    }

    private void removeObjectQuietly(String objectKey) {
        try {
            storage.remove(objectKey);
        } catch (RuntimeException ignored) {
            // Old avatar cleanup should not fail the profile update.
        }
    }

    public record AvatarObject(InputStream stream, String contentType) {
    }
}
