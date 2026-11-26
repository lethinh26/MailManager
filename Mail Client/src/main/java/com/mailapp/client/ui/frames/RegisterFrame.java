package com.mailapp.client.ui.frames;

import com.mailapp.client.model.RegisterRequest;
import com.mailapp.client.model.PublicConfigDto;
import com.mailapp.client.model.UserDto;
import com.mailapp.client.net.ApiClient;
import com.mailapp.client.net.ApiException;
import com.mailapp.client.ui.theme.AppIcons;
import com.mailapp.client.ui.theme.AuthUiKit;
import com.mailapp.client.ui.theme.Theme;
import com.mailapp.client.util.ClientConfig;
import com.mailapp.client.util.ClientSafetyUtil;
import com.mailapp.client.util.PasswordValidator;
import com.mailapp.client.util.FeatureAdmission;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.util.function.Consumer;

/**
 * Màn hình Đăng ký tài khoản mới của Veyra — đồng bộ ngôn ngữ thiết kế thẻ nổi,
 * ô nhập bo góc phát sáng focus, thanh đo độ mạnh mật khẩu tùy vẽ bằng Java2D và nút gradient.
 */
public class RegisterFrame extends JFrame {

    private final ApiClient apiClient;
    private final Consumer<String> onRegisterSuccess;

    private AuthUiKit.ElevatedCardPanel cardPanel;

    private JTextField usernameField;
    private JTextField fullNameField;
    private JTextField emailField;
    private JPasswordField passwordField;
    private JPasswordField confirmPasswordField;

    private AuthUiKit.ModernFieldWrapper userWrap;
    private AuthUiKit.ModernFieldWrapper fullNameWrap;
    private AuthUiKit.ModernFieldWrapper emailWrap;
    private AuthUiKit.ModernFieldWrapper passWrap;
    private AuthUiKit.ModernFieldWrapper confirmWrap;

    private int strengthPercent = 0;
    private Color strengthColor = Theme.border();
    private JPanel strengthBarPanel;
    private JLabel strengthLabel;
    private JLabel matchLabel;
    private JLabel errorLabel;

    private AuthUiKit.AnimatedGradientButton registerButton;
    private JButton backToLoginButton;
    private final FeatureAdmission registrationAdmission = new FeatureAdmission(true);
    private boolean submitting;
    private volatile boolean disposed;

    public RegisterFrame(ApiClient apiClient, Consumer<String> onRegisterSuccess) {
        super("Đăng Ký Tài Khoản — " + AppIcons.BRAND_FULL);
        this.apiClient = apiClient;
        this.onRegisterSuccess = onRegisterSuccess;

        initComponents();
        setupEvents();
    }

