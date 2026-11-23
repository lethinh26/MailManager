package com.mailapp.client.ui.theme;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/**
 * Hệ thống biểu tượng & bộ nhận diện thương hiệu "Veyra" (Veyra Mail) vẽ hoàn toàn bằng Java2D.
 * Biểu trưng Prism-Wing chữ V (cánh thư origami + tia ánh sáng bảo mật lục bảo trên nền cực quang xanh tím),
 * không phụ thuộc thư viện UI ngoài; hỗ trợ xuất PNG 16..512px, tệp .ico đa kích thước và banner README.
 */
public final class AppIcons {

    public static final String BRAND_NAME = "Veyra";
    public static final String BRAND_UPPER = "VEYRA";
    public static final String BRAND_FULL = "Veyra Mail";
    public static final String BRAND_TAGLINE = "Không gian thư điện tử bảo mật & tinh tế";
    public static final int[] LOGO_SIZES = {16, 24, 32, 48, 64, 128, 256, 512};

    private AppIcons() {}

    /**
     * Vẽ logo thương hiệu "Veyra" — biểu trưng "Cánh Chim Quang Học" (Celestial Origami Swift):
     * 3 dải cánh cong theo tỷ lệ vàng (San hô - Vàng ấm, Ngọc trai - Băng tuyết, và Xanh Lam Điện)
     * đan tạo hình cánh chim đưa thư đang vút bay kết hợp chữ V cách điệu & tinh thể bảo mật
     * trên nền squircle vũ trụ sâu (100% Java2D vector, sắc nét từ 16px tới 512px).
     */
    public static BufferedImage renderBrandLogo(int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);

        double s = size / 64.0;
        g.scale(s, s);

        // 1. Nền squircle sâu sang trọng (Obsidian Plum -> Midnight Indigo -> Deep Cosmic Teal)
        RoundRectangle2D.Double squircle = new RoundRectangle2D.Double(2.5, 2.5, 59.0, 59.0, 19.5, 19.5);
        LinearGradientPaint baseGrad = new LinearGradientPaint(
                3f, 3f, 61f, 61f,
                new float[]{0.0f, 0.48f, 1.0f},
                new Color[]{new Color(14, 10, 36), new Color(30, 19, 74), new Color(10, 48, 78)}
        );
        g.setPaint(baseGrad);
        g.fill(squircle);

        Shape oldClip = g.getClip();
        g.setClip(squircle);

        // Quầng sáng cực quang hai cực: Hồng san hô ấm (trên-phải) & Lam ngọc điện (dưới-trái)
        g.setPaint(new RadialGradientPaint(
                new Point2D.Float(50f, 13f), 30f,
                new float[]{0f, 1f},
                new Color[]{new Color(244, 63, 94, 96), new Color(244, 63, 94, 0)}
        ));
        g.fillRect(0, 0, 64, 64);

        g.setPaint(new RadialGradientPaint(
                new Point2D.Float(14f, 52f), 29f,
                new float[]{0f, 1f},
                new Color[]{new Color(34, 211, 238, 92), new Color(34, 211, 238, 0)}
        ));
        g.fillRect(0, 0, 64, 64);

