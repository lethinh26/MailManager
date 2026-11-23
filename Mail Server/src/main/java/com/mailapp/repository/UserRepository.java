package com.mailapp.repository;

import com.mailapp.model.Role;
import com.mailapp.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    Optional<User> findByEmailIgnoreCase(String email);
    boolean existsByUsername(String username);
    boolean existsByEmailIgnoreCase(String email);
    boolean existsByRole(Role role);

    long countByRole(Role role);
    long countByRoleAndEnabledTrue(Role role);
    long countByEnabledTrue();
    long countByEnabledFalse();
    long countByCreatedAtAfter(LocalDateTime since);

    @Query("""
            SELECT u FROM User u
            WHERE (:q IS NULL OR :q = ''
                   OR LOWER(u.username) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(COALESCE(u.fullName, '')) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(COALESCE(u.email, '')) LIKE LOWER(CONCAT('%', :q, '%')))
              AND (:role IS NULL OR u.role = :role)
              AND (:enabled IS NULL OR u.enabled = :enabled)
            """)
    Page<User> searchAdminUsers(@Param("q") String q,
                                @Param("role") Role role,
                                @Param("enabled") Boolean enabled,
                                Pageable pageable);
}
