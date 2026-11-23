package com.mailapp.repository;

import com.mailapp.model.AccountStatus;
import com.mailapp.model.AuthType;
import com.mailapp.model.GmailAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GmailAccountRepository extends JpaRepository<GmailAccount, Long> {
    List<GmailAccount> findByUserId(Long userId);
    Optional<GmailAccount> findByUserIdAndEmail(Long userId, String email);
    Optional<GmailAccount> findByUserIdAndEmailKey(Long userId, String emailKey);
    Optional<GmailAccount> findByIdAndUserId(Long id, Long userId);
    long countByUserId(Long userId);
    long countByAuthType(AuthType authType);
    long countByStatus(AccountStatus status);
    boolean existsByUserIdAndEmail(Long userId, String email);
    boolean existsByUserIdAndEmailKey(Long userId, String emailKey);
    void deleteByUserIdAndId(Long userId, Long id);
    void deleteByUserId(Long userId);
}
