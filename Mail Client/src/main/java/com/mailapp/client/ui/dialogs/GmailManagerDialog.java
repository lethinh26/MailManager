package com.mailapp.client.ui.dialogs;

import com.mailapp.client.model.GmailAccountDto;
import com.mailapp.client.model.PublicConfigDto;
import com.mailapp.client.net.ApiClient;
import com.mailapp.client.net.ApiException;
import com.mailapp.client.ui.theme.AppIcons;
import com.mailapp.client.ui.theme.AvatarUtil;
import com.mailapp.client.ui.theme.Theme;
import com.mailapp.client.util.ClientConfig;
import com.mailapp.client.util.EmailNormalizer;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Trình quản lý tài khoản Gmail (hỗ trợ đa phương thức đăng nhập Google OAuth2 + App Password trên cùng 1 hộp thư):
 * - Mỗi hộp thư hiển thị MỘT dòng/thẻ gồm huy hiệu "Google" và "App Password" (đã có / chưa có),
 *   dấu sao ★ cho phương thức chính, dải cam cảnh báo fallback kèm nút Cấp lại quyền khi fallbackActive.
 * - Hành động: Thêm phương thức đăng nhập, Đặt làm chính, Gỡ phương thức, Đổi tên, Kiểm tra kết nối, Xóa liên kết.
 * - Wizard App Password kiểm tra trước theo email_key ngay trên form trước khi gọi mạng và xử lý outcome từ Server.
 * - Mọi lời gọi mạng đều chạy trên SwingWorker.
 */
public class GmailManagerDialog extends JDialog {

    private final ApiClient apiClient;
    private final PublicConfigDto publicConfig;
    private final Runnable onStartOAuth2;
    private final Runnable onChangedCallback;

    private JPanel listContainer;
    private JLabel statusLabel;
    private List<GmailAccountDto> currentAccounts = List.of();

    public record AppPasswordPrecheckResult(boolean matchedExisting, String bannerMessage, String buttonLabel) {}

    public GmailManagerDialog(Frame owner,
                              ApiClient apiClient,
                              PublicConfigDto publicConfig,
                              Runnable onStartOAuth2,
                              Runnable onChangedCallback) {
        super(owner, "Quản lý Tài khoản Gmail — " + AppIcons.BRAND_FULL, true);
        this.apiClient = apiClient;
        this.publicConfig = publicConfig;
        this.onStartOAuth2 = onStartOAuth2;
        this.onChangedCallback = onChangedCallback;

        setIconImages(AppIcons.getWindowIcons());
        setSize(820, 580);
        setMinimumSize(new Dimension(660, 460));
        setLocationRelativeTo(owner);

        initUI();
        refreshAccounts();
    }

    /**
     * Kiểm tra cục bộ ngay trên form Wizard App Password dựa trên hàm chuẩn hóa EmailNormalizer.normalizeKey
     * trước khi gọi mạng. Trả thông điệp thân thiện và nhãn nút tương ứng.
     */
    public static AppPasswordPrecheckResult evaluateAppPasswordPrecheck(String enteredEmail,
                                                                        List<GmailAccountDto> existingAccounts) {
        String key = EmailNormalizer.normalizeKey(enteredEmail);
        if (key.isEmpty() || !key.contains("@") || existingAccounts == null || existingAccounts.isEmpty()) {
            return new AppPasswordPrecheckResult(false, null, "Kiểm tra IMAP & Liên kết →");
        }
        for (GmailAccountDto acc : existingAccounts) {
            String accKey = (acc.emailKey() != null && !acc.emailKey().isBlank())
                    ? acc.emailKey()
                    : EmailNormalizer.normalizeKey(acc.email());
            if (key.equals(accKey)) {
                if (acc.hasOAuth() && !acc.hasAppPassword()) {
                    return new AppPasswordPrecheckResult(
                            true,
                            "Hộp thư này đã được liên kết bằng Google. Bấm 'Thêm App Password' để dùng làm dự phòng",
                            "Thêm App Password"
                    );
                }
                if (acc.hasAppPassword()) {
                    return new AppPasswordPrecheckResult(
                            true,
                            "Hộp thư này đã có App Password. Bấm 'Cập nhật App Password' để thay mật khẩu ứng dụng mới.",
                            "Cập nhật App Password"
                    );
                }
            }
        }
        return new AppPasswordPrecheckResult(false, null, "Kiểm tra IMAP & Liên kết →");
    }

    /**
     * Chuyển kết quả outcome (CREATED / METHOD_ADDED / METHOD_UPDATED) từ Server thành thông điệp tiếng Việt thân thiện.
     */
    public static String formatLinkOutcomeMessage(GmailAccountDto linked) {
        if (linked == null) {
            return "✓ Liên kết hộp thư thành công.";
        }
        String outcome = linked.outcome() != null ? linked.outcome().toUpperCase() : "CREATED";
        String heading = switch (outcome) {
            case "METHOD_ADDED" -> "✓ ĐÃ THÊM PHƯƠNG THỨC ĐĂNG NHẬP!";
            case "METHOD_UPDATED" -> "✓ ĐÃ CẬP NHẬT PHƯƠNG THỨC ĐĂNG NHẬP!";
            default -> "✓ LIÊN KẾT THÀNH CÔNG!";
        };
        StringBuilder sb = new StringBuilder(heading).append("\n\n");
        if (linked.message() != null && !linked.message().isBlank()) {
            sb.append(linked.message()).append("\n\n");
        }
        sb.append("Email: ").append(linked.email())
                .append("\nTên hiển thị: ").append(linked.displayName())
                .append("\nPhương thức chính: ").append(methodLabel(linked.effectivePreferredMethod()))
                .append("\nTrạng thái: ").append(linked.status())
                .append("\n\nBạn có thể bấm 'Hoàn tất' để tiếp tục đọc và gửi thư.");
        return sb.toString();
    }