        // Quỹ đạo sao mảnh tạo chiều sâu không gian
        g.setColor(new Color(255, 255, 255, 30));
        g.setStroke(new BasicStroke(1.05f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        AffineTransform oldTx = g.getTransform();
        g.rotate(Math.toRadians(-28), 32, 34);
        g.draw(new Arc2D.Double(6.0, 19.0, 52.0, 24.0, 175, 200, Arc2D.OPEN));
        g.setTransform(oldTx);

        // 2. Dải cánh dưới (Xanh Lam Điện -> Chàm) uốn lượn mềm mại như luồng khí & đuôi chim én
        Path2D lowerRibbon = new Path2D.Double();
        lowerRibbon.moveTo(11.0, 31.5);
        lowerRibbon.curveTo(17.0, 43.0, 24.5, 51.5, 31.5, 52.0);
        lowerRibbon.curveTo(39.0, 52.4, 46.5, 36.5, 53.5, 19.5);
        lowerRibbon.curveTo(44.5, 31.0, 36.8, 41.2, 30.8, 41.0);
        lowerRibbon.curveTo(24.6, 40.8, 19.0, 34.0, 11.0, 31.5);
        lowerRibbon.closePath();

        LinearGradientPaint cyanGrad = new LinearGradientPaint(
                11f, 32f, 53f, 20f,
                new float[]{0.0f, 0.5f, 1.0f},
                new Color[]{new Color(34, 211, 238), new Color(59, 130, 246), new Color(129, 140, 248)}
        );
        g.setPaint(cyanGrad);
        g.fill(lowerRibbon);

        // 3. Dải cánh trên rực rỡ (San hô -> Hồng đào -> Vàng hổ phách) vút cao tạo nhịp bay sáng tạo
        Path2D upperWing = new Path2D.Double();
        upperWing.moveTo(10.5, 21.0);
        upperWing.curveTo(19.5, 18.0, 29.0, 22.8, 34.8, 31.8);
        upperWing.curveTo(41.2, 24.5, 47.2, 18.2, 54.0, 13.8);
        upperWing.curveTo(48.0, 24.8, 41.8, 34.6, 33.4, 41.8);
        upperWing.curveTo(25.2, 34.2, 19.0, 26.8, 10.5, 21.0);
        upperWing.closePath();

        LinearGradientPaint coralGoldGrad = new LinearGradientPaint(
                10.5f, 20f, 54f, 14f,
                new float[]{0.0f, 0.45f, 0.82f, 1.0f},
                new Color[]{new Color(244, 63, 94), new Color(251, 113, 133), new Color(251, 191, 36), new Color(254, 243, 199)}
        );
        g.setPaint(coralGoldGrad);
        g.fill(upperWing);

        // 4. Bóng đổ mềm ngay dưới nếp cánh tạo độ nổi khối 3D
        Path2D innerShadow = new Path2D.Double();
        innerShadow.moveTo(27.5, 36.2);
        innerShadow.curveTo(31.5, 39.6, 34.5, 41.5, 37.8, 38.4);
        innerShadow.curveTo(41.5, 34.8, 46.8, 25.5, 53.5, 14.5);
        innerShadow.curveTo(45.5, 23.0, 38.5, 32.0, 33.5, 36.0);
        innerShadow.closePath();
        g.setColor(new Color(14, 10, 36, 95));
        g.fill(innerShadow);

        // 5. Lõi cánh thư ngọc trai (Pearl-White Origami Crest) bén nét — đảm bảo nổi bật cực rõ cả ở 16px & 28px
        Path2D pearlCrest = new Path2D.Double();
        pearlCrest.moveTo(19.2, 23.2);
        pearlCrest.curveTo(26.5, 25.4, 30.8, 30.8, 32.6, 36.4);
        pearlCrest.curveTo(39.4, 29.8, 46.2, 21.8, 54.0, 13.8);
        pearlCrest.curveTo(47.6, 26.8, 40.4, 37.6, 32.0, 45.2);
        pearlCrest.curveTo(27.2, 38.4, 23.8, 30.6, 19.2, 23.2);
        pearlCrest.closePath();

        LinearGradientPaint pearlGrad = new LinearGradientPaint(
                54f, 14f, 22f, 44f,
                new float[]{0.0f, 0.55f, 1.0f},
                new Color[]{Color.WHITE, new Color(240, 249, 255), new Color(186, 230, 253)}
        );
        g.setPaint(pearlGrad);
        g.fill(pearlCrest);

        // Nếp gấp mảnh ánh băng trên lõi cánh trắng
        g.setColor(new Color(56, 189, 248, 120));
        g.setStroke(new BasicStroke(0.9f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new QuadCurve2D.Double(32.5, 36.4, 41.5, 27.5, 52.8, 15.0));

        // 6. Ngôi sao bảo mật 4 cánh ánh vàng-lục bảo (điểm nhấn mật mã TLS/AES) phía trên-trái quỹ đạo
        RadialGradientPaint starGlow = new RadialGradientPaint(
                new Point2D.Float(31.5f, 16.5f), 8.0f,
                new float[]{0f, 1f},
                new Color[]{new Color(253, 224, 71, 155), new Color(253, 224, 71, 0)}
        );
        g.setPaint(starGlow);
        g.fill(new Ellipse2D.Double(23.5, 8.5, 16.0, 16.0));

        Path2D northStar = new Path2D.Double();
        northStar.moveTo(31.5, 11.4);
        northStar.quadTo(32.2, 15.8, 36.6, 16.5);
        northStar.quadTo(32.2, 17.2, 31.5, 21.6);
        northStar.quadTo(30.8, 17.2, 26.4, 16.5);
        northStar.quadTo(30.8, 15.8, 31.5, 11.4);
        northStar.closePath();
        g.setPaint(new LinearGradientPaint(31.5f, 11.4f, 31.5f, 21.6f,
                new float[]{0f, 1f}, new Color[]{Color.WHITE, new Color(253, 224, 71)}));
        g.fill(northStar);

        // Chấm sáng nhỏ đồng hành tạo cảm giác chòm sao
        g.setColor(new Color(165, 243, 252, 220));
        g.fill(new Ellipse2D.Double(20.2, 13.0, 2.6, 2.6));

        // 7. Viền kính ánh sắc cầu vồng tinh tế bao quanh squircle
        g.setClip(oldClip);
        LinearGradientPaint rimGrad = new LinearGradientPaint(
                4f, 4f, 60f, 60f,
                new float[]{0f, 0.45f, 1f},
                new Color[]{new Color(255, 255, 255, 120), new Color(244, 114, 182, 65), new Color(34, 211, 238, 110)}
        );
        g.setPaint(rimGrad);
        g.setStroke(new BasicStroke(1.3f));
        g.draw(new RoundRectangle2D.Double(3.15, 3.15, 57.7, 57.7, 18.8, 18.8));

        g.dispose();
        return img;
    }

    public static Icon logoIcon(int size) {
        return new ImageIcon(renderBrandLogo(size));
    }

    public static List<Image> getWindowIcons() {
        List<Image> icons = new ArrayList<>();
        for (int sz : new int[]{16, 24, 32, 48, 64, 128}) {
            icons.add(renderBrandLogo(sz));
        }
        return icons;
    }

    /**
     * Xuất bộ PNG 16..512px, tệp .ico đa kích thước vào resources/icons và banner README vào docs/banner.png.
     */
    public static void generateBrandAssets(File iconsDir, File bannerFile) throws IOException {
        iconsDir.mkdirs();
        List<byte[]> icoPngPayloads = new ArrayList<>();
        int[] icoSizes = {16, 24, 32, 48, 64, 128, 256};

        for (int sz : LOGO_SIZES) {
            BufferedImage img = renderBrandLogo(sz);
            // Xuất cả tên mới veyra-* và giữ tương thích tên cũ mailmanager-*
            ImageIO.write(img, "PNG", new File(iconsDir, "veyra-" + sz + ".png"));
            ImageIO.write(img, "PNG", new File(iconsDir, "mailmanager-" + sz + ".png"));

            for (int isz : icoSizes) {
                if (sz == isz) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    ImageIO.write(img, "PNG", baos);
                    icoPngPayloads.add(baos.toByteArray());
                }
            }
        }

        // Ghi tệp Windows .ico chứa PNG nhúng chuẩn Vista+ (16..256)
        writeMultiSizeIco(new File(iconsDir, "veyra.ico"), icoSizes, icoPngPayloads);
        writeMultiSizeIco(new File(iconsDir, "mailmanager.ico"), icoSizes, icoPngPayloads);

        // Sinh README banner (1200x300)
        if (bannerFile != null) {
            bannerFile.getParentFile().mkdirs();
            BufferedImage banner = renderReadmeBanner(1200, 300);
            ImageIO.write(banner, "PNG", bannerFile);
        }
    }

    private static void writeMultiSizeIco(File icoFile, int[] sizes, List<byte[]> pngPayloads) throws IOException {
        int count = sizes.length;
        int headerSize = 6 + count * 16;
        int offset = headerSize;

        try (FileOutputStream fos = new FileOutputStream(icoFile)) {
            ByteBuffer hdr = ByteBuffer.allocate(6).order(ByteOrder.LITTLE_ENDIAN);
            hdr.putShort((short) 0);      // Reserved
            hdr.putShort((short) 1);      // Type: ICO
            hdr.putShort((short) count);  // Image count
            fos.write(hdr.array());

            for (int i = 0; i < count; i++) {
                int sz = sizes[i];
                byte[] data = pngPayloads.get(i);
                ByteBuffer entry = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN);
                entry.put((byte) (sz >= 256 ? 0 : sz));
                entry.put((byte) (sz >= 256 ? 0 : sz));
                entry.put((byte) 0);          // Color palette
                entry.put((byte) 0);          // Reserved
                entry.putShort((short) 1);    // Color planes
                entry.putShort((short) 32);   // Bits per pixel
                entry.putInt(data.length);    // Size
                entry.putInt(offset);         // Offset
                fos.write(entry.array());
                offset += data.length;
            }
            for (byte[] data : pngPayloads) {
                fos.write(data);
            }
        }
    }

