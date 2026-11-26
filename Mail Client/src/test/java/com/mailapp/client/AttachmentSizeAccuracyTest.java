package com.mailapp.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mailapp.client.model.MailClientDtos.AttachmentSizeAccuracy;
import com.mailapp.client.model.MailClientDtos.MailAttachmentDto;
import com.mailapp.client.model.MailClientDtos.MailSummaryDto;
import com.mailapp.client.util.MailSizeDisplay;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AttachmentSizeAccuracyTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void currentAndLegacyAttachmentSizeFieldsRemainCompatible() throws Exception {
        MailAttachmentDto current = mapper.readValue("""
                {"index":1,"filename":"fixture.txt","fileName":"fixture.txt","size":23,
                 "sizeBytes":23,"inline":false,"sizeAccuracy":"ESTIMATED"}
                """, MailAttachmentDto.class);
        assertEquals(23L, current.size());
        assertEquals(23L, current.sizeBytes());
        assertEquals("fixture.txt", current.fileName());
        assertEquals(AttachmentSizeAccuracy.ESTIMATED, current.sizeAccuracy());
        assertEquals("≈ 23 B", MailSizeDisplay.attachment(current.size(), current.sizeAccuracy()));
        var serialized = mapper.readTree(mapper.writeValueAsString(current));
        assertEquals(23L, serialized.path("size").asLong());
        assertEquals(23L, serialized.path("sizeBytes").asLong());
        assertEquals("ESTIMATED", serialized.path("sizeAccuracy").asText());

        MailAttachmentDto legacy = mapper.readValue("""
                {"index":2,"fileName":"old.txt","sizeBytes":22}
                """, MailAttachmentDto.class);
        assertEquals("old.txt", legacy.filename());
        assertEquals(22L, legacy.size());
        assertEquals(AttachmentSizeAccuracy.ESTIMATED, legacy.sizeAccuracy());

        MailAttachmentDto both = mapper.readValue("""
                {"filename":"current.txt","fileName":"legacy.txt","sizeBytes":99,"size":23}
                """, MailAttachmentDto.class);
        assertEquals("current.txt", both.filename());
        assertEquals(23L, both.size(), "Canonical size wins if legacy metadata disagrees");
        assertEquals(AttachmentSizeAccuracy.ESTIMATED,
                new MailAttachmentDto(0, "legacy.txt", "text/plain", 23, false, null).sizeAccuracy());
    }

    @Test
    void exactZeroUnknownMissingAndFutureAccuracyAreNotMisrepresented() throws Exception {
        MailAttachmentDto empty = mapper.readValue("""
                {"size":0,"sizeAccuracy":"EXACT"}
                """, MailAttachmentDto.class);
        assertEquals(AttachmentSizeAccuracy.EXACT, empty.sizeAccuracy());
        assertEquals("0 B", MailSizeDisplay.attachment(empty.size(), empty.sizeAccuracy()));

        for (String json : List.of(
                "{\"size\":0,\"sizeAccuracy\":\"UNKNOWN\"}",
                "{\"size\":123,\"sizeAccuracy\":\"UNKNOWN\"}",
                "{\"size\":123,\"sizeAccuracy\":\"FUTURE_VALUE\"}",
                "{\"size\":0}",
                "{\"size\":-1,\"sizeAccuracy\":\"EXACT\"}",
                "{\"sizeAccuracy\":\"EXACT\"}",
                "{\"size\":null}",
                "{}")) {
            MailAttachmentDto unknown = mapper.readValue(json, MailAttachmentDto.class);
            assertEquals(AttachmentSizeAccuracy.UNKNOWN, unknown.sizeAccuracy(), json);
            assertEquals("Không rõ dung lượng",
                    MailSizeDisplay.attachment(unknown.size(), unknown.sizeAccuracy()), json);
        }
        assertEquals("≈ 0 B", MailSizeDisplay.attachment(0, AttachmentSizeAccuracy.ESTIMATED));
        assertEquals("22 B", MailSizeDisplay.attachment(22, AttachmentSizeAccuracy.EXACT));
    }

    @Test
    void wholeMessageTotalsSeparateKnownEstimatesFromUnknownValues() {
        assertEquals("≈ 20 B", MailSizeDisplay.message(20));
        assertEquals("Không rõ dung lượng", MailSizeDisplay.message(0));
        assertEquals("Không rõ dung lượng", MailSizeDisplay.message(-1));
        assertEquals("≈ 30 B", MailSizeDisplay.messageTotal(List.of(summary(10), summary(20))));
        assertEquals("≈ 30 B (chỉ các thư có số liệu; 2 thư chưa rõ)",
                MailSizeDisplay.messageTotal(List.of(summary(10), summary(-1), summary(20), summary(0))));
        assertEquals("Không rõ (2 thư chưa rõ dung lượng)",
                MailSizeDisplay.messageTotal(List.of(summary(0), summary(-1))));
        assertEquals("Không rõ",
                MailSizeDisplay.messageTotal(List.of(summary(Long.MAX_VALUE), summary(1))));
    }

    private static MailSummaryDto summary(long size) {
        return new MailSummaryDto(1, 1, null, List.of(), "fixture", "", null, null, size, true, 1);
    }
}
