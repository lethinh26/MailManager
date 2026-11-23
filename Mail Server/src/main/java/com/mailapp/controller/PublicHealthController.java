package com.mailapp.controller;

import com.mailapp.dto.PublicConfigResponse;
import com.mailapp.service.SystemSettingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Controller cung cấp các endpoint công khai không yêu cầu xác thực:
 * - GET /api/public/health (Kiểm tra sức khỏe hệ thống và cơ sở dữ liệu)
 * - GET /api/public/config (EP-P1: Lấy các cờ cấu hình và giới hạn công khai an toàn)
 *
 * @author Lê Thanh Cường
 */
@RestController
@RequestMapping("/api/public")
@Tag(name = "Public", description = "Các endpoint công khai dành cho client và hạ tầng")
public class PublicHealthController {

    private final DataSource dataSource;
    private final SystemSettingService systemSettingService;

    public PublicHealthController(DataSource dataSource, SystemSettingService systemSettingService) {
        this.dataSource = dataSource;
        this.systemSettingService = systemSettingService;
    }

    /**
     * Endpoint kiểm tra sức khỏe công khai.
     * Response: { "status": "UP", "components": { "db": { "status": "UP" } } }
     */
    @GetMapping("/health")
    @Operation(summary = "Kiểm tra sức khỏe công khai", description = "Trả về trạng thái máy chủ và kết nối database.")
    public ResponseEntity<Map<String, Object>> getHealth() {
        boolean dbUp = isDatabaseUp();
        String overallStatus = dbUp ? "UP" : "DOWN";

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", overallStatus);

        Map<String, Object> components = new LinkedHashMap<>();
        components.put("db", Map.of("status", dbUp ? "UP" : "DOWN"));
        body.put("components", components);

        return dbUp
                ? ResponseEntity.ok(body)
                : ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }

    /**
     * [EP-P1] Lấy các cờ cấu hình hệ thống và giới hạn công khai an toàn.
     */
    @GetMapping("/config")
    @Operation(summary = "Cấu hình công khai (EP-P1)", description = "Trả về trạng thái các tính năng cho phép và giới hạn an toàn. Tuyệt đối không lộ secret.")
    public ResponseEntity<PublicConfigResponse> getPublicConfig() {
        boolean regEnabled = systemSettingService.isRegistrationEnabled();
        boolean oauthEnabled = systemSettingService.isOAuth2LinkEnabled();
        boolean appPassEnabled = systemSettingService.isAppPasswordLinkEnabled();
        boolean maintenance = systemSettingService.isMaintenanceMode();
        boolean sendMailEnabled = systemSettingService.isSendMailEnabled();
        int maxGmail = systemSettingService.getMaxGmailPerUser();
        int maxAttachmentMb = systemSettingService.getMaxAttachmentSizeMb();

        PublicConfigResponse config = new PublicConfigResponse(
                regEnabled,
                oauthEnabled,
                appPassEnabled,
                maintenance,
                sendMailEnabled,
                maxGmail,
                maxAttachmentMb
        );

        return ResponseEntity.ok(config);
    }

    private boolean isDatabaseUp() {
        try (Connection conn = dataSource.getConnection()) {
            return conn.isValid(2);
        } catch (Exception e) {
            return false;
        }
    }
}
