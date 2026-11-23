#!/bin/bash
set -e

# Nếu KEYSTORE_PASSWORD chưa có, sinh ngẫu nhiên an toàn cho container
if [ -z "$KEYSTORE_PASSWORD" ]; then
    export KEYSTORE_PASSWORD=$(head /dev/urandom | tr -dc A-Za-z0-9 | head -c 24)
fi

mkdir -p /app/certs

# Tự động sinh keystore SSL PKCS12 tự ký nếu chưa có (ưu tiên keystore gắn từ ./certs trên host)
if [ ! -f /app/certs/keystore.p12 ]; then
    echo "[INFO] Đang tự động sinh keystore SSL PKCS12 tự ký tại /app/certs/keystore.p12..."
    keytool -genkeypair -alias mailapp -keyalg RSA -keysize 2048 -storetype PKCS12 \
        -keystore /app/certs/keystore.p12 -validity 365 \
        -storepass "$KEYSTORE_PASSWORD" -keypass "$KEYSTORE_PASSWORD" \
        -dname "CN=MailApp Server, OU=PTIT, O=FinalProject, L=Hanoi, C=VN" \
        -ext "SAN=dns:localhost,ip:127.0.0.1,dns:mailapp-server"
    keytool -exportcert -alias mailapp -keystore /app/certs/keystore.p12 \
        -storetype PKCS12 -storepass "$KEYSTORE_PASSWORD" -rfc -file /app/certs/mailapp.cer
fi

exec java -Djava.security.egd=file:/dev/./urandom \
    -Dserver.ssl.key-store=file:/app/certs/keystore.p12 \
    -Dserver.ssl.key-store-password="$KEYSTORE_PASSWORD" \
    -jar app.jar "$@"