    public static BufferedImage renderReadmeBanner(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        RoundRectangle2D.Double frame = new RoundRectangle2D.Double(0, 0, w, h, 24, 24);
        LinearGradientPaint bg = new LinearGradientPaint(
                0, 0, w, h,
                new float[]{0f, 0.55f, 1f},
                new Color[]{new Color(9, 14, 34), new Color(24, 28, 78), new Color(11, 68, 108)}
        );
        g.setPaint(bg);
        g.fill(frame);

        g.setClip(frame);
        // Quầng sáng cực quang nền
        g.setPaint(new RadialGradientPaint(new Point2D.Float(w - 180f, 50f), 320f,
                new float[]{0f, 1f}, new Color[]{new Color(56, 189, 248, 80), new Color(56, 189, 248, 0)}));
        g.fillRect(0, 0, w, h);
        g.setPaint(new RadialGradientPaint(new Point2D.Float(160f, h - 20f), 260f,
                new float[]{0f, 1f}, new Color[]{new Color(139, 92, 246, 75), new Color(139, 92, 246, 0)}));
        g.fillRect(0, 0, w, h);

        // Vẽ logo lớn bên trái
        BufferedImage logo = renderBrandLogo(144);
        g.drawImage(logo, 76, (h - 144) / 2, null);

        // Tiêu đề & khẩu hiệu
        g.setColor(Color.WHITE);
        g.setFont(new Font("Segoe UI", Font.BOLD, 50));
        g.drawString("VEYRA", 256, 128);

        // Nhãn SECURE MAIL căn giữa chính xác bằng FontMetrics cạnh tên thương hiệu
        g.setFont(new Font("Segoe UI", Font.BOLD, 13));
        String pillText = "SECURE MAIL";
        FontMetrics pillFm = g.getFontMetrics();
        int pillW = pillFm.stringWidth(pillText) + 32;
        int pillX = 438, pillY = 92, pillH = 32;
        g.setColor(new Color(56, 189, 248, 45));
        g.fill(new RoundRectangle2D.Double(pillX, pillY, pillW, pillH, pillH, pillH));
        g.setColor(new Color(125, 211, 252));
        g.setStroke(new BasicStroke(1.2f));
        g.draw(new RoundRectangle2D.Double(pillX, pillY, pillW, pillH, pillH, pillH));
        int textX = pillX + (pillW - pillFm.stringWidth(pillText)) / 2;
        int textY = pillY + (pillH + pillFm.getAscent() - pillFm.getDescent()) / 2;
        g.drawString(pillText, textX, textY);

        g.setColor(new Color(203, 213, 225));
        g.setFont(new Font("Segoe UI", Font.PLAIN, 22));
        g.drawString("Không gian thư điện tử bảo mật — 2 Lớp TLS • OAuth2 PKCE • IMAP/SMTP", 256, 178);

        g.setColor(new Color(110, 231, 183));
        g.setFont(new Font("Segoe UI", Font.BOLD, 16));
        g.drawString("Java 21 • Spring Boot 3.3.5 • Pure Java Swing & Java2D • AES-256-GCM • Auto-Download & Clean", 256, 220);

        g.dispose();
        return img;
    }

