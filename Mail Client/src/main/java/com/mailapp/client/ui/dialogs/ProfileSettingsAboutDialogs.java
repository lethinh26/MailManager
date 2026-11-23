package com.mailapp.client.ui.dialogs;

import com.mailapp.client.model.UserDto;
import com.mailapp.client.net.ApiClient;
import com.mailapp.client.ui.theme.AppIcons;
import com.mailapp.client.ui.theme.AvatarUtil;
import com.mailapp.client.ui.theme.Theme;
import com.mailapp.client.util.ClientConfig;
import com.mailapp.client.util.SessionStore;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.File;

/**
 * Các hộp thoại: Hồ sơ Tài khoản, Cài đặt (gồm chủ đề Sáng/Tối & Tự động dọn), Giới thiệu (About).
 */
public final class ProfileSettingsAboutDialogs {

    private ProfileSettingsAboutDialogs() {}

    public static void showProfileDialog(Frame owner, ApiClient apiClient, Runnable onChangePassword) {
        UserDto user = SessionStore.getInstance().getCurrentUser();
        JDialog dlg = new JDialog(owner, "Hồ sơ Tài khoản — " + AppIcons.BRAND_FULL, true);
        dlg.setIconImages(AppIcons.getWindowIcons());
        dlg.setSize(440, 320);
        dlg.setLocationRelativeTo(owner);

        JPanel root = new JPanel(new BorderLayout(0, Theme.SP_4));
        root.setBackground(Theme.bgSurface());
        root.setBorder(new EmptyBorder(Theme.SP_5, Theme.SP_5, Theme.SP_4, Theme.SP_5));

        String name = user != null ? user.fullName() : "Người dùng";
        String email = user != null ? user.email() : "";
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, Theme.SP_3, 0));
        top.setOpaque(false);
        top.add(new JLabel(AvatarUtil.createAvatarIcon(name, email, 56)));
        JPanel titles = new JPanel();
        titles.setOpaque(false);
        titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));
        JLabel nLbl = new JLabel(name);
        nLbl.setFont(Theme.fontTitle());
        nLbl.setForeground(Theme.textPrimary());
        JLabel eLbl = new JLabel(email);
        eLbl.setFont(Theme.fontSmall());
        eLbl.setForeground(Theme.textSecondary());
        titles.add(nLbl);
        titles.add(eLbl);
        top.add(titles);

        JPanel details = new JPanel(new GridLayout(0, 1, 4, 6));
        details.setOpaque(false);
        if (user != null) {
            details.add(infoLine("Tên đăng nhập:", user.username()));
            details.add(infoLine("Vai trò:", user.role()));
            details.add(infoLine("Ngày tạo:", user.createdAt() != null ? user.createdAt() : "N/A"));
            details.add(infoLine("Bắt buộc đổi mật khẩu:", user.mustChangePassword() ? "Có" : "Không"));
        }

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, Theme.SP_2, 0));
        btns.setOpaque(false);
        JButton changePwBtn = Theme.primaryButton("Đổi mật khẩu", null);
        changePwBtn.addActionListener(e -> {
            dlg.dispose();
            if (onChangePassword != null) onChangePassword.run();
        });
        JButton closeBtn = Theme.secondaryButton("Đóng", null);
        closeBtn.addActionListener(e -> dlg.dispose());
        btns.add(changePwBtn);
        btns.add(closeBtn);

        root.add(top, BorderLayout.NORTH);
        root.add(details, BorderLayout.CENTER);
        root.add(btns, BorderLayout.SOUTH);
        dlg.setContentPane(root);
        dlg.setVisible(true);
    }

    public static void showSettingsDialog(Frame owner, Runnable onSettingsSaved) {
        ClientConfig cfg = ClientConfig.getInstance();
        JDialog dlg = new JDialog(owner, "Cài đặt Hệ thống & Tự động dọn — " + AppIcons.BRAND_FULL, true);
        dlg.setIconImages(AppIcons.getWindowIcons());
        dlg.setSize(580, 520);
        dlg.setLocationRelativeTo(owner);

        JPanel root = new JPanel(new BorderLayout(0, Theme.SP_3));
        root.setBackground(Theme.bgSurface());
        root.setBorder(new EmptyBorder(Theme.SP_4, Theme.SP_4, Theme.SP_4, Theme.SP_4));

        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(Theme.fontBold());

        // === Tab 1: Chung & Giao diện ===
        JPanel general = new JPanel(new GridBagLayout());
        general.setBackground(Theme.bgSurface());
        general.setBorder(new EmptyBorder(Theme.SP_3, Theme.SP_3, Theme.SP_3, Theme.SP_3));
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(6, 4, 6, 4);
        g.fill = GridBagConstraints.HORIZONTAL;

        JTextField urlField = new JTextField(cfg.getServerUrl());
        JTextField certField = new JTextField(cfg.getCertPath());
        JTextField downloadDirField = new JTextField(cfg.getDownloadDir());
        Theme.styleTextField(urlField);
        Theme.styleTextField(certField);
        Theme.styleTextField(downloadDirField);

        JComboBox<String> themeCombo = new JComboBox<>(new String[]{"Sáng (Light)", "Tối (Dark)"});
        themeCombo.setSelectedIndex(Theme.isDark() ? 1 : 0);

        JButton pickDirBtn = Theme.secondaryButton("Chọn...", null);
        pickDirBtn.addActionListener(e -> {
            JFileChooser fc = new JFileChooser(new File(downloadDirField.getText()));
            fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (fc.showOpenDialog(dlg) == JFileChooser.APPROVE_OPTION) {
                downloadDirField.setText(fc.getSelectedFile().getAbsolutePath());
            }
        });

        JPanel dlRow = new JPanel(new BorderLayout(6, 0));
        dlRow.setOpaque(false);
        dlRow.add(downloadDirField, BorderLayout.CENTER);
        dlRow.add(pickDirBtn, BorderLayout.EAST);

        addField(general, g, 0, "URL Máy chủ:", urlField);
        addField(general, g, 1, "Chứng chỉ TLS (.cer):", certField);
        addField(general, g, 2, "Chủ đề giao diện:", themeCombo);
        addField(general, g, 3, "Thư mục tải về:", dlRow);

        // === Tab 2: Tự động dọn (Tải đính kèm rồi Xóa) ===
        JPanel autoClean = new JPanel(new GridBagLayout());
        autoClean.setBackground(Theme.bgSurface());
        autoClean.setBorder(new EmptyBorder(Theme.SP_3, Theme.SP_3, Theme.SP_3, Theme.SP_3));
        GridBagConstraints ag = new GridBagConstraints();
        ag.insets = new Insets(6, 4, 6, 4);
        ag.fill = GridBagConstraints.HORIZONTAL;

        JCheckBox enableAuto = new JCheckBox("Bật chế độ Tự động dọn định kỳ (Mặc định TẮT)", cfg.isAutoCleanEnabled());
        enableAuto.setOpaque(false);
        enableAuto.setFont(Theme.fontBold());
        enableAuto.setForeground(Theme.textPrimary());

        JCheckBox dryRunAuto = new JCheckBox("Chạy thử (chỉ ghi nhật ký báo cáo, KHÔNG xóa thư)", cfg.isAutoCleanDryRun());
        dryRunAuto.setOpaque(false);
        dryRunAuto.setForeground(Theme.textPrimary());

        JComboBox<Integer> intervalCombo = new JComboBox<>(new Integer[]{5, 15, 60});
        intervalCombo.setSelectedItem(cfg.getAutoCleanIntervalMinutes());

        JSpinner maxPerRunSpinner = new JSpinner(new SpinnerNumberModel(cfg.getAutoCleanMaxPerRun(), 1, 100, 1));
        JSpinner minAgeSpinner = new JSpinner(new SpinnerNumberModel(cfg.getAutoCleanMinAgeDays(), 0, 365, 1));
        JTextField senderFilterField = new JTextField(cfg.getAutoCleanSenderFilter());
        Theme.styleTextField(senderFilterField);

        ag.gridx = 0; ag.gridy = 0; ag.gridwidth = 2; autoClean.add(enableAuto, ag);
        ag.gridy = 1; autoClean.add(dryRunAuto, ag);
        ag.gridwidth = 1;
        addField(autoClean, ag, 2, "Chu kỳ quét (phút):", intervalCombo);
        addField(autoClean, ag, 3, "Tối đa số thư / lượt:", maxPerRunSpinner);
        addField(autoClean, ag, 4, "Lọc theo người gửi:", senderFilterField);
        addField(autoClean, ag, 5, "Tuổi thư tối thiểu (ngày):", minAgeSpinner);

        JLabel safeNote = new JLabel("<html><i>* Hệ thống tự động tắt chế độ này nếu gặp 3 lỗi liên tiếp hoặc tài khoản ở trạng thái NEEDS_REAUTH.</i></html>");
        safeNote.setFont(Theme.fontTiny());
        safeNote.setForeground(Theme.textSecondary());
        ag.gridx = 0; ag.gridy = 6; ag.gridwidth = 2; autoClean.add(safeNote, ag);

        tabs.addTab("Chung & Giao diện", general);
        tabs.addTab("Tự động dọn (Tải & Xóa)", autoClean);

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, Theme.SP_2, 0));
        btns.setOpaque(false);
        JButton cancelBtn = Theme.secondaryButton("Hủy", null);
        cancelBtn.addActionListener(e -> dlg.dispose());
        JButton saveBtn = Theme.primaryButton("Lưu cài đặt", null);
        saveBtn.addActionListener(e -> {
            cfg.setServerUrl(urlField.getText());
            cfg.setCertPath(certField.getText());
            cfg.setDownloadDir(downloadDirField.getText());
            Theme.setDark(themeCombo.getSelectedIndex() == 1);

            cfg.setAutoCleanEnabled(enableAuto.isSelected());
            cfg.setAutoCleanDryRun(dryRunAuto.isSelected());
            cfg.setAutoCleanIntervalMinutes((Integer) intervalCombo.getSelectedItem());
            cfg.setAutoCleanMaxPerRun((Integer) maxPerRunSpinner.getValue());
            cfg.setAutoCleanSenderFilter(senderFilterField.getText());
            cfg.setAutoCleanMinAgeDays((Integer) minAgeSpinner.getValue());

            dlg.dispose();
            if (onSettingsSaved != null) onSettingsSaved.run();
        });
        btns.add(cancelBtn);
        btns.add(saveBtn);

        root.add(tabs, BorderLayout.CENTER);
        root.add(btns, BorderLayout.SOUTH);
        dlg.setContentPane(root);
        dlg.setVisible(true);
    }

    public static void showAboutDialog(Frame owner) {
        JDialog dlg = new JDialog(owner, "Giới thiệu — " + AppIcons.BRAND_FULL, true);
        dlg.setIconImages(AppIcons.getWindowIcons());
        dlg.setSize(420, 300);
        dlg.setLocationRelativeTo(owner);

        JPanel root = new JPanel();
        root.setBackground(Theme.bgSurface());
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
        root.setBorder(new EmptyBorder(Theme.SP_5, Theme.SP_5, Theme.SP_4, Theme.SP_5));

        JLabel logo = new JLabel(AppIcons.logoIcon(76));
        logo.setAlignmentX(Component.CENTER_ALIGNMENT);
        JLabel title = new JLabel(AppIcons.BRAND_FULL + " v1.0");
        title.setFont(Theme.fontTitle());
        title.setForeground(Theme.primaryText());
        title.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel desc = new JLabel("<html><center>Đồ án Lập trình Mạng — PTIT<br/>Java 21 • Spring Boot 3.3.5 • Pure Java Swing<br/>TLS 2 Lớp • OAuth2 PKCE • IMAPS/SMTP</center></html>");
        desc.setFont(Theme.fontSmall());
        desc.setForeground(Theme.textSecondary());
        desc.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel url = new JLabel("Máy chủ: " + ClientConfig.getInstance().getServerUrl());
        url.setFont(Theme.fontTiny());
        url.setForeground(Theme.textMuted());
        url.setAlignmentX(Component.CENTER_ALIGNMENT);

        JButton ok = Theme.primaryButton("Đóng", null);
        ok.setAlignmentX(Component.CENTER_ALIGNMENT);
        ok.addActionListener(e -> dlg.dispose());

        root.add(logo);
        root.add(Box.createVerticalStrut(Theme.SP_3));
        root.add(title);
        root.add(Box.createVerticalStrut(Theme.SP_2));
        root.add(desc);
        root.add(Box.createVerticalStrut(Theme.SP_3));
        root.add(url);
        root.add(Box.createVerticalStrut(Theme.SP_4));
        root.add(ok);

        dlg.setContentPane(root);
        dlg.setVisible(true);
    }

    private static JPanel infoLine(String k, String v) {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        JLabel kl = new JLabel(k);
        kl.setFont(Theme.fontBold());
        kl.setForeground(Theme.textSecondary());
        JLabel vl = new JLabel(v);
        vl.setFont(Theme.fontRegular());
        vl.setForeground(Theme.textPrimary());
        p.add(kl, BorderLayout.WEST);
        p.add(vl, BorderLayout.EAST);
        return p;
    }

    private static void addField(JPanel panel, GridBagConstraints g, int row, String label, Component comp) {
        g.gridy = row;
        g.gridx = 0; g.weightx = 0;
        JLabel l = new JLabel(label);
        l.setFont(Theme.fontBold());
        l.setForeground(Theme.textSecondary());
        panel.add(l, g);
        g.gridx = 1; g.weightx = 1;
        panel.add(comp, g);
    }
}

