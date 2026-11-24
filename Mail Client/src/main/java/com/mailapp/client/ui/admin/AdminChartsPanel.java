package com.mailapp.client.ui.admin;

import com.mailapp.client.model.AdminClientDtos.AdminStatsResponse;
import com.mailapp.client.model.AdminClientDtos.DailyCountPoint;
import com.mailapp.client.ui.theme.Theme;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.util.Collections;
import java.util.List;

/**
 * Các biểu đồ thống kê tự vẽ hoàn toàn bằng Java2D (không dùng thư viện biểu đồ ngoài).
 */
public final class AdminChartsPanel {

    private AdminChartsPanel() {}

    /**
     * Biểu đồ cột + đường xu hướng: Số thư gửi đi 7 ngày gần nhất theo ngày.
     */
    public static class SentMailBarChartPanel extends JPanel {
        private List<DailyCountPoint> data = Collections.emptyList();

        public SentMailBarChartPanel() {
            setBackground(Theme.bgSurface());
            setPreferredSize(new Dimension(520, 260));
        }

        public void setData(List<DailyCountPoint> points) {
            this.data = points != null ? points : Collections.emptyList();
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Theme.applyTextHints(g);

            int w = getWidth();
            int h = getHeight();

            // Tiêu đề biểu đồ
            g.setFont(Theme.fontBold());
            g.setColor(Theme.textPrimary());
            g.drawString("Lượng thư gửi đi trong 7 ngày gần nhất", 16, 24);

            int padLeft = 46;
            int padRight = 24;
            int padTop = 44;
            int padBottom = 38;
            int plotW = Math.max(80, w - padLeft - padRight);
            int plotH = Math.max(60, h - padTop - padBottom);

            // Đường lưới ngang (4 mức)
            long maxVal = AdminViewModel.computeMaxChartValue(data);
            g.setFont(Theme.fontTiny());
            for (int i = 0; i <= 4; i++) {
                int y = padTop + plotH - (int) ((plotH * i) / 4.0);
                g.setColor(Theme.border());
                g.drawLine(padLeft, y, padLeft + plotW, y);
                long tick = Math.round((maxVal * i) / 4.0);
                g.setColor(Theme.textMuted());
                g.drawString(String.valueOf(tick), 10, y + 4);
            }

            if (data.isEmpty()) {
                g.setColor(Theme.textMuted());
                g.setFont(Theme.fontSmall());
                g.drawString("Chưa có dữ liệu thống kê.", padLeft + 20, padTop + plotH / 2);
                g.dispose();
                return;
            }

            int n = data.size();
            double slotW = (double) plotW / n;
            int barW = Math.max(18, Math.min(48, (int) (slotW * 0.52)));

            int[] cx = new int[n];
            int[] cy = new int[n];

            for (int i = 0; i < n; i++) {
                DailyCountPoint pt = data.get(i);
                int centerX = padLeft + (int) ((i + 0.5) * slotW);
                int barH = (int) Math.round((pt.count() / (double) maxVal) * plotH);
                int x = centerX - barW / 2;
                int y = padTop + plotH - barH;
                cx[i] = centerX;
                cy[i] = y;

                // Cột bo góc trên màu xanh thương hiệu
                if (barH > 0) {
                    GradientPaint gp = new GradientPaint(x, y, Theme.primary(), x, padTop + plotH, Theme.primaryHover());
                    g.setPaint(gp);
                    g.fill(new RoundRectangle2D.Double(x, y, barW, barH, 8, 8));
                } else {
                    g.setColor(Theme.border());
                    g.fillRect(x, padTop + plotH - 2, barW, 2);
                }

                // Giá trị trên đầu cột
                String valStr = String.valueOf(pt.count());
                g.setFont(Theme.fontTiny());
                g.setColor(Theme.textPrimary());
                int sw = g.getFontMetrics().stringWidth(valStr);
                g.drawString(valStr, centerX - sw / 2, Math.max(padTop - 4, y - 6));

                // Nhãn ngày dd/MM
                String dayLbl = AdminViewModel.formatShortDayLabel(pt.date());
                g.setColor(Theme.textSecondary());
                int lw = g.getFontMetrics().stringWidth(dayLbl);
                g.drawString(dayLbl, centerX - lw / 2, padTop + plotH + 20);
            }

            // Đường xu hướng nối đỉnh các cột
            if (n >= 2) {
                g.setColor(Theme.success());
                g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                for (int i = 0; i < n - 1; i++) {
                    g.drawLine(cx[i], cy[i], cx[i + 1], cy[i + 1]);
                }
                for (int i = 0; i < n; i++) {
                    g.setColor(Theme.bgSurface());
                    g.fillOval(cx[i] - 4, cy[i] - 4, 8, 8);
                    g.setColor(Theme.success());
                    g.drawOval(cx[i] - 4, cy[i] - 4, 8, 8);
                }
            }

            g.dispose();
        }
    }