    // === Vector Icons vẽ bằng Java2D cho Toolbar, Folder, Mail list & Auth UI ===
    public enum Glyph {
        COMPOSE, REFRESH, DELETE, STAR, STAR_FILLED, DOWNLOAD, SEARCH, SETTINGS,
        ATTACHMENT, INBOX, SENT, DRAFTS, TRASH, SPAM, FOLDER, REPLY, REPLY_ALL,
        FORWARD, UNREAD, SHIELD, PLUS, USER,
        LOCK, EYE, EYE_OFF, SUN, MOON, CHECK_CIRCLE, SERVER, ARROW_RIGHT, MAIL
    }

    public static Icon vectorIcon(Glyph glyph, int size, Color color) {
        return new Icon() {
            @Override public int getIconWidth() { return size; }
            @Override public int getIconHeight() { return size; }
            @Override
            public void paintIcon(Component c, Graphics g0, int x, int y) {
                Graphics2D g = (Graphics2D) g0.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.translate(x, y);
                double sc = size / 20.0;
                g.scale(sc, sc);
                g.setColor(color);
                g.setStroke(new BasicStroke(1.75f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

                switch (glyph) {
                    case COMPOSE -> {
                        g.draw(new RoundRectangle2D.Double(3, 4, 12, 13, 2, 2));
                        g.draw(new Line2D.Double(10, 10, 17, 3));
                    }
                    case REFRESH -> {
                        g.draw(new Arc2D.Double(3, 3, 14, 14, 30, 290, Arc2D.OPEN));
                        g.draw(new Line2D.Double(15, 3, 17, 7));
                        g.draw(new Line2D.Double(17, 7, 13, 7));
                    }
                    case DELETE, TRASH -> {
                        g.draw(new Line2D.Double(3, 5, 17, 5));
                        g.draw(new Line2D.Double(8, 3, 12, 3));
                        g.draw(new RoundRectangle2D.Double(5, 5, 10, 12, 2, 2));
                        g.draw(new Line2D.Double(8, 8, 8, 14));
                        g.draw(new Line2D.Double(12, 8, 12, 14));
                    }
                    case STAR, STAR_FILLED -> {
                        Path2D star = buildStarPath(10, 10, 7.5, 3.4);
                        if (glyph == Glyph.STAR_FILLED) g.fill(star);
                        else g.draw(star);
                    }
                    case DOWNLOAD -> {
                        g.draw(new Line2D.Double(10, 3, 10, 13));
                        g.draw(new Line2D.Double(6, 9, 10, 13));
                        g.draw(new Line2D.Double(14, 9, 10, 13));
                        g.draw(new Line2D.Double(4, 16, 16, 16));
                    }
                    case SEARCH -> {
                        g.draw(new Ellipse2D.Double(3, 3, 10, 10));
                        g.draw(new Line2D.Double(12, 12, 17, 17));
                    }
                    case SETTINGS -> {
                        g.draw(new Ellipse2D.Double(7, 7, 6, 6));
                        for (int i = 0; i < 8; i++) {
                            double ang = i * Math.PI / 4.0;
                            g.draw(new Line2D.Double(10 + Math.cos(ang) * 5.2, 10 + Math.sin(ang) * 5.2,
                                    10 + Math.cos(ang) * 7.8, 10 + Math.sin(ang) * 7.8));
                        }
                    }
                    case ATTACHMENT -> {
                        g.draw(new RoundRectangle2D.Double(6, 3, 7, 14, 7, 7));
                        g.draw(new Line2D.Double(9.5, 6, 9.5, 13));
                    }
                    case INBOX -> {
                        g.draw(new RoundRectangle2D.Double(2.5, 4, 15, 12, 3, 3));
                        g.draw(new Line2D.Double(3, 11, 7, 11));
                        g.draw(new Line2D.Double(13, 11, 17, 11));
                        g.draw(new Line2D.Double(7, 11, 8.5, 13));
                        g.draw(new Line2D.Double(13, 11, 11.5, 13));
                        g.draw(new Line2D.Double(8.5, 13, 11.5, 13));
                    }
                    case SENT -> {
                        Path2D p = new Path2D.Double();
                        p.moveTo(3, 10); p.lineTo(17, 4); p.lineTo(13, 17); p.lineTo(10, 11); p.closePath();
                        g.draw(p);
                    }
                    case DRAFTS, FOLDER -> {
                        Path2D f = new Path2D.Double();
                        f.moveTo(2.5, 5); f.lineTo(8, 5); f.lineTo(10, 7); f.lineTo(17.5, 7);
                        f.lineTo(17.5, 15.5); f.lineTo(2.5, 15.5); f.closePath();
                        g.draw(f);
                    }
                    case SPAM -> {
                        g.draw(new Ellipse2D.Double(3, 3, 14, 14));
                        g.draw(new Line2D.Double(10, 6.5, 10, 11));
                        g.fill(new Ellipse2D.Double(9, 13, 2, 2));
                    }
                    case REPLY, REPLY_ALL -> {
                        g.draw(new Line2D.Double(4, 9, 9, 4));
                        g.draw(new Line2D.Double(4, 9, 9, 14));
                        g.draw(new Line2D.Double(4, 9, 15, 9));
                        g.draw(new Line2D.Double(15, 9, 15, 15));
                    }
                    case FORWARD -> {
                        g.draw(new Line2D.Double(16, 9, 11, 4));
                        g.draw(new Line2D.Double(16, 9, 11, 14));
                        g.draw(new Line2D.Double(16, 9, 5, 9));
                        g.draw(new Line2D.Double(5, 9, 5, 15));
                    }
                    case UNREAD, MAIL -> {
                        g.draw(new RoundRectangle2D.Double(2.5, 4.5, 15, 11, 2.5, 2.5));
                        g.draw(new Line2D.Double(3.5, 5.5, 10, 10.5));
                        g.draw(new Line2D.Double(16.5, 5.5, 10, 10.5));
                    }
                    case SHIELD -> {
                        Path2D s = new Path2D.Double();
                        s.moveTo(10, 2.5); s.lineTo(16.5, 5.5); s.lineTo(16.5, 11);
                        s.curveTo(16.5, 14.5, 13.5, 16.8, 10, 18);
                        s.curveTo(6.5, 16.8, 3.5, 14.5, 3.5, 11);
                        s.lineTo(3.5, 5.5); s.closePath();
                        g.draw(s);
                    }
                    case PLUS -> {
                        g.draw(new Line2D.Double(10, 4, 10, 16));
                        g.draw(new Line2D.Double(4, 10, 16, 10));
                    }
                    case USER -> {
                        g.draw(new Ellipse2D.Double(6.5, 3.5, 7, 7));
                        g.draw(new Arc2D.Double(3.5, 12, 13, 9, 0, 180, Arc2D.OPEN));
                    }
                    case LOCK -> {
                        g.draw(new RoundRectangle2D.Double(4, 9, 12, 8.5, 2.5, 2.5));
                        g.draw(new Arc2D.Double(6.5, 3.5, 7, 10, 0, 180, Arc2D.OPEN));
                        g.fill(new Ellipse2D.Double(9.1, 12.2, 1.8, 1.8));
                    }
                    case EYE -> {
                        Path2D eye = new Path2D.Double();
                        eye.moveTo(2.2, 10);
                        eye.quadTo(10, 3.2, 17.8, 10);
                        eye.quadTo(10, 16.8, 2.2, 10);
                        g.draw(eye);
                        g.draw(new Ellipse2D.Double(7.5, 7.5, 5, 5));
                    }
                    case EYE_OFF -> {
                        Path2D eye = new Path2D.Double();
                        eye.moveTo(2.5, 10);
                        eye.quadTo(10, 3.8, 17.5, 10);
                        eye.quadTo(10, 16.2, 2.5, 10);
                        g.draw(eye);
                        g.draw(new Line2D.Double(3.2, 16.8, 16.8, 3.2));
                    }
                    case SUN -> {
                        g.draw(new Ellipse2D.Double(6.5, 6.5, 7, 7));
                        for (int i = 0; i < 8; i++) {
                            double ang = i * Math.PI / 4.0;
                            g.draw(new Line2D.Double(10 + Math.cos(ang) * 5.8, 10 + Math.sin(ang) * 5.8,
                                    10 + Math.cos(ang) * 8.0, 10 + Math.sin(ang) * 8.0));
                        }
                    }
                    case MOON -> {
                        Path2D m = new Path2D.Double();
                        m.moveTo(13.5, 3.5);
                        m.curveTo(8.0, 4.2, 4.5, 8.8, 6.0, 13.8);
                        m.curveTo(7.4, 17.8, 13.0, 18.2, 16.4, 14.5);
                        m.curveTo(12.2, 15.2, 9.2, 12.2, 9.8, 8.0);
                        m.curveTo(10.2, 5.8, 11.6, 4.4, 13.5, 3.5);
                        g.draw(m);
                    }
                    case CHECK_CIRCLE -> {
                        g.draw(new Ellipse2D.Double(2.5, 2.5, 15, 15));
                        g.draw(new Line2D.Double(6.5, 10.2, 9.0, 12.8));
                        g.draw(new Line2D.Double(9.0, 12.8, 13.8, 7.6));
                    }
                    case SERVER -> {
                        g.draw(new RoundRectangle2D.Double(3, 3.5, 14, 5.5, 2, 2));
                        g.draw(new RoundRectangle2D.Double(3, 11, 14, 5.5, 2, 2));
                        g.fill(new Ellipse2D.Double(5.5, 5.5, 1.6, 1.6));
                        g.fill(new Ellipse2D.Double(5.5, 13, 1.6, 1.6));
                    }
                    case ARROW_RIGHT -> {
                        g.draw(new Line2D.Double(4, 10, 16, 10));
                        g.draw(new Line2D.Double(11.5, 5.5, 16, 10));
                        g.draw(new Line2D.Double(11.5, 14.5, 16, 10));
                    }
                }
                g.dispose();
            }
        };
    }

    private static Path2D buildStarPath(double cx, double cy, double outerR, double innerR) {
        Path2D p = new Path2D.Double();
        for (int i = 0; i < 10; i++) {
            double r = (i % 2 == 0) ? outerR : innerR;
            double angle = Math.toRadians(-90 + i * 36);
            double x = cx + Math.cos(angle) * r;
            double y = cy + Math.sin(angle) * r;
            if (i == 0) p.moveTo(x, y);
            else p.lineTo(x, y);
        }
        p.closePath();
        return p;
    }
}
