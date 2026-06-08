package com.voyager.docs.service;

import com.voyager.docs.domain.AppUser;
import com.voyager.docs.domain.AuditEvent;
import com.voyager.docs.dto.AuditDtos;
import com.voyager.docs.repository.AppUserRepository;
import com.voyager.docs.repository.AuditEventRepository;
import jakarta.persistence.criteria.Predicate;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
public class AuditService {
    private final AuditEventRepository auditEvents;
    private final AppUserRepository users;

    public AuditService(AuditEventRepository auditEvents, AppUserRepository users) {
        this.auditEvents = auditEvents;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public AuditDtos.AuditEventPageResponse list(
            String actor,
            String action,
            Boolean success,
            Instant from,
            Instant to,
            int page,
            int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, 100));
        Page<AuditEvent> result = auditEvents.findAll(
                specification(actor, action, success, from, to),
                PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt")));
        return new AuditDtos.AuditEventPageResponse(
                result.getContent().stream().map(this::toResponse).toList(),
                result.getTotalElements(),
                safePage,
                safeSize);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordCurrent(String action, String resourceType, String resourceId, boolean success, String summary) {
        try {
            saveEvent(currentActor(), null, action, resourceType, resourceId, success, summary);
        } catch (RuntimeException ignored) {
            // Auditing should never break the user-facing operation.
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordForUser(AppUser actor, String action, String resourceType, String resourceId, boolean success, String summary) {
        try {
            saveEvent(actor, null, action, resourceType, resourceId, success, summary);
        } catch (RuntimeException ignored) {
            // Auditing should never break scheduled/background work.
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordAnonymous(String actorUsername, String action, boolean success, String summary) {
        try {
            saveEvent(null, actorUsername, action, "AUTH", actorUsername, success, summary);
        } catch (RuntimeException ignored) {
            // Login auditing must not mask authentication results.
        }
    }

    public String currentIpAddress() {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return "system";
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            return trim(forwarded.split(",", 2)[0].trim(), 100);
        }
        return trim(request.getRemoteAddr(), 100);
    }

    private void saveEvent(
            AppUser actor,
            String anonymousUsername,
            String action,
            String resourceType,
            String resourceId,
            boolean success,
            String summary) {
        AuditEvent event = new AuditEvent();
        event.setActor(actor);
        event.setActorUsername(actor == null ? trim(anonymousUsername, 80) : trim(actor.getUsername(), 80));
        event.setActorRole(actor == null ? null : actor.getRole().name());
        event.setIpAddress(currentIpAddress());
        event.setUserAgent(userAgent());
        event.setAction(trim(action, 100));
        event.setResourceType(trim(resourceType, 80));
        event.setResourceId(trim(resourceId, 160));
        event.setSuccess(success);
        event.setSummary(trim(summary, 4000));
        auditEvents.save(event);
    }

    private AppUser currentActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || !StringUtils.hasText(authentication.getName())) {
            return null;
        }
        return users.findByUsername(authentication.getName()).orElse(null);
    }

    private String userAgent() {
        HttpServletRequest request = currentRequest();
        return request == null ? null : trim(request.getHeader("User-Agent"), 500);
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }

    private Specification<AuditEvent> specification(
            String actor,
            String action,
            Boolean success,
            Instant from,
            Instant to) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (StringUtils.hasText(actor)) {
                String like = "%" + actor.trim().toLowerCase() + "%";
                predicates.add(builder.like(builder.lower(root.get("actorUsername")), like));
            }
            if (StringUtils.hasText(action)) {
                predicates.add(builder.equal(root.get("action"), action.trim()));
            }
            if (success != null) {
                predicates.add(builder.equal(root.get("success"), success));
            }
            if (from != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("createdAt"), from));
            }
            if (to != null) {
                predicates.add(builder.lessThanOrEqualTo(root.get("createdAt"), to));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private AuditDtos.AuditEventResponse toResponse(AuditEvent event) {
        return new AuditDtos.AuditEventResponse(
                event.getId(),
                event.getActor() == null ? null : event.getActor().getId(),
                event.getActorUsername(),
                event.getActorRole(),
                event.getIpAddress(),
                event.getUserAgent(),
                event.getAction(),
                event.getResourceType(),
                event.getResourceId(),
                event.isSuccess(),
                event.getSummary(),
                DateTimeFormatter.ISO_INSTANT.format(event.getCreatedAt()));
    }

    private String trim(String value, int max) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() > max ? trimmed.substring(0, max) : trimmed;
    }
}
