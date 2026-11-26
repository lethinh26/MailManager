package com.mailapp.client.ui.frames;

import com.mailapp.client.model.AdminClientDtos.*;
import com.mailapp.client.model.UserDto;
import com.mailapp.client.net.ApiClient;
import com.mailapp.client.net.ApiException;
import com.mailapp.client.ui.admin.AdminChartsPanel.BreakdownChartPanel;
import com.mailapp.client.ui.admin.AdminChartsPanel.SentMailBarChartPanel;
import com.mailapp.client.ui.admin.AdminViewModel;
import com.mailapp.client.ui.theme.AppIcons;
import com.mailapp.client.ui.theme.AvatarUtil;
import com.mailapp.client.ui.theme.Theme;
import com.mailapp.client.util.ClientConfig;
import com.mailapp.client.util.ClientSafetyUtil;
import com.mailapp.client.util.ComposeValidation;
import com.mailapp.client.util.SessionStore;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.util.*;
import java.util.List;

/**
 * Cửa sổ Quản trị Hệ thống (AdminFrame — 4 tab thuần Java Swing + Java2D).
 * Chỉ hiển thị và cho phép mở khi người dùng có vai trò ADMIN.
 * Ranh giới bảo mật: KHÔNG có chức năng xem nội dung thư hay mật khẩu Gmail của người dùng.
 */
public class AdminFrame extends JFrame {

    private final ApiClient apiClient;
    private final Runnable onSessionExpired;
    private final boolean autoLoad;
    private Runnable onSettingsChanged;
    private Runnable onPermissionLost;
    private volatile boolean disposed;
    private final long windowGeneration;
    private boolean exportingLogs;
    private boolean userMutationBusy;

    private JTabbedPane tabbedPane;
    private JLabel statusLabel;

    // Tab 1: Người dùng
    private JTextField userSearchField;
    private JComboBox<String> userRoleFilter;
    private JComboBox<String> userStatusFilter;
    private JComboBox<String> userSortCombo;
    private UserTableModel userTableModel;
    private JTable userTable;
    private JLabel userPageLabel;
    private JButton userPrevBtn;
    private JButton userNextBtn;
    private JButton lockUnlockBtn;
    private JButton roleBtn;
    private JButton resetPwBtn;
    private JButton deleteUserBtn;
    private JButton detailUserBtn;
    private int userPage = 0;
    private int userTotalPages = 1;
    private long usersRequestGeneration;
    private boolean usersLoading;
    private UserSearchCriteria activeUserCriteria;
    private record UserSearchCriteria(String query, String role, Boolean enabled, String sortBy, String sortDir) {}

    // Tab 2: Cấu hình hệ thống
    private JLabel regUserCountLabel;
    private JCheckBox chkAllowRegistration;
    private JCheckBox chkAllowOAuth2;
    private JCheckBox chkAllowAppPassword;
    private JCheckBox chkAllowSendMail;
    private JCheckBox chkMaintenanceMode;
    private JSpinner spnMaxGmail;
    private boolean legacyUnlimitedQuota;
    private JSpinner spnMaxAttachMb;
    private JSpinner spnMailPageSize;
    private JSpinner spnLoginMaxFailed;
    private JLabel settingsUpdatedMetaLabel;
    private JButton settingsReloadBtn;
    private JButton settingsSaveBtn;
    private boolean settingsLoaded;
    private boolean settingsBusy;
    private boolean lastMaintenanceValue = false;

    // Tab 3: Thống kê
    private JLabel kpiTotalUsersVal;
    private JLabel kpiActiveLockedVal;
    private JLabel kpiAdminsVal;
    private JLabel kpiGmailVal;
    private JLabel kpiRegVal;
    private JLabel kpiFailedLoginVal;
    private SentMailBarChartPanel sentMailChart;
    private BreakdownChartPanel breakdownChart;

    // Tab 4: Nhật ký
    private JTextField logUserField;
    private JTextField logUserIdField;
    private JComboBox<String> logActionCombo;
    private JTextField logFromField;
    private JTextField logToField;
    private AuditLogTableModel logTableModel;
    private JTable logTable;
    private JLabel logPageLabel;
    private JButton logPrevBtn;
    private JButton logNextBtn;
    private int logPage = 0;
    private int logTotalPages = 1;
    private long logRequestGeneration;
    private long statsRequestGeneration;
    private LogSearchCriteria activeLogCriteria;
    private record LogSearchCriteria(Long userId, String username, String action, String from, String to) {}

    public AdminFrame(ApiClient apiClient, Runnable onSessionExpired) {
        this(apiClient, onSessionExpired, true);
    }

    public AdminFrame(ApiClient apiClient, Runnable onSessionExpired, boolean autoLoad) {
        super("Quản trị Hệ thống — " + AppIcons.BRAND_FULL + " (ADMIN)");
        this.apiClient = apiClient;
        this.onSessionExpired = onSessionExpired;
        this.autoLoad = autoLoad;
        this.windowGeneration = SessionStore.getInstance().getGeneration();

        setIconImages(AppIcons.getWindowIcons());
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(1160, 740);
        setMinimumSize(new Dimension(800, 600));
        setLocationRelativeTo(null);

        initUI();

        if (autoLoad) {
            UserDto u = SessionStore.getInstance().getCurrentUser();
            if (u == null || !"ADMIN".equalsIgnoreCase(u.role())) {
                JOptionPane.showMessageDialog(null,
                        "Chỉ tài khoản Quản trị viên (ADMIN) mới được mở khu vực Quản trị.",
                        "Không có quyền truy cập", JOptionPane.WARNING_MESSAGE);
                dispose();
                return;
            }
            loadUsers();
            loadSettings();
            loadStats();
            loadLogs();
        }
    }

    public void setOnSettingsChanged(Runnable callback) {
        onSettingsChanged = callback;
    }

    public void setOnPermissionLost(Runnable callback) {
        onPermissionLost = callback;
    }