    private static String methodLabel(String method) {
        if ("OAUTH2".equalsIgnoreCase(method)) return "Google (OAuth2)";
        if ("APP_PASSWORD".equalsIgnoreCase(method)) return "App Password";
        return method != null ? method : "Chưa rõ";
    }

    private void initUI() {
        JPanel outer = new JPanel(new BorderLayout());
        outer.setBackground(Theme.bgApp());
        if (ClientConfig.getInstance().isInsecureMode()) {
            outer.add(ClientConfig.createInsecureBanner(), BorderLayout.NORTH);
        }

        JPanel main = new JPanel(new BorderLayout(0, Theme.SP_3));
        main.setBackground(Theme.bgSurface());
        main.setBorder(new EmptyBorder(Theme.SP_4, Theme.SP_4, Theme.SP_4, Theme.SP_4));

        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.setOpaque(false);

        JLabel title = new JLabel("Tài khoản Gmail đã liên kết  ");
        title.setFont(Theme.fontTitle());
        title.setForeground(Theme.textPrimary());
        title.setBorder(new EmptyBorder(0, 0, 0, 12));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        title.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));

        JLabel subtitle = new JLabel("Mỗi hộp thư có thể gắn đồng thời Google OAuth2 và App Password (dự phòng tự động qua IMAP/SMTP).");
        subtitle.setFont(Theme.fontSmall());
        subtitle.setForeground(Theme.textSecondary());
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel linkBtns = Theme.createWrapPanel(Theme.SP_2, Theme.SP_1);
        linkBtns.setAlignmentX(Component.LEFT_ALIGNMENT);

        boolean allowOAuth = publicConfig == null || publicConfig.oauth2LinkEnabled();
        boolean allowAppPass = publicConfig == null || publicConfig.appPasswordLinkEnabled();

        if (allowOAuth) {
            JButton oauthBtn = Theme.primaryButton("Đăng nhập Google (OAuth2)",
                    AppIcons.vectorIcon(AppIcons.Glyph.SHIELD, 16, Color.WHITE));
            oauthBtn.addActionListener(e -> {
                dispose();
                if (onStartOAuth2 != null) onStartOAuth2.run();
            });
            linkBtns.add(oauthBtn);
        }

        if (allowAppPass) {
            JButton appPassBtn = Theme.successButton("Dùng App Password (3 bước)",
                    AppIcons.vectorIcon(AppIcons.Glyph.PLUS, 16, Color.WHITE));
            appPassBtn.addActionListener(e -> openAppPasswordWizard(null, null, false));
            linkBtns.add(appPassBtn);
        }

        JButton reloadBtn = Theme.secondaryButton("Làm mới",
                AppIcons.vectorIcon(AppIcons.Glyph.REFRESH, 15, Theme.textPrimary()));
        reloadBtn.addActionListener(e -> refreshAccounts());
        linkBtns.add(reloadBtn);

        top.add(title);
        top.add(Box.createVerticalStrut(2));
        top.add(subtitle);
        top.add(Box.createVerticalStrut(Theme.SP_2));
        top.add(linkBtns);
        main.add(top, BorderLayout.NORTH);

        listContainer = new ScrollableListPanel();
        listContainer.setLayout(new BoxLayout(listContainer, BoxLayout.Y_AXIS));
        listContainer.setBackground(Theme.bgApp());
        listContainer.setBorder(new EmptyBorder(Theme.SP_3, Theme.SP_3, Theme.SP_3, Theme.SP_3));

        JScrollPane scroll = new JScrollPane(listContainer);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(BorderFactory.createLineBorder(Theme.border(), 1));
        scroll.getViewport().setBackground(Theme.bgApp());
        main.add(scroll, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setOpaque(false);
        statusLabel = new JLabel(" ");
        statusLabel.setFont(Theme.fontSmall());
        statusLabel.setForeground(Theme.textSecondary());
        JButton closeBtn = Theme.secondaryButton("Đóng", null);
        closeBtn.addActionListener(e -> dispose());
        bottom.add(statusLabel, BorderLayout.WEST);
        bottom.add(closeBtn, BorderLayout.EAST);
        main.add(bottom, BorderLayout.SOUTH);

        outer.add(main, BorderLayout.CENTER);
        setContentPane(outer);
    }

    private void refreshAccounts() {
        statusLabel.setText("Đang tải danh sách tài khoản Gmail...");
        SwingWorker<List<GmailAccountDto>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<GmailAccountDto> doInBackground() throws Exception {
                return apiClient.getGmailAccounts();
            }

            @Override
            protected void done() {
                try {
                    List<GmailAccountDto> list = get();
                    currentAccounts = list != null ? list : List.of();
                    renderAccounts(currentAccounts);
                    statusLabel.setText("Đã cập nhật danh sách (" + currentAccounts.size() + " hộp thư).");
                } catch (Exception ex) {
                    statusLabel.setText("Lỗi tải danh sách: " + ex.getMessage());
                }
            }
        };
        worker.execute();
    }

    private void renderAccounts(List<GmailAccountDto> accounts) {
        listContainer.removeAll();
        if (accounts == null || accounts.isEmpty()) {
            JLabel empty = new JLabel("Chưa có tài khoản Gmail nào được liên kết. Hãy bấm một trong hai nút liên kết phía trên.");
            empty.setFont(Theme.fontRegular());
            empty.setForeground(Theme.textSecondary());
            empty.setBorder(new EmptyBorder(Theme.SP_6, Theme.SP_4, Theme.SP_6, Theme.SP_4));
            listContainer.add(empty);
        } else {
            for (GmailAccountDto acc : accounts) {
                listContainer.add(createAccountRow(acc));
                listContainer.add(Box.createVerticalStrut(Theme.SP_2));
            }
        }
        listContainer.revalidate();
        listContainer.repaint();
    }

    private JPanel createAccountRow(GmailAccountDto acc) {
        boolean needsReauth = "NEEDS_REAUTH".equalsIgnoreCase(acc.status());
        boolean fallbackActive = acc.isFallbackActive();
        boolean hasOAuth = acc.hasOAuth();
        boolean hasAppPass = acc.hasAppPassword();
        String preferred = acc.effectivePreferredMethod();
        boolean oauthPrimary = "OAUTH2".equalsIgnoreCase(preferred);
        boolean appPassPrimary = "APP_PASSWORD".equalsIgnoreCase(preferred);

        JPanel card = new JPanel(new BorderLayout(Theme.SP_3, Theme.SP_2)) {
            @Override
            public Dimension getMaximumSize() {
                Dimension pref = getPreferredSize();
                return new Dimension(Integer.MAX_VALUE, pref.height);
            }
        };
        card.setBackground(Theme.bgSurface());
        Color borderColor = (fallbackActive || needsReauth) ? Theme.warning() : Theme.border();
        card.setBorder(new CompoundBorder(
                BorderFactory.createLineBorder(borderColor, (fallbackActive || needsReauth) ? 2 : 1),
                new EmptyBorder(Theme.SP_3, Theme.SP_3, Theme.SP_2, Theme.SP_3)
        ));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel avatar = new JLabel(AvatarUtil.createAvatarIcon(acc.displayName(), acc.email(), 42));
        if (acc.id() != null) {
            new SwingWorker<java.awt.image.BufferedImage, Void>() {
                @Override protected java.awt.image.BufferedImage doInBackground() throws Exception {
                    byte[] bytes = apiClient.getGmailAccountAvatar(acc.id());
                    if (bytes == null || bytes.length == 0) return null;
                    return javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(bytes));
                }
                @Override protected void done() {
                    try {
                        java.awt.image.BufferedImage photo = get();
                        if (photo != null) {
                            avatar.setIcon(AvatarUtil.createCircularPhotoIcon(photo, 42));
                        }
                    } catch (Exception ignored) {}
                }
            }.execute();
        }

        JPanel avWrap = new JPanel(new BorderLayout());
        avWrap.setOpaque(false);
        avWrap.add(avatar, BorderLayout.NORTH);

        JPanel info = new JPanel();
        info.setOpaque(false);
        info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));

        JPanel nameRow = Theme.createWrapPanel(6, 4);
        nameRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel nameLbl = new JLabel(acc.displayName() != null && !acc.displayName().isBlank() ? acc.displayName() : acc.email());
        nameLbl.setFont(Theme.fontSubTitle());
        nameLbl.setForeground(Theme.textPrimary());
        nameRow.add(nameLbl);

        // Huy hiệu Google (đã có / chưa có) + dấu sao ★ cho phương thức chính
        JLabel googleBadge = hasOAuth
                ? Theme.badge(oauthPrimary ? "★ Google" : "✓ Google", Theme.bgSelected(), Theme.primaryText())
                : Theme.badge("Google (chưa có)", Theme.bgHover(), Theme.textMuted());
        googleBadge.setToolTipText(hasOAuth
                ? (oauthPrimary ? "Google OAuth2 — Phương thức chính" : "Google OAuth2 — Đã liên kết")
                : "Chưa liên kết Google OAuth2");

        // Huy hiệu App Password (đã có / chưa có) + dấu sao ★ cho phương thức chính
        JLabel appPassBadge = hasAppPass
                ? Theme.badge(appPassPrimary ? "★ App Password" : "✓ App Password", Theme.successSoft(), Theme.success())
                : Theme.badge("App Password (chưa có)", Theme.bgHover(), Theme.textMuted());
        appPassBadge.setToolTipText(hasAppPass
                ? (appPassPrimary ? "App Password — Phương thức chính" : "App Password — Dự phòng sẵn sàng")
                : "Chưa thêm App Password");

        JLabel statusBadge = fallbackActive
                ? Theme.badge("DỰ PHÒNG", Theme.warningSoft(), Theme.warning())
                : (needsReauth
                ? Theme.badge("CẦN CẤP LẠI QUYỀN", Theme.warningSoft(), Theme.warning())
                : Theme.badge("HOẠT ĐỘNG", Theme.successSoft(), Theme.success()));

        nameRow.add(googleBadge);
        nameRow.add(appPassBadge);
        nameRow.add(statusBadge);

        JLabel emailLbl = new JLabel(acc.email());
        emailLbl.setFont(Theme.fontSmall());
        emailLbl.setForeground(Theme.textSecondary());
        emailLbl.setBorder(new EmptyBorder(0, 6, 0, 0));
        emailLbl.setAlignmentX(Component.LEFT_ALIGNMENT);

        String dates = "Ngày liên kết: " + shortTime(acc.createdAt())
                + "   •   Kiểm tra gần nhất: " + shortTime(acc.lastVerifiedAt())
                + "   •   Đang dùng: " + methodLabel(acc.effectiveActiveMethod());
        JLabel metaLbl = new JLabel(dates);
        metaLbl.setFont(Theme.fontTiny());
        metaLbl.setForeground(Theme.textMuted());
        metaLbl.setBorder(new EmptyBorder(0, 6, 0, 0));
        metaLbl.setAlignmentX(Component.LEFT_ALIGNMENT);

        info.add(nameRow);
        info.add(Box.createVerticalStrut(3));
        info.add(emailLbl);
        info.add(Box.createVerticalStrut(3));
        info.add(metaLbl);

        // Dải cam nổi bật khi đang chạy App Password dự phòng (Google OAuth2 cần cấp lại quyền)
        if (fallbackActive) {
            JPanel fallbackStrip = new JPanel(new BorderLayout(8, 0));
            fallbackStrip.setBackground(Theme.warningSoft());
            fallbackStrip.setBorder(new CompoundBorder(
                    BorderFactory.createLineBorder(Theme.warning(), 1),
                    new EmptyBorder(6, 10, 6, 10)
            ));
            fallbackStrip.setAlignmentX(Component.LEFT_ALIGNMENT);

            JLabel warnText = new JLabel("Đang dùng App Password dự phòng - Google cần cấp lại quyền");
            warnText.setFont(Theme.fontSmall().deriveFont(Font.BOLD));
            warnText.setForeground(Theme.warning());

            JButton reauthBtn = Theme.primaryButton("Cấp lại quyền", null);
            reauthBtn.addActionListener(e -> {
                dispose();
                if (onStartOAuth2 != null) onStartOAuth2.run();
            });

            fallbackStrip.add(warnText, BorderLayout.CENTER);
            fallbackStrip.add(reauthBtn, BorderLayout.EAST);
            info.add(Box.createVerticalStrut(6));
            info.add(fallbackStrip);
        }

        JPanel topBody = new JPanel(new BorderLayout(Theme.SP_3, 0));
        topBody.setOpaque(false);
        topBody.add(avWrap, BorderLayout.WEST);
        topBody.add(info, BorderLayout.CENTER);

        JPanel actions = Theme.createWrapPanel(6, 4);
        actions.setBorder(new CompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.border()),
                new EmptyBorder(6, 0, 0, 0)
        ));

        boolean allowOAuth = publicConfig == null || publicConfig.oauth2LinkEnabled();
        boolean allowAppPass = publicConfig == null || publicConfig.appPasswordLinkEnabled();

        // Nút "Thêm phương thức đăng nhập" khi hộp thư mới có 1 phương thức
        if ((!hasOAuth && allowOAuth) || (!hasAppPass && allowAppPass)) {
            JButton addMethodBtn = Theme.primaryButton("Thêm phương thức đăng nhập", null);
            addMethodBtn.addActionListener(e -> handleAddMethod(acc));
            actions.add(addMethodBtn);
        }

        // Nút "Đặt làm chính" khi hộp thư đã có cả 2 phương thức
        if (hasOAuth && hasAppPass) {
            String targetMethod = oauthPrimary ? "APP_PASSWORD" : "OAUTH2";
            String targetLabel = oauthPrimary ? "App Password" : "Google";
            JButton setPrimaryBtn = Theme.secondaryButton("Đặt " + targetLabel + " làm chính", null);
            setPrimaryBtn.setToolTipText("Đặt làm chính");
            setPrimaryBtn.addActionListener(e -> setPreferredMethod(acc, targetMethod));
            actions.add(setPrimaryBtn);
        }

        // Nút "Gỡ phương thức"
        JButton removeMethodBtn = Theme.secondaryButton("Gỡ phương thức", null);
        removeMethodBtn.addActionListener(e -> handleRemoveMethod(acc));
        actions.add(removeMethodBtn);

        JButton renameBtn = Theme.secondaryButton("Đổi tên", null);
        renameBtn.addActionListener(e -> renameAccount(acc));
        actions.add(renameBtn);

        JButton testBtn = Theme.secondaryButton("Kiểm tra kết nối", null);
        testBtn.addActionListener(e -> testConnection(acc, testBtn));
        actions.add(testBtn);

        if (needsReauth && !fallbackActive) {
            JButton reauthBtn = Theme.primaryButton("Cấp lại quyền", null);
            reauthBtn.addActionListener(e -> {
                dispose();
                if (onStartOAuth2 != null) onStartOAuth2.run();
            });
            actions.add(reauthBtn);
        }

        JButton delBtn = Theme.dangerButton("Xóa liên kết", null);
        delBtn.addActionListener(e -> unlinkAccount(acc));
        actions.add(delBtn);

        card.add(topBody, BorderLayout.CENTER);
        card.add(actions, BorderLayout.SOUTH);
        return card;
    }

    private void handleAddMethod(GmailAccountDto acc) {
        if (!acc.hasAppPassword()) {
            openAppPasswordWizard(acc.email(), acc.displayName(), true);
        } else if (!acc.hasOAuth()) {
            dispose();
            if (onStartOAuth2 != null) onStartOAuth2.run();
        }
    }

    private void setPreferredMethod(GmailAccountDto acc, String targetMethod) {
        statusLabel.setText("Đang đổi phương thức chính cho " + acc.email() + "...");
        SwingWorker<GmailAccountDto, Void> w = new SwingWorker<>() {
            @Override protected GmailAccountDto doInBackground() throws Exception {
                return apiClient.setPreferredGmailMethod(acc.id(), targetMethod);
            }
            @Override protected void done() {
                try {
                    GmailAccountDto updated = get();
                    String msg = (updated != null && updated.message() != null)
                            ? updated.message()
                            : "Đã đổi phương thức đăng nhập chính thành công.";
                    statusLabel.setText(msg);
                    refreshAccounts();
                    if (onChangedCallback != null) onChangedCallback.run();
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    JOptionPane.showMessageDialog(GmailManagerDialog.this,
                            "Không thể đổi phương thức chính: " + cause.getMessage(),
                            "Thông báo", JOptionPane.WARNING_MESSAGE);
                }
            }
        };
        w.execute();
    }

    private void handleRemoveMethod(GmailAccountDto acc) {
        List<String> options = new ArrayList<>();
        if (acc.hasOAuth()) options.add("Google (OAUTH2)");
        if (acc.hasAppPassword()) options.add("App Password (APP_PASSWORD)");
        if (options.isEmpty()) return;

        String chosen;
        if (options.size() == 1) {
            chosen = options.get(0);
        } else {
            Object pick = JOptionPane.showInputDialog(
                    this,
                    "Chọn phương thức đăng nhập muốn gỡ khỏi hộp thư " + acc.email() + ":",
                    "Gỡ phương thức đăng nhập",
                    JOptionPane.QUESTION_MESSAGE,
                    null,
                    options.toArray(),
                    options.get(0)
            );
            if (pick == null) return;
            chosen = pick.toString();
        }

        String methodCode = chosen.contains("OAUTH2") ? "OAUTH2" : "APP_PASSWORD";
        boolean isLastMethod = options.size() == 1;

        String confirmMsg = isLastMethod
                ? "Đây là phương thức đăng nhập cuối cùng của hộp thư " + acc.email()
                + ".\nGỡ phương thức này sẽ hủy toàn bộ liên kết hộp thư. Bạn có chắc chắn muốn tiếp tục?"
                : "Bạn có chắc muốn gỡ phương thức " + chosen + " khỏi hộp thư " + acc.email() + "?"
                + ("OAUTH2".equals(methodCode) ? "\n(Token Google OAuth2 sẽ được thu hồi, hộp thư vẫn hoạt động bằng App Password.)" : "");

        int ok = JOptionPane.showConfirmDialog(this, confirmMsg, "Xác nhận gỡ phương thức",
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (ok != JOptionPane.YES_OPTION) return;

        statusLabel.setText("Đang gỡ phương thức " + methodCode + "...");
        SwingWorker<Map<String, Object>, Void> w = new SwingWorker<>() {
            @Override protected Map<String, Object> doInBackground() throws Exception {
                return apiClient.removeGmailMethod(acc.id(), methodCode, isLastMethod);
            }
            @Override protected void done() {
                try {
                    Map<String, Object> res = get();
                    String msg = res != null && res.get("message") != null
                            ? String.valueOf(res.get("message"))
                            : "Đã gỡ phương thức đăng nhập thành công.";
                    statusLabel.setText(msg);
                    JOptionPane.showMessageDialog(GmailManagerDialog.this, msg,
                            "Hoàn tất", JOptionPane.INFORMATION_MESSAGE);
                    refreshAccounts();
                    if (onChangedCallback != null) onChangedCallback.run();
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    JOptionPane.showMessageDialog(GmailManagerDialog.this,
                            "Không thể gỡ phương thức: " + cause.getMessage(),
                            "Thông báo", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        w.execute();
    }

    /** Panel khóa chiều rộng theo JScrollPane viewport để không xuất hiện thanh cuộn ngang. */
    private static class ScrollableListPanel extends JPanel implements Scrollable {
        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return 24; }
        @Override public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return 96; }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() { return false; }
    }

    private void renameAccount(GmailAccountDto acc) {
        String newName = JOptionPane.showInputDialog(this, "Nhập tên hiển thị mới cho " + acc.email() + ":", acc.displayName());
        if (newName == null || newName.isBlank()) return;

        SwingWorker<GmailAccountDto, Void> w = new SwingWorker<>() {
            @Override protected GmailAccountDto doInBackground() throws Exception {
                return apiClient.patchGmailDisplayName(acc.id(), newName.trim());
            }
            @Override protected void done() {
                try {
                    get();
                    refreshAccounts();
                    if (onChangedCallback != null) onChangedCallback.run();
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(GmailManagerDialog.this, "Lỗi đổi tên: " + ex.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        w.execute();
    }

    private void testConnection(GmailAccountDto acc, JButton btn) {
        btn.setEnabled(false);
        SwingWorker<Map<String, Object>, Void> w = new SwingWorker<>() {
            @Override protected Map<String, Object> doInBackground() throws Exception {
                return apiClient.testGmailConnection(acc.id());
            }
            @Override protected void done() {
                try {
                    Map<String, Object> res = get();
                    btn.setEnabled(true);
                    String active = res.get("activeMethod") != null ? String.valueOf(res.get("activeMethod")) : "";
                    boolean fb = Boolean.TRUE.equals(res.get("fallbackActive"));
                    JOptionPane.showMessageDialog(GmailManagerDialog.this,
                            "Kết nối thành công!\n• IMAP: " + res.get("imapOk")
                                    + " (INBOX: " + res.get("inboxCount") + " thư)"
                                    + "\n• SMTP: " + res.get("smtpOk")
                                    + (active.isBlank() ? "" : "\n• Phương thức đang dùng: " + methodLabel(active) + (fb ? " (dự phòng)" : ""))
                                    + "\n• Độ trễ: " + res.get("durationMs") + " ms",
                            "Kiểm tra kết nối Gmail", JOptionPane.INFORMATION_MESSAGE);
                    refreshAccounts();
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    if (cause instanceof ApiException ae && ae.getStatusCode() == 404) {
                        btn.setEnabled(false);
                        btn.setToolTipText("Chỉ khả dụng trong môi trường phát triển (dev)");
                        statusLabel.setText("Endpoint selftest chỉ khả dụng trong profile dev.");
                    } else {
                        btn.setEnabled(true);
                        JOptionPane.showMessageDialog(GmailManagerDialog.this,
                                "Kiểm tra kết nối thất bại: " + cause.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
                    }
                }
            }
        };
        w.execute();
    }

    private void unlinkAccount(GmailAccountDto acc) {
        int c = JOptionPane.showConfirmDialog(this,
                "Bạn có chắc chắn muốn hủy liên kết tài khoản " + acc.email() + "?\n(Nếu có OAuth2, token trên Google cũng sẽ được thu hồi.)",
                "Xác nhận hủy liên kết", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (c != JOptionPane.YES_OPTION) return;

        SwingWorker<Void, Void> w = new SwingWorker<>() {
            @Override protected Void doInBackground() throws Exception {
                apiClient.deleteGmailAccount(acc.id());
                return null;
            }
            @Override protected void done() {
                try {
                    get();
                    refreshAccounts();
                    if (onChangedCallback != null) onChangedCallback.run();
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(GmailManagerDialog.this, "Lỗi hủy liên kết: " + ex.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        w.execute();
    }

    /**
     * Wizard 3 bước liên kết hoặc bổ sung App Password (EP-G2).
     * Kiểm tra ngay email đã có theo email_key (dùng EmailNormalizer) ngay trên form trước khi gọi mạng.
     */
    private void openAppPasswordWizard(String prefillEmail, String prefillDisplayName, boolean jumpToStep2) {
        JDialog wiz = new JDialog(this, "Liên kết Gmail bằng App Password — Hướng dẫn 3 bước", true);
        wiz.setIconImages(AppIcons.getWindowIcons());
        wiz.setSize(640, 460);
        wiz.setMinimumSize(new Dimension(560, 410));
        wiz.setLocationRelativeTo(this);

        CardLayout cl = new CardLayout();
        JPanel cards = new JPanel(cl);
        cards.setBackground(Theme.bgSurface());

        // === Bước 1: Hướng dẫn bật 2-Step Verification & tạo App Password ===
        JPanel step1 = new JPanel(new BorderLayout(0, Theme.SP_3));
        step1.setBackground(Theme.bgSurface());
        step1.setBorder(new EmptyBorder(Theme.SP_4, Theme.SP_4, Theme.SP_4, Theme.SP_4));

        JLabel s1Title = new JLabel("Bước 1/3: Chuẩn bị App Password trên Google");
        s1Title.setFont(Theme.fontSubTitle());
        s1Title.setForeground(Theme.primaryText());

        JTextArea guide = new JTextArea(
                "1. Truy cập Bảo mật Tài khoản Google và bật Xác minh 2 bước (2-Step Verification).\n"
                + "2. Mở trang App Passwords của Google, đặt tên '" + AppIcons.BRAND_FULL + "' rồi bấm Tạo.\n"
                + "3. Sao chép chuỗi mật khẩu ứng dụng 16 ký tự (có thể giữ nguyên khoảng trắng).\n"
                + "4. Đảm bảo đã bật IMAP trong Gmail Web -> Cài đặt -> Chuyển tiếp và POP/IMAP."
        );
        guide.setEditable(false);
        guide.setLineWrap(true);
        guide.setWrapStyleWord(true);
        guide.setFont(Theme.fontRegular());
        guide.setBackground(Theme.bgSurfaceAlt());
        guide.setForeground(Theme.textPrimary());
        guide.setBorder(new EmptyBorder(Theme.SP_3, Theme.SP_3, Theme.SP_3, Theme.SP_3));

        JButton openGoogleBtn = Theme.secondaryButton("Mở Google App Passwords", null);
        openGoogleBtn.addActionListener(e -> {
            try { Desktop.getDesktop().browse(new URI("https://myaccount.google.com/apppasswords")); }
            catch (Exception ignored) {}
        });

        JButton next1Btn = Theme.primaryButton("Tiếp tục (Bước 2) →", null);
        next1Btn.addActionListener(e -> cl.show(cards, "STEP2"));

        JPanel s1Bot = Theme.createWrapPanel(Theme.SP_2, Theme.SP_1);
        s1Bot.add(openGoogleBtn);
        s1Bot.add(next1Btn);

        step1.add(s1Title, BorderLayout.NORTH);
        step1.add(guide, BorderLayout.CENTER);
        step1.add(s1Bot, BorderLayout.SOUTH);

        // === Bước 2: Nhập Email + JPasswordField + Kiểm tra trước email_key ngay trên form ===
        JPanel step2 = new JPanel(new BorderLayout(0, Theme.SP_3));
        step2.setBackground(Theme.bgSurface());
        step2.setBorder(new EmptyBorder(Theme.SP_4, Theme.SP_4, Theme.SP_4, Theme.SP_4));

        JLabel s2Title = new JLabel("Bước 2/3: Nhập thông tin Gmail & App Password");
        s2Title.setFont(Theme.fontSubTitle());
        s2Title.setForeground(Theme.primaryText());

        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(6, 4, 6, 4);
        g.fill = GridBagConstraints.HORIZONTAL;

        JTextField emailField = new JTextField(24);
        JPasswordField passField = new JPasswordField(24);
        JTextField nameField = new JTextField(24);
        Theme.styleTextField(emailField);
        Theme.styleTextField(passField);
        Theme.styleTextField(nameField);

        JLabel lEmail = new JLabel("Email Gmail (*):");
        lEmail.setFont(Theme.fontRegular());
        lEmail.setForeground(Theme.textPrimary());
        JLabel lPass = new JLabel("App Password 16 ký tự (*):");
        lPass.setFont(Theme.fontRegular());
        lPass.setForeground(Theme.textPrimary());
        JLabel lName = new JLabel("Tên hiển thị:");
        lName.setFont(Theme.fontRegular());
        lName.setForeground(Theme.textPrimary());

        g.gridx = 0; g.gridy = 0; g.weightx = 0; form.add(lEmail, g);
        g.gridx = 1; g.weightx = 1; form.add(emailField, g);

        // Banner thông báo ngay khi phát hiện hộp thư đã tồn tại theo email_key (không chờ gọi mạng!)
        JPanel precheckBanner = new JPanel(new BorderLayout());
        precheckBanner.setBackground(Theme.bgSelected());
        precheckBanner.setBorder(new CompoundBorder(
                BorderFactory.createLineBorder(Theme.primary(), 1),
                new EmptyBorder(6, 10, 6, 10)
        ));
        JLabel precheckLbl = new JLabel();
        precheckLbl.setFont(Theme.fontSmall().deriveFont(Font.BOLD));
        precheckLbl.setForeground(Theme.primaryText());
        precheckBanner.add(precheckLbl, BorderLayout.CENTER);
        precheckBanner.setVisible(false);

        g.gridx = 0; g.gridy = 1; g.gridwidth = 2; g.weightx = 1; form.add(precheckBanner, g);
        g.gridwidth = 1;

        g.gridx = 0; g.gridy = 2; g.weightx = 0; form.add(lPass, g);
        g.gridx = 1; g.weightx = 1; form.add(passField, g);
        g.gridx = 0; g.gridy = 3; g.weightx = 0; form.add(lName, g);
        g.gridx = 1; g.weightx = 1; form.add(nameField, g);

        JLabel step2Err = new JLabel(" ");
        step2Err.setForeground(Theme.danger());
        step2Err.setFont(Theme.fontSmall());
        g.gridx = 0; g.gridy = 4; g.gridwidth = 2; form.add(step2Err, g);

        // === Bước 3: Thử kết nối IMAP thật & Báo kết quả theo outcome ===
        JPanel step3 = new JPanel(new BorderLayout(0, Theme.SP_3));
        step3.setBackground(Theme.bgSurface());
        step3.setBorder(new EmptyBorder(Theme.SP_4, Theme.SP_4, Theme.SP_4, Theme.SP_4));
        JLabel s3Title = new JLabel("Bước 3/3: Kiểm tra kết nối IMAP & Lưu bảo mật");
        s3Title.setFont(Theme.fontSubTitle());
        s3Title.setForeground(Theme.primaryText());
        JTextArea s3Log = new JTextArea();
        s3Log.setEditable(false);
        s3Log.setLineWrap(true);
        s3Log.setWrapStyleWord(true);
        s3Log.setFont(Theme.fontRegular());
        s3Log.setBackground(Theme.bgSurfaceAlt());
        s3Log.setForeground(Theme.textPrimary());
        s3Log.setBorder(new EmptyBorder(Theme.SP_2, Theme.SP_2, Theme.SP_2, Theme.SP_2));
        JProgressBar s3Bar = new JProgressBar();
        s3Bar.setIndeterminate(true);
        JButton s3BackBtn = Theme.secondaryButton("← Quay lại sửa", null);
        JButton s3DoneBtn = Theme.primaryButton("Hoàn tất", null);
        s3DoneBtn.setEnabled(false);
        s3BackBtn.addActionListener(e -> cl.show(cards, "STEP2"));
        s3DoneBtn.addActionListener(e -> wiz.dispose());

        JPanel s3Bot = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        s3Bot.setOpaque(false);
        s3Bot.add(s3BackBtn);
        s3Bot.add(s3DoneBtn);
        JPanel s3Center = new JPanel(new BorderLayout(0, 8));
        s3Center.setOpaque(false);
        s3Center.add(s3Bar, BorderLayout.NORTH);
        s3Center.add(new JScrollPane(s3Log), BorderLayout.CENTER);
        step3.add(s3Title, BorderLayout.NORTH);
        step3.add(s3Center, BorderLayout.CENTER);
        step3.add(s3Bot, BorderLayout.SOUTH);

        JButton back2Btn = Theme.secondaryButton("← Bước 1", null);
        back2Btn.addActionListener(e -> cl.show(cards, "STEP1"));
        JButton verifyBtn = Theme.successButton("Kiểm tra IMAP & Liên kết →", null);

        Runnable updatePrecheck = () -> {
            AppPasswordPrecheckResult res = evaluateAppPasswordPrecheck(emailField.getText(), currentAccounts);
            if (res.matchedExisting()) {
                precheckLbl.setText("<html>" + res.bannerMessage() + "</html>");
                precheckBanner.setVisible(true);
            } else {
                precheckBanner.setVisible(false);
            }
            verifyBtn.setText(res.buttonLabel());
            form.revalidate();
            form.repaint();
        };

        emailField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { updatePrecheck.run(); }
            @Override public void removeUpdate(DocumentEvent e) { updatePrecheck.run(); }
            @Override public void changedUpdate(DocumentEvent e) { updatePrecheck.run(); }
        });

        if (prefillEmail != null && !prefillEmail.isBlank()) {
            emailField.setText(prefillEmail);
        }
        if (prefillDisplayName != null && !prefillDisplayName.isBlank()) {
            nameField.setText(prefillDisplayName);
        }
        updatePrecheck.run();

        verifyBtn.addActionListener(e -> {
            String email = emailField.getText().trim();
            String disp = nameField.getText().trim();
            char[] pwChars = passField.getPassword();
            if (email.isEmpty() || pwChars.length == 0) {
                Arrays.fill(pwChars, '\0');
                step2Err.setText("Vui lòng nhập đầy đủ Email và App Password.");
                return;
            }
            final String appPass = new String(pwChars);
            Arrays.fill(pwChars, '\0'); // Xóa mảng char[] khỏi RAM ngay sau khi sử dụng
            passField.setText("");

            cl.show(cards, "STEP3");
            s3Bar.setVisible(true);
            s3BackBtn.setEnabled(false);
            s3DoneBtn.setEnabled(false);
            s3Log.setText("Đang kết nối IMAPS (993) tới máy chủ Gmail để xác thực...\n(Mật khẩu được truyền qua TLS và mã hóa AES-256-GCM trên Server.)");

            SwingWorker<GmailAccountDto, Void> w = new SwingWorker<>() {
                @Override protected GmailAccountDto doInBackground() throws Exception {
                    return apiClient.linkGmailWithAppPassword(email, appPass, disp);
                }
                @Override protected void done() {
                    s3Bar.setVisible(false);
                    try {
                        GmailAccountDto linked = get();
                        s3Log.setText(formatLinkOutcomeMessage(linked));
                        s3DoneBtn.setEnabled(true);
                        refreshAccounts();
                        if (onChangedCallback != null) onChangedCallback.run();
                    } catch (Exception ex) {
                        s3BackBtn.setEnabled(true);
                        Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                        String detail = cause.getMessage();
                        if (cause instanceof ApiException ae) {
                            detail = switch (ae.getCode()) {
                                case "GMAIL_AUTH_FAILED" -> "✗ SAI MẬT KHẨU HOẶC EMAIL:\n" + ae.getMessage();
                                case "IMAP_DISABLED" -> "✗ IMAP CHƯA ĐƯỢC BẬT TRÊN GMAIL:\n" + ae.getMessage();
                                case "TWO_STEP_VERIFICATION_REQUIRED" -> "✗ CHƯA BẬT XÁC MINH 2 BƯỚC:\n" + ae.getMessage();
                                default -> "✗ THÔNG BÁO [" + ae.getCode() + "]:\n" + ae.getMessage();
                            };
                        }
                        s3Log.setText(detail);
                    }
                }
            };
            w.execute();
        });

        JPanel s2Bot = new JPanel(new BorderLayout());
        s2Bot.setOpaque(false);
        s2Bot.add(back2Btn, BorderLayout.WEST);
        s2Bot.add(verifyBtn, BorderLayout.EAST);
        step2.add(s2Title, BorderLayout.NORTH);
        step2.add(form, BorderLayout.CENTER);
        step2.add(s2Bot, BorderLayout.SOUTH);

        cards.add(step1, "STEP1");
        cards.add(step2, "STEP2");
        cards.add(step3, "STEP3");
        wiz.setContentPane(cards);
        if (jumpToStep2) {
            cl.show(cards, "STEP2");
        }
        wiz.setVisible(true);
    }

    private String shortTime(String iso) {
        if (iso == null || iso.isBlank()) return "Chưa có";
        return iso.length() > 19 ? iso.substring(0, 19).replace('T', ' ') : iso.replace('T', ' ');
    }
}
