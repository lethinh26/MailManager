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
public interface UserRepository extends JpaRepository<User, Long>, UserLockRepository {
    @Query("SELECT u FROM User u WHERE LOWER(u.username) = LOWER(:username)")
    Optional<User> findByUsername(@Param("username") String username);
    Optional<User> findByEmailIgnoreCase(String email);
    @Query("SELECT CASE WHEN COUNT(u) > 0 THEN true ELSE false END FROM User u WHERE LOWER(u.username) = LOWER(:username)")
    boolean existsByUsername(@Param("username") String username);
    boolean existsByEmailIgnoreCase(String email);
    boolean existsByRole(Role role);

    long countByRole(Role role);
    long countByRoleAndEnabledTrue(Role role);
    long countByEnabledTrue();
    long countByEnabledFalse();
    long countByCreatedAtAfter(LocalDateTime since);

    // Scalar locking read deliberately bypasses any stale authenticated User in the persistence context.
    @Query(value = "SELECT id FROM users WHERE role = 'ADMIN' AND enabled = true ORDER BY id FOR UPDATE", nativeQuery = true)
    java.util.List<Long> lockActiveAdminIds();

    // Update only profile metadata; do not merge an authenticated User snapshot.
    @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true)
    @org.springframework.transaction.annotation.Transactional
    @Query("UPDATE User u SET u.fullName = :name, u.updatedAt = :now WHERE u.id = :id AND (u.fullName IS NULL OR TRIM(u.fullName) = '')")
    int fillMissingFullName(@Param("id") Long id, @Param("name") String name, @Param("now") LocalDateTime now);

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
