# Java Mail App — Project Final

Môn: Lập trình mạng | 6 thành viên | 3 tuần (26/10 → 15/11)

## Cấu trúc

```
.
├── architechture/docs/   # Spec + phân công + task files
├── Mail Server/          # Spring Boot 3 backend (port 8443, HTTPS)
└── Mail Client/          # Java Swing client
```

## Yêu cầu

- JDK 21+ (Corretto / Temurin / Oracle đều được)
- MySQL 8
- Gradle wrapper (tự tải)

## Chạy

```bash
# Server (cần MySQL + keystore.p12 + env vars, xem application.properties)
cd "Mail Server"
./gradlew bootRun

# Client
cd "Mail Client"
./gradlew run
```

## Docs

- `architechture/docs/01-thiet-ke-tong-the.md` — kiến trúc
- `architechture/docs/02-danh-sach-endpoint-va-phan-cong.md` — API + phân công
- `architechture/docs/03-task-breakdown.md` — timeline 3 tuần

Backlog Lark: https://msgmclt9kdj2.sg.larksuite.com/base/CVR2bm043ahjzCs1ohHltC9Zgcg
