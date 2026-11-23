package com.mailapp.client.ui.dialogs;

import com.mailapp.client.model.ChangePasswordResponse;
import com.mailapp.client.net.ApiClient;
import com.mailapp.client.net.ApiException;
import com.mailapp.client.ui.theme.AppIcons;
import com.mailapp.client.util.PasswordValidator;
import com.mailapp.client.util.SessionStore;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

public class ChangePasswordDialog extends JDialog {

    private final ApiClient apiClient;
    private final boolean forced;
    private boolean passwordChangedSuccessfully = false;

    private JPasswordField currentPasswordField;
    private JPasswordField newPasswordField;
    private JPasswordField confirmPasswordField;
    private JProgressBar strengthBar;
    private JLabel strengthLabel;
    private JLabel matchLabel;
    private JLabel errorLabel;
    private JButton submitButton;
    private JButton cancelButton;

    public ChangePasswordDialog(Frame parent, ApiClient apiClient, boolean forced) {
        super(parent, forced ? "Bắt Buộc Đổi Mật Khẩu" : "Đổi Mật Khẩu Tài Khoản", true);
        this.apiClient = apiClient;
        this.forced = forced;

        initComponents();
        setupEvents();
    }

    private void initComponents() {
        setIconImages(AppIcons.getWindowIcons());
        setSize(480, 430);
        setLocationRelativeTo(getParent());
        setResizable(false);

        if (forced) {
            setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
            addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    JOptionPane.showMessageDialog(
                            ChangePasswordDialog.this,
                            "Bạn phải đổi mật khẩu thành công trước khi có thể tiếp tục sử dụng hệ thống.",
                            "Bắt buộc đổi mật khẩu",
                            JOptionPane.WARNING_MESSAGE
                    );
                }
            });
        } else {
            setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        }

        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(new EmptyBorder(15, 20, 15, 20));
        mainPanel.setBackground(Color.WHITE);

        // Header Panel
        JPanel headerPanel = new JPanel(new BorderLayout(5, 5));
        headerPanel.setBackground(Color.WHITE);

        JLabel titleLabel = new JLabel(forced ? "BẮT BUỘC ĐỔI MẬT KHẨU" : "ĐỔI MẬT KHẨU");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
        titleLabel.setForeground(forced ? new Color(220, 53, 69) : new Color(33, 37, 41));

        JLabel descLabel = new JLabel(forced
                ? "<html>Tài khoản của bạn yêu cầu phải đổi mật khẩu trước khi tiếp tục thao tác.</html>"
                : "<html>Nhập mật khẩu hiện tại và mật khẩu mới (tối thiểu 8 ký tự, có chữ và số).</html>");
        descLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        descLabel.setForeground(Color.GRAY);

        headerPanel.add(titleLabel, BorderLayout.NORTH);
        headerPanel.add(descLabel, BorderLayout.CENTER);
        mainPanel.add(headerPanel, BorderLayout.NORTH);

        // Form Panel
        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setBackground(Color.WHITE);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(6, 4, 6, 4);

        // 1. Mật khẩu hiện tại
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.35;
        formPanel.add(new JLabel("Mật khẩu hiện tại:"), gbc);

        gbc.gridx = 1; gbc.weightx = 0.65;
        currentPasswordField = new JPasswordField(20);
        formPanel.add(currentPasswordField, gbc);

        // 2. Mật khẩu mới
        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0.35;
        formPanel.add(new JLabel("Mật khẩu mới:"), gbc);

        gbc.gridx = 1; gbc.weightx = 0.65;
        newPasswordField = new JPasswordField(20);
        formPanel.add(newPasswordField, gbc);

        // Thanh đo độ mạnh mật khẩu
        gbc.gridx = 1; gbc.gridy = 2;
        JPanel strengthPanel = new JPanel(new BorderLayout(5, 2));
        strengthPanel.setBackground(Color.WHITE);

        strengthBar = new JProgressBar(0, 100);
        strengthBar.setValue(0);
        strengthBar.setStringPainted(false);
        strengthBar.setPreferredSize(new Dimension(150, 6));

        strengthLabel = new JLabel("Chưa nhập mật khẩu");
        strengthLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        strengthLabel.setForeground(Color.GRAY);

        strengthPanel.add(strengthBar, BorderLayout.NORTH);
        strengthPanel.add(strengthLabel, BorderLayout.SOUTH);
        formPanel.add(strengthPanel, gbc);

        // 3. Nhập lại mật khẩu mới
        gbc.gridx = 0; gbc.gridy = 3; gbc.weightx = 0.35;
        formPanel.add(new JLabel("Xác nhận mật khẩu:"), gbc);

        gbc.gridx = 1; gbc.weightx = 0.65;
        confirmPasswordField = new JPasswordField(20);
        formPanel.add(confirmPasswordField, gbc);

        // Nhãn kiểm tra trùng khớp
        gbc.gridx = 1; gbc.gridy = 4;
        matchLabel = new JLabel(" ");
        matchLabel.setFont(new Font("Segoe UI", Font.ITALIC, 11));
        formPanel.add(matchLabel, gbc);

        // Nhãn báo lỗi chung từ server
        gbc.gridx = 0; gbc.gridy = 5; gbc.gridwidth = 2;
        errorLabel = new JLabel(" ");
        errorLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        errorLabel.setForeground(new Color(220, 53, 69));
        formPanel.add(errorLabel, gbc);

        mainPanel.add(formPanel, BorderLayout.CENTER);

        // Buttons Panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 5));
        buttonPanel.setBackground(Color.WHITE);

        if (!forced) {
            cancelButton = new JButton("Hủy bỏ");
            cancelButton.addActionListener(e -> dispose());
            buttonPanel.add(cancelButton);
        }

        submitButton = new JButton("Cập nhật mật khẩu");
        submitButton.setUI(new javax.swing.plaf.basic.BasicButtonUI());
        submitButton.setBackground(new Color(13, 110, 253));
        submitButton.setForeground(Color.WHITE);
        submitButton.setFont(new Font("Segoe UI", Font.BOLD, 12));
        submitButton.setPreferredSize(new Dimension(160, 32));
        buttonPanel.add(submitButton);

        mainPanel.add(buttonPanel, BorderLayout.SOUTH);

        JPanel outerPanel = new JPanel(new BorderLayout());
        if (com.mailapp.client.util.ClientConfig.getInstance().isInsecureMode()) {
            outerPanel.add(com.mailapp.client.util.ClientConfig.createInsecureBanner(), BorderLayout.NORTH);
        }
        outerPanel.add(mainPanel, BorderLayout.CENTER);
        setContentPane(outerPanel);
    }

    private void setupEvents() {
        DocumentListener pwListener = new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) { updateStrengthAndMatch(); }
            @Override
            public void removeUpdate(DocumentEvent e) { updateStrengthAndMatch(); }
            @Override
            public void changedUpdate(DocumentEvent e) { updateStrengthAndMatch(); }
        };

        newPasswordField.getDocument().addDocumentListener(pwListener);
        confirmPasswordField.getDocument().addDocumentListener(pwListener);

        submitButton.addActionListener(e -> executeChangePassword());
    }

    public void setFormFields(String currentPass, String newPass, String confirmPass) {
        this.currentPasswordField.setText(currentPass);
        this.newPasswordField.setText(newPass);
        this.confirmPasswordField.setText(confirmPass);
        updateStrengthAndMatch();
    }

    private void updateStrengthAndMatch() {
        String newPw = new String(newPasswordField.getPassword());
        String confirmPw = new String(confirmPasswordField.getPassword());

        if (newPw.isEmpty()) {
            strengthBar.setValue(0);
            strengthLabel.setText("Chưa nhập mật khẩu");
            strengthLabel.setForeground(Color.GRAY);
        } else {
            PasswordValidator.Strength strength = PasswordValidator.evaluateStrength(newPw);
            strengthBar.setValue(strength.getPercentage());
            strengthBar.setForeground(strength.getColor());
            strengthLabel.setText(strength.getLabel());
            strengthLabel.setForeground(strength.getColor());
        }

        if (!confirmPw.isEmpty()) {
            if (newPw.equals(confirmPw)) {
                matchLabel.setText("[OK] Mật khẩu xác nhận khớp");
                matchLabel.setForeground(new Color(40, 167, 69));
            } else {
                matchLabel.setText("[X] Mật khẩu xác nhận chưa khớp");
                matchLabel.setForeground(new Color(220, 53, 69));
            }
        } else {
            matchLabel.setText(" ");
        }
    }

    private void executeChangePassword() {
        String currentPass = new String(currentPasswordField.getPassword()).trim();
        String newPass = new String(newPasswordField.getPassword());
        String confirmPass = new String(confirmPasswordField.getPassword());

        errorLabel.setText(" ");
        resetBorders();

        if (currentPass.isEmpty()) {
            errorLabel.setText("Vui lòng nhập mật khẩu hiện tại.");
            currentPasswordField.setBorder(new LineBorder(Color.RED, 1));
            return;
        }

        if (!PasswordValidator.meetsPolicy(newPass)) {
            errorLabel.setText("Mật khẩu mới phải có ít nhất 8 ký tự, gồm cả chữ và số.");
            newPasswordField.setBorder(new LineBorder(Color.RED, 1));
            return;
        }

        if (!newPass.equals(confirmPass)) {
            errorLabel.setText("Mật khẩu xác nhận không trùng khớp.");
            confirmPasswordField.setBorder(new LineBorder(Color.RED, 1));
            return;
        }

        if (currentPass.equals(newPass)) {
            errorLabel.setText("Mật khẩu mới không được trùng với mật khẩu hiện tại.");
            newPasswordField.setBorder(new LineBorder(Color.RED, 1));
            return;
        }

        submitButton.setEnabled(false);
        submitButton.setText("Đang xử lý...");

        SwingWorker<ChangePasswordResponse, Void> worker = new SwingWorker<>() {
            @Override
            protected ChangePasswordResponse doInBackground() throws Exception {
                return apiClient.changePassword(currentPass, newPass);
            }

            @Override
            protected void done() {
                submitButton.setEnabled(true);
                submitButton.setText("Cập nhật mật khẩu");

                try {
                    ChangePasswordResponse response = get();
                    passwordChangedSuccessfully = true;

                    // Cập nhật session token mới
                    SessionStore.getInstance().updateToken(response.accessToken());
                    if (response.user() != null) {
                        SessionStore.getInstance().updateUser(response.user());
                    }

                    JOptionPane.showMessageDialog(
                            ChangePasswordDialog.this,
                            response.message() != null ? response.message() : "Đổi mật khẩu thành công!",
                            "Thông báo",
                            JOptionPane.INFORMATION_MESSAGE
                    );

                    dispose();
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    if (cause instanceof ApiException apiEx) {
                        if ("PASSWORD_MISMATCH".equals(apiEx.getCode()) || apiEx.getStatusCode() == 400) {
                            currentPasswordField.setBorder(new LineBorder(Color.RED, 1));
                        }
                        errorLabel.setText(apiEx.getMessage());
                    } else {
                        errorLabel.setText("Lỗi kết nối: " + cause.getMessage());
                    }
                }
            }
        };

        worker.execute();
    }

    private void resetBorders() {
        currentPasswordField.setBorder(UIManager.getBorder("TextField.border"));
        newPasswordField.setBorder(UIManager.getBorder("TextField.border"));
        confirmPasswordField.setBorder(UIManager.getBorder("TextField.border"));
    }

    public boolean isPasswordChangedSuccessfully() {
        return passwordChangedSuccessfully;
    }
}
