package com.mailapp.repository;

import com.mailapp.model.SystemSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SystemSettingRepository extends JpaRepository<SystemSetting, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM SystemSetting s WHERE s.key IN ('ALLOW_OAUTH2_LINK', 'ALLOW_APP_PASSWORD_LINK') ORDER BY s.key")
    java.util.List<SystemSetting> lockLinkSettings();

    @Modifying
    @Query("UPDATE SystemSetting s SET s.updatedBy = null WHERE s.updatedBy.id = :userId")
    void detachUpdatedBy(@Param("userId") Long userId);
}
