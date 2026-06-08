package com.voyager.docs.repository;

import com.voyager.docs.domain.AppUser;
import com.voyager.docs.domain.UserRole;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByUsername(String username);

    boolean existsByUsername(String username);

    boolean existsByUsernameAndIdNot(String username, Long id);

    List<AppUser> findByRoleOrderByCreatedAtDesc(UserRole role);
}
