package com.mailapp.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Cấu hình SpringDoc OpenAPI 3 (Swagger UI).
 * Khai báo lược đồ bảo mật Bearer JWT Token cho toàn bộ API.
 * Chỉ kích hoạt trong môi trường phát triển (profile dev).
 *
 * @author Lê Thanh Cường
 */
@Configuration
@Profile("dev")
public class OpenApiConfig {

    private static final String SECURITY_SCHEME_NAME = "BearerAuth";

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Veyra Mail API Documentation")
                        .version("1.0.0")
                        .description("Tài liệu đặc tả các REST API cho hệ thống Veyra Mail an toàn qua 2 lớp TLS (PTIT Final Project).")
                        .contact(new Contact().name("Veyra Mail Team (PTIT)")))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME, new SecurityScheme()
                                .name(SECURITY_SCHEME_NAME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Nhập JWT Access Token nhận được từ API đăng nhập (EP-A2).")));
    }
}

