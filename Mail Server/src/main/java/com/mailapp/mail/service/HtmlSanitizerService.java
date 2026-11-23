package com.mailapp.mail.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Dịch vụ làm sạch HTML nội dung thư (chống XSS, chặn script/iframe/form/on* và ảnh theo dõi từ xa).
 * Sử dụng jsoup Safelist chặt chẽ.
 */
@Component
public class HtmlSanitizerService {

    public static final int MAX_BODY_CHARS = 512 * 1024; // 512 KB giới hạn an toàn

    public record SanitizeResult(String html, boolean hasBlockedRemoteImages, boolean truncated) {}

    private static final Safelist MAIL_SAFELIST = Safelist.relaxed()
            .removeTags("iframe", "form", "input", "button", "select", "textarea", "script", "style", "object", "embed", "meta", "link", "base")
            .addAttributes(":all", "class", "align", "valign", "dir")
            .addAttributes("a", "target", "rel")
            .addAttributes("img", "src", "alt", "title", "width", "height", "data-blocked-src")
            .addAttributes("td", "colspan", "rowspan", "width", "bgcolor")
            .addAttributes("th", "colspan", "rowspan", "width", "bgcolor")
            .addAttributes("table", "border", "cellpadding", "cellspacing", "width")
            .addProtocols("a", "href", "http", "https", "mailto")
            .addProtocols("img", "src", "http", "https", "cid", "data");

    public SanitizeResult sanitize(String rawHtml, boolean loadRemote) {
        if (rawHtml == null || rawHtml.isBlank()) {
            return new SanitizeResult(null, false, false);
        }

        boolean truncated = false;
        String input = rawHtml;
        if (input.length() > MAX_BODY_CHARS) {
            input = input.substring(0, MAX_BODY_CHARS);
            truncated = true;
        }

        // Parse & clean với Safelist (loại bỏ toàn bộ script, iframe, form, sự kiện on*, style nguy hiểm)
        Document dirty = Jsoup.parseBodyFragment(input);
        // Xóa tất cả thẻ script/style/iframe/form cùng nội dung bên trong trước
        dirty.select("script, style, iframe, object, embed, form, input, button, select, textarea, link, meta, base, svg, math").remove();

        String cleaned = Jsoup.clean(dirty.body().html(), "", MAIL_SAFELIST,
                new Document.OutputSettings().prettyPrint(false));

        Document doc = Jsoup.parseBodyFragment(cleaned);
        boolean blockedRemote = false;

        // Kiểm tra & xử lý tất cả thẻ <img>
        for (Element img : doc.select("img")) {
            String src = img.attr("src").trim();
            String lower = src.toLowerCase(Locale.ROOT);

            // Chặn data: không phải ảnh an toàn (VD: data:text/html, data:image/svg+xml)
            if (lower.startsWith("data:")) {
                if (!(lower.startsWith("data:image/png;") || lower.startsWith("data:image/jpeg;")
                        || lower.startsWith("data:image/gif;") || lower.startsWith("data:image/webp;"))) {
                    img.remove();
                    continue;
                }
            }

            // Giữ ảnh inline cid:
            if (lower.startsWith("cid:")) {
                continue;
            }

            // Chặn ảnh từ xa http(s):// trừ khi loadRemote=true
            if (lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("//")) {
                if (!loadRemote) {
                    blockedRemote = true;
                    img.attr("data-blocked-remote", "true");
                    img.removeAttr("src");
                    if (!img.hasAttr("alt") || img.attr("alt").isBlank()) {
                        img.attr("alt", "[Ảnh từ xa đã bị chặn để bảo vệ quyền riêng tư]");
                    }
                }
            } else if (!lower.startsWith("data:image/")) {
                // Giao thức lạ không hợp lệ
                img.removeAttr("src");
            }
        }

        // Gắn rel="noopener noreferrer" và target="_blank" cho mọi link
        for (Element a : doc.select("a[href]")) {
            a.attr("rel", "noopener noreferrer");
            a.attr("target", "_blank");
        }

        return new SanitizeResult(doc.body().html(), blockedRemote, truncated);
    }

    public String htmlToPlainText(String rawHtml) {
        if (rawHtml == null || rawHtml.isBlank()) {
            return "";
        }
        Document doc = Jsoup.parse(rawHtml);
        doc.select("script, style").remove();
        return doc.text();
    }
}