    /**
     * Biểu đồ thanh tỷ lệ phân bổ: Gmail theo phương thức xác thực & Trạng thái người dùng.
     */
    public static class BreakdownChartPanel extends JPanel {
        private AdminStatsResponse stats;

        public BreakdownChartPanel() {
            setBackground(Theme.bgSurface());
            setPreferredSize(new Dimension(340, 260));
        }

        public void setStats(AdminStatsResponse stats) {
            this.stats = stats;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Theme.applyTextHints(g);

            g.setFont(Theme.fontBold());
            g.setColor(Theme.textPrimary());
            g.drawString("Phân bổ tài khoản & kết nối Gmail", 16, 24);

            if (stats == null) {
                g.setFont(Theme.fontSmall());
                g.setColor(Theme.textMuted());
                g.drawString("Đang chờ dữ liệu...", 16, 60);
                g.dispose();
                return;
            }

            int barW = Math.max(120, getWidth() - 32);
            drawSegmentedRow(g, 16, 52, barW,
                    "Trạng thái người dùng (" + stats.totalUsers() + ")",
                    new String[]{"Hoạt động", "Bị khóa"},
                    new long[]{stats.activeUsers(), stats.lockedUsers()},
                    new Color[]{Theme.success(), Theme.danger()});

            drawSegmentedRow(g, 16, 134, barW,
                    "Liên kết Gmail theo loại (" + stats.totalGmailAccounts() + ")",
                    new String[]{"OAuth2", "App Password"},
                    new long[]{stats.gmailByOAuth2(), stats.gmailByAppPassword()},
                    new Color[]{Theme.primary(), Theme.warning()});

            // Cảnh báo NEEDS_REAUTH nếu có
            g.setFont(Theme.fontSmall());
            g.setColor(stats.gmailNeedsReauth() > 0 ? Theme.danger() : Theme.success());
            g.drawString("Tài khoản Gmail cần cấp lại quyền (NEEDS_REAUTH): " + stats.gmailNeedsReauth(), 16, 224);

            g.dispose();
        }

        private void drawSegmentedRow(Graphics2D g, int x, int y, int width, String title,
                                      String[] labels, long[] values, Color[] colors) {
            g.setFont(Theme.fontSmall());
            g.setColor(Theme.textSecondary());
            g.drawString(title, x, y);

            long sum = 0;
            for (long v : values) sum += v;

            int barY = y + 10;
            int barH = 18;
            g.setColor(Theme.bgHover());
            g.fill(new RoundRectangle2D.Double(x, barY, width, barH, 8, 8));

            if (sum > 0) {
                int curX = x;
                for (int i = 0; i < values.length; i++) {
                    int segW = (i == values.length - 1)
                            ? (x + width - curX)
                            : (int) Math.round((values[i] / (double) sum) * width);
                    if (values[i] > 0 && segW > 0) {
                        g.setColor(colors[i]);
                        g.fillRect(curX, barY, segW, barH);
                        curX += segW;
                    }
                }
            }

            // Chú thích legend
            int lx = x;
            int ly = barY + barH + 18;
            g.setFont(Theme.fontTiny());
            for (int i = 0; i < labels.length; i++) {
                g.setColor(colors[i]);
                g.fillOval(lx, ly - 8, 9, 9);
                g.setColor(Theme.textPrimary());
                String txt = labels[i] + ": " + values[i];
                g.drawString(txt, lx + 14, ly);
                lx += g.getFontMetrics().stringWidth(txt) + 28;
            }
        }
    }
}

