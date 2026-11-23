package com.mailapp.repository;

import com.mailapp.model.SystemSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SystemSettingRepository extends JpaRepository<SystemSetting, String> {
    @Modifying
    @Query("UPDATE SystemSetting s SET s.updatedBy = null WHERE s.updatedBy.id = :userId")
    void detachUpdatedBy(@Param("userId") Long userId);
}
