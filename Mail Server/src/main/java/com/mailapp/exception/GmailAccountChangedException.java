package com.mailapp.exception;

import org.springframework.http.HttpStatus;

/** A stale account snapshot cannot safely update the current mailbox. */
public final class GmailAccountChangedException extends AppException {
    public GmailAccountChangedException() {
        super(HttpStatus.CONFLICT, "GMAIL_ACCOUNT_CHANGED",
                "Liên kết Gmail đã thay đổi trong lúc xử lý. Vui lòng tải lại thông tin hộp thư.");
    }
}
