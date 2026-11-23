package com.mailapp.client.ui.frames;

import com.mailapp.client.model.LoginResponse;
import com.mailapp.client.model.PublicConfigDto;
import com.mailapp.client.model.UserDto;
import com.mailapp.client.net.ApiClient;
import com.mailapp.client.net.ApiException;
import com.mailapp.client.ui.dialogs.ChangePasswordDialog;
import com.mailapp.client.ui.theme.AppIcons;
import com.mailapp.client.ui.theme.AuthUiKit;
import com.mailapp.client.ui.theme.Theme;
import com.mailapp.client.util.ClientConfig;
import com.mailapp.client.util.SessionStore;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.geom.RoundRectangle2D;

/**
 * Màn hình Đăng nhập hiện đại của Veyra (thuần Java Swing + Java2D):
 * Bố cục chia đôi (Hero cực quang động bên trái + Thẻ đăng nhập đổ bóng mềm bên phải),
 * tự thu gọn linh hoạt khi cửa sổ hẹp, ô nhập bo góc phát sáng focus, ẩn/hiện mật khẩu,
 * rung báo lỗi mượt và nút gradient có vòng xoay tải.
 */
public class LoginFrame extends JFrame {

    private final ApiClient apiClient;

    private AuthUiKit.AuroraHeroPanel heroPanel;
    private JPanel rightContainer;
    private AuthUiKit.ElevatedCardPanel cardPanel;
    private JPanel compactBrandHeader;

    private JPanel maintenanceBanner;
    private JLabel maintenanceLabel;

    private JLabel welcomeTitle;
    private JLabel welcomeSub;
    private JLabel userLbl;
    private JLabel passLbl;
    private JLabel noAccountLbl;
    private JLabel compactTitleLbl;

    private JTextField usernameField;
    private JPasswordField passwordField;
    private AuthUiKit.ModernFieldWrapper userWrapper;
    private AuthUiKit.ModernFieldWrapper passWrapper;

    private AuthUiKit.AnimatedGradientButton loginButton;
    private JPanel oauthDivider;
    private AuthUiKit.GoogleSignInButton googleLoginButton;
    private AuthUiKit.AccentLinkButton registerButton;
    private AuthUiKit.PillGhostButton testConnectionButton;
    private AuthUiKit.PillGhostButton themeToggleButton;

    private JLabel connectionStatusLabel;
    private JLabel errorLabel;

    private Timer rateLimitTimer;
    private int remainingSeconds = 0;

    public LoginFrame(ApiClient apiClient) {
        this(apiClient, true);
    }

    public LoginFrame(ApiClient apiClient, boolean autoFetch) {
        super("Đăng Nhập — " + AppIcons.BRAND_FULL);
        this.apiClient = apiClient;

        initComponents();
        setupEvents();
        if (autoFetch) {
            fetchPublicConfig();
        }
    }