    private void initComponents() {
        setIconImages(AppIcons.getWindowIcons());
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(580, 680);
        setMinimumSize(new Dimension(480, 580));
        setLocationRelativeTo(null);
        setResizable(true);

        JPanel outerPanel = new JPanel(new BorderLayout());
        outerPanel.setBackground(Theme.bgApp());
        if (ClientConfig.getInstance().isInsecureMode()) {
            outerPanel.add(ClientConfig.createInsecureBanner(), BorderLayout.NORTH);
        }

        JPanel centerStage = new JPanel(new GridBagLayout());
        centerStage.setBackground(Theme.bgApp());
        centerStage.setBorder(new EmptyBorder(12, 18, 14, 18));

        cardPanel = new AuthUiKit.ElevatedCardPanel(18);
        cardPanel.setLayout(new BoxLayout(cardPanel, BoxLayout.Y_AXIS));
        cardPanel.setPreferredSize(new Dimension(500, 610));
        cardPanel.setMaximumSize(new Dimension(520, 650));

        // Header thương hiệu + tiêu đề (BorderLayout đảm bảo không bao giờ cắt chữ khi HiDPI)
        JPanel headerRow = new JPanel(new BorderLayout(12, 0));
        headerRow.setOpaque(false);
        headerRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        headerRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 52));
        headerRow.add(new JLabel(AppIcons.logoIcon(42)), BorderLayout.WEST);

        JPanel titleStack = new JPanel();
        titleStack.setOpaque(false);
        titleStack.setLayout(new BoxLayout(titleStack, BoxLayout.Y_AXIS));
        JLabel titleLabel = new JLabel("Tạo tài khoản " + AppIcons.BRAND_NAME + "  ");
        titleLabel.setFont(Theme.fontTitle());
        titleLabel.setForeground(Theme.textPrimary());
        titleLabel.setBorder(new EmptyBorder(0, 0, 0, 12));
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        titleLabel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        JLabel subLabel = new JLabel("Điền thông tin để bắt đầu sử dụng hộp thư bảo mật.");
        subLabel.setFont(Theme.fontSmall());
        subLabel.setForeground(Theme.textSecondary());
        subLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        titleStack.add(titleLabel);
        titleStack.add(Box.createVerticalStrut(2));
        titleStack.add(subLabel);
        headerRow.add(titleStack, BorderLayout.CENTER);

        usernameField = new JTextField(20);
        userWrap = new AuthUiKit.ModernFieldWrapper(usernameField, AppIcons.Glyph.USER, "VD: nguyen_van_a (3–64 ký tự)");
        userWrap.setAlignmentX(Component.LEFT_ALIGNMENT);

        fullNameField = new JTextField(20);
        fullNameWrap = new AuthUiKit.ModernFieldWrapper(fullNameField, AppIcons.Glyph.USER, "Nhập họ và tên đầy đủ");
        fullNameWrap.setAlignmentX(Component.LEFT_ALIGNMENT);

        emailField = new JTextField(20);
        emailWrap = new AuthUiKit.ModernFieldWrapper(emailField, AppIcons.Glyph.MAIL, "VD: ban@ptit.edu.vn");
        emailWrap.setAlignmentX(Component.LEFT_ALIGNMENT);

        passwordField = new JPasswordField(20);
        passWrap = new AuthUiKit.ModernFieldWrapper(passwordField, AppIcons.Glyph.LOCK, "Ít nhất 8 ký tự gồm chữ & số");
        passWrap.setAlignmentX(Component.LEFT_ALIGNMENT);

        // Thanh đo độ mạnh mật khẩu vẽ bo tròn mượt bằng Java2D
        strengthBarPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth(), h = getHeight();
                g2.setColor(Theme.bgHover());
                g2.fill(new RoundRectangle2D.Float(0, 0, w, h, h, h));
                if (strengthPercent > 0) {
                    int fillW = Math.max(h, Math.round(w * (strengthPercent / 100f)));
                    g2.setColor(strengthColor);
                    g2.fill(new RoundRectangle2D.Float(0, 0, fillW, h, h, h));
                }
                g2.dispose();
            }
        };
        strengthBarPanel.setOpaque(false);
        strengthBarPanel.setPreferredSize(new Dimension(200, 6));
        strengthBarPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 6));
        strengthBarPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        strengthLabel = new JLabel("Cần ít nhất 8 ký tự gồm cả chữ và số");
        strengthLabel.setFont(Theme.fontTiny());
        strengthLabel.setForeground(Theme.textSecondary());
        strengthLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        confirmPasswordField = new JPasswordField(20);
        confirmWrap = new AuthUiKit.ModernFieldWrapper(confirmPasswordField, AppIcons.Glyph.LOCK, "Nhập lại mật khẩu để xác nhận");
        confirmWrap.setAlignmentX(Component.LEFT_ALIGNMENT);

        matchLabel = new JLabel(" ");
        matchLabel.setFont(Theme.fontTiny().deriveFont(Font.BOLD));
        matchLabel.setForeground(Theme.textSecondary());
        matchLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        errorLabel = new JLabel(" ");
        errorLabel.setFont(Theme.fontSmall().deriveFont(Font.BOLD));
        errorLabel.setForeground(Theme.danger());
        errorLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        // Hàng nút hành động
        JPanel buttonPanel = new JPanel(new BorderLayout(10, 0));
        buttonPanel.setOpaque(false);
        buttonPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        buttonPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));

        backToLoginButton = Theme.secondaryButton("Quay lại Đăng nhập", null);
        backToLoginButton.addActionListener(e -> dispose());

        registerButton = new AuthUiKit.AnimatedGradientButton("Đăng Ký Tài Khoản");
        registerButton.setPreferredSize(new Dimension(210, 44));

        buttonPanel.add(backToLoginButton, BorderLayout.WEST);
        buttonPanel.add(registerButton, BorderLayout.CENTER);

        cardPanel.add(headerRow);
        cardPanel.add(Box.createVerticalStrut(16));
        cardPanel.add(AuthUiKit.fieldLabel("Tên đăng nhập"));
        cardPanel.add(Box.createVerticalStrut(4));
        cardPanel.add(userWrap);
        cardPanel.add(Box.createVerticalStrut(10));

        cardPanel.add(AuthUiKit.fieldLabel("Họ và tên"));
        cardPanel.add(Box.createVerticalStrut(4));
        cardPanel.add(fullNameWrap);
        cardPanel.add(Box.createVerticalStrut(10));

        cardPanel.add(AuthUiKit.fieldLabel("Địa chỉ Email"));
        cardPanel.add(Box.createVerticalStrut(4));
        cardPanel.add(emailWrap);
        cardPanel.add(Box.createVerticalStrut(10));

        cardPanel.add(AuthUiKit.fieldLabel("Mật khẩu"));
        cardPanel.add(Box.createVerticalStrut(4));
        cardPanel.add(passWrap);
        cardPanel.add(Box.createVerticalStrut(6));
        cardPanel.add(strengthBarPanel);
        cardPanel.add(Box.createVerticalStrut(3));
        cardPanel.add(strengthLabel);
        cardPanel.add(Box.createVerticalStrut(8));

        cardPanel.add(AuthUiKit.fieldLabel("Nhập lại mật khẩu"));
        cardPanel.add(Box.createVerticalStrut(4));
        cardPanel.add(confirmWrap);
        cardPanel.add(Box.createVerticalStrut(4));
        cardPanel.add(matchLabel);
        cardPanel.add(Box.createVerticalStrut(4));
        cardPanel.add(errorLabel);
        cardPanel.add(Box.createVerticalStrut(10));
        cardPanel.add(buttonPanel);

        centerStage.add(cardPanel);
        JScrollPane scroll = new JScrollPane(centerStage);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getViewport().setBackground(Theme.bgApp());

        outerPanel.add(scroll, BorderLayout.CENTER);
        setContentPane(outerPanel);

        // Thứ tự điều hướng Tab chuẩn từ trên xuống dưới (7 phần tử)
        java.util.List<Component> tabOrder = getExpectedTabOrder();
        setFocusTraversalPolicyProvider(true);
        setFocusTraversalPolicy(new FocusTraversalPolicy() {
            private Component next(Container a, Component c, int step) {
                int idx = tabOrder.indexOf(c);
                for (int i = 0; i < tabOrder.size(); i++) {
                    idx = Math.floorMod(idx + step, tabOrder.size());
                    Component candidate = tabOrder.get(idx);
                    if (candidate.isEnabled() && candidate.isVisible()) return candidate;
                }
                return backToLoginButton;
            }
            @Override public Component getComponentAfter(Container a, Component c) {
                return next(a, c, 1);
            }
            @Override public Component getComponentBefore(Container a, Component c) {
                return next(a, c, -1);
            }
            @Override public Component getFirstComponent(Container a) { return next(a, backToLoginButton, 1); }
            @Override public Component getLastComponent(Container a) { return tabOrder.get(tabOrder.size() - 1); }
            @Override public Component getDefaultComponent(Container a) { return getFirstComponent(a); }
        });
    }

    public java.util.List<Component> getExpectedTabOrder() {
        return java.util.List.of(usernameField, fullNameField, emailField, passwordField, confirmPasswordField, registerButton, backToLoginButton);
    }

    private void setupEvents() {
        DocumentListener pwListener = new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e)  { updateStrengthAndMatch(); }
            @Override public void removeUpdate(DocumentEvent e)  { updateStrengthAndMatch(); }
            @Override public void changedUpdate(DocumentEvent e) { updateStrengthAndMatch(); }
        };

        passwordField.getDocument().addDocumentListener(pwListener);
        confirmPasswordField.getDocument().addDocumentListener(pwListener);
        confirmPasswordField.addActionListener(e -> executeRegister());
        registerButton.addActionListener(e -> executeRegister());
    }

    public void setFormFields(String username, String fullName, String email, String password, String confirmPassword) {
        this.usernameField.setText(username);
        this.fullNameField.setText(fullName);
        this.emailField.setText(email);
        this.passwordField.setText(password);
        this.confirmPasswordField.setText(confirmPassword);
        updateStrengthAndMatch();
    }

    private void updateStrengthAndMatch() {
        String pw = new String(passwordField.getPassword());
        String confirmPw = new String(confirmPasswordField.getPassword());

        if (pw.isEmpty()) {
            strengthPercent = 0;
            strengthColor = Theme.border();
            strengthLabel.setText("Cần ít nhất 8 ký tự gồm cả chữ và số");
            strengthLabel.setForeground(Theme.textSecondary());
        } else {
            PasswordValidator.Strength strength = PasswordValidator.evaluateStrength(pw);
            strengthPercent = strength.getPercentage();
            strengthColor = strength.getColor();
            strengthLabel.setText(strength.getLabel());
            strengthLabel.setForeground(strength.getColor());
        }
        strengthBarPanel.repaint();

        if (!confirmPw.isEmpty()) {
            if (pw.equals(confirmPw)) {
                matchLabel.setText("[OK] Mật khẩu xác nhận khớp");
                matchLabel.setForeground(Theme.success());
            } else {
                matchLabel.setText("[X] Mật khẩu xác nhận chưa khớp");
                matchLabel.setForeground(Theme.danger());
            }
        } else {
            matchLabel.setText(" ");
        }
    }

    private void executeRegister() {
        if (disposed || submitting || !registrationAdmission.isAllowed() || !registerButton.isEnabled()) return;
        FeatureAdmission.Ticket admission = registrationAdmission.capture();
        String username = usernameField.getText().trim();
        String fullName = fullNameField.getText().trim();
        String email = emailField.getText().trim();
        String password = new String(passwordField.getPassword());
        String confirmPassword = new String(confirmPasswordField.getPassword());

        errorLabel.setText(" ");
        resetBorders();

        boolean hasClientError = false;

        if (username.length() < 3 || username.length() > 64 || !username.matches("^[a-zA-Z0-9_]+$")) {
            userWrap.setError(true);
            errorLabel.setText("Tên đăng nhập phải từ 3 đến 64 ký tự (chỉ gồm chữ, số, _).");
            hasClientError = true;
        }

        if (fullName.isEmpty() || fullName.length() > 255) {
            fullNameWrap.setError(true);
            if (!hasClientError) errorLabel.setText("Họ và tên phải có từ 1 đến 255 ký tự.");
            hasClientError = true;
        }

        if (email.length() > 255 || !com.mailapp.client.util.ComposeValidation.isEmail(email)) {
            emailWrap.setError(true);
            if (!hasClientError) errorLabel.setText("Địa chỉ email không đúng định dạng.");
            hasClientError = true;
        }

        if (!PasswordValidator.meetsPolicy(password)) {
            passWrap.setError(true);
            if (!hasClientError) errorLabel.setText("Mật khẩu tối thiểu 8 ký tự, gồm chữ và số, tối đa 72 byte UTF-8.");
            hasClientError = true;
        }

        if (!password.equals(confirmPassword)) {
            confirmWrap.setError(true);
            if (!hasClientError) errorLabel.setText("Mật khẩu xác nhận không trùng khớp.");
            hasClientError = true;
        }

        if (hasClientError) {
            cardPanel.triggerShake();
            return;
        }

        submitting = true;
        registerButton.setLoading(true, "Đang tạo tài khoản...");
        updateFormEnabled();

        RegisterRequest request = new RegisterRequest(username, email, password, fullName, "USER");

        SwingWorker<UserDto, Void> worker = new SwingWorker<>() {
            @Override
            protected UserDto doInBackground() throws Exception {
                PublicConfigDto config = apiClient.getPublicConfig();
                if (disposed) throw new InterruptedException();
                requireRegistrationAdmission(admission);
                if (config == null) {
                    throw new ApiException(503, "CONFIG_UNAVAILABLE", "Chưa tải được cấu hình đăng ký.");
                }
                if (!config.registrationEnabled() || config.maintenanceMode()) {
                    throw new ApiException(403, "REGISTRATION_DISABLED", "Đăng ký đã bị tắt");
                }
                if (disposed) throw new InterruptedException();
                requireRegistrationAdmission(admission);
                return apiClient.register(request);
            }

            @Override
            protected void done() {
                submitting = false;
                if (disposed) return;
                registerButton.setLoading(false, "Đăng Ký Tài Khoản");
                updateFormEnabled();

                try {
                    UserDto createdUser = get();

                    JOptionPane.showMessageDialog(
                            RegisterFrame.this,
                            "Đăng ký thành công tài khoản '" + createdUser.username() + "'!\nHệ thống sẽ chuyển bạn về màn hình Đăng nhập.",
                            "Thành công",
                            JOptionPane.INFORMATION_MESSAGE
                    );

                    if (!disposed && onRegisterSuccess != null) {
                        onRegisterSuccess.accept(createdUser.username());
                    }

                    dispose();
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    if (cause instanceof ApiException apiEx) {
                        handleServerError(apiEx, admission);
                    } else {
                        errorLabel.setText(ClientSafetyUtil.toFriendlyVietnameseError(cause));
                        cardPanel.triggerShake();
                    }
                }
            }
        };

        worker.execute();
    }

    private void handleServerError(ApiException ex, FeatureAdmission.Ticket admission) {
        String code = ex.getCode();
        int status = ex.getStatusCode();
        cardPanel.triggerShake();

        if ("FORM_INVALIDATED".equals(code)) {
            if (registrationAdmission.isAllowed()) errorLabel.setText(ex.getMessage());
            else setRegistrationAllowed(false);
        } else if (status == 409 && "EMAIL_TAKEN".equalsIgnoreCase(code)) {
            emailWrap.setError(true);
            errorLabel.setText(ex.getMessage());
        } else if (status == 409 || "USERNAME_TAKEN".equalsIgnoreCase(code)) {
            userWrap.setError(true);
            errorLabel.setText("Tên đăng nhập '" + usernameField.getText().trim() + "' đã tồn tại. Vui lòng chọn tên khác.");
        } else if ("REGISTRATION_DISABLED".equalsIgnoreCase(code)) {
            if (registrationAdmission.isCurrent(admission)) setRegistrationAllowed(false);
            else if (registrationAdmission.isAllowed()) errorLabel.setText(ex.getMessage());
        } else if ("CONFIG_UNAVAILABLE".equalsIgnoreCase(code)) {
            if (registrationAdmission.isCurrent(admission)) applyPublicConfig(null);
            else if (registrationAdmission.isAllowed()) errorLabel.setText(ex.getMessage());
        } else if (status == 429) {
            errorLabel.setText("Quá nhiều lần gửi yêu cầu. Vui lòng thử lại sau " + ex.getRetryAfterSeconds() + " giây.");
        } else if (status == 400) {
            String msg = ex.getMessage();
            errorLabel.setText(msg);
            if (msg.toLowerCase().contains("username") || msg.toLowerCase().contains("tên đăng nhập")) {
                userWrap.setError(true);
            }
            if (msg.toLowerCase().contains("email")) {
                emailWrap.setError(true);
            }
            if (msg.toLowerCase().contains("password") || msg.toLowerCase().contains("mật khẩu")) {
                passWrap.setError(true);
            }
            if (msg.toLowerCase().contains("fullname") || msg.toLowerCase().contains("họ và tên")) {
                fullNameWrap.setError(true);
            }
        } else {
            errorLabel.setText(ex.getMessage());
        }
    }

    private void resetBorders() {
        userWrap.setError(false);
        fullNameWrap.setError(false);
        emailWrap.setError(false);
        passWrap.setError(false);
        confirmWrap.setError(false);
    }

    private void requireRegistrationAdmission(FeatureAdmission.Ticket admission) {
        if (!registrationAdmission.isCurrent(admission)) {
            throw new ApiException(400, "FORM_INVALIDATED",
                    "Cấu hình đăng ký đã thay đổi. Vui lòng nhập lại mật khẩu và bấm Đăng ký.");
        }
    }

    public void applyPublicConfig(PublicConfigDto config) {
        if (disposed) return;
        setRegistrationAllowed(config != null && config.registrationEnabled() && !config.maintenanceMode());
        if (config == null) errorLabel.setText("Chưa tải được cấu hình đăng ký. Hãy kiểm tra kết nối.");
    }

    private void setRegistrationAllowed(boolean allowed) {
        boolean changed = registrationAdmission.isAllowed() != allowed;
        registrationAdmission.update(allowed);
        if (!allowed) {
            passwordField.setText("");
            confirmPasswordField.setText("");
            errorLabel.setText("Đăng ký đã bị tắt hoặc hệ thống đang bảo trì. Vui lòng liên hệ Quản trị viên.");
        } else if (changed) {
            errorLabel.setText(" ");
        }
        updateFormEnabled();
    }

    private void updateFormEnabled() {
        boolean enabled = registrationAdmission.isAllowed() && !submitting;
        registerButton.setEnabled(enabled);
        usernameField.setEnabled(enabled);
        fullNameField.setEnabled(enabled);
        emailField.setEnabled(enabled);
        passwordField.setEnabled(enabled);
        confirmPasswordField.setEnabled(enabled);
    }

    @Override
    public void dispose() {
        disposed = true;
        if (passwordField != null) passwordField.setText("");
        if (confirmPasswordField != null) confirmPasswordField.setText("");
        if (cardPanel != null) cardPanel.stopAnimation();
        if (userWrap != null) userWrap.stopAnimation();
        if (fullNameWrap != null) fullNameWrap.stopAnimation();
        if (emailWrap != null) emailWrap.stopAnimation();
        if (passWrap != null) passWrap.stopAnimation();
        if (confirmWrap != null) confirmWrap.stopAnimation();
        if (registerButton != null) registerButton.stopAnimation();
        super.dispose();
    }
}
