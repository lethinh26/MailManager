package com.mailapp.controller;

import com.mailapp.dto.mail.MailDtos.*;
import com.mailapp.exception.AppException;
import com.mailapp.exception.GlobalExceptionHandler;
import com.mailapp.exception.RateLimitException;
import com.mailapp.mail.service.MailService;
import com.mailapp.model.Role;
import com.mailapp.model.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Exercises wire shapes with the MVC stack, no Spring Boot context, sockets, or Gmail. */
class MailControllerCompatibilityTest {
    private final MailService service = mock(MailService.class);
    private final User user = new User(1L, "user", "user@example.test", Role.USER);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of()));
        mvc = MockMvcBuilders.standaloneSetup(new MailController(service))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void listsBothCurrentAndOriginalPaginationFields() throws Exception {
        MailSummaryDto item = new MailSummaryDto(7L, 9L, new MailAddressDto("Sender", "sender@example.test"),
                List.of(), "Subject", "", "2026-11-26T00:00:00Z",
                new MailFlagsDto(true, false, false, false), 10L, true, 1);
        when(service.listMails(user, 2L, "INBOX", 0, 0, null, null, null, null, null))
                .thenReturn(new MailListResponse(List.of(item), 0, 20, 41, true, 9L));
        mvc.perform(get("/api/mails").param("gmailAccountId", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].uid").value(7))
                .andExpect(jsonPath("$.items[0].flags.seen").value(true))
                .andExpect(jsonPath("$.items[0].isRead").value(true))
                .andExpect(jsonPath("$.content[0].uid").value(7))
                .andExpect(jsonPath("$.content[0].from.address").value("sender@example.test"))
                .andExpect(jsonPath("$.content[0].date").value("2026-11-26T00:00:00Z"))
                .andExpect(jsonPath("$.content[0].isRead").value(true))
                .andExpect(jsonPath("$.total").value(41))
                .andExpect(jsonPath("$.totalElements").value(41))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.hasMore").value(true))
                .andExpect(jsonPath("$.uidValidity").value(9));
    }

    @Test
    void detailExposesBothBodyAndAttachmentNames() throws Exception {
        MailDetailResponse detail = new MailDetailResponse(7L, 9L, new MailAddressDto("Sender", "sender@example.test"),
                List.of(), List.of(), List.of(), List.of(), "2026-11-26T00:00:00Z",
                "<message@example.test>", null, null, "Subject", "plain", "<b>html</b>", false, false,
                List.of(new MailAttachmentDto(0, "report.pdf", "application/pdf", 22L, false, null)),
                new MailFlagsDto(true, false, false, false));
        when(service.getMailDetail(user, 2L, "INBOX", 7L, false, 9L)).thenReturn(detail);
        mvc.perform(get("/api/mails/2/INBOX/7").param("uidValidity", "9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.textBody").value("plain"))
                .andExpect(jsonPath("$.bodyText").value("plain"))
                .andExpect(jsonPath("$.htmlBody").value("<b>html</b>"))
                .andExpect(jsonPath("$.bodyHtml").value("<b>html</b>"))
                .andExpect(jsonPath("$.attachments[0].filename").value("report.pdf"))
                .andExpect(jsonPath("$.attachments[0].fileName").value("report.pdf"))
                .andExpect(jsonPath("$.attachments[0].size").value(22))
                .andExpect(jsonPath("$.attachments[0].sizeBytes").value(22))
                .andExpect(jsonPath("$.attachments[0].sizeAccuracy").value("ESTIMATED"));
    }

    @Test
    void previewQueryDelegatesToReadOnlyDetailOverload() throws Exception {
        MailDetailResponse detail = new MailDetailResponse(7L, 9L, null,
                List.of(), List.of(), List.of(), List.of(), null,
                null, null, null, "Subject", "plain", null, false, false,
                List.of(), new MailFlagsDto(false, false, false, false));
        when(service.getMailDetail(user, 2L, "INBOX", 7L, false, 9L, false)).thenReturn(detail);
        mvc.perform(get("/api/mails/2/INBOX/7").param("uidValidity", "9").param("markSeen", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flags.seen").value(false));
        verify(service).getMailDetail(user, 2L, "INBOX", 7L, false, 9L, false);
        verify(service, never()).getMailDetail(user, 2L, "INBOX", 7L, false, 9L);
    }

    @Test
    void sendsCurrentJsonMultipartIncludingEmptyNamedAttachment() throws Exception {
        MockMultipartFile payload = payload("{\"gmailId\":2,\"to\":[\"to@example.test\"],\"subject\":\"New\",\"textBody\":\"hello\"}");
        MockMultipartFile file = new MockMultipartFile("attachments", "empty.txt", "text/plain", new byte[0]);
        when(service.sendMail(eq(user), any(SendMailRequest.class), anyList(), anyString()))
                .thenReturn(new SendMailResponse("<id@example.test>", "SENT"));
        mvc.perform(multipart("/api/mails/send").file(payload).file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messageId").value("<id@example.test>"))
                .andExpect(jsonPath("$.status").value("SENT"))
                .andExpect(jsonPath("$.message").value("Sent"))
                .andExpect(jsonPath("$.sentAt").isNotEmpty());
        verify(service).sendMail(eq(user), argThat(req -> req.gmailId() == 2L
                && req.to().equals(List.of("to@example.test")) && "hello".equals(req.textBody())),
                argThat(files -> files.size() == 1 && "empty.txt".equals(files.getFirst().getOriginalFilename())), anyString());
    }

    @Test
    void sendsLegacyFormFieldsAndFilesThroughSameService() throws Exception {
        MockMultipartFile pdf = new MockMultipartFile("files", "report.pdf", "application/pdf", new byte[]{1, 2});
        MockMultipartFile text = new MockMultipartFile("files[]", "notes.txt", "text/plain", new byte[]{3});
        when(service.sendMail(eq(user), any(SendMailRequest.class), anyList(), anyString()))
                .thenReturn(new SendMailResponse("<id@example.test>", "SENT"));
        mvc.perform(multipart("/api/mails/send").file(pdf).file(text)
                        .param("gmailAccountId", "2")
                        .param("to", "a@example.test, b@example.test", "c@example.test")
                        .param("cc", "cc@example.test").param("bcc", "")
                        .param("subject", "Legacy").param("body", "<p>hello</p>").param("isHtml", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Sent"))
                .andExpect(jsonPath("$.status").value("SENT"));
        verify(service).sendMail(eq(user), argThat(req -> req.gmailId() == 2L
                        && req.to().equals(List.of("a@example.test", "b@example.test", "c@example.test"))
                        && req.cc().equals(List.of("cc@example.test")) && req.bcc().isEmpty()
                        && req.textBody() == null && "<p>hello</p>".equals(req.htmlBody())),
                argThat(files -> files.size() == 2
                        && files.stream().map(MultipartFile::getOriginalFilename).toList()
                        .equals(List.of("report.pdf", "notes.txt"))), anyString());
    }

    @Test
    void rejectsInvalidLegacyShapeAndMixedBodyWithoutSending() throws Exception {
        mvc.perform(multipart("/api/mails/send").param("gmailAccountId", "2")
                        .param("isHtml", "maybe").param("to", "to@example.test"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(multipart("/api/mails/send").param("gmailAccountId", "2")
                        .param("to", "to@example.test\r\nBcc: bad@example.test"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("HEADER_INJECTION_DETECTED"));
        mvc.perform(multipart("/api/mails/send")
                        .file(payload("{\"gmailId\":2,\"to\":[\"to@example.test\"]}"))
                        .param("body", "conflicting"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(multipart("/api/mails/send"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(service);
    }

    @Test
    void legacyPlainBodyUsesSameServiceValidationErrors() throws Exception {
        when(service.sendMail(eq(user), any(SendMailRequest.class), anyList(), anyString()))
                .thenThrow(new AppException(HttpStatus.PAYLOAD_TOO_LARGE, "PAYLOAD_TOO_LARGE", "Too large"));
        mvc.perform(multipart("/api/mails/send").param("gmailAccountId", "2")
                        .param("to", "to@example.test").param("body", "plain").param("isHtml", "false"))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("PAYLOAD_TOO_LARGE"));
        verify(service).sendMail(eq(user), argThat(req -> "plain".equals(req.textBody()) && req.htmlBody() == null),
                anyList(), anyString());
    }

    @Test
    void deleteDefaultsToNoContentAndSupportsExplicitRepresentation() throws Exception {
        when(service.deleteMail(eq(user), eq(2L), eq("INBOX"), eq(7L), eq(false), eq(9L), anyString()))
                .thenReturn(Map.of("status", "DELETED", "permanent", false, "message", "Moved"));
        mvc.perform(delete("/api/mails/2/INBOX/7").param("uidValidity", "9"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        mvc.perform(delete("/api/mails/2/INBOX/7").param("uidValidity", "9")
                        .header("Prefer", "return=representation"))
                .andExpect(status().isOk())
                .andExpect(header().string("Preference-Applied", "return=representation"))
                .andExpect(jsonPath("$.status").value("DELETED"))
                .andExpect(jsonPath("$.permanent").value(false));
        verify(service, times(2)).deleteMail(eq(user), eq(2L), eq("INBOX"), eq(7L), eq(false), eq(9L), anyString());
    }

    @Test
    void flagsAcceptStarredAndReturnBothNamesAndUid() throws Exception {
        when(service.updateFlags(eq(user), eq(2L), eq("INBOX"), eq(7L),
                any(UpdateFlagsRequest.class), eq(9L), anyString()))
                .thenReturn(new MailFlagsDto(false, true, false, false));
        mvc.perform(put("/api/mails/2/INBOX/7/flags").param("uidValidity", "9")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"starred\":true,\"seen\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uid").value(7))
                .andExpect(jsonPath("$.flagged").value(true))
                .andExpect(jsonPath("$.starred").value(true))
                .andExpect(jsonPath("$.seen").value(false));
        verify(service).updateFlags(eq(user), eq(2L), eq("INBOX"), eq(7L),
                eq(new UpdateFlagsRequest(false, true)), eq(9L), anyString());
    }

    @Test
    void downloadsExactBytesWithSafeHeadersAndCleansUp() throws Exception {
        byte[] bytes = {0, 1, 2, 3};
        AtomicBoolean closed = new AtomicBoolean();
        when(service.prepareAttachmentDownload(user, 2L, "INBOX", 7L, 0, 9L))
                .thenReturn(new MailService.AttachmentDownloadHandle("ghi-chú.txt", "text/plain", bytes.length,
                        out -> out.write(bytes), () -> closed.set(true)));
        mvc.perform(get("/api/mails/2/INBOX/7/attachments/0/download").param("uidValidity", "9"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Length", "4"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("filename*=UTF-8''")))
                .andExpect(content().bytes(bytes));
        assertTrue(closed.get());
    }

    @Test
    void oversizedDownloadReturnsJsonBeforeBinaryHeaders() throws Exception {
        when(service.prepareAttachmentDownload(user, 2L, "INBOX", 7L, 0, 9L))
                .thenThrow(new AppException(HttpStatus.PAYLOAD_TOO_LARGE, "PAYLOAD_TOO_LARGE", "Too large"));
        mvc.perform(get("/api/mails/2/INBOX/7/attachments/0/download").param("uidValidity", "9")
                        .accept(MediaType.APPLICATION_OCTET_STREAM))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().doesNotExist("Content-Disposition"))
                .andExpect(jsonPath("$.code").value("PAYLOAD_TOO_LARGE"));
    }

    @Test
    void binaryDownloadRateLimitReturnsJson429AndRetryAfter() throws Exception {
        when(service.prepareAttachmentDownload(user, 2L, "INBOX", 7L, 0, 9L))
                .thenThrow(new RateLimitException("Fixture download limit", 30));
        mvc.perform(get("/api/mails/2/INBOX/7/attachments/0/download").param("uidValidity", "9")
                        .accept(MediaType.APPLICATION_OCTET_STREAM))
                .andExpect(status().isTooManyRequests())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().string("Retry-After", "30"))
                .andExpect(header().doesNotExist("Content-Disposition"))
                .andExpect(jsonPath("$.code").value("TOO_MANY_ATTEMPTS"))
                .andExpect(jsonPath("$.path").value("/api/mails/2/INBOX/7/attachments/0/download"));
    }

    private static MockMultipartFile payload(String json) {
        return new MockMultipartFile("payload", "", "application/json", json.getBytes(StandardCharsets.UTF_8));
    }
}