    private void initComponents() {
        setIconImages(AppIcons.getWindowIcons());
        setTitle("Đăng Nhập — " + AppIcons.BRAND_FULL);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(940, 650);
        setMinimumSize(new Dimension(440, 580));
        setLocationRelativeTo(null);
        setResizable(true);

        JPanel outerPanel = new JPanel(new BorderLayout());
        outerPanel.setBackground(Theme.bgApp());

        // Dải cảnh báo cố định khi bật -Dmailclient.insecure=true
        if (ClientConfig.getInstance().isInsecureMode()) {
            outerPanel.add(ClientConfig.createInsecureBanner(), BorderLayout.NORTH);
        }

        // Bố cục chia đôi: Trái = AuroraHeroPanel (44%), Phải = Form Card (56%)
        JPanel splitRoot = new JPanel(new GridBagLayout());
        splitRoot.setBackground(Theme.bgApp());
        GridBagConstraints rootGbc = new GridBagConstraints();
        rootGbc.fill = GridBagConstraints.BOTH;
        rootGbc.gridy = 0;
        rootGbc.weighty = 1.0;

        heroPanel = new AuthUiKit.AuroraHeroPanel(apiClient.getBaseUrl());
        heroPanel.setPreferredSize(new Dimension(390, 560));
        rootGbc.gridx = 0;
        rootGbc.weightx = 0.44;
        splitRoot.add(heroPanel, rootGbc);

        // Vùng phải: nền mềm + thanh công cụ nhỏ (đổi theme) + thẻ nổi ở giữa
        rightContainer = new JPanel(new BorderLayout());
        rightContainer.setBackground(Theme.bgApp());
        rightContainer.setBorder(new EmptyBorder(14, 24, 18, 24));

        // Thanh trên cùng bên phải: nút chuyển nhanh Sáng/Tối
        JPanel topBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        topBar.setOpaque(false);
        themeToggleButton = new AuthUiKit.PillGhostButton(
                Theme.isDark() ? "Chế độ Sáng" : "Chế độ Tối",
                AppIcons.vectorIcon(Theme.isDark() ? AppIcons.Glyph.SUN : AppIcons.Glyph.MOON, 13, Theme.textSecondary())
        );
        themeToggleButton.setFocusable(false);
        topBar.add(themeToggleButton);
        rightContainer.add(topBar, BorderLayout.NORTH);

        // Bọc thẻ nổi căn giữa theo cả 2 chiều
        JPanel cardCenterWrap = new JPanel(new GridBagLayout());
        cardCenterWrap.setOpaque(false);

        cardPanel = new AuthUiKit.ElevatedCardPanel(18);
        cardPanel.setLayout(new BoxLayout(cardPanel, BoxLayout.Y_AXIS));
        cardPanel.setPreferredSize(new Dimension(422, 540));
        cardPanel.setMaximumSize(new Dimension(438, 600));

        // Header thương hiệu gọn (chỉ hiện khi cửa sổ thu hẹp < 700px và ẩn heroPanel)
        compactBrandHeader = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        compactBrandHeader.setOpaque(false);
        compactBrandHeader.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel miniLogo = new JLabel(AppIcons.logoIcon(32));
        compactTitleLbl = new JLabel(AppIcons.BRAND_UPPER);
        compactTitleLbl.setFont(new Font(Theme.fontTitle().getFamily(), Font.BOLD, 18));
        compactTitleLbl.setForeground(Theme.primaryText());
        compactBrandHeader.add(miniLogo);
        compactBrandHeader.add(compactTitleLbl);
        compactBrandHeader.setVisible(false);

        // Banner bảo trì bo góc (ẩn mặc định)
        maintenanceBanner = new JPanel(new BorderLayout(8, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Theme.warningSoft());
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 12, 12));
                g2.setColor(Theme.warning());
                g2.setStroke(new BasicStroke(1f));
                g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 1f, getHeight() - 1f, 12, 12));
                g2.dispose();
            }
        };
        maintenanceBanner.setOpaque(false);
        maintenanceBanner.setBorder(new EmptyBorder(9, 12, 9, 12));
        maintenanceBanner.setAlignmentX(Component.LEFT_ALIGNMENT);
        maintenanceBanner.setMaximumSize(new Dimension(Integer.MAX_VALUE, 64));
        maintenanceLabel = new JLabel("<html><b>[!] HỆ THỐNG ĐANG BẢO TRÌ</b><br>Chỉ Quản trị viên (ADMIN) mới có thể đăng nhập.</html>");
        maintenanceLabel.setFont(Theme.fontTiny());
        maintenanceLabel.setForeground(Theme.warning());
        maintenanceBanner.add(maintenanceLabel, BorderLayout.CENTER);
        maintenanceBanner.setVisible(false);

        // Tiêu đề chào mừng
        welcomeTitle = new JLabel("Chào mừng trở lại");
        welcomeTitle.setFont(new Font(Theme.fontDisplay().getFamily(), Font.BOLD, 24));
        welcomeTitle.setForeground(Theme.textPrimary());
        welcomeTitle.setAlignmentX(Component.LEFT_ALIGNMENT);

        welcomeSub = new JLabel("Đăng nhập vào không gian thư " + AppIcons.BRAND_NAME + " của bạn");
        welcomeSub.setFont(Theme.fontSmall());
        welcomeSub.setForeground(Theme.textSecondary());
        welcomeSub.setAlignmentX(Component.LEFT_ALIGNMENT);

        // Ô tên đăng nhập hoặc Gmail
        userLbl = AuthUiKit.fieldLabel("Tên đăng nhập hoặc Gmail");
        usernameField = new JTextField(20);
        userWrapper = new AuthUiKit.ModernFieldWrapper(usernameField, AppIcons.Glyph.USER, "Nhập tên đăng nhập hoặc địa chỉ Gmail...");
        userWrapper.setAlignmentX(Component.LEFT_ALIGNMENT);

        // Ô mật khẩu (có nút ẩn/hiện)
        passLbl = AuthUiKit.fieldLabel("Mật khẩu");
        passwordField = new JPasswordField(20);
        passWrapper = new AuthUiKit.ModernFieldWrapper(passwordField, AppIcons.Glyph.LOCK, "Nhập mật khẩu của bạn...");
        passWrapper.setAlignmentX(Component.LEFT_ALIGNMENT);

        // Thông báo lỗi
        errorLabel = new JLabel(" ");
        errorLabel.setFont(Theme.fontSmall().deriveFont(Font.BOLD));
        errorLabel.setForeground(Theme.danger());
        errorLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        // Nút đăng nhập gradient + animation
        loginButton = new AuthUiKit.AnimatedGradientButton("Đăng Nhập");
        loginButton.setAlignmentX(Component.LEFT_ALIGNMENT);

        // Phân cách + Nút Đăng nhập bằng Google OAuth2 (tự hợp nhất nếu trùng email)
        oauthDivider = AuthUiKit.orDivider("hoặc");
        googleLoginButton = new AuthUiKit.GoogleSignInButton("Tiếp tục với Google (OAuth2)");
        googleLoginButton.setToolTipText("Đăng nhập nhanh bằng Google OAuth2; tự động hợp nhất nếu email đã có tài khoản");

        // Hàng đăng ký
        JPanel regRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 4, 0));
        regRow.setOpaque(false);
        regRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        regRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        noAccountLbl = new JLabel("Chưa có tài khoản?");
        noAccountLbl.setFont(Theme.fontSmall());
        noAccountLbl.setForeground(Theme.textSecondary());
        registerButton = new AuthUiKit.AccentLinkButton("Đăng ký ngay");
        regRow.add(noAccountLbl);
        regRow.add(registerButton);

        // Hàng kiểm tra kết nối máy chủ
        JPanel pingRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        pingRow.setOpaque(false);
        pingRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        pingRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        testConnectionButton = new AuthUiKit.PillGhostButton(
                "Kiểm tra kết nối máy chủ",
                AppIcons.vectorIcon(AppIcons.Glyph.SERVER, 13, Theme.textSecondary())
        );
        pingRow.add(testConnectionButton);

        connectionStatusLabel = new JLabel(" ", SwingConstants.CENTER);
        connectionStatusLabel.setFont(Theme.fontTiny().deriveFont(Font.BOLD));
        connectionStatusLabel.setForeground(Theme.textSecondary());
        connectionStatusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        connectionStatusLabel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));

        // Ghép các phần tử vào thẻ với khoảng thở gọn gàng (bội số 4px)
        cardPanel.add(compactBrandHeader);
        cardPanel.add(maintenanceBanner);
        cardPanel.add(Box.createVerticalStrut(4));
        cardPanel.add(welcomeTitle);
        cardPanel.add(Box.createVerticalStrut(4));
        cardPanel.add(welcomeSub);
        cardPanel.add(Box.createVerticalStrut(20));

        cardPanel.add(userLbl);
        cardPanel.add(Box.createVerticalStrut(6));
        cardPanel.add(userWrapper);
        cardPanel.add(Box.createVerticalStrut(14));

        cardPanel.add(passLbl);
        cardPanel.add(Box.createVerticalStrut(6));
        cardPanel.add(passWrapper);
        cardPanel.add(Box.createVerticalStrut(10));

        cardPanel.add(errorLabel);
        cardPanel.add(Box.createVerticalStrut(12));

        cardPanel.add(loginButton);
        cardPanel.add(Box.createVerticalStrut(10));
        cardPanel.add(oauthDivider);
        cardPanel.add(Box.createVerticalStrut(10));
        cardPanel.add(googleLoginButton);
        cardPanel.add(Box.createVerticalStrut(12));

        cardPanel.add(regRow);
        cardPanel.add(Box.createVerticalStrut(8));
        cardPanel.add(pingRow);
        cardPanel.add(Box.createVerticalStrut(6));
        cardPanel.add(connectionStatusLabel);

        GridBagConstraints cardGbc = new GridBagConstraints();
        cardGbc.gridx = 0;
        cardGbc.gridy = 0;
        cardGbc.weightx = 1.0;
        cardGbc.weighty = 1.0;
        cardGbc.fill = GridBagConstraints.NONE;
        cardGbc.anchor = GridBagConstraints.CENTER;
        cardCenterWrap.add(cardPanel, cardGbc);

        rightContainer.add(cardCenterWrap, BorderLayout.CENTER);

        rootGbc.gridx = 1;
        rootGbc.weightx = 0.56;
        splitRoot.add(rightContainer, rootGbc);

        outerPanel.add(splitRoot, BorderLayout.CENTER);
        setContentPane(outerPanel);

        // Tự động ẩn Hero bên trái khi cửa sổ thu hẹp (< 700px) để không bao giờ bị chật hay cắt chữ
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                updateResponsiveLayout();
            }
        });

        // Thứ tự điều hướng Tab chuẩn từ trên xuống dưới (5 phần tử)
        java.util.List<Component> tabOrder = getExpectedTabOrder();
        setFocusTraversalPolicyProvider(true);
        setFocusTraversalPolicy(new FocusTraversalPolicy() {
            @Override public Component getComponentAfter(Container a, Component c) {
                int idx = tabOrder.indexOf(c);
                return tabOrder.get((idx + 1) % tabOrder.size());
            }
            @Override public Component getComponentBefore(Container a, Component c) {
                int idx = tabOrder.indexOf(c);
                return tabOrder.get((idx - 1 + tabOrder.size()) % tabOrder.size());
            }
            @Override public Component getFirstComponent(Container a) { return tabOrder.get(0); }
            @Override public Component getLastComponent(Container a) { return tabOrder.get(tabOrder.size() - 1); }
            @Override public Component getDefaultComponent(Container a) { return tabOrder.get(0); }
        });
    }

    private void updateResponsiveLayout() {
        boolean wide = getWidth() >= 700;
        if (heroPanel.isVisible() != wide) {
            heroPanel.setVisible(wide);
            compactBrandHeader.setVisible(!wide);
            revalidate();
            repaint();
        }
    }

    private void applyThemeColors() {
        getContentPane().setBackground(Theme.bgApp());
        rightContainer.setBackground(Theme.bgApp());
        welcomeTitle.setForeground(Theme.textPrimary());
        welcomeSub.setForeground(Theme.textSecondary());
        userLbl.setForeground(Theme.textPrimary());
        passLbl.setForeground(Theme.textPrimary());
        noAccountLbl.setForeground(Theme.textSecondary());
        compactTitleLbl.setForeground(Theme.primaryText());
        errorLabel.setForeground(Theme.danger());
        maintenanceLabel.setForeground(Theme.warning());
        userWrapper.refreshTheme();
        passWrapper.refreshTheme();
        themeToggleButton.setText(Theme.isDark() ? "Chế độ Sáng" : "Chế độ Tối");
        themeToggleButton.setIcon(AppIcons.vectorIcon(
                Theme.isDark() ? AppIcons.Glyph.SUN : AppIcons.Glyph.MOON, 13, Theme.textSecondary()));
        testConnectionButton.setIcon(AppIcons.vectorIcon(AppIcons.Glyph.SERVER, 13, Theme.textSecondary()));
        repaint();
    }

    public java.util.List<Component> getExpectedTabOrder() {
        return java.util.List.of(usernameField, passwordField, loginButton, googleLoginButton, registerButton, testConnectionButton);
    }

    private void setupEvents() {
        loginButton.addActionListener(e -> executeLogin());
        googleLoginButton.addActionListener(e -> executeGoogleOAuthLogin());
        usernameField.addActionListener(e -> passwordField.requestFocusInWindow());
        passwordField.addActionListener(e -> executeLogin());

        registerButton.addActionListener(e -> {
            RegisterFrame registerFrame = new RegisterFrame(apiClient, prefilledUsername -> {
                setPrefilledUsername(prefilledUsername);
                setVisible(true);
            });
            registerFrame.setVisible(true);
        });

        testConnectionButton.addActionListener(e -> checkServerConnection());

        themeToggleButton.addActionListener(e -> {
            Theme.setDark(!Theme.isDark());
            applyThemeColors();
        });
    }

    /**
     * Kiểm tra trạng thái máy chủ và đo độ trễ kết nối HTTPS.
     */
    public void checkServerConnection() {
        connectionStatusLabel.setText("Đang kiểm tra kết nối tới " + apiClient.getBaseUrl() + "...");
        connectionStatusLabel.setForeground(Theme.textSecondary());

        SwingWorker<Long, Void> worker = new SwingWorker<>() {
            @Override
            protected Long doInBackground() throws Exception {
                return apiClient.pingServer();
            }

            @Override
            protected void done() {
                try {
                    long latency = get();
                    connectionStatusLabel.setText("[OK] Kết nối TLS máy chủ thành công (" + latency + " ms)");
                    connectionStatusLabel.setForeground(Theme.success());
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    connectionStatusLabel.setText("[X] Lỗi kết nối máy chủ: " + cause.getMessage());
                    connectionStatusLabel.setForeground(Theme.danger());
                }
            }
        };
        worker.execute();
    }

    /**
     * [EP-P1] Lấy cấu hình công khai của hệ thống để điều chỉnh giao diện.
     */
    public void fetchPublicConfig() {
        SwingWorker<PublicConfigDto, Void> worker = new SwingWorker<>() {
            @Override
            protected PublicConfigDto doInBackground() throws Exception {
                return apiClient.getPublicConfig();
            }

            @Override
            protected void done() {
                try {
                    PublicConfigDto config = get();
                    SessionStore.getInstance().setPublicConfig(config);
                    applyPublicConfig(config);
                } catch (Exception ignored) {
                    // Nếu lỗi kết nối ban đầu, dùng cấu hình mặc định
                }
            }
        };
        worker.execute();
    }

    public void applyPublicConfig(PublicConfigDto config) {
        if (config == null) return;

        maintenanceBanner.setVisible(config.maintenanceMode());
        boolean oauthEnabled = config.oauth2LinkEnabled();
        oauthDivider.setVisible(oauthEnabled);
        googleLoginButton.setVisible(oauthEnabled);
        registerButton.setVisible(config.registrationEnabled());
        noAccountLbl.setVisible(config.registrationEnabled());

        revalidate();
        repaint();
    }

    /**
     * Luồng Đăng nhập trực tiếp bằng Google OAuth2 (PKCE S256):
     * Mở trình duyệt để xác thực Google, tự động hợp nhất nếu trùng email đã đăng ký bằng mật khẩu,
     * rồi nhận JWT vào thẳng MainFrame (kèm hộp thư Gmail & ảnh đại diện sẵn sàng).
     */
    private void executeGoogleOAuthLogin() {
        errorLabel.setText(" ");
        connectionStatusLabel.setText(" ");
        resetBorders();

        JDialog waitDialog = new JDialog(this, "Đăng Nhập Google OAuth2 — " + AppIcons.BRAND_FULL, true);
        waitDialog.setIconImages(AppIcons.getWindowIcons());
        waitDialog.setSize(480, 210);
        waitDialog.setLocationRelativeTo(this);
        waitDialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        JPanel panel = new JPanel(new BorderLayout(10, 12));
        panel.setBackground(Theme.bgSurface());
        panel.setBorder(new EmptyBorder(18, 20, 16, 20));

        JLabel statusLbl = new JLabel("<html><b>Đang khởi tạo phiên Google OAuth2...</b><br>Đang kết nối tới máy chủ " + AppIcons.BRAND_NAME + ".</html>");
        statusLbl.setFont(Theme.fontSmall());
        statusLbl.setForeground(Theme.textPrimary());

        JProgressBar bar = new JProgressBar(0, 120);
        bar.setStringPainted(true);
        bar.setString("Đang chuẩn bị...");

        JButton cancelBtn = Theme.secondaryButton("Hủy bỏ", null);
        JPanel bot = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        bot.setOpaque(false);
        bot.add(cancelBtn);

        panel.add(statusLbl, BorderLayout.NORTH);
        panel.add(bar, BorderLayout.CENTER);
        panel.add(bot, BorderLayout.SOUTH);
        waitDialog.setContentPane(panel);

        java.util.concurrent.atomic.AtomicBoolean cancelled = new java.util.concurrent.atomic.AtomicBoolean(false);

        SwingWorker<ApiClient.GoogleLoginPollDto, Integer> worker = new SwingWorker<>() {
            @Override
            protected ApiClient.GoogleLoginPollDto doInBackground() throws Exception {
                ApiClient.GoogleLoginStartDto start = apiClient.startGoogleOAuthLogin();
                boolean opened = false;
                try {
                    if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                        Desktop.getDesktop().browse(new java.net.URI(start.authorizeUrl()));
                        opened = true;
                    }
                } catch (Exception ignored) {}

                final boolean browserOpened = opened;
                SwingUtilities.invokeLater(() -> statusLbl.setText(browserOpened
                        ? "<html><b>Trình duyệt đã mở.</b><br>Hãy chọn tài khoản Google và giữ nguyên quyền Gmail để đăng nhập.<br><small>(Nếu trùng email đã có mật khẩu, hệ thống sẽ tự động hợp nhất.)</small></html>"
                        : "<html><b>Hãy mở liên kết sau trên trình duyệt:</b><br><small>" + start.authorizeUrl() + "</small></html>"));

                for (int i = 1; i <= 120; i++) {
                    if (cancelled.get() || isCancelled()) {
                        return null;
                    }
                    publish(i);
                    try {
                        Thread.sleep(1500L);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return null;
                    }
                    if (cancelled.get() || isCancelled()) {
                        return null;
                    }
                    ApiClient.GoogleLoginPollDto poll = apiClient.pollGoogleOAuthLogin(start.pollToken());
                    if (poll != null && !"PENDING".equalsIgnoreCase(poll.status())) {
                        return poll;
                    }
                }
                return new ApiClient.GoogleLoginPollDto("FAILED", null, "TIMEOUT", "Hết thời gian chờ xác thực Google (3 phút). Vui lòng thử lại.");
            }

            @Override
            protected void process(java.util.List<Integer> chunks) {
                int step = chunks.get(chunks.size() - 1);
                bar.setValue(step);
                bar.setString("Đang chờ xác nhận trên trình duyệt (" + Math.round(step * 1.5) + "s / 180s)...");
            }

            @Override
            protected void done() {
                if (cancelled.get() || isCancelled()) {
                    return;
                }
                waitDialog.dispose();
                try {
                    ApiClient.GoogleLoginPollDto result = get();
                    if (result == null) return;
                    if ("SUCCESS".equalsIgnoreCase(result.status()) && result.loginResponse() != null) {
                        handleLoginSuccess(result.loginResponse());
                    } else {
                        String msg = (result.message() != null && !result.message().isBlank())
                                ? result.message()
                                : "Đăng nhập Google OAuth2 không thành công.";
                        errorLabel.setText(msg);
                        cardPanel.triggerShake();
                    }
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    errorLabel.setText("Lỗi đăng nhập Google: " + cause.getMessage());
                    cardPanel.triggerShake();
                }
            }
        };

        cancelBtn.addActionListener(e -> {
            cancelled.set(true);
            worker.cancel(true);
            waitDialog.dispose();
        });
        waitDialog.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosed(java.awt.event.WindowEvent e) {
                cancelled.set(true);
                worker.cancel(true);
            }
        });

        worker.execute();
        waitDialog.setVisible(true);
    }

    private void executeLogin() {
        String username = usernameField.getText().trim();
        String password = new String(passwordField.getPassword());

        errorLabel.setText(" ");
        connectionStatusLabel.setText(" ");
        resetBorders();

        if (username.isEmpty()) {
            userWrapper.setError(true);
            errorLabel.setText("Vui lòng nhập tên đăng nhập.");
            cardPanel.triggerShake();
            usernameField.requestFocusInWindow();
            return;
        }

        if (password.isEmpty()) {
            passWrapper.setError(true);
            errorLabel.setText("Vui lòng nhập mật khẩu.");
            cardPanel.triggerShake();
            passwordField.requestFocusInWindow();
            return;
        }

        loginButton.setLoading(true, "Đang xác thực...");

        SwingWorker<LoginResponse, Void> worker = new SwingWorker<>() {
            @Override
            protected LoginResponse doInBackground() throws Exception {
                return apiClient.login(username, password);
            }

            @Override
            protected void done() {
                loginButton.setLoading(false, "Đăng Nhập");

                try {
                    LoginResponse response = get();
                    handleLoginSuccess(response);
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    if (cause instanceof ApiException apiEx) {
                        handleLoginError(apiEx);
                    } else {
                        errorLabel.setText("Lỗi kết nối máy chủ: " + cause.getMessage());
                        cardPanel.triggerShake();
                    }
                }
            }
        };

        worker.execute();
    }

    private void handleLoginSuccess(LoginResponse response) {
        UserDto user = response.user();
        SessionStore.getInstance().saveSession(response.accessToken(), user);

        // Nếu mustChangePassword=true, BẮT BUỘC mở ChangePasswordDialog (modal, không tắt được)
        if (user != null && user.mustChangePassword()) {
            ChangePasswordDialog dialog = new ChangePasswordDialog(this, apiClient, true);
            dialog.setVisible(true);

            if (!dialog.isPasswordChangedSuccessfully()) {
                SessionStore.getInstance().clearSession();
                apiClient.clearAccessToken();
                errorLabel.setText("Bạn chưa hoàn tất đổi mật khẩu bắt buộc.");
                return;
            }
        }

        MainFrame mainFrame = new MainFrame(apiClient);
        mainFrame.setVisible(true);
        dispose();
    }

    private void handleLoginError(ApiException ex) {
        int status = ex.getStatusCode();
        String code = ex.getCode();
        cardPanel.triggerShake();

        if (status == 401 || "INVALID_CREDENTIALS".equalsIgnoreCase(code)) {
            userWrapper.setError(true);
            passWrapper.setError(true);
            errorLabel.setText("Sai tên đăng nhập hoặc mật khẩu.");
            passwordField.setText("");
            passwordField.requestFocusInWindow();
        } else if (status == 403 && "ACCOUNT_LOCKED".equalsIgnoreCase(code)) {
            errorLabel.setText("Tài khoản đã bị khóa. Vui lòng liên hệ Quản trị viên.");
        } else if (status == 429) {
            startRateLimitCountdown(ex.getRetryAfterSeconds());
        } else if (status == 503 || "MAINTENANCE_MODE".equalsIgnoreCase(code)) {
            maintenanceBanner.setVisible(true);
            errorLabel.setText("Hệ thống đang bảo trì. Chỉ Quản trị viên mới được đăng nhập.");
        } else {
            errorLabel.setText(ex.getMessage());
        }
    }

    private void startRateLimitCountdown(int seconds) {
        remainingSeconds = seconds > 0 ? seconds : 60;
        loginButton.setEnabled(false);

        if (rateLimitTimer != null && rateLimitTimer.isRunning()) {
            rateLimitTimer.stop();
        }

        errorLabel.setText("Đăng nhập sai quá 5 lần. Thử lại sau " + remainingSeconds + "s.");

        rateLimitTimer = new Timer(1000, (ActionEvent e) -> {
            remainingSeconds--;
            if (remainingSeconds <= 0) {
                rateLimitTimer.stop();
                loginButton.setEnabled(true);
                loginButton.setText("Đăng Nhập");
                errorLabel.setText(" ");
            } else {
                errorLabel.setText("Đăng nhập sai quá 5 lần. Thử lại sau " + remainingSeconds + "s.");
            }
        });
        rateLimitTimer.start();
    }

    public void setPrefilledUsername(String username) {
        this.usernameField.setText(username);
        this.passwordField.setText("");
        this.passwordField.requestFocusInWindow();
    }

    public void setPassword(String password) {
        this.passwordField.setText(password);
    }

    public void setErrorMessage(String message) {
        this.errorLabel.setText(message);
    }

    private void resetBorders() {
        userWrapper.setError(false);
        passWrapper.setError(false);
    }

    public JPanel getMaintenanceBanner() {
        return maintenanceBanner;
    }

    @Override
    public void dispose() {
        if (rateLimitTimer != null && rateLimitTimer.isRunning()) rateLimitTimer.stop();
        if (heroPanel != null) heroPanel.stopAnimation();
        if (cardPanel != null) cardPanel.stopAnimation();
        if (userWrapper != null) userWrapper.stopAnimation();
        if (passWrapper != null) passWrapper.stopAnimation();
        if (loginButton != null) loginButton.stopAnimation();
        super.dispose();
    }
}
