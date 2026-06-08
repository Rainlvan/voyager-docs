package com.voyager.docs.service;

import com.voyager.docs.domain.AppUser;
import com.voyager.docs.dto.AuthDtos;
import com.voyager.docs.repository.AppUserRepository;
import com.voyager.docs.security.JwtService;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private final AuthenticationManager authenticationManager;
    private final AppUserRepository users;
    private final JwtService jwtService;
    private final LoginRateLimitService loginRateLimitService;
    private final AuditService auditService;

    public AuthService(
            AuthenticationManager authenticationManager,
            AppUserRepository users,
            JwtService jwtService,
            LoginRateLimitService loginRateLimitService,
            AuditService auditService) {
        this.authenticationManager = authenticationManager;
        this.users = users;
        this.jwtService = jwtService;
        this.loginRateLimitService = loginRateLimitService;
        this.auditService = auditService;
    }

    public AuthDtos.LoginResponse login(AuthDtos.LoginRequest request) {
        String username = request.username().trim();
        try {
            loginRateLimitService.assertAllowed(username);
        } catch (LoginRateLimitException exception) {
            auditService.recordAnonymous(username, "LOGIN_RATE_LIMITED", false, exception.getMessage());
            throw exception;
        }
        try {
            AppUser user = users.findByUsername(username)
                    .filter(candidate -> candidate.getDeletedAt() == null)
                    .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));
            if (!user.isEnabled()) {
                throw new BadCredentialsException("User disabled");
            }
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, request.password()));
            loginRateLimitService.clear(username);
            auditService.recordForUser(user, "LOGIN_SUCCESS", "AUTH", username, true, "User logged in");
            return new AuthDtos.LoginResponse(jwtService.issue(user), toUserResponse(user));
        } catch (AuthenticationException exception) {
            loginRateLimitService.recordFailure(username);
            auditService.recordAnonymous(username, "LOGIN_FAILURE", false, "Invalid username or password");
            throw exception;
        } catch (RuntimeException exception) {
            if (!(exception instanceof LoginRateLimitException)) {
                loginRateLimitService.recordFailure(username);
                auditService.recordAnonymous(username, "LOGIN_FAILURE", false, "Invalid username or password");
            }
            throw exception;
        }
    }

    public AuthDtos.UserResponse toUserResponse(AppUser user) {
        return new AuthDtos.UserResponse(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getRole().name(),
                user.isEnabled(),
                user.getDeletedAt() != null,
                user.getAvatarObjectKey() == null ? null : "/api/account/avatar/" + user.getId());
    }
}
