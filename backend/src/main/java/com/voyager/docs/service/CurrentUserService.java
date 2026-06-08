package com.voyager.docs.service;

import com.voyager.docs.domain.AppUser;
import com.voyager.docs.repository.AppUserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class CurrentUserService {
    private final AppUserRepository users;

    public CurrentUserService(AppUserRepository users) {
        this.users = users;
    }

    public AppUser requireCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            throw new EntityNotFoundException("Current user not found");
        }
        AppUser user = users.findByUsername(authentication.getName())
                .orElseThrow(() -> new EntityNotFoundException("Current user not found"));
        if (!user.isEnabled() || user.getDeletedAt() != null) {
            throw new AccessDeniedException("Current user is disabled");
        }
        return user;
    }
}
