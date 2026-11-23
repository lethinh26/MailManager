# ==============================================================================
# Multi-stage Dockerfile cho Mail Server (Java 21, Spring Boot 3.3.5)
# Stage 1: Build artifact bằng Gradle và JDK 21
# Stage 2: Runtime image nhẹ JRE 21, user non-root
# ==============================================================================

# ------------------------------------------------------------------------------
# Stage 1: Build artifact
# ------------------------------------------------------------------------------
FROM eclipse-temurin:21-jdk-jammy AS builder

WORKDIR /build

# Copy Gradle wrapper và cấu hình dependencies để tận dụng Docker layer caching
COPY gradlew .
COPY gradle ./gradle
COPY build.gradle settings.gradle ./

# Chuẩn hóa xuống dòng LF và cấp quyền thực thi cho gradlew
RUN sed -i 's/\r$//' gradlew && chmod +x ./gradlew

# Tải dependencies trước
RUN ./gradlew dependencies --no-daemon || true

# Copy toàn bộ mã nguồn
COPY src ./src

# Build file JAR (bỏ qua test để tăng tốc độ đóng gói image)
RUN ./gradlew bootJar -x test --no-daemon

# ------------------------------------------------------------------------------
# Stage 2: Runtime image an toàn
# ------------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-jammy AS runner

# Tạo group và user non-root để chạy ứng dụng an toàn
RUN groupadd -r mailapp && useradd -r -g mailapp -m -s /bin/bash mailapp

WORKDIR /app

# Copy tệp JAR từ builder stage và entrypoint script
COPY --from=builder /build/build/libs/*.jar app.jar
COPY entrypoint.sh /app/entrypoint.sh

# Cấp quyền thực thi và sở hữu cho mailapp user
RUN sed -i 's/\r$//' /app/entrypoint.sh && chmod +x /app/entrypoint.sh \
    && mkdir -p /app/logs && chown -R mailapp:mailapp /app

# Cài đặt curl phục vụ healthcheck trong container
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*

# Chuyển sang user non-root
USER mailapp:mailapp

# Khai báo cổng HTTPS 8443
EXPOSE 8443

# Cấu hình Healthcheck định kỳ
HEALTHCHECK --interval=30s --timeout=5s --start-period=20s --retries=3 \
  CMD curl -k -f https://localhost:8443/actuator/health || exit 1

ENTRYPOINT ["/app/entrypoint.sh"]

