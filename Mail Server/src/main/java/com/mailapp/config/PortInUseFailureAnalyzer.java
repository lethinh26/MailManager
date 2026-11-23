package com.mailapp.config;

import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;
import org.springframework.boot.web.server.PortInUseException;

/**
 * Phân tích lỗi khi cổng lắng nghe (8443) bị chiếm.
 * Thay vì xuất ra chuỗi stack trace khó hiểu, xuất hướng dẫn xử lý trực tiếp.
 */
public class PortInUseFailureAnalyzer extends AbstractFailureAnalyzer<PortInUseException> {

    @Override
    protected FailureAnalysis analyze(Throwable rootFailure, PortInUseException cause) {
        return new FailureAnalysis(
                "Cổng " + cause.getPort() + " đang bị chiếm, chạy docker compose stop mail-server",
                "Chạy 'docker compose stop mail-server' trong thư mục 'Mail Server' hoặc thay đổi biến SERVER_PORT",
                cause
        );
    }
}