    private void initUI() {
        JPanel outer = new JPanel(new BorderLayout());
        outer.setBackground(Theme.bgApp());

        JPanel northStack = new JPanel();
        northStack.setLayout(new BoxLayout(northStack, BoxLayout.Y_AXIS));
        northStack.setBackground(Theme.bgSurface());
        if (ClientConfig.getInstance().isInsecureMode()) {
            northStack.add(ClientConfig.createInsecureBanner());
        }
        northStack.add(buildTopHeader());
        outer.add(northStack, BorderLayout.NORTH);

        tabbedPane = new JTabbedPane();
        tabbedPane.setFont(Theme.fontBold());
        tabbedPane.addTab("Người dùng", buildUsersTab());
        tabbedPane.addTab("Cấu hình hệ thống", buildSettingsTab());
        tabbedPane.addTab("Thống kê", buildStatsTab());
        tabbedPane.addTab("Nhật ký hệ thống", buildLogsTab());

        outer.add(tabbedPane, BorderLayout.CENTER);

        JPanel statusBar = new JPanel(new BorderLayout());
        statusBar.setBackground(Theme.bgSurfaceAlt());
        statusBar.setBorder(new CompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.border()),
                new EmptyBorder(Theme.SP_1, Theme.SP_3, Theme.SP_1, Theme.SP_3)
        ));
        statusLabel = new JLabel("Khu vực Quản trị Hệ thống — Bảo mật: Không truy cập nội dung thư hay mật khẩu Gmail người dùng.");
        statusLabel.setFont(Theme.fontTiny());
        statusLabel.setForeground(Theme.textSecondary());
        statusBar.add(statusLabel, BorderLayout.WEST);
        outer.add(statusBar, BorderLayout.SOUTH);

        setContentPane(outer);
    }

    private JPanel buildTopHeader() {
        JPanel bar = new JPanel(new BorderLayout(Theme.SP_3, 0));
        bar.setBackground(Theme.bgSurface());
        bar.setBorder(new CompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.border()),
                new EmptyBorder(Theme.SP_2, Theme.SP_4, Theme.SP_2, Theme.SP_4)
        ));

        JLabel title = new JLabel(AppIcons.BRAND_NAME + " — Bảng Điều Khiển Quản Trị", AppIcons.logoIcon(28), SwingConstants.LEFT);
        title.setFont(Theme.fontSubTitle());
        title.setForeground(Theme.primary());
        title.setIconTextGap(Theme.SP_2);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, Theme.SP_2, 0));
        right.setOpaque(false);
        right.add(Theme.badge("QUẢN TRỊ VIÊN", Theme.warningSoft(), Theme.warning()));

        JButton closeBtn = Theme.secondaryButton("Đóng cửa sổ", null);
        closeBtn.addActionListener(e -> dispose());
        right.add(closeBtn);

        bar.add(title, BorderLayout.WEST);
        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    // ==================== TAB 1: NGƯỜI DÙNG ====================

    private JPanel buildUsersTab() {
        JPanel panel = new JPanel(new BorderLayout(0, Theme.SP_2));
        panel.setBackground(Theme.bgApp());
        panel.setBorder(new EmptyBorder(Theme.SP_3, Theme.SP_3, Theme.SP_3, Theme.SP_3));

        // Filter & Action bar (tự xuống dòng theo chiều rộng để không che nút ở 800x600)
        JPanel top = Theme.createWrapPanel(Theme.SP_2, Theme.SP_1);
        userSearchField = new JTextField(14);
        Theme.styleTextField(userSearchField);
        userSearchField.setToolTipText("Tìm theo tên đăng nhập, họ tên hoặc email");
        userSearchField.addActionListener(e -> { userPage = 0; loadUsers(); });

        userRoleFilter = new JComboBox<>(new String[]{"Tất cả vai trò", "ADMIN", "USER"});
        userStatusFilter = new JComboBox<>(new String[]{"Tất cả trạng thái", "Đang hoạt động", "Đang khóa"});
        userSortCombo = new JComboBox<>(new String[]{"Mới nhất trước", "Cũ nhất trước", "Username A-Z"});

        JButton searchBtn = Theme.primaryButton("Tìm", AppIcons.vectorIcon(AppIcons.Glyph.SEARCH, 14, Color.WHITE));
        searchBtn.addActionListener(e -> { userPage = 0; loadUsers(); });

        JButton refreshBtn = Theme.secondaryButton("Làm mới", AppIcons.vectorIcon(AppIcons.Glyph.REFRESH, 14, Theme.textPrimary()));
        refreshBtn.addActionListener(e -> loadUsers());

        JButton createBtn = Theme.successButton("Tạo người dùng", AppIcons.vectorIcon(AppIcons.Glyph.PLUS, 14, Color.WHITE));
        createBtn.addActionListener(e -> openCreateUserDialog());

        JLabel findLbl = new JLabel("Tìm:");
        findLbl.setForeground(Theme.textPrimary());
        top.add(findLbl);
        top.add(userSearchField);
        top.add(userRoleFilter);
        top.add(userStatusFilter);
        top.add(userSortCombo);
        top.add(searchBtn);
        top.add(refreshBtn);
        top.add(createBtn);

        // Table
        userTableModel = new UserTableModel();
        userTable = new JTable(userTableModel);
        userTable.setRowHeight(42);
        userTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        userTable.setFont(Theme.fontRegular());
        userTable.getTableHeader().setFont(Theme.fontBold());
        userTable.setBackground(Theme.bgSurface());
        userTable.setForeground(Theme.textPrimary());
        userTable.setGridColor(Theme.border());
        userTable.getColumnModel().getColumn(0).setPreferredWidth(52);
        userTable.getColumnModel().getColumn(1).setPreferredWidth(210);
        userTable.getColumnModel().getColumn(1).setCellRenderer(new AvatarUserCellRenderer());
        userTable.getColumnModel().getColumn(2).setPreferredWidth(180);
        userTable.getColumnModel().getColumn(3).setPreferredWidth(90);
        userTable.getColumnModel().getColumn(4).setPreferredWidth(110);
        userTable.getColumnModel().getColumn(5).setPreferredWidth(70);
        userTable.getColumnModel().getColumn(6).setPreferredWidth(140);
        userTable.getSelectionModel().addListSelectionListener(e -> updateSelectedUserButtons());

        JScrollPane scroll = new JScrollPane(userTable);
        scroll.setBorder(BorderFactory.createLineBorder(Theme.border(), 1));

        // Bottom: Row actions + Pagination (xếp 2 hàng riêng khi hẹp để không chồng lấn nút ở 800x600)
        JPanel bottom = new JPanel(new BorderLayout(0, Theme.SP_1));
        bottom.setOpaque(false);

        JPanel actions = Theme.createWrapPanel(Theme.SP_2, Theme.SP_1);

        detailUserBtn = Theme.secondaryButton("Chi tiết", null);
        detailUserBtn.addActionListener(e -> showSelectedUserDetail());

        lockUnlockBtn = Theme.secondaryButton("Khóa / Mở khóa", null);
        lockUnlockBtn.addActionListener(e -> toggleLockSelectedUser());

        roleBtn = Theme.secondaryButton("Đổi vai trò", null);
        roleBtn.addActionListener(e -> changeSelectedUserRole());

        resetPwBtn = Theme.primaryButton("Đặt lại mật khẩu", null);
        resetPwBtn.addActionListener(e -> resetSelectedUserPassword());

        deleteUserBtn = Theme.dangerButton("Xóa", AppIcons.vectorIcon(AppIcons.Glyph.DELETE, 14, Color.WHITE));
        deleteUserBtn.addActionListener(e -> deleteSelectedUser());

        actions.add(detailUserBtn);
        actions.add(lockUnlockBtn);
        actions.add(roleBtn);
        actions.add(resetPwBtn);
        actions.add(deleteUserBtn);

        JPanel paging = new JPanel(new FlowLayout(FlowLayout.RIGHT, Theme.SP_2, 0));
        paging.setOpaque(false);
        userPrevBtn = Theme.secondaryButton("< Trước", null);
        userPrevBtn.addActionListener(e -> { if (userPage > 0) { userPage--; loadUsers(); } });
        userPageLabel = new JLabel("Trang 1 / 1 (0 người dùng)");
        userPageLabel.setFont(Theme.fontSmall());
        userPageLabel.setForeground(Theme.textSecondary());
        userNextBtn = Theme.secondaryButton("Sau >", null);
        userNextBtn.addActionListener(e -> { if (userPage + 1 < userTotalPages) { userPage++; loadUsers(); } });
        paging.add(userPrevBtn);
        paging.add(userPageLabel);
        paging.add(userNextBtn);

        bottom.add(actions, BorderLayout.NORTH);
        bottom.add(paging, BorderLayout.SOUTH);

        panel.add(top, BorderLayout.NORTH);
        panel.add(scroll, BorderLayout.CENTER);
        panel.add(bottom, BorderLayout.SOUTH);
        updateSelectedUserButtons();
        return panel;
    }

    // ==================== TAB 2: CẤU HÌNH HỆ THỐNG ====================

    private JPanel buildSettingsTab() {
        JPanel outer = new JPanel(new BorderLayout(0, Theme.SP_3));
        outer.setBackground(Theme.bgApp());
        outer.setBorder(new EmptyBorder(Theme.SP_4, Theme.SP_4, Theme.SP_4, Theme.SP_4));

        JPanel card = new JPanel(new GridBagLayout());
        card.setBackground(Theme.bgSurface());
        card.setBorder(Theme.cardBorder());
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(8, 10, 8, 10);
        g.anchor = GridBagConstraints.WEST;
        g.fill = GridBagConstraints.HORIZONTAL;

        regUserCountLabel = new JLabel("Tổng số người dùng hiện tại: 0");
        regUserCountLabel.setFont(Theme.fontSmall());
        regUserCountLabel.setForeground(Theme.primary());

        chkAllowRegistration = styledCheck("ALLOW_REGISTRATION — Cho phép người dùng tự đăng ký tài khoản mới");
        chkAllowOAuth2 = styledCheck("ALLOW_OAUTH2_LINK — Cho phép liên kết Gmail bằng Google OAuth2");
        chkAllowAppPassword = styledCheck("ALLOW_APP_PASSWORD_LINK — Cho phép liên kết Gmail bằng App Password");
        chkAllowSendMail = styledCheck("ALLOW_SEND_MAIL — Cho phép gửi thư qua SMTP");
        chkMaintenanceMode = styledCheck("MAINTENANCE_MODE — Chế độ bảo trì hệ thống (CHỈ Admin đăng nhập được)");
        chkMaintenanceMode.setForeground(Theme.danger());

        spnMaxGmail = new JSpinner(new SpinnerNumberModel(5, 1, 50, 1));
        spnMaxAttachMb = new JSpinner(new SpinnerNumberModel(25, 1, 50, 1));
        spnMailPageSize = new JSpinner(new SpinnerNumberModel(30, 10, 100, 5));
        spnLoginMaxFailed = new JSpinner(new SpinnerNumberModel(5, 3, 20, 1));

        int row = 0;
        g.gridx = 0; g.gridy = row++; g.gridwidth = 2;
        card.add(regUserCountLabel, g);
        g.gridy = row++; card.add(chkAllowRegistration, g);
        g.gridy = row++; card.add(chkAllowOAuth2, g);
        g.gridy = row++; card.add(chkAllowAppPassword, g);
        g.gridy = row++; card.add(chkAllowSendMail, g);
        g.gridy = row++; card.add(chkMaintenanceMode, g);

        g.gridwidth = 1;
        addSpinnerRow(card, g, row++, "MAX_GMAIL_PER_USER (1 – 50; giữ 0 cũ nếu có):", spnMaxGmail);
        addSpinnerRow(card, g, row++, "MAX_ATTACHMENT_SIZE_MB (1 – 50 MB):", spnMaxAttachMb);
        addSpinnerRow(card, g, row++, "MAIL_PAGE_SIZE (10 – 100 thư/trang):", spnMailPageSize);
        addSpinnerRow(card, g, row++, "LOGIN_MAX_FAILED_ATTEMPTS (3 – 20 lần):", spnLoginMaxFailed);

        settingsUpdatedMetaLabel = new JLabel("Cập nhật lần cuối: chưa rõ");
        settingsUpdatedMetaLabel.setFont(Theme.fontTiny());
        settingsUpdatedMetaLabel.setForeground(Theme.textMuted());
        g.gridx = 0; g.gridy = row; g.gridwidth = 2;
        card.add(settingsUpdatedMetaLabel, g);

        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, Theme.SP_2, 0));
        footer.setOpaque(false);
        settingsReloadBtn = Theme.secondaryButton("Tải lại", AppIcons.vectorIcon(AppIcons.Glyph.REFRESH, 14, Theme.textPrimary()));
        settingsReloadBtn.addActionListener(e -> loadSettings());
        settingsSaveBtn = Theme.primaryButton("Lưu cấu hình", null);
        settingsSaveBtn.addActionListener(e -> saveSettings());
        footer.add(settingsReloadBtn);
        footer.add(settingsSaveBtn);
        updateSettingsControls();

        outer.add(new JScrollPane(card), BorderLayout.CENTER);
        outer.add(footer, BorderLayout.SOUTH);
        return outer;
    }

    // ==================== TAB 3: THỐNG KÊ ====================

    private JPanel buildStatsTab() {
        JPanel outer = new JPanel(new BorderLayout(0, Theme.SP_3));
        outer.setBackground(Theme.bgApp());
        outer.setBorder(new EmptyBorder(Theme.SP_3, Theme.SP_3, Theme.SP_3, Theme.SP_3));

        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setOpaque(false);
        JLabel hdr = new JLabel("Tổng quan vận hành hệ thống " + AppIcons.BRAND_FULL);
        hdr.setFont(Theme.fontSubTitle());
        hdr.setForeground(Theme.textPrimary());
        JButton refreshBtn = Theme.primaryButton("Làm mới thống kê", AppIcons.vectorIcon(AppIcons.Glyph.REFRESH, 14, Color.WHITE));
        refreshBtn.addActionListener(e -> loadStats());
        topBar.add(hdr, BorderLayout.WEST);
        topBar.add(refreshBtn, BorderLayout.EAST);

        // 6 KPI Cards
        JPanel kpiGrid = new JPanel(new GridLayout(2, 3, Theme.SP_3, Theme.SP_3));
        kpiGrid.setOpaque(false);
        kpiTotalUsersVal = new JLabel("0");
        kpiActiveLockedVal = new JLabel("0 / 0");
        kpiAdminsVal = new JLabel("0");
        kpiGmailVal = new JLabel("0");
        kpiRegVal = new JLabel("0 / 0 / 0");
        kpiFailedLoginVal = new JLabel("0");

        kpiGrid.add(buildKpiCard("Tổng người dùng", kpiTotalUsersVal, Theme.primary()));
        kpiGrid.add(buildKpiCard("Hoạt động / Đang khóa", kpiActiveLockedVal, Theme.success()));
        kpiGrid.add(buildKpiCard("Quản trị viên (ADMIN)", kpiAdminsVal, Theme.warning()));
        kpiGrid.add(buildKpiCard("Gmail liên kết (OAuth2 / AppPass)", kpiGmailVal, Theme.primary()));
        kpiGrid.add(buildKpiCard("Đăng ký mới (hôm nay / 7 ngày / 30 ngày)", kpiRegVal, Theme.success()));
        kpiGrid.add(buildKpiCard("Đăng nhập thất bại (24h)", kpiFailedLoginVal, Theme.danger()));

        // 2 Java2D Charts
        JPanel chartsRow = new JPanel(new GridLayout(1, 2, Theme.SP_3, 0));
        chartsRow.setOpaque(false);
        sentMailChart = new SentMailBarChartPanel();
        sentMailChart.setBorder(BorderFactory.createLineBorder(Theme.border(), 1));
        breakdownChart = new BreakdownChartPanel();
        breakdownChart.setBorder(BorderFactory.createLineBorder(Theme.border(), 1));
        chartsRow.add(sentMailChart);
        chartsRow.add(breakdownChart);

        JPanel center = new JPanel(new BorderLayout(0, Theme.SP_3));
        center.setOpaque(false);
        center.add(kpiGrid, BorderLayout.NORTH);
        center.add(chartsRow, BorderLayout.CENTER);

        outer.add(topBar, BorderLayout.NORTH);
        outer.add(center, BorderLayout.CENTER);
        return outer;
    }

    private JPanel buildKpiCard(String title, JLabel valLabel, Color accent) {
        JPanel card = new JPanel(new BorderLayout(0, Theme.SP_1));
        card.setBackground(Theme.bgSurface());
        card.setBorder(new CompoundBorder(
                BorderFactory.createMatteBorder(3, 1, 1, 1, accent),
                new EmptyBorder(Theme.SP_3, Theme.SP_4, Theme.SP_3, Theme.SP_4)
        ));
        JLabel t = new JLabel(title);
        t.setFont(Theme.fontSmall());
        t.setForeground(Theme.textSecondary());
        valLabel.setFont(Theme.fontDisplay());
        valLabel.setForeground(accent);
        card.add(t, BorderLayout.NORTH);
        card.add(valLabel, BorderLayout.CENTER);
        return card;
    }

    // ==================== TAB 4: NHẬT KÝ HỆ THỐNG ====================

    private JPanel buildLogsTab() {
        JPanel panel = new JPanel(new BorderLayout(0, Theme.SP_2));
        panel.setBackground(Theme.bgApp());
        panel.setBorder(new EmptyBorder(Theme.SP_3, Theme.SP_3, Theme.SP_3, Theme.SP_3));

        JPanel filters = Theme.createWrapPanel(Theme.SP_2, 4);
        filters.setOpaque(false);

        logUserField = new JTextField(12);
        Theme.styleTextField(logUserField);
        logUserField.setToolTipText("Lọc theo username");
        logUserIdField = new JTextField(6);
        Theme.styleTextField(logUserIdField);
        logUserIdField.setToolTipText("ID người dùng (số nguyên dương)");

        logActionCombo = new JComboBox<>(new String[]{
                "", "LOGIN", "LOGIN_FAILED", "REGISTER", "CHANGE_PASSWORD", "CHANGE_PASSWORD_FAILED",
                "GOOGLE_LOGIN_NEW", "GOOGLE_LOGIN_MERGED", "GOOGLE_LOGIN_FAILED",
                "LINK_GMAIL", "LINK_GMAIL_DENIED_SCOPE", "OAUTH2_CALLBACK", "UNLINK_GMAIL",
                "ADD_GMAIL_METHOD", "UPDATE_GMAIL_METHOD", "REMOVE_GMAIL_METHOD", "CHANGE_PREFERRED_METHOD",
                "REVOKE_TOKEN_FAILED", "FALLBACK_TO_APP_PASSWORD",
                "SEND_MAIL", "MAIL_DELETE", "MAIL_FLAG_CHANGE",
                "ADMIN_CREATE_USER", "ADMIN_LOCK_USER", "ADMIN_UNLOCK_USER",
                "ADMIN_CHANGE_ROLE", "ADMIN_RESET_PASSWORD", "ADMIN_DELETE_USER", "ADMIN_CHANGE_SETTING"
        });
        logActionCombo.setEditable(true);

        logFromField = new JTextField(10);
        Theme.styleTextField(logFromField);
        logFromField.setToolTipText("Từ ngày (yyyy-MM-dd)");

        logToField = new JTextField(10);
        Theme.styleTextField(logToField);
        logToField.setToolTipText("Đến ngày (yyyy-MM-dd)");

        JButton filterBtn = Theme.primaryButton("Lọc", AppIcons.vectorIcon(AppIcons.Glyph.SEARCH, 14, Color.WHITE));
        filterBtn.addActionListener(e -> { logPage = 0; loadLogs(); });

        JButton clearBtn = Theme.secondaryButton("Xóa lọc", null);
        clearBtn.addActionListener(e -> {
            logUserField.setText("");
            logUserIdField.setText("");
            logActionCombo.setSelectedItem("");
            logFromField.setText("");
            logToField.setText("");
            logPage = 0;
            loadLogs();
        });

        JButton exportBtn = Theme.successButton("Xuất CSV", AppIcons.vectorIcon(AppIcons.Glyph.DOWNLOAD, 14, Color.WHITE));
        exportBtn.addActionListener(e -> exportLogsCsv());

        filters.add(new JLabel("User:"));
        filters.add(logUserField);
        filters.add(new JLabel("ID:"));
        filters.add(logUserIdField);
        filters.add(new JLabel("Hành động:"));
        filters.add(logActionCombo);
        filters.add(new JLabel("Từ:"));
        filters.add(logFromField);
        filters.add(new JLabel("Đến:"));
        filters.add(logToField);
        filters.add(filterBtn);
        filters.add(clearBtn);
        filters.add(exportBtn);

        logTableModel = new AuditLogTableModel();
        logTable = new JTable(logTableModel);
        logTable.setRowHeight(28);
        logTable.setFont(Theme.fontSmall());
        logTable.getTableHeader().setFont(Theme.fontBold());
        logTable.setBackground(Theme.bgSurface());
        logTable.setForeground(Theme.textPrimary());
        logTable.setGridColor(Theme.border());
        logTable.getColumnModel().getColumn(0).setPreferredWidth(50);
        logTable.getColumnModel().getColumn(1).setPreferredWidth(110);
        logTable.getColumnModel().getColumn(2).setPreferredWidth(160);
        logTable.getColumnModel().getColumn(3).setPreferredWidth(420);
        logTable.getColumnModel().getColumn(4).setPreferredWidth(100);
        logTable.getColumnModel().getColumn(5).setPreferredWidth(140);

        JScrollPane scroll = new JScrollPane(logTable);
        scroll.setBorder(BorderFactory.createLineBorder(Theme.border(), 1));

        JPanel paging = new JPanel(new FlowLayout(FlowLayout.RIGHT, Theme.SP_2, 0));
        paging.setOpaque(false);
        logPrevBtn = Theme.secondaryButton("< Trước", null);
        logPrevBtn.addActionListener(e -> { if (logPage > 0) { logPage--; loadLogs(); } });
        logPageLabel = new JLabel("Trang 1 / 1 (0 bản ghi)");
        logPageLabel.setFont(Theme.fontSmall());
        logPageLabel.setForeground(Theme.textSecondary());
        logNextBtn = Theme.secondaryButton("Sau >", null);
        logNextBtn.addActionListener(e -> { if (logPage + 1 < logTotalPages) { logPage++; loadLogs(); } });
        paging.add(logPrevBtn);
        paging.add(logPageLabel);
        paging.add(logNextBtn);

        panel.add(filters, BorderLayout.NORTH);
        panel.add(scroll, BorderLayout.CENTER);
        panel.add(paging, BorderLayout.SOUTH);
        return panel;
    }

    // ==================== SWINGWORKER DATA OPERATIONS ====================

    public void loadUsers() {
        if (!currentWindow()) return;
        String q = userSearchField.getText().trim();
        String roleSel = (String) userRoleFilter.getSelectedItem();
        String role = ("ADMIN".equals(roleSel) || "USER".equals(roleSel)) ? roleSel : null;
        int stIdx = userStatusFilter.getSelectedIndex();
        Boolean enabled = stIdx == 1 ? Boolean.TRUE : (stIdx == 2 ? Boolean.FALSE : null);
        int sortIdx = userSortCombo.getSelectedIndex();
        String sortBy = sortIdx == 2 ? "username" : "createdAt";
        String sortDir = sortIdx == 0 ? "desc" : "asc";
        UserSearchCriteria criteria = new UserSearchCriteria(q, role, enabled, sortBy, sortDir);
        if (!criteria.equals(activeUserCriteria)) userPage = 0;
        activeUserCriteria = criteria;
        int requestedPage = userPage;
        long generation = ++usersRequestGeneration;
        usersLoading = true;
        userTable.clearSelection();
        userPrevBtn.setEnabled(false);
        userNextBtn.setEnabled(false);
        updateSelectedUserButtons();

        setStatus("Đang tải danh sách người dùng...");
        new SwingWorker<AdminPageResponse<AdminUserSummary>, Void>() {
            @Override protected AdminPageResponse<AdminUserSummary> doInBackground() throws Exception {
                requireCurrentWindow();
                return apiClient.adminListUsers(q, role, enabled, requestedPage, 20, sortBy, sortDir);
            }
            @Override protected void done() {
                if (!currentWindow() || generation != usersRequestGeneration) return;
                usersLoading = false;
                try {
                    AdminPageResponse<AdminUserSummary> page = get();
                    if (page.page() > 0 && page.page() >= Math.max(1, page.totalPages())) {
                        userPage = Math.max(0, page.totalPages() - 1);
                        loadUsers();
                        return;
                    }
                    applyUsersPage(page);
                    setStatus("Đã tải " + page.content().size() + " / " + page.totalElements() + " người dùng.");
                } catch (Exception ex) {
                    userTableModel.setRows(List.of());
                    updateSelectedUserButtons();
                    handleAdminError(ex, "Không tải được danh sách người dùng");
                }
            }
        }.execute();
    }

    private void applyUsersPage(AdminPageResponse<AdminUserSummary> page) {
        userTableModel.setRows(page.content() != null ? page.content() : List.of());
        userPage = page.page();
        userTotalPages = Math.max(1, page.totalPages());
        userPageLabel.setText("Trang " + (userPage + 1) + " / " + userTotalPages + " (" + page.totalElements() + " người dùng)");
        userPrevBtn.setEnabled(userPage > 0);
        userNextBtn.setEnabled(userPage + 1 < userTotalPages);
        updateSelectedUserButtons();
    }

    private void updateSelectedUserButtons() {
        AdminUserSummary sel = getSelectedUser();
        boolean hasSel = sel != null;
        UserDto current = SessionStore.getInstance().getCurrentUser();
        boolean self = hasSel && current != null && AdminViewModel.isSelfAction(current.id(), sel.id());
        detailUserBtn.setEnabled(hasSel);
        lockUnlockBtn.setEnabled(hasSel && (!self || !sel.enabled()));
        roleBtn.setEnabled(hasSel && (!self || !"ADMIN".equalsIgnoreCase(sel.role())));
        resetPwBtn.setEnabled(hasSel);
        deleteUserBtn.setEnabled(hasSel && !self);
        if (hasSel) {
            lockUnlockBtn.setText(sel.enabled() ? "Khóa tài khoản" : "Mở khóa tài khoản");
        }
    }

    private AdminUserSummary getSelectedUser() {
        if (!currentWindow() || usersLoading || userMutationBusy) return null;
        int r = userTable.getSelectedRow();
        return r >= 0 ? userTableModel.getRow(userTable.convertRowIndexToModel(r)) : null;
    }

    private void openCreateUserDialog() {
        if (!currentAdminSession() || userMutationBusy) return;
        JDialog dlg = new JDialog(this, "Tạo tài khoản mới — Admin", true);
        dlg.setIconImages(AppIcons.getWindowIcons());
        dlg.setSize(450, 340);
        dlg.setLocationRelativeTo(this);

        JPanel root = new JPanel(new BorderLayout(0, Theme.SP_3));
        root.setBackground(Theme.bgSurface());
        root.setBorder(new EmptyBorder(Theme.SP_4, Theme.SP_4, Theme.SP_4, Theme.SP_4));

        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(6, 4, 6, 4);
        g.fill = GridBagConstraints.HORIZONTAL;

        JTextField uField = new JTextField(20);
        JTextField fnField = new JTextField(20);
        JTextField emField = new JTextField(20);
        JComboBox<String> rCombo = new JComboBox<>(new String[]{"USER", "ADMIN"});
        Theme.styleTextField(uField);
        Theme.styleTextField(fnField);
        Theme.styleTextField(emField);

        addField(form, g, 0, "Tên đăng nhập:", uField);
        addField(form, g, 1, "Họ và tên:", fnField);
        addField(form, g, 2, "Email:", emField);
        addField(form, g, 3, "Vai trò:", rCombo);

        JLabel note = new JLabel("<html><i>Mật khẩu tạm sẽ được hệ thống sinh ngẫu nhiên an toàn và hiển thị MỘT LẦN. Người dùng bắt buộc đổi mật khẩu ở lần đăng nhập đầu tiên.</i></html>");
        note.setFont(Theme.fontTiny());
        note.setForeground(Theme.textSecondary());
        g.gridx = 0; g.gridy = 4; g.gridwidth = 2;
        form.add(note, g);

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, Theme.SP_2, 0));
        btns.setOpaque(false);
        JButton cancel = Theme.secondaryButton("Hủy", null);
        cancel.addActionListener(e -> dlg.dispose());
        JButton create = Theme.primaryButton("Tạo tài khoản", null);
        create.addActionListener(e -> {
            if (!uField.getText().trim().matches("[a-zA-Z0-9_]{3,64}") || fnField.getText().isBlank()
                    || fnField.getText().trim().length() > 255 || emField.getText().trim().length() > 255
                    || !ComposeValidation.isEmail(emField.getText().trim())) {
                JOptionPane.showMessageDialog(dlg, "Tên đăng nhập phải có 3–64 ký tự hợp lệ, họ tên và email là bắt buộc.",
                        "Dữ liệu chưa hợp lệ", JOptionPane.WARNING_MESSAGE);
                return;
            }
            AdminCreateUserRequest req = new AdminCreateUserRequest(
                    uField.getText().trim(), fnField.getText().trim(), emField.getText().trim(),
                    (String) rCombo.getSelectedItem(), null);
            if (!beginUserMutation(null)) return;
            create.setEnabled(false);
            cancel.setEnabled(false);
            dlg.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
            new SwingWorker<AdminCreateUserResponse, Void>() {
                @Override protected AdminCreateUserResponse doInBackground() throws Exception {
                    requireCurrentAdminSession();
                    return apiClient.adminCreateUser(req);
                }
                @Override protected void done() {
                    if (!currentAdminSession()) {
                        dlg.dispose();
                        finishUserMutation();
                        return;
                    }
                    try {
                        AdminCreateUserResponse created = get();
                        dlg.dispose();
                        loadUsers();
                        loadStats();
                        if (created.temporaryPassword() != null) {
                            showOneTimePasswordDialog(created.username(), created.temporaryPassword(),
                                    "Tạo tài khoản thành công!");
                        }
                    } catch (Exception ex) {
                        handleAdminError(ex, "Không tạo được tài khoản");
                    } finally {
                        finishUserMutation();
                        if (currentAdminSession() && dlg.isDisplayable()) {
                            create.setEnabled(true);
                            cancel.setEnabled(true);
                            dlg.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
                        }
                    }
                }
            }.execute();
        });
        btns.add(cancel);
        btns.add(create);

        root.add(form, BorderLayout.CENTER);
        root.add(btns, BorderLayout.SOUTH);
        dlg.setContentPane(root);
        dlg.setVisible(true);
    }

    private void showSelectedUserDetail() {
        AdminUserSummary sel = getSelectedUser();
        if (sel == null || !currentAdminSession()) return;
        long generation = usersRequestGeneration;
        new SwingWorker<AdminUserDetail, Void>() {
            @Override protected AdminUserDetail doInBackground() throws Exception {
                requireCurrentAdminSession();
                return apiClient.adminGetUserDetail(sel.id());
            }
            @Override protected void done() {
                if (!currentAdminSession()) return;
                try {
                    AdminUserDetail d = get();
                    if (generation != usersRequestGeneration || !Objects.equals(sel, getSelectedUser())
                            || d == null || !Objects.equals(d.id(), sel.id())) return;
                    StringBuilder sb = new StringBuilder();
                    sb.append("ID: ").append(d.id()).append("\n")
                      .append("Tên đăng nhập: ").append(d.username()).append("\n")
                      .append("Họ tên: ").append(d.fullName()).append("\n")
                      .append("Email: ").append(d.email()).append("\n")
                      .append("Vai trò: ").append(d.role()).append("\n")
                      .append("Trạng thái: ").append(d.enabled() ? "Hoạt động" : "Đang khóa").append("\n")
                      .append("Bắt buộc đổi MK: ").append(d.mustChangePassword() ? "Có" : "Không").append("\n")
                      .append("Số Gmail liên kết: ").append(d.gmailCount()).append("\n\n");
                    if (d.gmailAccounts() != null && !d.gmailAccounts().isEmpty()) {
                        sb.append("--- Danh sách Gmail (chỉ metadata, không lộ mật khẩu/token) ---\n");
                        for (AdminGmailMeta g : d.gmailAccounts()) {
                            sb.append(" • ").append(g.email())
                              .append(" [").append(g.authType()).append(" | ").append(g.status()).append("]\n");
                        }
                    }
                    JOptionPane.showMessageDialog(AdminFrame.this, sb.toString(),
                            "Chi tiết người dùng — " + d.username(), JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception ex) {
                    handleAdminError(ex, "Không tải được chi tiết người dùng");
                }
            }
        }.execute();
    }

    private void toggleLockSelectedUser() {
        AdminUserSummary sel = getSelectedUser();
        if (sel == null) return;
        UserDto me = SessionStore.getInstance().getCurrentUser();
        if (sel.enabled() && me != null && AdminViewModel.isSelfAction(me.id(), sel.id())) {
            JOptionPane.showMessageDialog(this, "Bạn không thể tự khóa tài khoản của chính mình.",
                    "Thao tác bị chặn", JOptionPane.WARNING_MESSAGE);
            return;
        }
        boolean willLock = sel.enabled();
        String msg = willLock
                ? "Khóa tài khoản '" + sel.username() + "'? Phiên JWT hiện hành của người này sẽ bị vô hiệu ngay lập tức."
                : "Mở khóa tài khoản '" + sel.username() + "'?";
        if (JOptionPane.showConfirmDialog(this, msg, "Xác nhận", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        if (!beginUserMutation(sel)) return;

        new SwingWorker<AdminUserSummary, Void>() {
            @Override protected AdminUserSummary doInBackground() throws Exception {
                requireCurrentAdminSession();
                return willLock ? apiClient.adminLockUser(sel.id()) : apiClient.adminUnlockUser(sel.id());
            }
            @Override protected void done() {
                if (!currentAdminSession()) { finishUserMutation(); return; }
                try {
                    get();
                    loadUsers();
                    loadStats();
                    setStatus((willLock ? "Đã khóa " : "Đã mở khóa ") + sel.username());
                } catch (Exception ex) {
                    handleAdminError(ex, "Thao tác khóa/mở khóa thất bại");
                } finally {
                    finishUserMutation();
                }
            }
        }.execute();
    }

    private void changeSelectedUserRole() {
        AdminUserSummary sel = getSelectedUser();
        if (sel == null) return;
        String newRole = "ADMIN".equalsIgnoreCase(sel.role()) ? "USER" : "ADMIN";
        UserDto me = SessionStore.getInstance().getCurrentUser();
        if ("USER".equals(newRole) && me != null && AdminViewModel.isSelfAction(me.id(), sel.id())) {
            JOptionPane.showMessageDialog(this, "Bạn không thể tự hạ quyền của chính mình.",
                    "Thao tác bị chặn", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (JOptionPane.showConfirmDialog(this,
                "Đổi vai trò của '" + sel.username() + "' từ " + sel.role() + " sang " + newRole + "?",
                "Đổi vai trò", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        if (!beginUserMutation(sel)) return;

        new SwingWorker<AdminUserSummary, Void>() {
            @Override protected AdminUserSummary doInBackground() throws Exception {
                requireCurrentAdminSession();
                return apiClient.adminUpdateUserRole(sel.id(), newRole);
            }
            @Override protected void done() {
                if (!currentAdminSession()) { finishUserMutation(); return; }
                try {
                    get();
                    loadUsers();
                    loadStats();
                    setStatus("Đã đổi vai trò " + sel.username() + " -> " + newRole);
                } catch (Exception ex) {
                    handleAdminError(ex, "Không đổi được vai trò");
                } finally {
                    finishUserMutation();
                }
            }
        }.execute();
    }

    private void resetSelectedUserPassword() {
        AdminUserSummary sel = getSelectedUser();
        if (sel == null) return;
        if (JOptionPane.showConfirmDialog(this,
                "Đặt lại mật khẩu cho '" + sel.username() + "'?\nHệ thống sẽ sinh mật khẩu tạm ngẫu nhiên (chỉ hiển thị một lần) và buộc đổi mật khẩu khi đăng nhập.",
                "Đặt lại mật khẩu", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        if (!beginUserMutation(sel)) return;

        new SwingWorker<AdminResetPasswordResponse, Void>() {
            @Override protected AdminResetPasswordResponse doInBackground() throws Exception {
                requireCurrentAdminSession();
                return apiClient.adminResetPassword(sel.id());
            }
            @Override protected void done() {
                if (!currentAdminSession()) { finishUserMutation(); return; }
                try {
                    AdminResetPasswordResponse res = get();
                    loadUsers();
                    showOneTimePasswordDialog(res.username(), res.temporaryPassword(), "Đặt lại mật khẩu thành công");
                } catch (Exception ex) {
                    handleAdminError(ex, "Đặt lại mật khẩu thất bại");
                } finally {
                    finishUserMutation();
                }
            }
        }.execute();
    }

    private void showOneTimePasswordDialog(String username, String tempPassword, String title) {
        if (!currentAdminSession() || tempPassword == null || tempPassword.isBlank()) return;
        JDialog dlg = new JDialog(this, title, true);
        dlg.setIconImages(AppIcons.getWindowIcons());
        dlg.setSize(480, 230);
        dlg.setLocationRelativeTo(this);

        JPanel root = new JPanel(new BorderLayout(0, Theme.SP_3));
        root.setBackground(Theme.bgSurface());
        root.setBorder(new EmptyBorder(Theme.SP_4, Theme.SP_4, Theme.SP_4, Theme.SP_4));

        JLabel info = new JLabel("<html>Mật khẩu tạm cho tài khoản <b>" + username
                + "</b> (chỉ hiển thị <b>MỘT LẦN DUY NHẤT</b>, hãy sao chép ngay):</html>");
        info.setFont(Theme.fontRegular());
        info.setForeground(Theme.textPrimary());

        JTextField pwField = new JTextField(tempPassword);
        pwField.setEditable(false);
        pwField.setFont(new Font(Font.MONOSPACED, Font.BOLD, 16));
        pwField.setHorizontalAlignment(SwingConstants.CENTER);
        Theme.styleTextField(pwField);

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, Theme.SP_2, 0));
        btns.setOpaque(false);
        JButton copyBtn = Theme.primaryButton("Sao chép mật khẩu", null);
        copyBtn.addActionListener(e -> {
            if (!currentAdminSession()) { dlg.dispose(); return; }
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(tempPassword), null);
            copyBtn.setText("Đã sao chép!");
        });
        JButton closeBtn = Theme.secondaryButton("Đóng", null);
        closeBtn.addActionListener(e -> dlg.dispose());
        btns.add(copyBtn);
        btns.add(closeBtn);

        root.add(info, BorderLayout.NORTH);
        root.add(pwField, BorderLayout.CENTER);
        root.add(btns, BorderLayout.SOUTH);
        dlg.setContentPane(root);
        try {
            dlg.setVisible(true);
        } finally {
            pwField.setText("");
            dlg.dispose();
        }
    }

    private void deleteSelectedUser() {
        AdminUserSummary sel = getSelectedUser();
        if (sel == null) return;
        UserDto me = SessionStore.getInstance().getCurrentUser();
        if (me != null && AdminViewModel.isSelfAction(me.id(), sel.id())) {
            JOptionPane.showMessageDialog(this, "Bạn không thể tự xóa tài khoản của chính mình.",
                    "Thao tác bị chặn", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String typed = JOptionPane.showInputDialog(this,
                "Hành động này sẽ XÓA VĨNH VIỄN tài khoản '" + sel.username() + "' và toàn bộ liên kết Gmail (kèm thu hồi OAuth2).\n"
                        + "Hãy gõ lại chính xác tên đăng nhập '" + sel.username() + "' để xác nhận:",
                "Xác nhận xóa người dùng", JOptionPane.WARNING_MESSAGE);
        if (typed == null) return;
        if (!AdminViewModel.matchesDeleteConfirmation(sel.username(), typed)) {
            JOptionPane.showMessageDialog(this, "Tên đăng nhập xác nhận không khớp. Đã hủy thao tác xóa.",
                    "Hủy xóa", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        if (!beginUserMutation(sel)) return;

        new SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() throws Exception {
                requireCurrentAdminSession();
                apiClient.adminDeleteUser(sel.id());
                return null;
            }
            @Override protected void done() {
                if (!currentAdminSession()) { finishUserMutation(); return; }
                try {
                    get();
                    loadUsers();
                    loadStats();
                    setStatus("Đã xóa tài khoản " + sel.username());
                } catch (Exception ex) {
                    handleAdminError(ex, "Xóa người dùng thất bại");
                } finally {
                    finishUserMutation();
                }
            }
        }.execute();
    }

    public void loadSettings() {
        if (!currentWindow() || settingsBusy) return;
        settingsBusy = true;
        updateSettingsControls();
        new SwingWorker<List<AdminSettingItem>, Void>() {
            @Override protected List<AdminSettingItem> doInBackground() throws Exception {
                requireCurrentWindow();
                return apiClient.adminGetSettings();
            }
            @Override protected void done() {
                settingsBusy = false;
                if (!currentWindow()) return;
                try {
                    applySettings(get());
                    settingsLoaded = true;
                } catch (Exception ex) {
                    settingsLoaded = false;
                    handleAdminError(ex, "Không tải được cấu hình hệ thống");
                } finally {
                    updateSettingsControls();
                }
            }
        }.execute();
    }

    private void applySettings(List<AdminSettingItem> items) {
        String lastMeta = "Chưa có cập nhật thủ công";
        for (AdminSettingItem it : items) {
            switch (it.key()) {
                case "ALLOW_REGISTRATION" -> chkAllowRegistration.setSelected(Boolean.parseBoolean(it.value()));
                case "ALLOW_OAUTH2_LINK" -> chkAllowOAuth2.setSelected(Boolean.parseBoolean(it.value()));
                case "ALLOW_APP_PASSWORD_LINK" -> chkAllowAppPassword.setSelected(Boolean.parseBoolean(it.value()));
                case "ALLOW_SEND_MAIL" -> chkAllowSendMail.setSelected(Boolean.parseBoolean(it.value()));
                case "MAINTENANCE_MODE" -> {
                    lastMaintenanceValue = Boolean.parseBoolean(it.value());
                    chkMaintenanceMode.setSelected(lastMaintenanceValue);
                }
                case "MAX_GMAIL_PER_USER" -> {
                    int value = Integer.parseInt(it.value());
                    legacyUnlimitedQuota = value == 0 && (it.minValue() == null || it.minValue() == 0);
                    spnMaxGmail.setModel(new SpinnerNumberModel(value, legacyUnlimitedQuota ? 0 : 1, 50, 1));
                    spnMaxGmail.setToolTipText(legacyUnlimitedQuota
                            ? "0 cũ là không giới hạn. Sau khi lưu số dương, không thể quay lại 0."
                            : "Giới hạn số hộp thư từ 1 đến 50.");
                }
                case "MAX_ATTACHMENT_SIZE_MB" -> setSpinnerSafe(spnMaxAttachMb, it.value());
                case "MAIL_PAGE_SIZE" -> setSpinnerSafe(spnMailPageSize, it.value());
                case "LOGIN_MAX_FAILED_ATTEMPTS" -> setSpinnerSafe(spnLoginMaxFailed, it.value());
            }
            if (it.updatedByUsername() != null) {
                lastMeta = "Cập nhật gần nhất bởi: " + it.updatedByUsername()
                        + (it.updatedAt() != null ? " lúc " + it.updatedAt() : "");
            }
        }
        settingsUpdatedMetaLabel.setText(lastMeta);
    }

    private void updateSettingsControls() {
        boolean editable = settingsLoaded && !settingsBusy && !disposed;
        for (JComponent c : List.of(chkAllowRegistration, chkAllowOAuth2, chkAllowAppPassword,
                chkAllowSendMail, chkMaintenanceMode, spnMaxGmail, spnMaxAttachMb,
                spnMailPageSize, spnLoginMaxFailed, settingsSaveBtn)) {
            c.setEnabled(editable);
        }
        settingsReloadBtn.setEnabled(!settingsBusy && !disposed);
    }

    private void saveSettings() {
        if (!currentAdminSession() || !settingsLoaded || settingsBusy) return;
        try {
            for (JSpinner spinner : List.of(spnMaxGmail, spnMaxAttachMb, spnMailPageSize, spnLoginMaxFailed)) {
                spinner.commitEdit();
            }
        } catch (java.text.ParseException ex) {
            JOptionPane.showMessageDialog(this, "Giá trị số không hợp lệ.", "Cấu hình không hợp lệ", JOptionPane.WARNING_MESSAGE);
            return;
        }
        Map<String, String> map = new LinkedHashMap<>();
        map.put("ALLOW_REGISTRATION", String.valueOf(chkAllowRegistration.isSelected()));
        map.put("ALLOW_OAUTH2_LINK", String.valueOf(chkAllowOAuth2.isSelected()));
        map.put("ALLOW_APP_PASSWORD_LINK", String.valueOf(chkAllowAppPassword.isSelected()));
        map.put("ALLOW_SEND_MAIL", String.valueOf(chkAllowSendMail.isSelected()));
        map.put("MAINTENANCE_MODE", String.valueOf(chkMaintenanceMode.isSelected()));
        map.put("MAX_GMAIL_PER_USER", String.valueOf(spnMaxGmail.getValue()));
        map.put("MAX_ATTACHMENT_SIZE_MB", String.valueOf(spnMaxAttachMb.getValue()));
        map.put("MAIL_PAGE_SIZE", String.valueOf(spnMailPageSize.getValue()));
        map.put("LOGIN_MAX_FAILED_ATTEMPTS", String.valueOf(spnLoginMaxFailed.getValue()));

        String err = AdminViewModel.validateSettingsUpdate(map, legacyUnlimitedQuota);
        if (err != null) {
            JOptionPane.showMessageDialog(this, err, "Cấu hình không hợp lệ", JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (AdminViewModel.isMaintenanceTurningOn(lastMaintenanceValue, chkMaintenanceMode.isSelected())) {
            int ok = JOptionPane.showConfirmDialog(this,
                    "CẢNH BÁO: Bật MAINTENANCE_MODE sẽ chặn toàn bộ người dùng thường (USER) đăng nhập vào hệ thống!\nBạn có chắc chắn muốn bật chế độ bảo trì?",
                    "Cảnh báo chế độ bảo trì", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (ok != JOptionPane.YES_OPTION) return;
        }

        if (!currentAdminSession() || !settingsLoaded || settingsBusy) return;

        settingsBusy = true;
        updateSettingsControls();
        setStatus("Đang lưu cấu hình hệ thống...");
        new SwingWorker<List<AdminSettingItem>, Void>() {
            @Override protected List<AdminSettingItem> doInBackground() throws Exception {
                requireCurrentAdminSession();
                return apiClient.adminUpdateSettings(map);
            }
            @Override protected void done() {
                settingsBusy = false;
                if (!currentAdminSession()) return;
                try {
                    applySettings(get());
                    if (onSettingsChanged != null) onSettingsChanged.run();
                    setStatus("Đã lưu cấu hình hệ thống (có hiệu lực ngay).");
                    JOptionPane.showMessageDialog(AdminFrame.this, "Đã lưu cấu hình thành công!",
                            "Thành công", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception ex) {
                    handleAdminError(ex, "Lưu cấu hình thất bại");
                } finally {
                    updateSettingsControls();
                }
            }
        }.execute();
    }

    public void loadStats() {
        if (!currentWindow()) return;
        long generation = ++statsRequestGeneration;
        new SwingWorker<AdminStatsResponse, Void>() {
            @Override protected AdminStatsResponse doInBackground() throws Exception {
                requireCurrentWindow();
                return apiClient.adminGetStats();
            }
            @Override protected void done() {
                if (!currentWindow() || generation != statsRequestGeneration) return;
                try {
                    applyStats(get());
                } catch (Exception ex) {
                    handleAdminError(ex, "Không tải được thống kê");
                }
            }
        }.execute();
    }

    private void applyStats(AdminStatsResponse st) {
        kpiTotalUsersVal.setText(String.valueOf(st.totalUsers()));
        kpiActiveLockedVal.setText(st.activeUsers() + " / " + st.lockedUsers());
        kpiAdminsVal.setText(String.valueOf(st.totalAdmins()));
        kpiGmailVal.setText(st.totalGmailAccounts() + " (" + st.gmailByOAuth2() + " / " + st.gmailByAppPassword() + ")");
        kpiRegVal.setText(st.registrationsToday() + " / " + st.registrationsLast7Days() + " / " + st.registrationsLast30Days());
        kpiFailedLoginVal.setText(String.valueOf(st.failedLoginsLast24h()));
        sentMailChart.setData(st.mailsSentLast7DaysByDay());
        breakdownChart.setStats(st);
        regUserCountLabel.setText("Tổng số người dùng hiện tại: " + st.totalUsers());
    }

    public void loadLogs() {
        if (!currentWindow()) return;
        Long userId;
        try {
            userId = parseLogUserId();
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Bộ lọc nhật ký", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String uname = logUserField.getText().trim();
        Object actObj = logActionCombo.getSelectedItem();
        String action = actObj != null ? actObj.toString().trim() : "";
        String from = logFromField.getText().trim();
        String to = logToField.getText().trim();
        LogSearchCriteria criteria = new LogSearchCriteria(userId, uname, action, from, to);
        if (!criteria.equals(activeLogCriteria)) logPage = 0;
        activeLogCriteria = criteria;
        int requestedPage = logPage;
        long generation = ++logRequestGeneration;
        logPrevBtn.setEnabled(false);
        logNextBtn.setEnabled(false);

        new SwingWorker<AdminPageResponse<AdminAuditLogItem>, Void>() {
            @Override protected AdminPageResponse<AdminAuditLogItem> doInBackground() throws Exception {
                requireCurrentWindow();
                return apiClient.adminSearchLogs(userId, uname, action, from, to, requestedPage, 25);
            }
            @Override protected void done() {
                if (!currentWindow() || generation != logRequestGeneration) return;
                try {
                    AdminPageResponse<AdminAuditLogItem> page = get();
                    if (page.page() > 0 && page.page() >= Math.max(1, page.totalPages())) {
                        logPage = Math.max(0, page.totalPages() - 1);
                        loadLogs();
                        return;
                    }
                    applyLogsPage(page);
                } catch (Exception ex) {
                    logTableModel.setRows(List.of());
                    logPageLabel.setText("Không tải được nhật ký");
                    handleAdminError(ex, "Không tải được nhật ký hệ thống");
                }
            }
        }.execute();
    }

    private Long parseLogUserId() {
        String text = logUserIdField.getText().trim();
        if (text.isEmpty()) return null;
        try {
            long id = Long.parseLong(text);
            if (id > 0) return id;
        } catch (NumberFormatException ignored) {}
        throw new IllegalArgumentException("ID người dùng phải là số nguyên dương.");
    }

    private void applyLogsPage(AdminPageResponse<AdminAuditLogItem> page) {
        logTableModel.setRows(page.content() != null ? page.content() : List.of());
        logPage = page.page();
        logTotalPages = Math.max(1, page.totalPages());
        logPageLabel.setText("Trang " + (logPage + 1) + " / " + logTotalPages + " (" + page.totalElements() + " bản ghi)");
        logPrevBtn.setEnabled(logPage > 0);
        logNextBtn.setEnabled(logPage + 1 < logTotalPages);
    }

    private void exportLogsCsv() {
        if (!currentAdminExportSession() || exportingLogs) return;
        Long userId;
        try {
            userId = parseLogUserId();
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Bộ lọc nhật ký", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String uname = logUserField.getText().trim();
        Object actObj = logActionCombo.getSelectedItem();
        String action = actObj != null ? actObj.toString().trim() : "";
        String from = logFromField.getText().trim();
        String to = logToField.getText().trim();
        File directory;
        try {
            directory = java.nio.file.Files.createDirectories(
                    java.nio.file.Path.of(ClientConfig.getInstance().getDownloadDir()).toAbsolutePath().normalize()).toFile();
        } catch (java.io.IOException | SecurityException | java.nio.file.InvalidPathException ex) {
            handleAdminError(ex, "Không mở được thư mục xuất nhật ký");
            return;
        }
        JFileChooser fc = new JFileChooser(directory);
        fc.setSelectedFile(new File(directory, "audit-logs.csv"));
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        if (!currentAdminExportSession() || exportingLogs) return;
        File outFile = fc.getSelectedFile();
        if (java.nio.file.Files.exists(outFile.toPath(), java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            JOptionPane.showMessageDialog(this, "Tệp CSV đã tồn tại. Hãy chọn tên khác.",
                    "Xuất nhật ký", JOptionPane.WARNING_MESSAGE);
            return;
        }
        exportingLogs = true;

        setStatus("Đang xuất nhật ký CSV...");
        new SwingWorker<Long, Void>() {
            @Override protected Long doInBackground() throws Exception {
                return apiClient.adminExportLogsCsvToFile(userId, uname, action, from, to, outFile,
                        AdminFrame.this::currentAdminExportSession);
            }
            @Override protected void done() {
                exportingLogs = false;
                if (!currentAdminExportSession()) return;
                try {
                    long bytes = get();
                    setStatus("Đã xuất CSV thành công (" + bytes + " bytes): " + outFile.getAbsolutePath());
                    JOptionPane.showMessageDialog(AdminFrame.this,
                            "Đã xuất nhật ký CSV an toàn tại:\n" + outFile.getAbsolutePath(),
                            "Xuất CSV thành công", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception ex) {
                    handleAdminError(ex, "Xuất CSV thất bại");
                }
            }
        }.execute();
    }

    private boolean currentWindow() {
        return !disposed && SessionStore.getInstance().getGeneration() == windowGeneration;
    }

    private void requireCurrentWindow() throws InterruptedException {
        if (!currentWindow()) throw new InterruptedException("Admin window no longer current");
    }

    private boolean currentAdminExportSession() {
        return currentAdminSession();
    }

    private boolean currentAdminSession() {
        SessionStore session = SessionStore.getInstance();
        synchronized (session) {
            UserDto user = session.getCurrentUser();
            return !disposed && session.getGeneration() == windowGeneration
                    && session.hasActiveSession() && user != null && "ADMIN".equalsIgnoreCase(user.role());
        }
    }

    private void requireCurrentAdminSession() throws InterruptedException {
        if (!currentAdminSession()) throw new InterruptedException("Admin session no longer current");
    }

    private boolean beginUserMutation(AdminUserSummary expected) {
        if (!currentAdminSession() || userMutationBusy
                || (expected != null && !Objects.equals(expected, getSelectedUser()))) return false;
        userMutationBusy = true;
        updateSelectedUserButtons();
        return true;
    }

    private void finishUserMutation() {
        userMutationBusy = false;
        if (currentWindow()) updateSelectedUserButtons();
    }

    // ==================== ERROR HANDLING (401 & 403) ====================

    private void handleAdminError(Exception ex, String prefix) {
        if (!currentWindow()) return;
        Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
        if (cause instanceof ApiException apiEx) {
            if (apiEx.getStatusCode() == 401) {
                dispose();
                if (onSessionExpired != null) onSessionExpired.run();
                return;
            }
            if (apiEx.getStatusCode() == 403 && "FORBIDDEN".equalsIgnoreCase(apiEx.getCode())) {
                JOptionPane.showMessageDialog(this,
                        "Tài khoản của bạn không còn quyền Quản trị viên (403 FORBIDDEN). Cửa sổ Quản trị sẽ đóng.",
                        "Mất quyền Quản trị", JOptionPane.ERROR_MESSAGE);
                dispose();
                if (onPermissionLost != null) onPermissionLost.run();
                return;
            }
            JOptionPane.showMessageDialog(this, "[" + apiEx.getCode() + "] " + apiEx.getMessage(), prefix, JOptionPane.WARNING_MESSAGE);
            setStatus(prefix + ": " + apiEx.getMessage());
            return;
        }
        String friendly = ClientSafetyUtil.toFriendlyVietnameseError(cause);
        setStatus(prefix + ": " + friendly);
        JOptionPane.showMessageDialog(this, friendly, prefix, JOptionPane.ERROR_MESSAGE);
    }

    // ==================== MOCKUP HELPER CHỈ DÙNG CHO --screenshots ====================

    public void populateMockupForScreenshots(AdminPageResponse<AdminUserSummary> users,
                                             List<AdminSettingItem> settings,
                                             AdminStatsResponse stats,
                                             AdminPageResponse<AdminAuditLogItem> logs) {
        applyUsersPage(users);
        applySettings(settings);
        settingsLoaded = true;
        updateSettingsControls();
        applyStats(stats);
        applyLogsPage(logs);
        setStatus("Dữ liệu mô phỏng (chỉ dùng cho --screenshots)");
    }

    public void selectTab(int index) {
        tabbedPane.setSelectedIndex(index);
    }

    @Override public void dispose() {
        disposed = true;
        usersRequestGeneration++;
        logRequestGeneration++;
        statsRequestGeneration++;
        super.dispose();
    }

    // ==================== HELPERS & TABLE MODELS ====================

    private void setStatus(String s) {
        if (statusLabel != null) statusLabel.setText(s);
    }

    private JCheckBox styledCheck(String text) {
        JCheckBox c = new JCheckBox(text);
        c.setOpaque(false);
        c.setFont(Theme.fontRegular());
        c.setForeground(Theme.textPrimary());
        return c;
    }

    private void addSpinnerRow(JPanel panel, GridBagConstraints g, int row, String label, JSpinner spinner) {
        g.gridy = row;
        g.gridx = 0; g.weightx = 0.7;
        JLabel l = new JLabel(label);
        l.setFont(Theme.fontBold());
        l.setForeground(Theme.textSecondary());
        panel.add(l, g);
        g.gridx = 1; g.weightx = 0.3;
        panel.add(spinner, g);
    }

    private void addField(JPanel panel, GridBagConstraints g, int row, String label, Component comp) {
        g.gridy = row;
        g.gridx = 0; g.weightx = 0.35;
        JLabel l = new JLabel(label);
        l.setFont(Theme.fontBold());
        l.setForeground(Theme.textSecondary());
        panel.add(l, g);
        g.gridx = 1; g.weightx = 0.65;
        panel.add(comp, g);
    }

    private void setSpinnerSafe(JSpinner spinner, String rawVal) {
        try {
            spinner.setValue(Integer.parseInt(rawVal));
        } catch (Exception ignored) {}
    }

    private static class UserTableModel extends AbstractTableModel {
        private static final String[] COLS = {"ID", "Người dùng", "Email", "Vai trò", "Trạng thái", "Gmail", "Ngày tạo"};
        private List<AdminUserSummary> rows = List.of();

        void setRows(List<AdminUserSummary> list) {
            this.rows = list != null ? list : List.of();
            fireTableDataChanged();
        }
        AdminUserSummary getRow(int r) { return rows.get(r); }
        @Override public int getRowCount() { return rows.size(); }
        @Override public int getColumnCount() { return COLS.length; }
        @Override public String getColumnName(int c) { return COLS[c]; }
        @Override public Object getValueAt(int r, int c) {
            AdminUserSummary u = rows.get(r);
            return switch (c) {
                case 0 -> u.id();
                case 1 -> u;
                case 2 -> u.email();
                case 3 -> u.role();
                case 4 -> u.enabled() ? "Hoạt động" : "Đang khóa";
                case 5 -> u.gmailCount();
                case 6 -> u.createdAt() != null ? u.createdAt().replace("T", " ").substring(0, Math.min(16, u.createdAt().length())) : "";
                default -> "";
            };
        }
    }

    private static class AvatarUserCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int col) {
            JLabel lbl = (JLabel) super.getTableCellRendererComponent(table, "", isSelected, hasFocus, row, col);
            if (value instanceof AdminUserSummary u) {
                String name = (u.fullName() != null && !u.fullName().isBlank()) ? u.fullName() : u.username();
                lbl.setIcon(AvatarUtil.createAvatarIcon(name, u.email(), 28));
                lbl.setText("<html><b>" + u.username() + "</b><br><small>" + name + "</small></html>");
                lbl.setIconTextGap(8);
            }
            return lbl;
        }
    }

    private static class AuditLogTableModel extends AbstractTableModel {
        private static final String[] COLS = {"ID", "Người dùng", "Hành động", "Chi tiết", "IP", "Thời gian"};
        private List<AdminAuditLogItem> rows = List.of();

        void setRows(List<AdminAuditLogItem> list) {
            this.rows = list != null ? list : List.of();
            fireTableDataChanged();
        }
        @Override public int getRowCount() { return rows.size(); }
        @Override public int getColumnCount() { return COLS.length; }
        @Override public String getColumnName(int c) { return COLS[c]; }
        @Override public Object getValueAt(int r, int c) {
            AdminAuditLogItem a = rows.get(r);
            return switch (c) {
                case 0 -> a.id();
                case 1 -> a.username();
                case 2 -> a.action();
                case 3 -> a.detail();
                case 4 -> a.ipAddress();
                case 5 -> a.createdAt() != null ? a.createdAt().replace("T", " ").substring(0, Math.min(19, a.createdAt().length())) : "";
                default -> "";
            };
        }
    }
}

