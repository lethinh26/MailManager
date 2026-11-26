package com.mailapp.client.ui.frames;

import com.mailapp.client.model.GmailAccountDto;
import com.mailapp.client.model.MailClientDtos.*;
import com.mailapp.client.model.PublicConfigDto;
import com.mailapp.client.model.UserDto;
import com.mailapp.client.net.ApiClient;
import com.mailapp.client.net.ApiException;
import com.mailapp.client.service.AttachmentCleanupService;
import com.mailapp.client.service.AttachmentCleanupService.MailCleanupResult;
import com.mailapp.client.ui.dialogs.*;
import com.mailapp.client.ui.theme.AppIcons;
import com.mailapp.client.ui.theme.AvatarUtil;
import com.mailapp.client.ui.theme.Theme;
import com.mailapp.client.ui.theme.WrapLayout;
import com.mailapp.client.util.ClientConfig;
import com.mailapp.client.util.ClientSafetyUtil;
import com.mailapp.client.util.FileUtil;
import com.mailapp.client.util.MailSizeDisplay;
import com.mailapp.client.util.SessionStore;
import com.mailapp.client.util.FeatureAdmission;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.event.HyperlinkEvent;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.net.URI;
import java.nio.file.Path;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Cửa sổ chính 3 cột thuần Java Swing (System L&F + Java2D) của Mail Manager:
 * - Cột trái: Tài khoản Gmail (avatar, trạng thái, chuyển tài khoản) + Cây thư mục (số thư chưa đọc).
 * - Cột giữa: Tìm kiếm + Lọc (Chưa đọc, Có đính kèm) + Danh sách thư (multi-select, avatar, sao, đính kèm, tải thêm).
 * - Cột phải: Khung đọc thư (header mở rộng, chip đính kèm Tải/Mở, Trả lời/Chuyển tiếp/Xóa/Sao/Chưa đọc,
 *   chuyển HTML sạch / Văn bản thuần, thanh chặn ảnh từ xa + nút "Tải ảnh từ xa").
 * - Tích hợp nghiệp vụ "Tải đính kèm & Xóa" thủ công và "Tự động dọn" định kỳ.
 */
public class MainFrame extends JFrame {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter FULL_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final ApiClient apiClient;
    private final AttachmentCleanupService cleanupService;

    // State
    private List<GmailAccountDto> currentGmailAccounts = new ArrayList<>();
    private GmailAccountDto selectedAccount;
    private List<MailFolderDto> currentFolders = new ArrayList<>();
    private MailFolderDto selectedFolder;
    private int currentPage = 0;
    private int activePageSize = 20;
    private MailboxViewModel.SearchCriteria activeSearchCriteria;
    private boolean hasMoreMails = false;
    private long totalMails = 0;
    private long currentUidValidity = 0;
    private volatile long mailboxGeneration;
    private boolean mailboxReady;
    private MailDetailResponse currentDetail;
    private boolean showHtmlMode = true;
    private boolean headerExpanded = false;
    private boolean mockupMode = false;
    private volatile boolean closing;
    private boolean configRefreshing;
    private boolean foldersLoading;
    private boolean suppressDetailSelection;
    private long accountsRequestGeneration;
    private long userRequestGeneration;
    private boolean forcedPasswordDialogOpen;
    private javax.swing.Timer configTimer;
    private GmailManagerDialog activeGmailManager;
    private ComposeDialog activeComposeDialog;
    private CleanupPreviewDialog activeCleanupDialog;
    private AdminFrame activeAdminFrame;
    private final FeatureAdmission oauthLinkAdmission = new FeatureAdmission(false);
    private Runnable cancelOAuthLink;

    // Auto-cleanup timer & active SwingWorkers (chống race condition & rò rỉ luồng khi đóng cửa sổ)
    private javax.swing.Timer autoCleanTimer;
    private final AtomicBoolean autoCleanRunning = new AtomicBoolean(false);
    private final AtomicBoolean autoCleanCancelled = new AtomicBoolean(false);
    private final MailboxViewModel.WorkerCoordinator workerCoordinator = new MailboxViewModel.WorkerCoordinator();

    // Top bar & menus
    private JMenu adminMenu;
    private JButton adminToolbarBtn;
    private JButton composeBtn;
    private JMenuItem composeItem;
    private JButton userProfileBtn;
    private JButton autoCleanStopBtn;
    private JLabel autoCleanBadge;
    private JLabel statusBarLabel;
    private JLabel serverBadgeLabel;

    // Left column
    private JPanel accountsListPanel;
    private final Map<Long, java.awt.image.BufferedImage> accountAvatars = new java.util.concurrent.ConcurrentHashMap<>();
    private final Set<Long> avatarFetchedIds = java.util.Collections.synchronizedSet(new HashSet<>());
    private final Set<SessionStore.AttachmentKey> pendingDownloads = new HashSet<>();
    private final Set<MailIdentity> pendingFlagUpdates = new HashSet<>();
    private boolean deleting;
    private record MailIdentity(Long gmailId, String folder, long uidValidity, long uid) {}
    private DefaultListModel<MailFolderDto> folderListModel;
    private JList<MailFolderDto> folderList;

    // Middle column
    private JTextField searchField;
    private JToggleButton unreadFilterBtn;
    private JToggleButton attachFilterBtn;
    private CardLayout listCardLayout;
    private JPanel listCardPanel;
    private DefaultListModel<MailSummaryDto> mailListModel;
    /** UID đang có trong mailListModel, để "Tải thêm" chỉ thêm phần mới thay vì dựng lại cả danh sách. */
    private final java.util.Set<Long> loadedUids = new java.util.HashSet<>();
    private JList<MailSummaryDto> mailList;
    private JButton loadMoreBtn;
    private JButton previousPageBtn;
    private JButton nextPageBtn;
    private JComboBox<Integer> pageSizeCombo;
    private JLabel mailCountLabel;
    private JLabel listEmptyTitle;
    private JLabel listEmptySub;
    private JButton listEmptyActionBtn;
    private PublicConfigDto lastAppliedConfig;
    private boolean reauthCta;
    private JLabel userStatusLabel;

    // Right column
    private CardLayout detailCardLayout;
    private JPanel detailCardPanel;
    private JLabel detailSubjectLabel;
    private JLabel detailAvatarLabel;
    private JLabel detailFromLabel;
    private JLabel detailToDateLabel;
    private JPanel expandedHeaderPanel;
    private JButton expandHeaderBtn;
    private JPanel remoteImgBanner;
    private JLabel truncatedBanner;
    private JToggleButton modeToggleBtn;
    private JButton starActionBtn;
    private JButton unreadActionBtn;
    private JButton replyBtn;
    private JButton replyAllBtn;
    private JButton forwardBtn;
    private JButton cleanupOneBtn;
    private JButton deleteBtn;
    private JEditorPane bodyPane;
    private JPanel attachmentsContainer;
    private JPanel attachmentsStrip;
    private JLabel detailErrorLabel;

    public MainFrame(ApiClient apiClient) {
        this(apiClient, true);
    }

    public MainFrame(ApiClient apiClient, boolean autoLoad) {
        super(AppIcons.BRAND_FULL + " — " + AppIcons.BRAND_TAGLINE);
        this.apiClient = apiClient;
        this.cleanupService = new AttachmentCleanupService(apiClient);

        setIconImages(AppIcons.getWindowIcons());
        initComponents();
        setupMenuBar();
        setupKeyboardShortcuts();
        setupWindowBoundsPersistence();
        configureAutoCleanTimer();
        applyPublicConfig(SessionStore.getInstance().getPublicConfig());

        if (autoLoad) {
            loadPublicConfig();
            startConfigRefresh();
            loadUserInfo();
            loadGmailAccounts();
        }
    }

    private void initComponents() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        int w = ClientConfig.getInstance().getWindowWidth(1280);
        int h = ClientConfig.getInstance().getWindowHeight(780);
        setSize(Math.max(w, 1040), Math.max(h, 660));
        setMinimumSize(new Dimension(800, 600));
        setLocationRelativeTo(null);

        JPanel outerPanel = new JPanel(new BorderLayout());
        outerPanel.setBackground(Theme.bgApp());

        JPanel northStack = new JPanel();
        northStack.setLayout(new BoxLayout(northStack, BoxLayout.Y_AXIS));
        northStack.setBackground(Theme.bgSurface());

        if (ClientConfig.getInstance().isInsecureMode()) {
            northStack.add(ClientConfig.createInsecureBanner());
        }
        northStack.add(buildToolbar());
        outerPanel.add(northStack, BorderLayout.NORTH);

        // 3-Column Split Layout (kéo được, có nút thu/mở 1 chạm, nhớ vị trí)
        JPanel leftCol = buildLeftColumn();
        JPanel middleCol = buildMiddleColumn();
        JPanel rightCol = buildRightColumn();

        JSplitPane midRightSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, middleCol, rightCol);
        Theme.styleSplitter(midRightSplit);
        midRightSplit.setResizeWeight(0.34);

        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftCol, midRightSplit);
        Theme.styleSplitter(mainSplit);
        mainSplit.setResizeWeight(0.0);

        ClientConfig cfg = ClientConfig.getInstance();
        // Ưu tiên vị trí người dùng đã kéo (pixel); nếu chưa có thì dùng TỈ LỆ để lần layout đầu
        // tự co theo cửa sổ (800x600 vẫn giữ cột giữa >= ~250px và cột đọc thư đủ chỗ cho 7 nút icon).
        int savedLeft = cfg.getSplit("ui.split.left", -1);
        int savedMid = cfg.getSplit("ui.split.mid", -1);
        if (savedLeft > 0) mainSplit.setDividerLocation(savedLeft); else mainSplit.setDividerLocation(0.21);
        if (savedMid > 0) midRightSplit.setDividerLocation(savedMid); else midRightSplit.setDividerLocation(0.40);
        Theme.persistSplit(mainSplit, "ui.split.left");
        Theme.persistSplit(midRightSplit, "ui.split.mid");

        outerPanel.add(mainSplit, BorderLayout.CENTER);
        outerPanel.add(buildStatusBar(), BorderLayout.SOUTH);

        setContentPane(outerPanel);
    }

    /**
     * Toolbar 1 hàng, 3 vùng rõ ràng: nhóm hành động chính (trái) — nhóm hành động phụ icon-only (giữa)
     * — nhóm tài khoản/cấu hình (phải). Ở cửa sổ hẹp vùng giữa tự xuống dòng thay vì đẩy nút ra ngoài.
     */
    private JPanel buildToolbar() {
        JPanel bar = new JPanel(new BorderLayout(Theme.SP_3, 0));
        bar.setBackground(Theme.bgSurface());
        bar.setBorder(new CompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.border()),
                new EmptyBorder(Theme.SP_2, Theme.SP_3, Theme.SP_2, Theme.SP_3)
        ));
        bar.add(buildToolbarPrimary(), BorderLayout.WEST);
        bar.add(buildToolbarActions(), BorderLayout.CENTER);
        bar.add(buildToolbarAccount(), BorderLayout.EAST);
        return bar;
    }

    /** Trái: thương hiệu + Soạn thư (hành động chính duy nhất có nền màu). */
    private JPanel buildToolbarPrimary() {
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, Theme.SP_2, 0));
        left.setOpaque(false);

        JLabel brand = new JLabel(AppIcons.BRAND_NAME, AppIcons.logoIcon(26), SwingConstants.LEFT);
        brand.setFont(Theme.fontSubTitle());
        brand.setForeground(Theme.primaryText());
        brand.setIconTextGap(Theme.SP_2);
        left.add(brand);
        left.add(Box.createHorizontalStrut(Theme.SP_1));

        composeBtn = Theme.primaryButton("Soạn thư", AppIcons.vectorIcon(AppIcons.Glyph.COMPOSE, 16, Color.WHITE));
        composeBtn.setToolTipText("Soạn thư mới (Ctrl+N)");
        composeBtn.setMnemonic(KeyEvent.VK_S);
        composeBtn.addActionListener(e -> openComposeDialog(null, null));
        left.add(composeBtn);
        return left;
    }

    /** Giữa: hành động phụ, icon-only + tooltip để tiết kiệm chiều ngang. */
    private JPanel buildToolbarActions() {
        JPanel mid = new JPanel();
        mid.setOpaque(false);
        WrapLayout layout = new WrapLayout(FlowLayout.LEFT, Theme.SP_1, Theme.SP_1);
        mid.setLayout(layout);

        JButton refreshBtn = Theme.iconButton(AppIcons.Glyph.REFRESH, "Làm mới hộp thư (Ctrl+R)", Theme.textPrimary(), this::refreshCurrentMailbox);
        JButton cleanupBtn = Theme.iconButton(AppIcons.Glyph.DOWNLOAD, "Tải đính kèm & Xóa thư đã chọn", Theme.success(), this::openCleanupForSelectedMails);
        JButton gmailMgrBtn = Theme.iconButton(AppIcons.Glyph.MAIL, "Quản lý tài khoản Gmail (OAuth2 / App Password)", Theme.primaryText(), this::openGmailManagerDialog);

        adminToolbarBtn = Theme.iconButton(AppIcons.Glyph.SHIELD, "Bảng điều khiển Quản trị Hệ thống", Theme.warning(), this::openAdminFrame);
        adminToolbarBtn.setVisible(false);

        mid.add(refreshBtn);
        mid.add(cleanupBtn);
        mid.add(gmailMgrBtn);
        mid.add(adminToolbarBtn);
        return mid;
    }

    /** Phải: account chip (mở menu hồ sơ) + chuyển Sáng/Tối + Cài đặt. */
    private JPanel buildToolbarAccount() {
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, Theme.SP_1, 0));
        right.setOpaque(false);

        userProfileBtn = Theme.secondaryButton("Tài khoản", AppIcons.vectorIcon(AppIcons.Glyph.USER, 16, Theme.primaryText()));
        userProfileBtn.setToolTipText("Hồ sơ cá nhân, đổi mật khẩu và đăng xuất");
        userProfileBtn.addActionListener(e -> showAccountMenu());

        JButton themeBtn = Theme.iconButton(
                Theme.isDark() ? AppIcons.Glyph.SUN : AppIcons.Glyph.MOON,
                Theme.isDark() ? "Chuyển sang chế độ Sáng" : "Chuyển sang chế độ Tối",
                Theme.textPrimary(), this::toggleTheme);

        JButton settingsBtn = Theme.iconButton(AppIcons.Glyph.SETTINGS, "Cài đặt máy chủ, thư mục tải về và Tự động dọn", Theme.textPrimary(), this::openSettingsDialog);

        right.add(userProfileBtn);
        right.add(themeBtn);
        right.add(settingsBtn);
        return right;
    }

    /** Menu nhỏ dưới account chip: gộp các mục cá nhân thay vì chiếm thêm nút trên toolbar. */
    private void showAccountMenu() {
        JPopupMenu menu = new JPopupMenu();
        JMenuItem profile = new JMenuItem("Hồ sơ tài khoản");
        profile.addActionListener(e -> openProfileDialog());
        JMenuItem changePw = new JMenuItem("Đổi mật khẩu");
        changePw.addActionListener(e -> new ChangePasswordDialog(this, apiClient, false).setVisible(true));
        JMenuItem gmail = new JMenuItem("Quản lý tài khoản Gmail");
        gmail.addActionListener(e -> openGmailManagerDialog());
        JMenuItem logout = new JMenuItem("Đăng xuất");
        logout.addActionListener(e -> handleLogout());
        menu.add(profile);
        menu.add(changePw);
        menu.add(gmail);
        menu.addSeparator();
        menu.add(logout);
        menu.show(userProfileBtn, 0, userProfileBtn.getHeight());
    }

    private JPanel buildLeftColumn() {
        JPanel left = new JPanel(new BorderLayout());
        left.setBackground(Theme.bgSurface());
        left.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, Theme.border()));

        // Top half: Linked Gmail Accounts
        JPanel accSection = new JPanel(new BorderLayout(0, Theme.SP_1));
        accSection.setOpaque(false);
        accSection.setBorder(new EmptyBorder(Theme.SP_3, Theme.SP_3, Theme.SP_2, Theme.SP_3));

        JPanel accHeader = new JPanel(new BorderLayout());
        accHeader.setOpaque(false);
        JLabel accTitle = new JLabel("TÀI KHOẢN GMAIL");
        accTitle.setFont(Theme.fontTiny().deriveFont(Font.BOLD));
        accTitle.setForeground(Theme.textMuted());

        JButton addAccBtn = new JButton("+ Thêm");
        addAccBtn.setFont(Theme.fontTiny().deriveFont(Font.BOLD));
        addAccBtn.setForeground(Theme.primary());
        addAccBtn.setContentAreaFilled(false);
        addAccBtn.setBorderPainted(false);
        addAccBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        addAccBtn.setToolTipText("Liên kết thêm tài khoản Gmail");
        addAccBtn.addActionListener(e -> openGmailManagerDialog());

        accHeader.add(accTitle, BorderLayout.WEST);
        accHeader.add(addAccBtn, BorderLayout.EAST);
        accSection.add(accHeader, BorderLayout.NORTH);

        accountsListPanel = Theme.trackedWidthPanel(null);
        accountsListPanel.setLayout(new BoxLayout(accountsListPanel, BoxLayout.Y_AXIS));
        accountsListPanel.setBackground(Theme.bgSurface());

        JScrollPane accScroll = new JScrollPane(accountsListPanel);
        accScroll.setBorder(null);
        accScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        accScroll.getViewport().setBackground(Theme.bgSurface());
        accScroll.setMinimumSize(new Dimension(150, 60));
        accSection.add(accScroll, BorderLayout.CENTER);

        // Bottom half: Folders Tree/List
        JPanel folderSection = new JPanel(new BorderLayout(0, Theme.SP_1));
        folderSection.setOpaque(false);
        folderSection.setBorder(new EmptyBorder(Theme.SP_2, Theme.SP_2, Theme.SP_2, Theme.SP_2));

        JLabel folderTitle = new JLabel("  THƯ MỤC HỘP THƯ");
        folderTitle.setFont(Theme.fontTiny().deriveFont(Font.BOLD));
        folderTitle.setForeground(Theme.textMuted());
        folderSection.add(folderTitle, BorderLayout.NORTH);

        folderListModel = new DefaultListModel<>();
        folderList = new JList<>(folderListModel);
        folderList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        folderList.setBackground(Theme.bgSurface());
        folderList.setCellRenderer(new FolderCellRenderer());
        folderList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && !mockupMode) {
                MailFolderDto f = folderList.getSelectedValue();
                if (f != null && (selectedFolder == null || !f.name().equals(selectedFolder.name()))) {
                    resetMailboxDetail();
                    currentUidValidity = 0;
                    selectedFolder = f;
                    loadMails(0, false);
                }
            }
        });

        JScrollPane folderScroll = new JScrollPane(folderList);
        folderScroll.setBorder(null);
        folderScroll.getViewport().setBackground(Theme.bgSurface());
        folderSection.add(folderScroll, BorderLayout.CENTER);

        // Tài khoản ↔ Thư mục: thanh kéo dọc, double-click để thu/mở, nhớ vị trí.
        JSplitPane leftSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, accSection, folderSection);
        Theme.styleSplitter(leftSplit);
        leftSplit.setResizeWeight(0.0);
        leftSplit.setBorder(null);
        ClientConfig cfgLeft = ClientConfig.getInstance();
        int savedLeft = cfgLeft.getSplit("ui.split.accounts", -1);
        if (savedLeft > 0) {
            SwingUtilities.invokeLater(() -> leftSplit.setDividerLocation(Math.max(150, savedLeft)));
        } else {
            leftSplit.setDividerLocation(0.32);
        }
        Theme.persistSplit(leftSplit, "ui.split.accounts");

        left.add(leftSplit, BorderLayout.CENTER);
        return left;
    }

    private JPanel buildMiddleColumn() {
        JPanel mid = new JPanel(new BorderLayout());
        mid.setBackground(Theme.bgSurface());
        mid.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, Theme.border()));

        // Search + Filters header
        JPanel searchHeader = new JPanel();
        searchHeader.setLayout(new BoxLayout(searchHeader, BoxLayout.Y_AXIS));
        searchHeader.setBackground(Theme.bgSurface());
        searchHeader.setBorder(new CompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.border()),
                new EmptyBorder(Theme.SP_2, Theme.SP_3, Theme.SP_2, Theme.SP_3)
        ));

        JPanel searchRow = new JPanel(new BorderLayout(Theme.SP_1, 0));
        searchRow.setOpaque(false);
        searchField = new JTextField() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (getText().isEmpty()) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    Theme.applyTextHints(g2);
                    g2.setColor(Theme.textMuted());
                    g2.setFont(Theme.fontSmall());
                    Insets ins = getInsets();
                    FontMetrics fm = g2.getFontMetrics();
                    int y = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
                    g2.drawString("Tìm tiêu đề, người gửi...", ins.left + 2, y);
                    g2.dispose();
                }
            }
        };
        Theme.styleTextField(searchField);
        searchField.setToolTipText("Tìm theo tiêu đề, người gửi hoặc cú pháp Gmail (Ctrl+F)");
        searchField.addActionListener(e -> triggerSearchOrFilter());

        JButton searchBtn = Theme.primaryButton("", AppIcons.vectorIcon(AppIcons.Glyph.SEARCH, 15, Color.WHITE));
        searchBtn.setToolTipText("Tìm kiếm");
        searchBtn.addActionListener(e -> triggerSearchOrFilter());

        searchRow.add(searchField, BorderLayout.CENTER);
        searchRow.add(searchBtn, BorderLayout.EAST);

        JPanel filterRow = Theme.createWrapPanel(Theme.SP_1, 4);
        unreadFilterBtn = createFilterToggle("Chưa đọc", null);
        unreadFilterBtn.addActionListener(e -> triggerSearchOrFilter());

        attachFilterBtn = createFilterToggle("Có đính kèm", AppIcons.vectorIcon(AppIcons.Glyph.ATTACHMENT, 13, Theme.textSecondary()));
        attachFilterBtn.addActionListener(e -> triggerSearchOrFilter());

        JButton clearFilterBtn = new JButton("Xóa lọc");
        clearFilterBtn.setFont(Theme.fontTiny().deriveFont(Font.BOLD));
        clearFilterBtn.setContentAreaFilled(false);
        clearFilterBtn.setBorderPainted(false);
        clearFilterBtn.setForeground(Theme.primaryText());
        clearFilterBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        clearFilterBtn.addActionListener(e -> {
            searchField.setText("");
            unreadFilterBtn.setSelected(false);
            attachFilterBtn.setSelected(false);
            unreadFilterBtn.repaint();
            attachFilterBtn.repaint();
            if (selectedAccount == null || selectedFolder == null) {
                setStatus("Đã xóa bộ lọc tìm kiếm.");
            } else {
                loadMails(0, false);
            }
        });

        filterRow.add(unreadFilterBtn);
        filterRow.add(attachFilterBtn);
        filterRow.add(clearFilterBtn);

        searchHeader.add(searchRow);
        searchHeader.add(filterRow);
        mid.add(searchHeader, BorderLayout.NORTH);

        // Card container: LIST / LOADING / EMPTY
        listCardLayout = new CardLayout();
        listCardPanel = new JPanel(listCardLayout);
        listCardPanel.setBackground(Theme.bgSurface());

        // 1. LIST (khóa chiều rộng theo viewport để không xuất hiện thanh cuộn ngang ở 800x600)
        mailListModel = new DefaultListModel<>();
        mailList = new JList<>(mailListModel) {
            @Override
            public boolean getScrollableTracksViewportWidth() {
                return true;
            }
            private boolean starGesture;
            @Override protected void processMouseEvent(MouseEvent e) {
                if (e.getID() == MouseEvent.MOUSE_PRESSED) {
                    starGesture = false;
                    int index = locationToIndex(e.getPoint());
                    Rectangle bounds = index < 0 ? null : getCellBounds(index, index);
                    if (SwingUtilities.isLeftMouseButton(e) && bounds != null && bounds.contains(e.getPoint())
                            && e.getX() >= bounds.x + bounds.width - 34 && !mockupMode) {
                        starGesture = true;
                        toggleStarForMail(getModel().getElementAt(index));
                    }
                }
                if (starGesture && SwingUtilities.isLeftMouseButton(e)) {
                    e.consume();
                    if (e.getID() == MouseEvent.MOUSE_CLICKED) starGesture = false;
                    return;
                }
                super.processMouseEvent(e);
            }
        };
        mailList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        mailList.setBackground(Theme.bgSurface());
        mailList.setCellRenderer(new MailSummaryCellRenderer());
        mailList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && !mockupMode && !suppressDetailSelection) {
                MailSummaryDto sel = mailList.getSelectedValue();
                if (sel != null && mailList.getSelectedIndices().length == 1) {
                    loadMailDetail(sel, false);
                } else {
                    resetMailboxDetail();
                }
            }
        });
        mailList.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    MailSummaryDto sel = mailList.getSelectedValue();
                    if (sel != null && mailList.getSelectedIndices().length == 1) loadMailDetail(sel, false);
                }
            }
        });
        mailList.addMouseListener(new MouseAdapter() {
            private void popup(MouseEvent e) {
                if (!e.isPopupTrigger() || mockupMode || !mailboxReady) return;
                int index = mailList.locationToIndex(e.getPoint());
                Rectangle bounds = index < 0 ? null : mailList.getCellBounds(index, index);
                if (bounds == null || !bounds.contains(e.getPoint())) return;
                if (!mailList.isSelectedIndex(index)) {
                    suppressDetailSelection = true;
                    try {
                        mailList.setSelectedIndex(index);
                        resetMailboxDetail();
                    } finally {
                        suppressDetailSelection = false;
                    }
                }
                JPopupMenu menu = new JPopupMenu();
                MailSummaryDto item = mailListModel.get(index);
                long generation = mailboxGeneration;
                JMenuItem star = new JMenuItem(item.flags() != null && item.flags().flagged() ? "Bỏ gắn sao" : "Gắn sao");
                star.addActionListener(a -> { if (mailboxGeneration == generation) toggleStarForMail(item); });
                menu.add(star);
                JMenuItem delete = new JMenuItem("Xóa thư đã chọn");
                delete.addActionListener(a -> { if (mailboxGeneration == generation) deleteSelectedMails(); });
                menu.add(delete);
                menu.show(mailList, e.getX(), e.getY());
            }
            @Override public void mousePressed(MouseEvent e) { popup(e); }
            @Override public void mouseReleased(MouseEvent e) { popup(e); }
        });

        JScrollPane mailScroll = new JScrollPane(mailList);
        mailScroll.setBorder(null);
        mailScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        mailScroll.getViewport().setBackground(Theme.bgSurface());

        JPanel listWrapper = new JPanel(new BorderLayout());
        listWrapper.setBackground(Theme.bgSurface());
        listWrapper.add(mailScroll, BorderLayout.CENTER);

        JPanel listFooter = new JPanel(new BorderLayout(Theme.SP_2, 0));
        listFooter.setBackground(Theme.bgSurfaceAlt());
        listFooter.setBorder(new CompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.border()),
                new EmptyBorder(Theme.SP_1, Theme.SP_3, Theme.SP_1, Theme.SP_3)
        ));
        mailCountLabel = new JLabel("0 thư");
        mailCountLabel.setFont(Theme.fontTiny());
        mailCountLabel.setForeground(Theme.textSecondary());

        loadMoreBtn = new JButton("Tải thêm...");
        loadMoreBtn.setFont(Theme.fontTiny());
        loadMoreBtn.setForeground(Theme.primary());
        loadMoreBtn.setContentAreaFilled(false);
        loadMoreBtn.setBorderPainted(false);
        loadMoreBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        loadMoreBtn.setVisible(false);
        loadMoreBtn.addActionListener(e -> loadMails(currentPage + 1, true));

        JPanel counts = new JPanel(new BorderLayout(4, 0));
        counts.setOpaque(false);
        counts.add(mailCountLabel, BorderLayout.CENTER);
        counts.add(loadMoreBtn, BorderLayout.EAST);
        listFooter.add(counts, BorderLayout.NORTH);
        JPanel pageControls = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 0));
        pageControls.setOpaque(false);
        previousPageBtn = new JButton("‹");
        previousPageBtn.setToolTipText("Trang trước");
        previousPageBtn.setEnabled(false);
        previousPageBtn.addActionListener(e -> { if (currentPage > 0) loadMails(currentPage - 1, false); });
        nextPageBtn = new JButton("›");
        nextPageBtn.setToolTipText("Trang sau");
        nextPageBtn.setEnabled(false);
        nextPageBtn.addActionListener(e -> { if (hasMoreMails) loadMails(currentPage + 1, false); });
        pageSizeCombo = new JComboBox<>(new Integer[]{0, 10, 20, 50});
        pageSizeCombo.setRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                                     boolean selected, boolean focus) {
                return super.getListCellRendererComponent(list,
                        Integer.valueOf(0).equals(value) ? "Mặc định" : value, index, selected, focus);
            }
        });
        pageSizeCombo.setToolTipText("Số thư mỗi trang");
        pageSizeCombo.addActionListener(e -> { if (!mockupMode && selectedFolder != null) loadMails(0, false); });
        pageControls.add(previousPageBtn);
        pageControls.add(nextPageBtn);
        pageControls.add(pageSizeCombo);
        listFooter.add(pageControls, BorderLayout.SOUTH);
        listWrapper.add(listFooter, BorderLayout.SOUTH);

        // 2. LOADING (Skeleton rows drawn with Java2D)
        JPanel skeletonPanel = new SkeletonListPanel();

        // 3. EMPTY / CTA
        JPanel emptyPanel = new JPanel(new GridBagLayout());
        emptyPanel.setBackground(Theme.bgSurface());
        JPanel emptyBox = new JPanel();
        emptyBox.setOpaque(false);
        emptyBox.setLayout(new BoxLayout(emptyBox, BoxLayout.Y_AXIS));
        emptyBox.setBorder(new EmptyBorder(Theme.SP_5, Theme.SP_5, Theme.SP_5, Theme.SP_5));

        JLabel emptyIcon = new JLabel(AppIcons.vectorIcon(AppIcons.Glyph.INBOX, 42, Theme.textMuted()));
        emptyIcon.setAlignmentX(Component.CENTER_ALIGNMENT);
        listEmptyTitle = new JLabel("Chưa chọn tài khoản Gmail");
        listEmptyTitle.setFont(Theme.fontSubTitle());
        listEmptyTitle.setForeground(Theme.textPrimary());
        listEmptyTitle.setAlignmentX(Component.CENTER_ALIGNMENT);
        listEmptySub = new JLabel("Liên kết hoặc chọn một tài khoản ở cột trái để bắt đầu.");
        listEmptySub.setFont(Theme.fontSmall());
        listEmptySub.setForeground(Theme.textSecondary());
        listEmptySub.setAlignmentX(Component.CENTER_ALIGNMENT);
        listEmptyActionBtn = Theme.primaryButton("Quản lý Tài khoản Gmail", null);
        listEmptyActionBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        listEmptyActionBtn.addActionListener(e -> openGmailManagerDialog());

        emptyBox.add(emptyIcon);
        emptyBox.add(Box.createVerticalStrut(Theme.SP_3));
        emptyBox.add(listEmptyTitle);
        emptyBox.add(Box.createVerticalStrut(Theme.SP_1));
        emptyBox.add(listEmptySub);
        emptyBox.add(Box.createVerticalStrut(Theme.SP_3));
        emptyBox.add(listEmptyActionBtn);
        emptyPanel.add(emptyBox);

        listCardPanel.add(listWrapper, "LIST");
        listCardPanel.add(skeletonPanel, "LOADING");
        listCardPanel.add(emptyPanel, "EMPTY");
        listCardLayout.show(listCardPanel, "EMPTY");

        mid.add(listCardPanel, BorderLayout.CENTER);
        return mid;
    }

    private JPanel buildRightColumn() {
        detailCardLayout = new CardLayout();
        detailCardPanel = new JPanel(detailCardLayout);
        detailCardPanel.setBackground(Theme.bgSurface());

        // 1. EMPTY_DETAIL
        JPanel empty = new JPanel(new GridBagLayout());
        empty.setBackground(Theme.bgSurface());
        JPanel box = new JPanel();
        box.setOpaque(false);
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        JLabel icon = new JLabel(AppIcons.logoIcon(64));
        icon.setAlignmentX(Component.CENTER_ALIGNMENT);
        JLabel title = new JLabel("Chọn một thư để xem nội dung");
        title.setFont(Theme.fontSubTitle());
        title.setForeground(Theme.textPrimary());
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        JLabel hint = new JLabel("<html><div style='text-align:center;width:280px;'>"
                + "Phím tắt: Ctrl+N Soạn thư • Ctrl+R Làm mới<br>Ctrl+F Tìm kiếm • Delete Xóa thư"
                + "</div></html>");
        hint.setFont(Theme.fontSmall());
        hint.setForeground(Theme.textSecondary());
        hint.setAlignmentX(Component.CENTER_ALIGNMENT);
        box.add(icon);
        box.add(Box.createVerticalStrut(Theme.SP_3));
        box.add(title);
        box.add(Box.createVerticalStrut(Theme.SP_1));
        box.add(hint);
        empty.add(box);

        // 2. LOADING_DETAIL
        JPanel loading = new JPanel(new GridBagLayout());
        loading.setBackground(Theme.bgSurface());
        JLabel loadLbl = new JLabel("Đang tải nội dung thư an toàn từ máy chủ...");
        loadLbl.setFont(Theme.fontRegular());
        loadLbl.setForeground(Theme.textSecondary());
        loading.add(loadLbl);

        // 3. ERROR_DETAIL
        JPanel errPanel = new JPanel(new GridBagLayout());
        errPanel.setBackground(Theme.bgSurface());
        detailErrorLabel = new JLabel("Không tải được nội dung thư.");
        detailErrorLabel.setFont(Theme.fontBold());
        detailErrorLabel.setForeground(Theme.danger());
        errPanel.add(detailErrorLabel);

        // 4. DETAIL
        JPanel detail = new JPanel(new BorderLayout());
        detail.setBackground(Theme.bgSurface());

        // Top detail stack: action bar + subject + sender header + banners
        JPanel detailNorth = new JPanel();
        detailNorth.setLayout(new BoxLayout(detailNorth, BoxLayout.Y_AXIS));
        detailNorth.setBackground(Theme.bgSurface());
        detailNorth.setBorder(new CompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.border()),
                new EmptyBorder(Theme.SP_3, Theme.SP_4, Theme.SP_3, Theme.SP_4)
        ));

        // Action bar: 7 nút icon-only + tooltip (mẫu Gmail). Tổng ~188px nên luôn vừa 1 hàng
        // kể cả khi cột đọc thư co còn ~250px ở cửa sổ 800x600.
        // ponytail: dùng FlowLayout (không wrap) vì WrapLayout cần revalidate mới tính lại số hàng;
        // đổi lại thành segmented control / overflow "⋯" khi số hành động vượt quá 8.
        JPanel actionBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 1, 2));
        actionBar.setOpaque(false);
        EmptyBorder compactBtnPad = new EmptyBorder(4, 4, 4, 4);
        int iconSz = 16;

        replyBtn = Theme.iconButton(AppIcons.Glyph.REPLY, iconSz, "Trả lời", Theme.textPrimary(), () -> openComposeDialog(currentDetail, "REPLY"));
        replyBtn.setBorder(compactBtnPad);

        replyAllBtn = Theme.iconButton(AppIcons.Glyph.REPLY_ALL, iconSz, "Trả lời tất cả", Theme.textPrimary(), () -> openComposeDialog(currentDetail, "REPLY_ALL"));
        replyAllBtn.setBorder(compactBtnPad);

        forwardBtn = Theme.iconButton(AppIcons.Glyph.FORWARD, iconSz, "Chuyển tiếp", Theme.textPrimary(), () -> openComposeDialog(currentDetail, "FORWARD"));
        forwardBtn.setBorder(compactBtnPad);

        starActionBtn = Theme.iconButton(AppIcons.Glyph.STAR, iconSz, "Gắn sao", Theme.starGold(), this::toggleStarCurrentMail);
        starActionBtn.setBorder(compactBtnPad);

        unreadActionBtn = Theme.iconButton(AppIcons.Glyph.UNREAD, iconSz, "Đánh dấu chưa đọc", Theme.textPrimary(), this::markCurrentMailUnread);
        unreadActionBtn.setBorder(compactBtnPad);

        cleanupOneBtn = Theme.iconButton(AppIcons.Glyph.DOWNLOAD, iconSz, "Tải đính kèm & Xóa thư này", Theme.success(), this::openCleanupForCurrentMail);
        cleanupOneBtn.setBorder(compactBtnPad);

        deleteBtn = Theme.iconButton(AppIcons.Glyph.DELETE, iconSz, "Xóa thư", Theme.danger(), this::deleteCurrentDetail);
        deleteBtn.setBorder(compactBtnPad);

        actionBar.add(replyBtn);
        actionBar.add(replyAllBtn);
        actionBar.add(forwardBtn);
        actionBar.add(starActionBtn);
        actionBar.add(unreadActionBtn);
        actionBar.add(cleanupOneBtn);
        actionBar.add(deleteBtn);

        // ponytail: 1 nút gạt thay cho 2 toggle HTML/Văn bản — đủ ở cột rộng 250px;
        // đổi lại thành segmented control khi cột đọc thư được bảo đảm rộng >= 480px.
        modeToggleBtn = createFilterToggle("HTML sạch", null);
        modeToggleBtn.setSelected(true);
        modeToggleBtn.setToolTipText("Chuyển giữa chế độ xem HTML sạch và văn bản thuần");
        modeToggleBtn.addActionListener(e -> {
            showHtmlMode = !showHtmlMode;
            modeToggleBtn.setSelected(showHtmlMode);
            modeToggleBtn.setText(showHtmlMode ? "HTML sạch" : "Văn bản");
            modeToggleBtn.repaint();
            renderCurrentBody();
        });

        // Subject (căn trái tuyệt đối trong BoxLayout)
        detailSubjectLabel = Theme.ellipsisLabel("(Không có tiêu đề)");
        detailSubjectLabel.setFont(Theme.fontSubTitle());
        detailSubjectLabel.setForeground(Theme.textPrimary());
        detailSubjectLabel.setHorizontalAlignment(SwingConstants.LEFT);
        JPanel subjectRow = new JPanel(new BorderLayout());
        subjectRow.setOpaque(false);
        subjectRow.add(detailSubjectLabel, BorderLayout.CENTER);

        // Sender row
        JPanel senderRow = new JPanel(new BorderLayout(Theme.SP_3, 0));
        senderRow.setOpaque(false);
        detailAvatarLabel = new JLabel();
        JPanel senderMeta = new JPanel();
        senderMeta.setOpaque(false);
        senderMeta.setLayout(new BoxLayout(senderMeta, BoxLayout.Y_AXIS));
        detailFromLabel = Theme.ellipsisLabel("");
        detailFromLabel.setFont(Theme.fontBold());
        detailFromLabel.setForeground(Theme.textPrimary());
        detailToDateLabel = Theme.ellipsisLabel("");
        detailToDateLabel.setFont(Theme.fontSmall());
        detailToDateLabel.setForeground(Theme.textSecondary());
        senderMeta.add(detailFromLabel);
        senderMeta.add(detailToDateLabel);

        expandHeaderBtn = new JButton("Chi tiết (+)");
        expandHeaderBtn.setFont(Theme.fontTiny());
        expandHeaderBtn.setForeground(Theme.primaryText());
        expandHeaderBtn.setContentAreaFilled(false);
        expandHeaderBtn.setBorderPainted(false);
        expandHeaderBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        expandHeaderBtn.addActionListener(e -> {
            headerExpanded = !headerExpanded;
            expandedHeaderPanel.setVisible(headerExpanded);
            expandHeaderBtn.setText(headerExpanded ? "Thu gọn (-)" : "Chi tiết (+)");
            detailNorth.revalidate();
        });

        senderRow.add(detailAvatarLabel, BorderLayout.WEST);
        senderRow.add(senderMeta, BorderLayout.CENTER);
        senderRow.add(expandHeaderBtn, BorderLayout.EAST);

        expandedHeaderPanel = new JPanel();
        expandedHeaderPanel.setLayout(new BoxLayout(expandedHeaderPanel, BoxLayout.Y_AXIS));
        expandedHeaderPanel.setBackground(Theme.bgSurfaceAlt());
        expandedHeaderPanel.setBorder(new EmptyBorder(Theme.SP_2, Theme.SP_3, Theme.SP_2, Theme.SP_3));
        expandedHeaderPanel.setVisible(false);

        // Blocked remote images banner (wrap: ở cửa sổ hẹp nút xuống dòng thay vì bóp chữ thành "…")
        remoteImgBanner = Theme.createWrapPanel(Theme.SP_2, Theme.SP_1);
        remoteImgBanner.setBackground(Theme.warningSoft());
        remoteImgBanner.setBorder(new EmptyBorder(Theme.SP_2, Theme.SP_3, Theme.SP_2, Theme.SP_3));
        JLabel remLbl = Theme.ellipsisLabel("Ảnh từ xa đã bị chặn để bảo vệ quyền riêng tư của bạn.");
        remLbl.setFont(Theme.fontSmall());
        remLbl.setForeground(Theme.warning());
        remLbl.setToolTipText("Ảnh từ xa đã bị chặn để bảo vệ quyền riêng tư của bạn.");
        JButton loadRemoteBtn = Theme.secondaryButton("Tải ảnh từ xa", null);
        loadRemoteBtn.addActionListener(e -> {
            if (currentDetail != null) {
                MailSummaryDto cur = mailList.getSelectedValue();
                if (cur != null) loadMailDetail(cur, true);
            }
        });
        remoteImgBanner.add(remLbl);
        remoteImgBanner.add(loadRemoteBtn);
        remoteImgBanner.setVisible(false);

        truncatedBanner = new JLabel("Nội dung thư rất lớn nên đã được cắt gọn an toàn ở mức 512 KB.");
        truncatedBanner.setFont(Theme.fontSmall());
        truncatedBanner.setForeground(Theme.warning());
        truncatedBanner.setVisible(false);

        detailNorth.add(actionBar);
        detailNorth.add(Box.createVerticalStrut(Theme.SP_1));
        detailNorth.add(subjectRow);
        detailNorth.add(Box.createVerticalStrut(Theme.SP_1));
        detailNorth.add(senderRow);
        detailNorth.add(Box.createVerticalStrut(Theme.SP_1));
        detailNorth.add(expandedHeaderPanel);
        detailNorth.add(Box.createVerticalStrut(Theme.SP_1));
        detailNorth.add(remoteImgBanner);
        detailNorth.add(truncatedBanner);

        // Body pane
        bodyPane = new JEditorPane();
        bodyPane.setEditable(false);
        bodyPane.setBackground(Theme.bgSurface());
        bodyPane.setForeground(Theme.textPrimary());
        bodyPane.setFont(Theme.fontRegular());
        bodyPane.addHyperlinkListener(e -> {
            if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED && e.getURL() != null) {
                String scheme = e.getURL().getProtocol();
                if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
                    String targetUrl = e.getURL().toString();
                    int confirm = JOptionPane.showConfirmDialog(
                            MainFrame.this,
                            "Bạn sắp mở liên kết ngoài bằng trình duyệt web:\n\n" + targetUrl
                                    + "\n\nChỉ tiếp tục nếu bạn tin tưởng người gửi thư này.",
                            "Xác nhận mở liên kết ngoài",
                            JOptionPane.YES_NO_OPTION,
                            JOptionPane.WARNING_MESSAGE
                    );
                    if (confirm == JOptionPane.YES_OPTION) {
                        try {
                            if (Desktop.isDesktopSupported()) {
                                Desktop.getDesktop().browse(e.getURL().toURI());
                            }
                        } catch (Exception ex) {
                            JOptionPane.showMessageDialog(MainFrame.this,
                                    ClientSafetyUtil.toFriendlyVietnameseError(ex),
                                    "Không mở được trình duyệt", JOptionPane.WARNING_MESSAGE);
                        }
                    }
                }
            }
        });
        JScrollPane bodyScroll = new JScrollPane(bodyPane);
        bodyScroll.setBorder(null);
        bodyScroll.getViewport().setBackground(Theme.bgSurface());

        // Thanh chọn chế độ xem đặt ngay trên nội dung thư: ở đây nó mới thể hiện rõ tác dụng.
        // Dùng 1 hàng wrap (nhãn + 2 toggle) để ở cửa sổ hẹp các nút xuống dòng thay vì chồng lên nhãn.
        JPanel bodyModeBar = Theme.createWrapPanel(Theme.SP_1, 2);
        JLabel bodyModeLbl = new JLabel("Chế độ xem:");
        bodyModeLbl.setFont(Theme.fontTiny());
        bodyModeLbl.setForeground(Theme.textMuted());
        bodyModeBar.add(bodyModeLbl);
        bodyModeBar.add(modeToggleBtn);

        JPanel bodyModeStrip = new JPanel(new BorderLayout());
        bodyModeStrip.setBackground(Theme.bgSurfaceAlt());
        bodyModeStrip.setBorder(new CompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.border()),
                new EmptyBorder(2, Theme.SP_3, 2, Theme.SP_3)
        ));
        bodyModeStrip.add(bodyModeBar, BorderLayout.CENTER);

        JPanel bodyArea = new JPanel(new BorderLayout());
        bodyArea.setBackground(Theme.bgSurface());
        bodyArea.add(bodyModeStrip, BorderLayout.NORTH);
        bodyArea.add(bodyScroll, BorderLayout.CENTER);

        // Attachments footer strip
        attachmentsContainer = new JPanel(new BorderLayout(0, Theme.SP_1));
        attachmentsContainer.setBackground(Theme.bgSurfaceAlt());
        attachmentsContainer.setBorder(new CompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.border()),
                new EmptyBorder(Theme.SP_2, Theme.SP_4, Theme.SP_2, Theme.SP_4)
        ));
        JLabel attTitle = new JLabel("Tệp đính kèm:", AppIcons.vectorIcon(AppIcons.Glyph.ATTACHMENT, 14, Theme.primary()), SwingConstants.LEFT);
        attTitle.setFont(Theme.fontBold());
        attTitle.setForeground(Theme.textPrimary());
        attachmentsStrip = new JPanel(new FlowLayout(FlowLayout.LEFT, Theme.SP_2, Theme.SP_1));
        attachmentsStrip.setOpaque(false);
        attachmentsContainer.add(attTitle, BorderLayout.NORTH);
        attachmentsContainer.add(Theme.horizontalChipScroll(attachmentsStrip), BorderLayout.CENTER);
        attachmentsContainer.setVisible(false);

        detail.add(detailNorth, BorderLayout.NORTH);
        detail.add(bodyArea, BorderLayout.CENTER);
        detail.add(attachmentsContainer, BorderLayout.SOUTH);

        detailCardPanel.add(empty, "EMPTY_DETAIL");
        detailCardPanel.add(loading, "LOADING_DETAIL");
        detailCardPanel.add(errPanel, "ERROR_DETAIL");
        detailCardPanel.add(detail, "DETAIL");
        detailCardLayout.show(detailCardPanel, "EMPTY_DETAIL");

        return detailCardPanel;
    }

    private JPanel buildStatusBar() {
        JPanel bar = new JPanel(new BorderLayout(Theme.SP_3, 0));
        bar.setBackground(Theme.bgSurfaceAlt());
        bar.setBorder(new CompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.border()),
                new EmptyBorder(Theme.SP_1, Theme.SP_3, Theme.SP_1, Theme.SP_3)
        ));

        statusBarLabel = new JLabel("Sẵn sàng");
        statusBarLabel.setFont(Theme.fontTiny());
        statusBarLabel.setForeground(Theme.textSecondary());

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, Theme.SP_2, 0));
        right.setOpaque(false);

        autoCleanBadge = Theme.badge("Tự động dọn: TẮT", Theme.bgHover(), Theme.textSecondary());
        autoCleanStopBtn = Theme.dangerButton("Dừng dọn", null);
        autoCleanStopBtn.setFont(Theme.fontTiny());
        autoCleanStopBtn.setVisible(false);
        autoCleanStopBtn.addActionListener(e -> {
            autoCleanCancelled.set(true);
            setStatus("Đang yêu cầu dừng Tự động dọn...");
        });

        serverBadgeLabel = new JLabel("HTTPS: " + apiClient.getBaseUrl());
        serverBadgeLabel.setFont(Theme.fontTiny());
        serverBadgeLabel.setForeground(Theme.textMuted());

        right.add(autoCleanBadge);
        right.add(autoCleanStopBtn);
        userStatusLabel = new JLabel();
        userStatusLabel.setFont(Theme.fontTiny());
        userStatusLabel.setForeground(Theme.textMuted());
        right.add(userStatusLabel);
        right.add(serverBadgeLabel);

        bar.add(statusBarLabel, BorderLayout.CENTER);
        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    private void setupMenuBar() {
        JMenuBar menuBar = new JMenuBar();

        JMenu accountMenu = new JMenu("Tài khoản");
        accountMenu.setMnemonic(KeyEvent.VK_T);
        JMenuItem profileItem = new JMenuItem("Hồ sơ tài khoản");
        profileItem.addActionListener(e -> openProfileDialog());
        JMenuItem changePwItem = new JMenuItem("Đổi mật khẩu");
        changePwItem.addActionListener(e -> new ChangePasswordDialog(this, apiClient, false).setVisible(true));
        JMenuItem gmailMgrItem = new JMenuItem("Quản lý tài khoản Gmail");
        gmailMgrItem.addActionListener(e -> openGmailManagerDialog());
        JMenuItem logoutItem = new JMenuItem("Đăng xuất");
        logoutItem.addActionListener(e -> handleLogout());
        accountMenu.add(profileItem);
        accountMenu.add(changePwItem);
        accountMenu.add(gmailMgrItem);
        accountMenu.addSeparator();
        accountMenu.add(logoutItem);

        JMenu mailMenu = new JMenu("Hộp thư");
        mailMenu.setMnemonic(KeyEvent.VK_H);
        composeItem = new JMenuItem("Soạn thư mới (Ctrl+N)");
        composeItem.addActionListener(e -> openComposeDialog(null, null));
        JMenuItem refreshItem = new JMenuItem("Làm mới (Ctrl+R)");
        refreshItem.addActionListener(e -> refreshCurrentMailbox());
        JMenuItem cleanupItem = new JMenuItem("Tải đính kèm & Xóa thư đã chọn");
        cleanupItem.addActionListener(e -> openCleanupForSelectedMails());
        mailMenu.add(composeItem);
        mailMenu.add(refreshItem);
        mailMenu.add(cleanupItem);

        JMenu viewMenu = new JMenu("Giao diện");
        JMenuItem toggleThemeItem = new JMenuItem("Chuyển Sáng / Tối");
        toggleThemeItem.addActionListener(e -> toggleTheme());
        JMenuItem settingsItem = new JMenuItem("Cài đặt & Tự động dọn");
        settingsItem.addActionListener(e -> openSettingsDialog());
        viewMenu.add(toggleThemeItem);
        viewMenu.add(settingsItem);

        adminMenu = new JMenu("Quản trị hệ thống");
        JMenuItem openAdminItem = new JMenuItem("Mở Bảng điều khiển Quản trị (4 tab)");
        openAdminItem.addActionListener(e -> openAdminFrame());
        adminMenu.add(openAdminItem);
        adminMenu.setVisible(false);

        JMenu helpMenu = new JMenu("Trợ giúp");
        JMenuItem aboutItem = new JMenuItem("Giới thiệu " + AppIcons.BRAND_FULL);
        aboutItem.addActionListener(e -> ProfileSettingsAboutDialogs.showAboutDialog(this));
        helpMenu.add(aboutItem);

        menuBar.add(accountMenu);
        menuBar.add(mailMenu);
        menuBar.add(viewMenu);
        menuBar.add(adminMenu);
        menuBar.add(helpMenu);
        setJMenuBar(menuBar);
    }

    private void setupKeyboardShortcuts() {
        JRootPane root = getRootPane();
        InputMap im = root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = root.getActionMap();

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_N, InputEvent.CTRL_DOWN_MASK), "compose");
        am.put("compose", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { openComposeDialog(null, null); }
        });

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_R, InputEvent.CTRL_DOWN_MASK), "refresh");
        am.put("refresh", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { refreshCurrentMailbox(); }
        });

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK), "focusSearch");
        am.put("focusSearch", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                searchField.requestFocusInWindow();
                searchField.selectAll();
            }
        });

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "deleteMail");
        am.put("deleteMail", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                if (!(KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner() instanceof javax.swing.text.JTextComponent)) {
                    deleteSelectedMails();
                }
            }
        });
    }

    private void setupWindowBoundsPersistence() {
        addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent e) { saveBounds(); }
            @Override public void componentMoved(ComponentEvent e) { saveBounds(); }
            private void saveBounds() {
                if (!mockupMode) {
                    ClientConfig.getInstance().saveWindowBounds(getWidth(), getHeight(), getX(), getY());
                }
            }
        });
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) {
                cancelAllBackgroundTasks();
                if (!mockupMode) {
                    ClientConfig.getInstance().saveWindowBounds(getWidth(), getHeight(), getX(), getY());
                }
            }
        });
    }

    private void cancelAllBackgroundTasks() {
        closing = true;
        accountsRequestGeneration++;
        userRequestGeneration++;
        if (configTimer != null) configTimer.stop();
        if (autoCleanTimer != null) autoCleanTimer.stop();
        autoCleanCancelled.set(true);
        workerCoordinator.cancelAll();
        if (cancelOAuthLink != null) {
            cancelOAuthLink.run();
            cancelOAuthLink = null;
        }
        if (activeAdminFrame != null) {
            activeAdminFrame.dispose();
            activeAdminFrame = null;
        }
        if (activeGmailManager != null) activeGmailManager.dispose();
        if (activeComposeDialog != null) activeComposeDialog.dispose();
        if (activeCleanupDialog != null) activeCleanupDialog.dispose();
    }

    @Override public void dispose() {
        cancelAllBackgroundTasks();
        super.dispose();
    }

    // ==================== DATA LOADING (SWINGWORKER) ====================

    private void loadPublicConfig() {
        if (closing || configRefreshing) return;
        configRefreshing = true;
        long generation = SessionStore.getInstance().getGeneration();
        new SwingWorker<PublicConfigDto, Void>() {
            @Override protected PublicConfigDto doInBackground() throws Exception {
                return apiClient.getPublicConfig();
            }
            @Override protected void done() {
                configRefreshing = false;
                if (closing || generation != SessionStore.getInstance().getGeneration()) return;
                try {
                    PublicConfigDto config = get();
                    if (config == null) throw new IllegalStateException("Missing public config");
                    serverBadgeLabel.setText("Đã kết nối: " + apiClient.getBaseUrl());
                    applyPublicConfig(config);
                } catch (Exception ignored) {
                    serverBadgeLabel.setText("Mất kết nối: " + apiClient.getBaseUrl());
                    applyPublicConfig(null);
                }
            }
        }.execute();
    }

    private void startConfigRefresh() {
        configTimer = new javax.swing.Timer(15000, e -> loadPublicConfig());
        configTimer.start();
    }

    private boolean sendEnabled() {
        PublicConfigDto config = SessionStore.getInstance().getPublicConfig();
        return config != null && config.sendMailEnabled();
    }

    private void applyPublicConfig(PublicConfigDto config) {
        PublicConfigDto old = lastAppliedConfig;
        lastAppliedConfig = config;
        SessionStore.getInstance().setPublicConfig(config);
        oauthLinkAdmission.update(config != null && config.oauth2LinkEnabled());
        boolean enabled = config != null && config.sendMailEnabled();
        String disabled = "Tính năng gửi thư bị tắt bởi Admin hoặc chưa tải được cấu hình";
        if (composeBtn != null) {
            composeBtn.setEnabled(enabled);
            composeBtn.setToolTipText(enabled ? "Soạn thư mới (Ctrl+N)" : disabled);
        }
        if (composeItem != null) {
            composeItem.setEnabled(enabled);
            composeItem.setToolTipText(enabled ? null : disabled);
        }
        if (replyBtn != null) replyBtn.setEnabled(enabled);
        if (replyAllBtn != null) replyAllBtn.setEnabled(enabled);
        if (forwardBtn != null) forwardBtn.setEnabled(enabled);
        if (activeGmailManager != null) activeGmailManager.applyPublicConfig(config);
        if (activeComposeDialog != null) activeComposeDialog.applyPublicConfig(config);
        updateEmptyActionVisibility();
        if (config != null && selectedAccount != null && selectedFolder != null && activePageSize != effectivePageSize() && !mockupMode) {
            loadMails(0, false);
        }
        if (selectedAccount != null && !Objects.equals(old, config)) renderAccountCards();
    }

    private int effectivePageSize() {
        Integer selected = pageSizeCombo == null ? null : (Integer) pageSizeCombo.getSelectedItem();
        if (selected != null && selected > 0) return selected;
        PublicConfigDto config = SessionStore.getInstance().getPublicConfig();
        return config == null ? 20 : config.effectiveMailPageSize();
    }

    private void resetMailboxDetail() {
        workerCoordinator.beginDetailLoad();
        currentDetail = null;
        detailCardLayout.show(detailCardPanel, "EMPTY_DETAIL");
    }

    private void invalidateMailboxSelection() {
        mailboxGeneration++;
        mailboxReady = false;
        currentUidValidity = 0;
        workerCoordinator.beginMailsLoad();
        resetMailboxDetail();
        mailList.clearSelection();
        mailListModel.clear();
        loadedUids.clear();
        previousPageBtn.setEnabled(false);
        nextPageBtn.setEnabled(false);
        loadMoreBtn.setEnabled(false);
    }

    public void loadUserInfo() {
        if (closing) return;
        long generation = ++userRequestGeneration;
        new SwingWorker<UserDto, Void>() {
            @Override protected UserDto doInBackground() throws Exception {
                return apiClient.getCurrentUser();
            }
            @Override protected void done() {
                if (closing || generation != userRequestGeneration) return;
                try {
                    UserDto user = get();
                    SessionStore.getInstance().updateUser(user);
                    updateUserUI(user);
                } catch (Exception ex) {
                    handleCommonError(ex, "Không tải được thông tin người dùng");
                }
            }
        }.execute();
    }

    public void updateUserUI(UserDto user) {
        if (user == null) return;
        String display = (user.fullName() != null && !user.fullName().isBlank()) ? user.fullName() : user.username();
        userProfileBtn.setText(display);
        if (userStatusLabel != null) userStatusLabel.setText(user.username() + " (" + user.role() + ")");
        userProfileBtn.setIcon(AvatarUtil.createAvatarIcon(display, user.email(), 20));
        boolean isAdmin = "ADMIN".equalsIgnoreCase(user.role());
        adminMenu.setVisible(isAdmin);
        if (adminToolbarBtn != null) adminToolbarBtn.setVisible(isAdmin);
        if (!isAdmin && activeAdminFrame != null) {
            activeAdminFrame.dispose();
            activeAdminFrame = null;
        }
        if (user.mustChangePassword() && !mockupMode) {
            promptForcedPasswordChange();
        }
    }

    private void openAdminFrame() {
        if (closing) return;
        UserDto user = SessionStore.getInstance().getCurrentUser();
        if (user == null || !"ADMIN".equalsIgnoreCase(user.role())) {
            JOptionPane.showMessageDialog(this, "Chỉ Quản trị viên (ADMIN) mới có quyền truy cập.",
                    "Không có quyền", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (activeAdminFrame != null && activeAdminFrame.isDisplayable()) {
            activeAdminFrame.toFront();
            return;
        }
        AdminFrame admin = new AdminFrame(apiClient, this::handleSessionExpired);
        activeAdminFrame = admin;
        admin.setOnSettingsChanged(this::loadPublicConfig);
        admin.setOnPermissionLost(this::loadUserInfo);
        admin.setVisible(true);
    }

    public void promptForcedPasswordChange() {
        if (closing || forcedPasswordDialogOpen) return;
        forcedPasswordDialogOpen = true;
        ChangePasswordDialog dialog = new ChangePasswordDialog(this, apiClient, true);
        try {
            dialog.setVisible(true);
            if (!closing && dialog.isPasswordChangedSuccessfully()) {
                loadUserInfo();
            } else if (!closing) {
                returnToLogin();
            }
        } finally {
            forcedPasswordDialogOpen = false;
        }
    }

    public void loadGmailAccounts() {
        if (closing) return;
        long generation = ++accountsRequestGeneration;
        avatarFetchedIds.clear();
        setStatus("Đang tải danh sách tài khoản Gmail...");
        new SwingWorker<List<GmailAccountDto>, Void>() {
            @Override protected List<GmailAccountDto> doInBackground() throws Exception {
                return apiClient.getGmailAccounts();
            }
            @Override protected void done() {
                if (closing || generation != accountsRequestGeneration) return;
                try {
                    List<GmailAccountDto> accounts = get();
                    GmailAccountDto target = selectedAccount;
                    displayGmailAccounts(accounts);
                    setStatus("Đã tải " + currentGmailAccounts.size() + " tài khoản Gmail.");
                    if (!currentGmailAccounts.isEmpty()) {
                        if (target == null || selectedFolder == null || currentGmailAccounts.stream().noneMatch(a -> a.id().equals(target.id()))) {
                            selectGmailAccount(currentGmailAccounts.stream()
                                    .filter(a -> target != null && a.id().equals(target.id()))
                                    .findFirst().orElse(currentGmailAccounts.get(0)));
                        } else {
                            // Cập nhật bản ghi mới nhất của tài khoản đang chọn
                            currentGmailAccounts.stream().filter(a -> a.id().equals(target.id())).findFirst()
                                    .ifPresent(a -> selectedAccount = a);
                            renderAccountCards();
                        }
                    } else {
                        workerCoordinator.cancelAll();
                        foldersLoading = false;
                        invalidateMailboxSelection();
                        selectedAccount = null;
                        selectedFolder = null;
                        folderListModel.clear();
                        loadedUids.clear();
                        mailListModel.clear();
                        showEmptyListState("Chưa liên kết tài khoản Gmail",
                                "Bấm nút bên dưới để liên kết Gmail qua OAuth2 hoặc App Password.",
                                "Liên kết Gmail ngay");
                        detailCardLayout.show(detailCardPanel, "EMPTY_DETAIL");
                    }
                } catch (Exception ex) {
                    handleCommonError(ex, "Không tải được danh sách tài khoản Gmail");
                }
            }
        }.execute();
    }

    public void displayGmailAccounts(List<GmailAccountDto> accounts) {
        this.currentGmailAccounts = accounts != null ? new ArrayList<>(accounts) : new ArrayList<>();
        if (selectedAccount == null && !currentGmailAccounts.isEmpty()) {
            selectedAccount = currentGmailAccounts.get(0);
        }
        renderAccountCards();
    }

    private void renderAccountCards() {
        accountsListPanel.removeAll();
        if (currentGmailAccounts.isEmpty()) {
            JLabel empty = new JLabel("<html><i>Chưa liên kết Gmail nào.</i></html>");
            empty.setFont(Theme.fontSmall());
            empty.setForeground(Theme.textMuted());
            empty.setBorder(new EmptyBorder(Theme.SP_2, Theme.SP_1, Theme.SP_2, Theme.SP_1));
            accountsListPanel.add(empty);
        } else {
            for (GmailAccountDto acc : currentGmailAccounts) {
                accountsListPanel.add(buildSidebarAccountItem(acc));
                accountsListPanel.add(Box.createVerticalStrut(4));
            }
        }
        accountsListPanel.revalidate();
        accountsListPanel.repaint();
    }

    private JPanel buildSidebarAccountItem(GmailAccountDto acc) {
        boolean isSelected = selectedAccount != null && Objects.equals(selectedAccount.id(), acc.id());
        boolean needsReauth = "NEEDS_REAUTH".equalsIgnoreCase(acc.status());
        boolean fallbackActive = acc.isFallbackActive();
        boolean hasOAuth = acc.hasOAuth();
        boolean hasAppPass = acc.hasAppPassword();
        String preferred = acc.effectivePreferredMethod();
        boolean oauthPrimary = "OAUTH2".equalsIgnoreCase(preferred);
        boolean appPassPrimary = "APP_PASSWORD".equalsIgnoreCase(preferred);

        JPanel item = new JPanel(new BorderLayout(Theme.SP_2, 4)) {
            @Override
            public Dimension getMaximumSize() {
                Dimension pref = getPreferredSize();
                return new Dimension(Integer.MAX_VALUE, pref.height);
            }
        };
        item.setBackground(isSelected ? Theme.bgSelected() : Theme.bgSurface());
        Color borderColor = fallbackActive ? Theme.warning() : (isSelected ? Theme.primary() : Theme.border());
        item.setBorder(new CompoundBorder(
                BorderFactory.createLineBorder(borderColor, fallbackActive ? 2 : 1),
                new EmptyBorder(Theme.SP_2, Theme.SP_2, Theme.SP_2, Theme.SP_2)
        ));
        item.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        String name = (acc.displayName() != null && !acc.displayName().isBlank()) ? acc.displayName() : acc.email();
        java.awt.image.BufferedImage cachedPhoto = acc.id() != null ? accountAvatars.get(acc.id()) : null;
        Icon avIcon = cachedPhoto != null
                ? AvatarUtil.createCircularPhotoIcon(cachedPhoto, 34)
                : AvatarUtil.createAvatarIcon(name, acc.email(), 34);
        JLabel av = new JLabel(avIcon);
        item.add(av, BorderLayout.WEST);

        if (!mockupMode && acc.id() != null && cachedPhoto == null && avatarFetchedIds.add(acc.id())) {
            loadAccountAvatarAsync(acc);
        }

        JPanel info = new JPanel();
        info.setOpaque(false);
        info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));

        JLabel nameLbl = Theme.ellipsisLabel(name);
        nameLbl.setFont(Theme.fontBold());
        nameLbl.setForeground(Theme.textPrimary());
        nameLbl.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel emailLbl = Theme.ellipsisLabel(acc.email());
        emailLbl.setFont(Theme.fontTiny());
        emailLbl.setForeground(Theme.textSecondary());
        emailLbl.setAlignmentX(Component.LEFT_ALIGNMENT);

        // Huy hiệu phương thức: chỉ hiện phương thức đang có, dùng nhãn ngắn để vừa cột hẹp.
        // Dấu ★ (U+2605) không có trong Segoe UI → vẽ sao vector thay vì ký tự, tránh ô vuông lỗi font.
        JPanel badgeRow = Theme.createWrapPanel(4, 3);
        badgeRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        if (hasOAuth) {
            Icon star = oauthPrimary ? AppIcons.vectorIcon(AppIcons.Glyph.STAR_FILLED, 10, Theme.primaryText()) : null;
            badgeRow.add(Theme.badge("Google", star, Theme.bgSelected(), Theme.primaryText()));
        }
        if (hasAppPass) {
            Icon star = appPassPrimary ? AppIcons.vectorIcon(AppIcons.Glyph.STAR_FILLED, 10, Theme.success()) : null;
            badgeRow.add(Theme.badge("App Password", star, Theme.successSoft(), Theme.success()));
        }

        info.add(nameLbl);
        info.add(emailLbl);
        if (badgeRow.getComponentCount() > 0) {
            info.add(Box.createVerticalStrut(3));
            info.add(badgeRow);
        }

        if (fallbackActive || needsReauth) {
            JPanel warnRow = Theme.createWrapPanel(4, 2);
            warnRow.setAlignmentX(Component.LEFT_ALIGNMENT);
            warnRow.add(Theme.badge(fallbackActive ? "Đang dùng App Password dự phòng" : "Cần cấp lại quyền",
                    fallbackActive ? Theme.warningSoft() : Theme.dangerSoft(),
                    fallbackActive ? Theme.warning() : Theme.danger()));
            JButton reBtn = new JButton(fallbackActive ? "Cấp lại quyền" : "Cấp lại");
            reBtn.setFont(Theme.fontTiny().deriveFont(Font.BOLD));
            reBtn.setForeground(Theme.primaryText());
            reBtn.setContentAreaFilled(false);
            reBtn.setBorderPainted(false);
            reBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            reBtn.setToolTipText("Cấp lại quyền truy cập Gmail bằng OAuth2");
            reBtn.addActionListener(e -> initiateOAuth2Flow(acc.id()));
            PublicConfigDto config = SessionStore.getInstance().getPublicConfig();
            if (hasOAuth && config != null && config.oauth2LinkEnabled()) warnRow.add(reBtn);
            info.add(Box.createVerticalStrut(2));
            info.add(warnRow);
        }

        item.add(info, BorderLayout.CENTER);
        item.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (!mockupMode) selectGmailAccount(acc);
            }
        });
        return item;
    }

    private void selectGmailAccount(GmailAccountDto acc) {
        if (closing) return;
        workerCoordinator.cancelAll();
        foldersLoading = false;
        invalidateMailboxSelection();
        folderListModel.clear();
        this.selectedAccount = acc;
        this.selectedFolder = null;
        this.currentDetail = null;
        renderAccountCards();
        detailCardLayout.show(detailCardPanel, "EMPTY_DETAIL");

        // Nếu NEEDS_REAUTH nhưng có App Password dự phòng (hoặc đang fallbackActive), vẫn cho phép đọc/gửi thư qua dự phòng
        if (acc.requiresReauthBeforeMail()) {
            folderListModel.clear();
            loadedUids.clear();
            mailListModel.clear();
            showEmptyListState("Tài khoản cần xác thực lại",
                    acc.email() + " đã hết hạn hoặc bị thu hồi quyền. Hãy cấp lại quyền để tiếp tục.",
                    acc.hasOAuth() ? "Cấp lại quyền (OAuth2)" : "Quản lý tài khoản Gmail");
            return;
        }
        loadFolders();
    }

    private void loadAccountAvatarAsync(GmailAccountDto acc) {
        final Long accId = acc.id();
        new SwingWorker<java.awt.image.BufferedImage, Void>() {
            @Override protected java.awt.image.BufferedImage doInBackground() throws Exception {
                byte[] bytes = apiClient.getGmailAccountAvatar(accId);
                if (bytes == null || bytes.length == 0) return null;
                return javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(bytes));
            }
            @Override protected void done() {
                if (closing || currentGmailAccounts.stream().noneMatch(a -> Objects.equals(a.id(), accId))) return;
                try {
                    java.awt.image.BufferedImage img = get();
                    if (img != null) {
                        accountAvatars.put(accId, img);
                        renderAccountCards();
                        // Cập nhật luôn ảnh đại diện trên nút hồ sơ góc trên phải
                        UserDto u = SessionStore.getInstance().getCurrentUser();
                        boolean matchUser = (u != null && u.email() != null && u.email().equalsIgnoreCase(acc.email()))
                                || (selectedAccount != null && Objects.equals(selectedAccount.id(), accId))
                                || currentGmailAccounts.size() == 1;
                        if (matchUser && userProfileBtn != null) {
                            userProfileBtn.setIcon(AvatarUtil.createCircularPhotoIcon(img, 22));
                        }
                    }
                } catch (Exception ignored) {
                    // Fallback về vòng tròn chữ cái đầu
                }
            }
        }.execute();
    }

    private void loadFolders() {
        if (closing || selectedAccount == null) return;
        foldersLoading = true;
        invalidateMailboxSelection();
        folderList.setEnabled(false);
        final Long gid = selectedAccount.id();
        final long gen = workerCoordinator.beginFoldersLoad(null);
        listCardLayout.show(listCardPanel, "LOADING");
        setStatus("Đang tải danh sách thư mục cho " + selectedAccount.email() + "...");

        SwingWorker<List<MailFolderDto>, Void> worker = new SwingWorker<>() {
            @Override protected List<MailFolderDto> doInBackground() throws Exception {
                return apiClient.getMailFolders(gid);
            }
            @Override protected void done() {
                if (isCancelled() || !workerCoordinator.isFoldersCurrent(gen)) return;
                foldersLoading = false;
                folderList.setEnabled(true);
                try {
                    currentFolders = get();
                    folderListModel.clear();
                    MailFolderDto inbox = null;
                    for (MailFolderDto f : currentFolders) {
                        folderListModel.addElement(f);
                        if ("INBOX".equalsIgnoreCase(f.type()) || "INBOX".equalsIgnoreCase(f.name())) {
                            inbox = f;
                        }
                    }
                    MailFolderDto toSelect = (selectedFolder != null)
                            ? currentFolders.stream().filter(f -> f.name().equals(selectedFolder.name())).findFirst().orElse(inbox)
                            : inbox;
                    if (toSelect == null && !currentFolders.isEmpty()) {
                        toSelect = currentFolders.get(0);
                    }
                    if (toSelect != null) {
                        selectedFolder = toSelect;
                        folderList.setSelectedValue(toSelect, true);
                        loadMails(0, false);
                    } else {
                        selectedFolder = null;
                        showEmptyListState("Không có thư mục", "Hộp thư chưa có thư mục để hiển thị.", "Làm mới");
                    }
                } catch (Exception ex) {
                    if (!isCancelled()) {
                        selectedFolder = null;
                        currentFolders = List.of();
                        folderListModel.clear();
                        handleCommonError(ex, "Không tải được danh sách thư mục");
                        showEmptyListState("Không thể tải thư mục", extractMsg(ex), "Thử lại");
                    }
                }
            }
        };
        workerCoordinator.attachFoldersTask(worker);
        worker.execute();
    }

    private void loadMails(int page, boolean append) {
        if (closing || foldersLoading || selectedAccount == null || selectedFolder == null) return;
        int size = effectivePageSize();
        var criteria = new MailboxViewModel.SearchCriteria(searchField.getText(),
                unreadFilterBtn.isSelected(), attachFilterBtn.isSelected());
        if (activePageSize != size || !criteria.equals(activeSearchCriteria)) {
            loadMailsAtSize(0, false, size, criteria);
        } else {
            loadMailsAtSize(page, append, size, criteria);
        }
    }

    private void loadMailsAtSize(int page, boolean append, int size, MailboxViewModel.SearchCriteria criteria) {
        activePageSize = size;
        activeSearchCriteria = criteria;
        final Long gid = selectedAccount.id();
        final String folderName = selectedFolder.name();
        final String query = criteria.query();
        final Boolean unreadOnly = criteria.unreadOnly() ? Boolean.TRUE : null;
        final Boolean hasAttachOnly = criteria.hasAttachment() ? Boolean.TRUE : null;
        if (!append) {
            invalidateMailboxSelection();
            listCardLayout.show(listCardPanel, "LOADING");
        } else {
            loadMoreBtn.setEnabled(false);
            loadMoreBtn.setText("Đang tải...");
        }
        final long gen = workerCoordinator.beginMailsLoad();
        if (!append) mailboxReady = false;
        previousPageBtn.setEnabled(false);
        nextPageBtn.setEnabled(false);
        loadMoreBtn.setEnabled(false);
        setStatus("Đang tải thư trong " + selectedFolder.displayName() + "...");

        SwingWorker<MailListResponse, Void> worker = new SwingWorker<>() {
            @Override protected MailListResponse doInBackground() throws Exception {
                return apiClient.getMails(gid, folderName, page, size, query, unreadOnly, hasAttachOnly, null, null);
            }
            @Override protected void done() {
                if (isCancelled() || !workerCoordinator.isMailsCurrent(gen)) return;
                loadMoreBtn.setEnabled(true);
                loadMoreBtn.setText("Tải thêm...");
                try {
                    MailListResponse resp = get();
                    if (page > 0 && resp.total() > 0 && (resp.items() == null || resp.items().isEmpty())
                            && (long) page * size >= resp.total()) {
                        loadMails((int) ((resp.total() - 1) / size), false);
                        return;
                    }
                    // Nếu hộp thư đổi UIDVALIDITY giữa lúc tải thêm -> đồng bộ lại từ trang 0
                    if (MailboxViewModel.shouldReloadFromFirstPage(currentUidValidity, resp, append)) {
                        loadMails(0, false);
                        return;
                    }
                    currentPage = resp.page();
                    totalMails = resp.total();
                    hasMoreMails = resp.hasMore();
                    if (resp.uidValidity() != 0) {
                        currentUidValidity = resp.uidValidity();
                    }
                    mailboxReady = currentUidValidity > 0;

                    if (!append) {
                        loadedUids.clear();
                        mailListModel.clear();
                    }
                    // Chỉ thêm thư chưa có: O(số thư mới) thay vì O(toàn bộ danh sách) mỗi lần tải thêm
                    for (MailSummaryDto item : MailboxViewModel.selectNewItems(loadedUids, resp)) {
                        mailListModel.addElement(item);
                    }

                    if (mailListModel.isEmpty()) {
                        showEmptyListState("Không có thư nào",
                                "Thư mục \"" + selectedFolder.displayName() + "\" hiện trống hoặc không khớp bộ lọc.",
                                "Làm mới");
                    } else {
                        listCardLayout.show(listCardPanel, "LIST");
                    }

                    long totalPages = Math.max(1L, (totalMails + size - 1) / size);
                    mailCountLabel.setText("Trang " + (currentPage + 1) + "/" + totalPages + " · " + totalMails + " thư");
                    loadMoreBtn.setVisible(hasMoreMails);
                    previousPageBtn.setEnabled(currentPage > 0);
                    nextPageBtn.setEnabled(hasMoreMails);
                    setStatus("Đã tải " + mailListModel.size() + " / " + totalMails + " thư (" + selectedFolder.displayName() + ").");
                } catch (Exception ex) {
                    if (!isCancelled()) {
                        invalidateMailboxSelection();
                        handleCommonError(ex, "Không tải được danh sách thư");
                        showEmptyListState("Không thể tải danh sách thư", extractMsg(ex), "Thử lại");
                    }
                }
            }
        };
        workerCoordinator.attachMailsTask(worker);
        worker.execute();
    }

    private void loadMailDetail(MailSummaryDto summary, boolean loadRemote) {
        if (!mailboxReady || selectedAccount == null || selectedFolder == null || summary == null
                || summary.uidValidity() != currentUidValidity) return;
        final Long gid = selectedAccount.id();
        final String folderName = selectedFolder.name();
        final long gen = workerCoordinator.beginDetailLoad();
        currentDetail = null;
        detailCardLayout.show(detailCardPanel, "LOADING_DETAIL");
        setStatus("Đang tải thư #" + summary.uid() + "...");

        SwingWorker<MailDetailResponse, Void> worker = new SwingWorker<>() {
            @Override protected MailDetailResponse doInBackground() throws Exception {
                return apiClient.getMailDetail(gid, folderName, summary.uid(), loadRemote, summary.uidValidity());
            }
            @Override protected void done() {
                if (isCancelled() || !workerCoordinator.isDetailCurrent(gen)) return;
                try {
                    MailDetailResponse detail = get();
                    if (!isCurrentMailbox(gid, folderName, summary.uidValidity())) return;
                    if (detail == null || detail.uid() != summary.uid() || detail.uidValidity() != summary.uidValidity()) {
                        throw new java.io.IOException("Chi tiết thư không khớp UID/UIDVALIDITY đã yêu cầu.");
                    }
                    displayMailDetail(detail);
                    if (detail.flags() != null) updateListItemFlags(summary.uid(), detail.flags());
                    setStatus("Đã mở thư: " + (detail.subject() != null ? detail.subject() : "(Không tiêu đề)"));
                } catch (Exception ex) {
                    if (isCancelled()) return;
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    if (cause instanceof ApiException apiEx && "UID_VALIDITY_CHANGED".equalsIgnoreCase(apiEx.getCode())) {
                        setStatus("Hộp thư đã thay đổi cấu trúc — đang tự động đồng bộ lại...");
                        loadMails(0, false);
                        return;
                    }
                    handleCommonError(ex, "Không tải được chi tiết thư");
                    detailErrorLabel.setText("Lỗi tải thư: " + extractMsg(ex));
                    detailCardLayout.show(detailCardPanel, "ERROR_DETAIL");
                }
            }
        };
        workerCoordinator.attachDetailTask(worker);
        worker.execute();
    }

    public void displayMailDetail(MailDetailResponse detail) {
        this.currentDetail = detail;
        this.headerExpanded = false;
        expandedHeaderPanel.setVisible(false);
        expandHeaderBtn.setText("Chi tiết (+)");

        detailSubjectLabel.putClientProperty("html.disable", Boolean.TRUE);
        detailSubjectLabel.setText((detail.subject() != null && !detail.subject().isBlank()) ? detail.subject() : "(Không có tiêu đề)");

        String senderName = detail.from() != null ? detail.from().displayText() : "(Không rõ người gửi)";
        String senderAddr = detail.from() != null ? detail.from().address() : "";
        detailAvatarLabel.setIcon(AvatarUtil.createAvatarIcon(senderName, senderAddr, 40));
        detailFromLabel.putClientProperty("html.disable", Boolean.TRUE);
        detailFromLabel.setText(senderName);

        String toSummary = formatAddressList(detail.to());
        detailToDateLabel.setText("Đến: " + (toSummary.isEmpty() ? "(Tôi)" : toSummary) + "   •   " + formatFullDate(detail.date()));

        // Expanded headers
        expandedHeaderPanel.removeAll();
        addExpandedRow("Từ:", senderName);
        addExpandedRow("Đến:", toSummary);
        if (detail.cc() != null && !detail.cc().isEmpty()) addExpandedRow("Cc:", formatAddressList(detail.cc()));
        if (detail.replyTo() != null && !detail.replyTo().isEmpty()) addExpandedRow("Trả lời tới:", formatAddressList(detail.replyTo()));
        if (detail.messageId() != null) addExpandedRow("Mã thư:", detail.messageId());
        expandedHeaderPanel.revalidate();

        remoteImgBanner.setVisible(detail.hasBlockedRemoteImages());
        truncatedBanner.setVisible(detail.truncated());

        boolean starred = detail.flags() != null && detail.flags().flagged();
        starActionBtn.setToolTipText(starred ? "Bỏ gắn sao" : "Gắn sao");
        starActionBtn.setIcon(AppIcons.vectorIcon(starred ? AppIcons.Glyph.STAR_FILLED : AppIcons.Glyph.STAR, 17, Theme.starGold()));

        // Mặc định ưu tiên HTML nếu có, không thì văn bản thuần
        showHtmlMode = detail.htmlBody() != null && !detail.htmlBody().isBlank();
        modeToggleBtn.setSelected(showHtmlMode);
        modeToggleBtn.setText(showHtmlMode ? "HTML sạch" : "Văn bản");
        renderCurrentBody();

        // Attachments chips
        attachmentsStrip.removeAll();
        List<MailAttachmentDto> atts = detail.attachments() != null ? detail.attachments() : List.of();
        if (!atts.isEmpty()) {
            for (MailAttachmentDto att : atts) {
                attachmentsStrip.add(buildAttachmentChip(detail, att));
            }
            attachmentsContainer.setVisible(true);
        } else {
            attachmentsContainer.setVisible(false);
        }
        attachmentsStrip.revalidate();
        attachmentsStrip.repaint();

        detailCardLayout.show(detailCardPanel, "DETAIL");
    }

    private JPanel buildAttachmentChip(MailDetailResponse detail, MailAttachmentDto att) {
        JPanel chip = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 3));
        chip.setBackground(Theme.bgSurface());
        chip.setBorder(BorderFactory.createLineBorder(Theme.border(), 1));

        JLabel name = new JLabel(att.filename() + " (" + MailSizeDisplay.attachment(att.size(), att.sizeAccuracy()) + ")");
        name.setToolTipText(switch (att.sizeAccuracy()) {
            case EXACT -> "Dung lượng chính xác";
            case ESTIMATED -> "Dung lượng ước tính từ IMAP; số byte tải thực tế có thể khác";
            case UNKNOWN -> "Máy chủ chưa xác định được dung lượng tệp";
        });
        name.setFont(Theme.fontSmall());
        name.setForeground(Theme.textPrimary());

        JButton dlBtn = new JButton("Tải xuống");
        dlBtn.setFont(Theme.fontTiny());
        dlBtn.setForeground(Theme.primary());
        dlBtn.setContentAreaFilled(false);
        dlBtn.setBorderPainted(false);
        dlBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        dlBtn.addActionListener(e -> downloadSingleAttachment(detail, att, false));

        JButton openBtn = new JButton("Mở");
        openBtn.setFont(Theme.fontTiny());
        openBtn.setForeground(Theme.success());
        openBtn.setContentAreaFilled(false);
        openBtn.setBorderPainted(false);
        openBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        openBtn.addActionListener(e -> downloadSingleAttachment(detail, att, true));

        chip.add(name);
        chip.add(dlBtn);
        chip.add(openBtn);
        return chip;
    }

    private void downloadSingleAttachment(MailDetailResponse detail, MailAttachmentDto att, boolean openAfter) {
        if (closing || !mailboxReady || selectedAccount == null || selectedFolder == null
                || detail == null || currentDetail == null || detail.uid() != currentDetail.uid()
                || detail.uidValidity() != currentUidValidity) return;
        SessionStore session = SessionStore.getInstance();
        final long generation = session.getGeneration();
        final SessionStore.AttachmentKey key = new SessionStore.AttachmentKey(
                selectedAccount.id(), selectedFolder.name(), detail.uidValidity(), detail.uid(), att.index());
        final long selectionGeneration = mailboxGeneration;
        if (openAfter) {
            Path cached = session.findDownloadedAttachment(key);
            if (cached != null) {
                openDownloadedAttachment(cached.toFile(), key, generation, selectionGeneration);
                return;
            }
        }
        if (pendingDownloads.contains(key)) {
            setStatus("Tệp này đang được tải. Vui lòng đợi tải xong.");
            return;
        }
        File baseDir = new File(ClientConfig.getInstance().getDownloadDir());
        Path targetDir = FileUtil.attachmentDirectory(baseDir.toPath(), selectedAccount.email());
        String safeName = AttachmentCleanupService.sanitizeFilename(att.filename());

        File targetFile;
        if (!openAfter) {
            try {
                // Ensure the first save opens inside the configured mailbox
                // directory, not the OS default Documents folder.
                targetDir = FileUtil.prepareAttachmentDirectory(baseDir.toPath(), selectedAccount.email());
            } catch (java.io.IOException | SecurityException ex) {
                handleCommonError(ex, "Không tạo được thư mục tải tệp đính kèm");
                return;
            }
            JFileChooser fc = new JFileChooser(targetDir.toFile());
            fc.setSelectedFile(new File(targetDir.toFile(), safeName));
            if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
            targetFile = fc.getSelectedFile();
        } else {
            targetFile = targetDir.resolve(safeName).toFile();
        }

        if (selectionGeneration != mailboxGeneration || generation != session.getGeneration()
                || !isCurrentAttachmentDetail(key)) return;
        pendingDownloads.add(key);
        setStatus("Đang tải tệp " + att.filename() + "...");

        new SwingWorker<ApiClient.DownloadResult, Void>() {
            @Override protected ApiClient.DownloadResult doInBackground() throws Exception {
                if (generation != session.getGeneration()) throw new InterruptedException();
                return FileUtil.download(apiClient, key, targetFile.toPath());
            }
            @Override protected void done() {
                pendingDownloads.remove(key);
                if (closing || generation != session.getGeneration()) return;
                try {
                    ApiClient.DownloadResult res = get();
                    File saved = res.file();
                    session.rememberDownloadedAttachment(generation, key, saved.toPath());
                    // Keep a completed download in the same session's cache, but don't
                    // open a file or show an old dialog after the user selected another mail.
                    if (selectionGeneration != mailboxGeneration || !isCurrentAttachmentDetail(key)) return;
                    setStatus("Đã tải xong " + saved.getName() + " (" + ComposeDialog.formatBytes(res.bytesWritten()) + ")");
                    if (openAfter) {
                        openDownloadedAttachment(saved, key, generation, selectionGeneration);
                    } else {
                        JOptionPane.showMessageDialog(MainFrame.this,
                                "Đã lưu tệp thành công:\n" + saved.getAbsolutePath() + "\nSHA-256: " + res.sha256Hex().substring(0, 16) + "...",
                                "Tải tệp thành công", JOptionPane.INFORMATION_MESSAGE);
                    }
                } catch (Exception ex) {
                    if (selectionGeneration == mailboxGeneration && isCurrentAttachmentDetail(key)) {
                        handleCommonError(ex, "Lỗi tải tệp đính kèm");
                    }
                }
            }
        }.execute();
    }

    private boolean isCurrentAttachmentDetail(SessionStore.AttachmentKey key) {
        return isCurrentMailbox(key.gmailId(), key.folder(), key.uidValidity())
                && currentDetail != null && currentDetail.uid() == key.uid()
                && currentDetail.uidValidity() == key.uidValidity();
    }

    private void openDownloadedAttachment(File file, SessionStore.AttachmentKey key,
                                          long sessionGeneration, long selectionGeneration) {
        SessionStore session = SessionStore.getInstance();
        if (ClientSafetyUtil.isDangerousExecutableExtension(file.getName())) {
            int warn = JOptionPane.showConfirmDialog(this,
                    "Tệp '" + file.getName() + "' có định dạng có thể thực thi mã.\n"
                            + "Bạn có chắc chắn muốn mở tệp này không?",
                    "Cảnh báo tệp đính kèm nguy hiểm", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (warn != JOptionPane.YES_OPTION) return;
        }
        // A confirmation dialog runs a nested EDT event loop. Revalidate the
        // exact cached file and originating session/detail after it closes.
        if (closing || sessionGeneration != session.getGeneration()
                || selectionGeneration != mailboxGeneration || !isCurrentAttachmentDetail(key)) return;
        Path cached = session.findDownloadedAttachment(key);
        if (cached == null || !cached.equals(file.toPath().toAbsolutePath().normalize())) {
            setStatus("Tệp đã tải không còn nguyên trạng. Hãy tải lại trước khi mở.");
            return;
        }
        try {
            if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                throw new java.io.IOException("Không có ứng dụng mở tệp mặc định.");
            }
            Desktop.getDesktop().open(cached.toFile());
            setStatus("Đã mở " + file.getName());
        } catch (Exception ex) {
            handleCommonError(ex, "Không mở được tệp đã tải");
        }
    }

    private void renderCurrentBody() {
        if (currentDetail == null) return;
        if (showHtmlMode && currentDetail.htmlBody() != null && !currentDetail.htmlBody().isBlank()) {
            String bgHex = Theme.isDark() ? "#1c2027" : "#ffffff";
            String fgHex = Theme.isDark() ? "#ecf0f6" : "#1c2330";
            String linkHex = Theme.isDark() ? "#7abaff" : "#125cc2";
            String cleanHtml = currentDetail.htmlBody();
            // Bỏ thẻ <img> đã bị chặn nguồn (không có src) để Swing HTMLEditorKit không vẽ khung đỏ [x] đè lên văn bản
            // (Banner vàng phía trên đã thông báo rõ và cung cấp nút 'Tải ảnh từ xa')
            if (currentDetail.hasBlockedRemoteImages()) {
                cleanHtml = cleanHtml.replaceAll("(?i)<img\\b[^>]*data-blocked-remote=\"true\"[^>]*>", "");
            }
            bodyPane.setContentType("text/html; charset=UTF-8");
            bodyPane.setText("<html><head><style>"
                    + "body{font-family:'" + Theme.fontRegular().getFamily() + "',sans-serif;"
                    + "font-size:" + Theme.fontRegular().getSize() + "px;"
                    + "padding:18px 22px;background:" + bgHex + ";color:" + fgHex + ";line-height:1.62;margin:0;}"
                    + "a{color:" + linkHex + ";text-decoration:underline;}"
                    + "table{max-width:100%;border-collapse:collapse;}"
                    + "img{max-width:100%;height:auto;}"
                    + "blockquote{border-left:3px solid #94a3b8;padding-left:12px;margin-left:4px;color:#64748b;}"
                    + "</style></head><body>"
                    + cleanHtml + "</body></html>");
        } else {
            bodyPane.setContentType("text/plain; charset=UTF-8");
            String text = (currentDetail.textBody() != null && !currentDetail.textBody().isBlank())
                    ? currentDetail.textBody()
                    : "(Thư không có nội dung văn bản thuần)";
            bodyPane.setText(text);
        }
        bodyPane.setCaretPosition(0);
    }

    // ==================== MAIL ACTIONS ====================

    private JToggleButton createFilterToggle(String text, Icon icon) {
        JToggleButton btn = new JToggleButton(text, icon) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                boolean sel = isSelected();
                g2.setColor(sel ? Theme.bgSelected() : Theme.bgSurfaceAlt());
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                g2.setColor(sel ? Theme.primaryText() : Theme.border());
                g2.setStroke(new BasicStroke(sel ? 1.4f : 1f));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
                g2.dispose();
                setForeground(sel ? Theme.primaryText() : Theme.textPrimary());
                super.paintComponent(g);
            }
        };
        btn.setOpaque(false);
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setFont(Theme.fontTiny().deriveFont(Font.BOLD));
        btn.setForeground(Theme.textPrimary());
        btn.setBorder(new EmptyBorder(4, 9, 4, 9));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }

    private void triggerSearchOrFilter() {
        unreadFilterBtn.repaint();
        attachFilterBtn.repaint();
        if (selectedAccount == null || selectedFolder == null) {
            setStatus("Chưa liên kết Gmail — hãy bấm 'Tài khoản Gmail' để liên kết trước khi tìm hoặc lọc thư.");
            return;
        }
        loadMails(0, false);
    }

    private void openComposeDialog(MailDetailResponse original, String mode) {
        if (closing) return;
        if (!sendEnabled()) {
            JOptionPane.showMessageDialog(this, "Tính năng gửi thư bị tắt bởi Admin hoặc chưa tải được cấu hình.",
                    "Không thể soạn thư", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (currentGmailAccounts.isEmpty()) {
            int opt = JOptionPane.showConfirmDialog(this,
                    "Bạn chưa liên kết tài khoản Gmail nào.\nBạn có muốn mở Quản lý Tài khoản Gmail để liên kết ngay không?",
                    "Chưa có tài khoản Gmail", JOptionPane.YES_NO_OPTION, JOptionPane.INFORMATION_MESSAGE);
            if (opt == JOptionPane.YES_OPTION) {
                openGmailManagerDialog();
            }
            return;
        }
        ComposeDialog dlg = new ComposeDialog(this, apiClient, currentGmailAccounts, selectedAccount,
                SessionStore.getInstance().getPublicConfig(), () -> {
            setStatus("Đã gửi thư thành công.");
            loadFolders();
        });
        if (original != null && mode != null) {
            dlg.prefillReplyOrForward(original, mode);
        }
        activeComposeDialog = dlg;
        dlg.setOnSessionExpired(this::handleSessionExpired);
        try {
            dlg.setVisible(true);
        } finally {
            activeComposeDialog = null;
        }
    }

    private void toggleStarCurrentMail() {
        if (currentDetail == null || selectedAccount == null || selectedFolder == null) return;
        boolean flagged = currentDetail.flags() != null && currentDetail.flags().flagged();
        updateMailFlags(currentDetail.uid(), currentDetail.uidValidity(), null, !flagged);
    }

    private void toggleStarForMail(MailSummaryDto item) {
        if (item == null) return;
        boolean flagged = item.flags() != null && item.flags().flagged();
        updateMailFlags(item.uid(), item.uidValidity(), null, !flagged);
    }

    private void markCurrentMailUnread() {
        if (currentDetail == null || selectedAccount == null || selectedFolder == null) return;
        updateMailFlags(currentDetail.uid(), currentDetail.uidValidity(), false, null);
    }

    private boolean isCurrentMailbox(Long gmailId, String folderName, long uidValidity) {
        return !closing && mailboxReady && selectedAccount != null && selectedFolder != null
                && Objects.equals(gmailId, selectedAccount.id()) && Objects.equals(folderName, selectedFolder.name())
                && currentUidValidity > 0 && uidValidity == currentUidValidity;
    }

    private void updateMailFlags(long uid, long uidValidity, Boolean seen, Boolean flagged) {
        if (closing || !mailboxReady || selectedAccount == null || selectedFolder == null
                || uidValidity != currentUidValidity) return;
        final Long gid = selectedAccount.id();
        final String folderName = selectedFolder.name();
        MailIdentity identity = new MailIdentity(gid, folderName, uidValidity, uid);
        if (!pendingFlagUpdates.add(identity)) return;
        final SessionStore session = SessionStore.getInstance();
        final long sessionGeneration = session.getGeneration();
        final long selectionGeneration = mailboxGeneration;
        new SwingWorker<MailFlagsDto, Void>() {
            @Override protected MailFlagsDto doInBackground() throws Exception {
                if (closing || sessionGeneration != session.getGeneration() || selectionGeneration != mailboxGeneration)
                    throw new java.util.concurrent.CancellationException();
                return apiClient.updateMailFlags(gid, folderName, uid, seen, flagged, uidValidity);
            }
            @Override protected void done() {
                pendingFlagUpdates.remove(identity);
                if (closing || sessionGeneration != session.getGeneration()) return;
                if (selectionGeneration != mailboxGeneration) {
                    // A→B→A may restore the same UIDVALIDITY but the old
                    // response must not overwrite the newly loaded flags.
                    if (isCurrentMailbox(gid, folderName, uidValidity)) loadMails(0, false);
                    return;
                }
                if (!isCurrentMailbox(gid, folderName, uidValidity)) return;
                try {
                    MailFlagsDto updated = get();
                    updateListItemFlags(uid, updated);
                    MailDetailResponse detail = currentDetail;
                    if (detail != null && detail.uid() == uid && detail.uidValidity() == uidValidity) {
                        currentDetail = new MailDetailResponse(detail.uid(), detail.uidValidity(), detail.from(),
                                detail.to(), detail.cc(), detail.bcc(), detail.replyTo(), detail.date(),
                                detail.messageId(), detail.inReplyTo(), detail.references(), detail.subject(),
                                detail.textBody(), detail.htmlBody(), detail.truncated(), detail.hasBlockedRemoteImages(),
                                detail.attachments(), updated);
                        starActionBtn.setToolTipText(updated.flagged() ? "Bỏ gắn sao" : "Gắn sao");
                        starActionBtn.setIcon(AppIcons.vectorIcon(updated.flagged() ? AppIcons.Glyph.STAR_FILLED
                                : AppIcons.Glyph.STAR, 17, Theme.starGold()));
                    }
                    setStatus(seen != null && !seen ? "Đã đánh dấu thư là Chưa đọc." : "Đã cập nhật trạng thái sao.");
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    if (cause instanceof ApiException apiEx && "UID_VALIDITY_CHANGED".equalsIgnoreCase(apiEx.getCode())) {
                        setStatus("Hộp thư đã thay đổi cấu trúc — đang tự động đồng bộ lại...");
                        loadMails(0, false);
                        return;
                    }
                    if (ApiException.isMutationOutcomeUncertain(ex)) {
                        setStatus("Chưa xác định được kết quả cập nhật cờ thư. Đang tải lại danh sách; không tự thử lại.");
                        currentDetail = null;
                        detailCardLayout.show(detailCardPanel, "EMPTY_DETAIL");
                        loadMails(0, false);
                        return;
                    }
                    handleCommonError(ex, "Không cập nhật được trạng thái thư");
                }
            }
        }.execute();
    }

    private void deleteSelectedMails() {
        deleteMailTargets(List.copyOf(mailList.getSelectedValuesList()));
    }

    private void deleteCurrentDetail() {
        deleteMailTargets(MailboxViewModel.detailMutationTarget(currentDetail,
                List.copyOf(mailList.getSelectedValuesList()), currentUidValidity));
    }

    private void deleteMailTargets(List<MailSummaryDto> selected) {
        if (closing || deleting || autoCleanRunning.get() || activeCleanupDialog != null
                || !mailboxReady || selectedAccount == null || selectedFolder == null) return;
        final Long gid = selectedAccount.id();
        final String folderName = selectedFolder.name();
        final long uidValidity = currentUidValidity;
        final long generation = mailboxGeneration;
        final SessionStore session = SessionStore.getInstance();
        final long sessionGeneration = session.getGeneration();
        if (!MailboxViewModel.validMutationSelection(selected, uidValidity)) return;

        boolean inTrash = "TRASH".equalsIgnoreCase(selectedFolder.name()) || "TRASH".equalsIgnoreCase(selectedFolder.type());
        String msg = inTrash
                ? "Bạn có chắc chắn muốn XÓA VĨNH VIỄN " + selected.size() + " thư trong Thùng rác?"
                : "Chuyển " + selected.size() + " thư đã chọn vào Thùng rác?";
        if (JOptionPane.showConfirmDialog(this, msg, "Xác nhận xóa thư", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE)
                != JOptionPane.YES_OPTION) {
            return;
        }

        if (generation != mailboxGeneration || sessionGeneration != session.getGeneration()
                || deleting || autoCleanRunning.get() || activeCleanupDialog != null
                || !isCurrentMailbox(gid, folderName, uidValidity)) return;
        deleting = true;
        setStatus("Đang xóa " + selected.size() + " thư...");

        new SwingWorker<MailboxViewModel.DeleteBatchResult, Void>() {
            @Override protected MailboxViewModel.DeleteBatchResult doInBackground() {
                return MailboxViewModel.deleteConfirmedBatch(selected,
                        () -> !closing && sessionGeneration == session.getGeneration() && generation == mailboxGeneration,
                        m -> apiClient.deleteMail(gid, folderName, m.uid(), inTrash, m.uidValidity()));
            }
            @Override protected void done() {
                deleting = false;
                try {
                    var result = get();
                    Exception failure = result.failure();
                    if (failure != null && ApiException.isDeleteOutcomeUncertain(failure)) {
                        disableAutoCleanWithWarning("Đã xác nhận " + result.acknowledged() + "/" + result.requested()
                                + " thư. Chưa xác định kết quả thư tiếp theo; đã dừng lô và tự động dọn. Kiểm tra nguồn và Thùng rác, không tự thử lại.");
                        if (!closing && sessionGeneration == session.getGeneration()
                                && selectedAccount != null && Objects.equals(selectedAccount.id(), gid)) loadFolders();
                        return;
                    }
                    if (closing || sessionGeneration != session.getGeneration()) return;
                    if (failure != null) {
                        handleCommonError(failure, "Đã xác nhận " + result.acknowledged() + "/" + result.requested() + " thư, dừng lô");
                        if (isCurrentMailbox(gid, folderName, uidValidity)) loadMails(0, false);
                        return;
                    }
                    if (!isCurrentMailbox(gid, folderName, uidValidity)) return;
                    setStatus(result.stopped() ? "Đã dừng lô: xác nhận " + result.acknowledged() + "/" + result.requested() + " thư."
                            : "Đã xóa " + result.acknowledged() + " thư.");
                    detailCardLayout.show(detailCardPanel, "EMPTY_DETAIL");
                    currentDetail = null;
                    loadMails(0, false);
                } catch (Exception ex) {
                    if (ApiException.isDeleteOutcomeUncertain(ex)) {
                        disableAutoCleanWithWarning("Không xác định được kết quả xóa của lô thư. Đã dừng tự động dọn, không tự động thử lại. Hãy kiểm tra thư mục nguồn và Thùng rác trước khi thao tác tiếp.");
                        if (!closing && selectedAccount != null && Objects.equals(selectedAccount.id(), gid)) loadFolders();
                    } else {
                        if (closing || !isCurrentMailbox(gid, folderName, uidValidity)) return;
                        handleCommonError(ex, "Lỗi khi xóa thư");
                        if (!closing) loadMails(0, false);
                    }
                }
            }
        }.execute();
    }

    // ==================== CORE FEATURE: DOWNLOAD ATTACHMENTS THEN DELETE ====================

    private void openCleanupForSelectedMails() {
        if (closing) return;
        if (currentGmailAccounts.isEmpty()) {
            int opt = JOptionPane.showConfirmDialog(this,
                    "Bạn chưa liên kết tài khoản Gmail nào.\nMở Quản lý Tài khoản Gmail để liên kết ngay?",
                    "Chưa có tài khoản Gmail", JOptionPane.YES_NO_OPTION, JOptionPane.INFORMATION_MESSAGE);
            if (opt == JOptionPane.YES_OPTION) {
                openGmailManagerDialog();
            }
            return;
        }
        if (!mailboxReady || selectedAccount == null || selectedFolder == null) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn tài khoản Gmail và thư mục trước.", "Thông báo", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        List<MailSummaryDto> targets = mailList.getSelectedValuesList();
        if (!MailboxViewModel.validMutationSelection(targets, currentUidValidity)) {
            JOptionPane.showMessageDialog(this, "Hãy chọn một hoặc nhiều thư trong danh sách (giữ Ctrl/Shift để chọn nhiều).",
                    "Chưa chọn thư", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        showCleanupDialog(targets);
    }

    private void openCleanupForCurrentMail() {
        if (closing || !mailboxReady || selectedAccount == null || selectedFolder == null) return;
        List<MailSummaryDto> target = MailboxViewModel.detailMutationTarget(currentDetail,
                List.copyOf(mailList.getSelectedValuesList()), currentUidValidity);
        if (!target.isEmpty()) showCleanupDialog(target);
    }

    private void showCleanupDialog(List<MailSummaryDto> targets) {
        if (closing || deleting || autoCleanRunning.get() || activeCleanupDialog != null) return;
        long generation = mailboxGeneration;
        long sessionGeneration = SessionStore.getInstance().getGeneration();
        CleanupPreviewDialog dlg = new CleanupPreviewDialog(
                this, cleanupService, selectedAccount.id(), selectedAccount.email(),
                selectedFolder.name(), List.copyOf(targets), this::refreshAfterCleanup,
                () -> !closing && generation == mailboxGeneration
                        && sessionGeneration == SessionStore.getInstance().getGeneration());
        activeCleanupDialog = dlg;
        try {
            dlg.setVisible(true);
        } finally {
            activeCleanupDialog = null;
            dlg.dispose();
        }
    }

    private void configureAutoCleanTimer() {
        if (autoCleanTimer != null) {
            autoCleanTimer.stop();
        }
        ClientConfig cfg = ClientConfig.getInstance();
        if (!cfg.isAutoCleanEnabled()) {
            autoCleanBadge.setText("Tự động dọn: TẮT");
            autoCleanBadge.setForeground(Theme.textSecondary());
            return;
        }
        int minutes = cfg.getAutoCleanIntervalMinutes();
        autoCleanBadge.setText("Tự động dọn: BẬT (" + minutes + "p" + (cfg.isAutoCleanDryRun() ? " - thử" : "") + ")");
        autoCleanBadge.setForeground(Theme.success());

        autoCleanTimer = new javax.swing.Timer(minutes * 60 * 1000, e -> triggerScheduledAutoClean());
        autoCleanTimer.setRepeats(true);
        autoCleanTimer.start();
    }

    private void triggerScheduledAutoClean() {
        if (closing || mockupMode || selectedAccount == null || autoCleanRunning.get()
                || deleting || activeCleanupDialog != null) return;
        if (selectedAccount.requiresReauthBeforeMail()) {
            disableAutoCleanWithWarning("Tài khoản " + selectedAccount.email() + " đang ở trạng thái NEEDS_REAUTH. Tự động dọn đã được tắt.");
            return;
        }

        ClientConfig cfg = ClientConfig.getInstance();
        if (!cfg.isAutoCleanEnabled()) return;

        autoCleanRunning.set(true);
        autoCleanCancelled.set(false);
        autoCleanStopBtn.setVisible(true);
        setStatus("Tự động dọn đang quét hộp thư " + selectedAccount.email() + "...");

        final Long gid = selectedAccount.id();
        final String email = selectedAccount.email();
        final int maxPerRun = cfg.getAutoCleanMaxPerRun();
        final boolean dryRun = cfg.isAutoCleanDryRun();
        final String senderFilter = cfg.getAutoCleanSenderFilter();
        final int minAgeDays = cfg.getAutoCleanMinAgeDays();
        final File baseDir = new File(cfg.getDownloadDir());
        final long generation = mailboxGeneration;
        final long sessionGeneration = SessionStore.getInstance().getGeneration();

        new SwingWorker<List<MailCleanupResult>, String>() {
            @Override protected List<MailCleanupResult> doInBackground() throws Exception {
                MailListResponse list = apiClient.getMails(gid, "INBOX", 0, maxPerRun, null, null, true,
                        senderFilter.isBlank() ? null : senderFilter, null);
                List<MailSummaryDto> candidates = new ArrayList<>();
                Instant cutoff = minAgeDays > 0 ? Instant.now().minus(Duration.ofDays(minAgeDays)) : null;
                if (list.items() != null) {
                    for (MailSummaryDto m : list.items()) {
                        if (!MailboxViewModel.passesCleanupAge(m.date(), cutoff)) continue;
                        candidates.add(m);
                    }
                }
                return cleanupService.processMails(gid, email, "INBOX", candidates, baseDir,
                        false, dryRun, autoCleanCancelled, this::publish,
                        () -> !closing && generation == mailboxGeneration
                                && sessionGeneration == SessionStore.getInstance().getGeneration()
                                && ClientConfig.getInstance().isAutoCleanEnabled());
            }

            @Override protected void process(List<String> chunks) {
                setStatus("[Tự động dọn] " + chunks.get(chunks.size() - 1));
            }

            @Override protected void done() {
                autoCleanRunning.set(false);
                autoCleanStopBtn.setVisible(false);
                try {
                    List<MailCleanupResult> res = get();
                    long ok = res.stream().filter(r -> r.status() == AttachmentCleanupService.ItemStatus.SUCCESS).count();
                    long fail = res.stream().filter(r -> r.status() == AttachmentCleanupService.ItemStatus.FAILED).count();
                    setStatus("Tự động dọn hoàn tất: " + ok + " thành công, " + fail + " lỗi.");
                    if (res.stream().anyMatch(r -> r.status() == AttachmentCleanupService.ItemStatus.DELETE_UNCERTAIN)) {
                        disableAutoCleanWithWarning("Chưa xác nhận được kết quả xóa. Đã dừng tự động dọn; hãy kiểm tra thư mục nguồn và Thùng rác trước khi bật lại.");
                        if (!closing && selectedAccount != null && java.util.Objects.equals(selectedAccount.id(), gid)) loadFolders();
                    } else if (cleanupService.getConsecutiveErrors() >= 3) {
                        disableAutoCleanWithWarning("Tự động dọn gặp 3 lỗi liên tiếp và đã tự tắt để bảo vệ dữ liệu.");
                    } else if (ok > 0) {
                        loadFolders();
                    }
                } catch (Exception ex) {
                    if (ex.getCause() instanceof ApiException apiEx && "GMAIL_NEEDS_REAUTH".equalsIgnoreCase(apiEx.getCode())) {
                        disableAutoCleanWithWarning("Tài khoản cần xác thực lại (NEEDS_REAUTH). Tự động dọn đã tự tắt.");
                    } else {
                        setStatus("Lỗi Tự động dọn: " + extractMsg(ex));
                    }
                }
            }
        }.execute();
    }

    private void disableAutoCleanWithWarning(String reason) {
        autoCleanCancelled.set(true);
        ClientConfig.getInstance().setAutoCleanEnabled(false);
        if (closing) return;
        configureAutoCleanTimer();
        setStatus(reason);
        JOptionPane.showMessageDialog(this, reason, "Tự động dọn đã tắt", JOptionPane.WARNING_MESSAGE);
    }

    private void refreshAfterCleanup() {
        if (!ClientConfig.getInstance().isAutoCleanEnabled()) autoCleanCancelled.set(true);
        if (closing) return;
        configureAutoCleanTimer();
        if (selectedAccount != null) loadFolders();
        else loadGmailAccounts();
    }

    // ==================== DIALOGS & OAUTH2 ====================

    private void openGmailManagerDialog() {
        if (closing) return;
        loadPublicConfig();
        GmailManagerDialog dlg = new GmailManagerDialog(
                this, apiClient, SessionStore.getInstance().getPublicConfig(),
                this::initiateOAuth2Flow, this::loadGmailAccounts);
        activeGmailManager = dlg;
        dlg.setOnSessionExpired(this::handleSessionExpired);
        try {
            dlg.setVisible(true);
        } finally {
            activeGmailManager = null;
        }
    }

    private void openProfileDialog() {
        ProfileSettingsAboutDialogs.showProfileDialog(this, apiClient,
                () -> new ChangePasswordDialog(this, apiClient, false).setVisible(true));
    }

    private void openSettingsDialog() {
        boolean wasDark = Theme.isDark();
        ProfileSettingsAboutDialogs.showSettingsDialog(this, () -> {
            configureAutoCleanTimer();
            serverBadgeLabel.setText("HTTPS: " + ClientConfig.getInstance().getServerUrl());
            if (wasDark != Theme.isDark()) {
                rebuildForThemeChange();
            }
        });
    }

    private void toggleTheme() {
        Theme.setDark(!Theme.isDark());
        rebuildForThemeChange();
    }

    private void rebuildForThemeChange() {
        cancelAllBackgroundTasks();
        String savedQuery = searchField != null ? searchField.getText() : "";
        boolean savedUnread = unreadFilterBtn != null && unreadFilterBtn.isSelected();
        boolean savedAttach = attachFilterBtn != null && attachFilterBtn.isSelected();
        Integer savedPageSize = (Integer) pageSizeCombo.getSelectedItem();

        MainFrame fresh = new MainFrame(apiClient, false);
        fresh.setBounds(getBounds());
        fresh.accountAvatars.putAll(this.accountAvatars);
        fresh.avatarFetchedIds.addAll(this.avatarFetchedIds);
        fresh.updateUserUI(SessionStore.getInstance().getCurrentUser());
        fresh.selectedAccount = this.selectedAccount;
        fresh.selectedFolder = this.selectedFolder;
        fresh.displayGmailAccounts(currentGmailAccounts);
        if (fresh.selectedAccount != null && fresh.accountAvatars.containsKey(fresh.selectedAccount.id())) {
            fresh.userProfileBtn.setIcon(AvatarUtil.createCircularPhotoIcon(fresh.accountAvatars.get(fresh.selectedAccount.id()), 22));
        }
        fresh.searchField.setText(savedQuery);
        fresh.unreadFilterBtn.setSelected(savedUnread);
        fresh.attachFilterBtn.setSelected(savedAttach);
        fresh.pageSizeCombo.setSelectedItem(savedPageSize);
        fresh.setVisible(true);
        fresh.startConfigRefresh();
        fresh.loadPublicConfig();
        if (fresh.selectedAccount != null) {
            fresh.loadFolders();
        } else {
            fresh.showEmptyListState("Chưa liên kết tài khoản Gmail",
                    "Bấm nút bên dưới để liên kết Gmail qua OAuth2 hoặc App Password.",
                    "Liên kết Gmail ngay");
            fresh.setStatus("Đã tải 0 tài khoản Gmail.");
        }
        dispose();
    }

    public void initiateOAuth2Flow() {
        initiateOAuth2Flow(null);
    }

    public void initiateOAuth2Flow(Long accountId) {
        SessionStore session = SessionStore.getInstance();
        if (closing || cancelOAuthLink != null || !session.hasActiveSession()) return;
        if (!oauthLinkAdmission.isAllowed()) {
            JOptionPane.showMessageDialog(this,
                    "Tính năng liên kết Google OAuth2 hiện đang bị tắt bởi Quản trị viên.",
                    "Tính năng bị tắt", JOptionPane.WARNING_MESSAGE);
            return;
        }
        final long generation = session.getGeneration();
        FeatureAdmission.Ticket admission = oauthLinkAdmission.capture();

        JDialog pollDialog = new JDialog(this, "Liên Kết Google OAuth2 — " + AppIcons.BRAND_FULL, true);
        pollDialog.setIconImages(AppIcons.getWindowIcons());
        pollDialog.setSize(490, 210);
        pollDialog.setLocationRelativeTo(this);
        pollDialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);

        JPanel dialogPanel = new JPanel(new BorderLayout(10, 12));
        dialogPanel.setBorder(new EmptyBorder(18, 20, 15, 20));
        dialogPanel.setBackground(Theme.bgSurface());

        JLabel statusLabel = new JLabel("<html><b>Đang kết nối tới máy chủ...</b><br>Đang tạo liên kết ủy quyền Google OAuth2 (PKCE).</html>");
        statusLabel.setFont(Theme.fontSmall());
        statusLabel.setForeground(Theme.textPrimary());

        JProgressBar progressBar = new JProgressBar(0, 90);
        progressBar.setStringPainted(true);
        progressBar.setString("Đang chuẩn bị...");

        JButton cancelButton = Theme.secondaryButton("Hủy bỏ", null);
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        btnPanel.setOpaque(false);
        btnPanel.add(cancelButton);

        dialogPanel.add(statusLabel, BorderLayout.NORTH);
        dialogPanel.add(progressBar, BorderLayout.CENTER);
        dialogPanel.add(btnPanel, BorderLayout.SOUTH);
        pollDialog.setContentPane(dialogPanel);

        final AtomicBoolean pollCancelled = new AtomicBoolean(false);
        java.util.function.BooleanSupplier current = () -> !closing && !pollCancelled.get()
                && generation == session.getGeneration() && session.hasActiveSession();

        SwingWorker<ApiClient.OAuthLinkPollDto, Integer> oauthWorker = new SwingWorker<>() {
            @Override protected ApiClient.OAuthLinkPollDto doInBackground() throws Exception {
                MailboxViewModel.preflightOAuthLink(oauthLinkAdmission, admission, current, apiClient::getPublicConfig);
                MailboxViewModel.requireOAuthLinkAdmission(oauthLinkAdmission, admission, current);
                ApiClient.OAuthLinkStartDto start = apiClient.startOAuth2Link(accountId);
                MailboxViewModel.requireOAuthLinkAdmission(oauthLinkAdmission, admission, current);
                if (start == null || start.pollToken() == null || start.pollToken().isBlank()
                        || start.authorizeUrl() == null || start.authorizeUrl().isBlank()) {
                    throw new ApiException(500, "PARSE_ERROR", "Máy chủ chưa hỗ trợ kiểm tra kết quả liên kết OAuth2.");
                }
                boolean opened = false;
                try {
                    if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                        URI uri = new URI(start.authorizeUrl());
                        if (uri.getHost() == null || uri.getUserInfo() != null
                                || !("https".equalsIgnoreCase(uri.getScheme())
                                || ("http".equalsIgnoreCase(uri.getScheme())
                                && ("localhost".equalsIgnoreCase(uri.getHost()) || "127.0.0.1".equals(uri.getHost()))))) {
                            throw new IllegalArgumentException("Invalid authorization URL");
                        }
                        MailboxViewModel.requireOAuthLinkAdmission(oauthLinkAdmission, admission, current);
                        Desktop.getDesktop().browse(uri);
                        opened = true;
                    }
                } catch (InterruptedException | ApiException ex) {
                    throw ex;
                } catch (Exception ignored) {
                    // Do not expose a raw authorization URL or provider exception in Swing.
                }

                if (!opened) {
                    return new ApiClient.OAuthLinkPollDto("FAILED", "BROWSER_UNAVAILABLE",
                            "Không thể mở trình duyệt mặc định. Hãy kiểm tra trình duyệt rồi thực hiện liên kết lại.", null);
                }
                // Once the browser is opened, do not invent cancellation of a remote consent.
                // Continue reading the actual result while the local session is current.
                SwingUtilities.invokeLater(() -> {
                    if (current.getAsBoolean() && pollDialog.isDisplayable()) {
                        statusLabel.setText("<html><b>Trình duyệt đã mở.</b><br>Hãy chọn tài khoản Google và giữ nguyên dấu tick quyền Gmail.</html>");
                    }
                });

                return MailboxViewModel.pollForOAuthLink(
                        () -> apiClient.pollOAuth2Link(start.pollToken()),
                        90,
                        2000L,
                        pollCancelled,
                        current,
                        this::publish
                );

            }
            @Override protected void process(List<Integer> chunks) {
                if (!current.getAsBoolean() || !pollDialog.isDisplayable() || chunks.isEmpty()) return;
                int v = chunks.get(chunks.size() - 1);
                progressBar.setValue(v);
                progressBar.setString("Đang chờ xác nhận (" + (v * 2) + "s / 180s)...");
            }
            @Override protected void done() {
                try {
                    if (isCancelled() || !current.getAsBoolean()) return;
                    ApiClient.OAuthLinkPollDto result = get();
                    pollDialog.dispose();
                    if (!current.getAsBoolean() || result == null || "CANCELLED".equalsIgnoreCase(result.status())) return;
                    if ("SUCCESS".equalsIgnoreCase(result.status())
                            && result.accountId() != null && result.accountId() > 0
                            && (accountId == null || Objects.equals(accountId, result.accountId()))) {
                        loadGmailAccounts();
                        JOptionPane.showMessageDialog(MainFrame.this, "Liên kết tài khoản Google OAuth2 thành công!",
                                "Thành công", JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        JOptionPane.showMessageDialog(MainFrame.this,
                                result.message() != null ? result.message() : "Liên kết Google OAuth2 không thành công.",
                                "Không thể liên kết", JOptionPane.WARNING_MESSAGE);
                    }
                } catch (Exception ex) {
                    if (isCancelled() || !current.getAsBoolean()) return;
                    pollDialog.dispose();
                    handleCommonError(ex, "Lỗi liên kết Google OAuth2");
                } finally {
                    pollDialog.dispose();
                }
            }
        };
        Runnable cancel = () -> {
            pollCancelled.set(true);
            oauthWorker.cancel(true);
            pollDialog.dispose();
        };
        cancelOAuthLink = cancel;
        cancelButton.addActionListener(e -> {
            cancel.run();
            if (!closing) setStatus("Đã dừng chờ liên kết. Nếu đã xác nhận trên trình duyệt, hãy làm mới danh sách để kiểm tra kết quả.");
        });
        pollDialog.addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) {
                cancelButton.doClick();
            }
        });
        try {
            oauthWorker.execute();
            pollDialog.setVisible(true);
        } finally {
            if (!oauthWorker.isDone()) cancel.run();
            if (cancelOAuthLink == cancel) cancelOAuthLink = null;
        }
    }

    private void refreshCurrentMailbox() {
        loadPublicConfig();
        loadUserInfo();
        if (selectedAccount == null) {
            loadGmailAccounts();
        } else if (selectedFolder == null) {
            if (!foldersLoading) loadFolders();
        } else {
            // Refresh chỉ cần tải lại danh sách thư. Gọi loadFolders() ở đây từng gây
            // thêm một request IMAP đắt đỏ (~8s với Gmail) cho mỗi lần bấm Làm mới.
            loadMails(0, false);
        }
    }

    private void handleLogout() {
        if (JOptionPane.showConfirmDialog(this, "Bạn có chắc chắn muốn đăng xuất?",
                "Đăng xuất", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
            returnToLogin();
        }
    }

    private void returnToLogin() {
        cancelAllBackgroundTasks();
        SessionStore.getInstance().clearSession();
        apiClient.clearAccessToken();
        new LoginFrame(apiClient.newUnauthenticatedClient()).setVisible(true);
        dispose();
    }

    private void handleSessionExpired() {
        if (closing) return;
        cancelAllBackgroundTasks();
        SessionStore.getInstance().clearSession();
        apiClient.clearAccessToken();
        JOptionPane.showMessageDialog(this, "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.",
                "Hết phiên đăng nhập", JOptionPane.WARNING_MESSAGE);
        new LoginFrame(apiClient.newUnauthenticatedClient()).setVisible(true);
        dispose();
    }

    private void handleCommonError(Exception ex, String fallbackTitle) {
        if (closing) return;
        Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
        if (cause instanceof ApiException apiEx) {
            if (apiEx.getStatusCode() == 401) {
                handleSessionExpired();
                return;
            }
            if ("MAINTENANCE_MODE".equalsIgnoreCase(apiEx.getCode())) {
                cancelAllBackgroundTasks();
                JOptionPane.showMessageDialog(this, apiEx.getMessage(), "Hệ thống đang bảo trì", JOptionPane.WARNING_MESSAGE);
                SessionStore.getInstance().clearSession();
                apiClient.clearAccessToken();
                new LoginFrame(apiClient.newUnauthenticatedClient()).setVisible(true);
                dispose();
                return;
            }
            if ("PASSWORD_CHANGE_REQUIRED".equalsIgnoreCase(apiEx.getCode())) {
                promptForcedPasswordChange();
                return;
            }
            if ("GMAIL_NEEDS_REAUTH".equalsIgnoreCase(apiEx.getCode())) {
                setStatus("Tài khoản cần cấp lại quyền truy cập Gmail.");
                loadGmailAccounts();
                return;
            }
            if ("GMAIL_ACCOUNT_CHANGED".equalsIgnoreCase(apiEx.getCode())) {
                setStatus("Liên kết Gmail đã thay đổi. Đang tải lại thông tin; không tự gửi lại thao tác.");
                loadGmailAccounts();
                return;
            }
        }
        setStatus(fallbackTitle + ": " + extractMsg(ex));
    }

    private void showEmptyListState(String title, String sub, String ctaText) {
        listEmptyTitle.setText(title);
        listEmptySub.setText("<html><div style='text-align:center;width:210px;'>" + escapeHtml(sub) + "</div></html>");
        listEmptyActionBtn.setText(ctaText);
        reauthCta = ctaText.contains("Cấp lại");
        updateEmptyActionVisibility();
        for (ActionListener al : listEmptyActionBtn.getActionListeners()) {
            listEmptyActionBtn.removeActionListener(al);
        }
        listEmptyActionBtn.addActionListener(e -> {
            if (ctaText.contains("Cấp lại") && selectedAccount != null) initiateOAuth2Flow(selectedAccount.id());
            else if (ctaText.contains("Liên kết") || ctaText.contains("Quản lý")) openGmailManagerDialog();
            else refreshCurrentMailbox();
        });
        listCardLayout.show(listCardPanel, "EMPTY");
    }

    private void updateEmptyActionVisibility() {
        if (listEmptyActionBtn == null) return;
        PublicConfigDto config = SessionStore.getInstance().getPublicConfig();
        listEmptyActionBtn.setVisible(!reauthCta || (selectedAccount != null && selectedAccount.hasOAuth()
                && config != null && config.oauth2LinkEnabled()));
    }

    private void updateListItemSeenFlag(long uid, boolean seen) {
        for (int i = 0; i < mailListModel.size(); i++) {
            MailSummaryDto m = mailListModel.get(i);
            if (m.uid() == uid && m.flags() != null && m.flags().seen() != seen) {
                MailFlagsDto nf = new MailFlagsDto(seen, m.flags().flagged(), m.flags().answered(), m.flags().draft());
                mailListModel.set(i, withFlags(m, nf));
                break;
            }
        }
    }

    private void updateListItemFlags(long uid, MailFlagsDto flags) {
        for (int i = 0; i < mailListModel.size(); i++) {
            MailSummaryDto m = mailListModel.get(i);
            if (m.uid() == uid) {
                mailListModel.set(i, withFlags(m, flags));
                break;
            }
        }
    }

    private static MailSummaryDto withFlags(MailSummaryDto m, MailFlagsDto nf) {
        return new MailSummaryDto(m.uid(), m.uidValidity(), m.from(), m.to(), m.subject(),
                m.snippet(), m.date(), nf, m.size(), m.hasAttachment(), m.attachmentCount());
    }

    private void addExpandedRow(String label, String value) {
        JLabel l = new JLabel("<html><b>" + label + "</b> " + escapeHtml(value) + "</html>");
        l.setFont(Theme.fontTiny());
        l.setForeground(Theme.textSecondary());
        expandedHeaderPanel.add(l);
    }

    private void setStatus(String text) {
        if (statusBarLabel != null) statusBarLabel.setText(text);
    }

    private static String extractMsg(Exception ex) {
        return ClientSafetyUtil.toFriendlyVietnameseError(ex);
    }

    private static String formatAddressList(List<MailAddressDto> list) {
        if (list == null || list.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(list.get(i).displayText());
        }
        return sb.toString();
    }

    public static ZonedDateTime parseDateToSystemZone(String isoDate) {
        return MailboxViewModel.parseDateToZone(isoDate, ZoneId.systemDefault());
    }

    public static String formatFriendlyDate(String isoDate) {
        if (isoDate == null || isoDate.isBlank()) return "";
        ZonedDateTime zdt = parseDateToSystemZone(isoDate);
        if (zdt == null) {
            return isoDate.length() > 10 ? isoDate.substring(0, 10) : isoDate;
        }
        LocalDate d = zdt.toLocalDate();
        LocalDate today = LocalDate.now();
        if (d.equals(today)) return TIME_FMT.format(zdt);
        if (d.equals(today.minusDays(1))) return "Hôm qua";
        return DATE_FMT.format(zdt);
    }

    private static String formatFullDate(String isoDate) {
        if (isoDate == null || isoDate.isBlank()) return "";
        ZonedDateTime zdt = parseDateToSystemZone(isoDate);
        return zdt != null ? FULL_FMT.format(zdt) : isoDate;
    }

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    // ==================== CHẾ ĐỘ CHỤP ẢNH MOCKUP (--screenshots CHỈ DÙNG ĐỂ XUẤT ẢNH) ====================

    public void populateMockupForScreenshots(UserDto user,
                                             List<GmailAccountDto> accounts,
                                             List<MailFolderDto> folders,
                                             List<MailSummaryDto> mails,
                                             MailDetailResponse detail) {
        this.mockupMode = true;
        updateUserUI(user);
        displayGmailAccounts(accounts);
        folderListModel.clear();
        for (MailFolderDto f : folders) folderListModel.addElement(f);
        if (!folders.isEmpty()) {
            selectedFolder = folders.get(0);
            folderList.setSelectedIndex(0);
        }
        loadedUids.clear();
        mailListModel.clear();
        for (MailSummaryDto m : mails) {
            if (loadedUids.add(m.uid())) mailListModel.addElement(m);
        }
        listCardLayout.show(listCardPanel, "LIST");
        mailCountLabel.setText("Hiển thị " + mails.size() + " / " + mails.size() + " thư");
        if (!mails.isEmpty()) mailList.setSelectedIndex(0);
        if (detail != null) displayMailDetail(detail);
        setStatus("Dữ liệu mô phỏng (chỉ dùng cho --screenshots)");
    }

    // ==================== CUSTOM JAVA2D RENDERERS ====================

    private static class FolderCellRenderer extends JPanel implements ListCellRenderer<MailFolderDto> {
        private final JLabel nameLbl = new JLabel();
        private final JLabel badgeLbl = new JLabel();

        FolderCellRenderer() {
            setLayout(new BorderLayout(Theme.SP_2, 0));
            setBorder(new EmptyBorder(6, 10, 6, 10));
            badgeLbl.setFont(Theme.fontTiny());
            badgeLbl.setHorizontalAlignment(SwingConstants.CENTER);
            add(nameLbl, BorderLayout.CENTER);
            add(badgeLbl, BorderLayout.EAST);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends MailFolderDto> list, MailFolderDto f,
                                                      int index, boolean isSelected, boolean cellHasFocus) {
            setBackground(isSelected ? Theme.bgSelected() : Theme.bgSurface());
            Color fg = isSelected ? Theme.primary() : Theme.textPrimary();
            nameLbl.setText(f.displayName());
            nameLbl.setFont(f.unread() > 0 || isSelected ? Theme.fontBold() : Theme.fontRegular());
            nameLbl.setForeground(fg);
            nameLbl.setIcon(AppIcons.vectorIcon(glyphForFolder(f.type()), 16, fg));
            nameLbl.setIconTextGap(Theme.SP_2);

            if (f.unread() > 0) {
                badgeLbl.setText(" " + f.unread() + " ");
                badgeLbl.setForeground(Theme.primary());
                badgeLbl.setVisible(true);
            } else {
                badgeLbl.setText("");
                badgeLbl.setVisible(false);
            }
            return this;
        }

        private static AppIcons.Glyph glyphForFolder(String type) {
            if (type == null) return AppIcons.Glyph.FOLDER;
            return switch (type.toUpperCase(Locale.ROOT)) {
                case "INBOX" -> AppIcons.Glyph.INBOX;
                case "SENT" -> AppIcons.Glyph.SENT;
                case "DRAFTS" -> AppIcons.Glyph.DRAFTS;
                case "TRASH" -> AppIcons.Glyph.TRASH;
                case "SPAM" -> AppIcons.Glyph.SPAM;
                case "STARRED" -> AppIcons.Glyph.STAR;
                default -> AppIcons.Glyph.FOLDER;
            };
        }
    }

    private static class MailSummaryCellRenderer extends JPanel implements ListCellRenderer<MailSummaryDto> {
        private final JLabel avatarLbl = new JLabel();
        private final JLabel senderLbl = new JLabel();
        private final JLabel dateLbl = new JLabel();
        private final JLabel iconsLbl = new JLabel();
        private final JLabel starLbl = new JLabel();
        private final JLabel subjectLbl = new JLabel();
        private final JLabel snippetLbl = new JLabel();
        private boolean isUnreadRow;

        MailSummaryCellRenderer() {
            setLayout(new BorderLayout(Theme.SP_3, 0));
            setBorder(new CompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.border()),
                    new EmptyBorder(Theme.SP_2, Theme.SP_3, Theme.SP_2, Theme.SP_3)
            ));

            JPanel center = new JPanel();
            center.setOpaque(false);
            center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));

            // Chiều cao dòng suy ra từ font thay vì số cứng, để tăng cỡ chữ không bị cắt chân chữ.
            int baseLine = getFontMetrics(Theme.fontRegular()).getHeight();
            int smallLine = getFontMetrics(Theme.fontSmall()).getHeight();
            int tinyLine = getFontMetrics(Theme.fontTiny()).getHeight();

            JPanel topRow = new JPanel(new BorderLayout(Theme.SP_1, 0));
            topRow.setOpaque(false);
            topRow.setAlignmentX(Component.LEFT_ALIGNMENT);
            topRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, baseLine + 2));
            JPanel rightTop = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
            rightTop.setOpaque(false);
            rightTop.add(iconsLbl);
            rightTop.add(dateLbl);
            topRow.add(senderLbl, BorderLayout.CENTER);
            topRow.add(rightTop, BorderLayout.EAST);

            subjectLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
            subjectLbl.setMaximumSize(new Dimension(Integer.MAX_VALUE, baseLine + 2));
            snippetLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
            snippetLbl.setMaximumSize(new Dimension(Integer.MAX_VALUE, Math.max(smallLine, tinyLine) + 2));

            center.add(topRow);
            center.add(Box.createVerticalStrut(3));
            center.add(subjectLbl);
            center.add(Box.createVerticalStrut(2));
            center.add(snippetLbl);

            JPanel avWrap = new JPanel(new GridBagLayout());
            avWrap.setOpaque(false);
            avWrap.add(avatarLbl);

            add(avWrap, BorderLayout.WEST);
            add(center, BorderLayout.CENTER);
            starLbl.setHorizontalAlignment(SwingConstants.CENTER);
            starLbl.setPreferredSize(new Dimension(22, 22));
            add(starLbl, BorderLayout.EAST);
        }

        private static String cleanDisplayText(String raw) {
            if (raw == null) return "";
            // Loại bỏ ký tự emoji surrogate/variation selector gây ô vuông lỗi font trên Swing Windows
            return raw.replaceAll("[\\x{10000}-\\x{10FFFF}\\uFE0F\\u200D]", "").replaceAll("\\s+", " ").trim();
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends MailSummaryDto> list, MailSummaryDto m,
                                                      int index, boolean isSelected, boolean cellHasFocus) {
            isUnreadRow = m.flags() != null && !m.flags().seen();
            boolean starred = m.flags() != null && m.flags().flagged();

            setBackground(isSelected ? Theme.bgSelected() : Theme.bgSurface());

            String sender = (m.from() != null && m.from().name() != null && !m.from().name().isBlank())
                    ? cleanDisplayText(m.from().name())
                    : (m.from() != null && m.from().address() != null ? m.from().address() : "(Không rõ)");
            String email = m.from() != null ? m.from().address() : "";

            avatarLbl.setIcon(AvatarUtil.createAvatarIcon(sender, email, 36));
            senderLbl.setText(sender);
            senderLbl.setFont(isUnreadRow ? Theme.fontBold() : Theme.fontRegular());
            senderLbl.setForeground(Theme.textPrimary());

            dateLbl.setText(formatFriendlyDate(m.date()));
            dateLbl.setFont(Theme.fontTiny());
            dateLbl.setForeground(isUnreadRow ? Theme.primaryText() : Theme.textMuted());

            iconsLbl.setIcon(null);
            iconsLbl.setText("");
            starLbl.setIcon(AppIcons.vectorIcon(starred ? AppIcons.Glyph.STAR_FILLED : AppIcons.Glyph.STAR, 16, Theme.starGold()));
            if (m.hasAttachment()) {
                iconsLbl.setIcon(AppIcons.vectorIcon(AppIcons.Glyph.ATTACHMENT, 13, Theme.textSecondary()));
            }

            String rawSubj = (m.subject() != null && !m.subject().isBlank()) ? cleanDisplayText(m.subject()) : "(Không có tiêu đề)";
            subjectLbl.setText(rawSubj.isEmpty() ? "(Không có tiêu đề)" : rawSubj);
            subjectLbl.setFont(isUnreadRow ? Theme.fontBold() : Theme.fontSmall());
            subjectLbl.setForeground(isUnreadRow ? Theme.textPrimary() : Theme.textSecondary());

            String snip = (m.snippet() != null && !m.snippet().isBlank())
                    ? cleanDisplayText(m.snippet())
                    : (m.hasAttachment() ? "Có tệp đính kèm • " + MailSizeDisplay.message(m.size()) : MailSizeDisplay.message(m.size()));
            snippetLbl.setText(snip);
            snippetLbl.setFont(Theme.fontTiny());
            snippetLbl.setForeground(Theme.textMuted());
            return this;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (isUnreadRow) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Theme.primary());
                g2.fillRect(0, 4, 3, getHeight() - 8);
                g2.dispose();
            }
        }
    }

    /** Skeleton loading vẽ bằng Java2D trong lúc nạp danh sách thư. */
    private static class SkeletonListPanel extends JPanel {
        SkeletonListPanel() {
            setBackground(Theme.bgSurface());
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color base = Theme.bgHover();
            int y = 16;
            for (int i = 0; i < 6 && y < getHeight(); i++) {
                g2.setColor(base);
                g2.fillOval(14, y, 36, 36);
                g2.fillRoundRect(62, y + 2, Math.max(120, getWidth() - 150), 12, 6, 6);
                g2.fillRoundRect(62, y + 20, Math.max(90, getWidth() - 100), 10, 5, 5);
                g2.fillRoundRect(62, y + 36, Math.max(70, getWidth() - 180), 8, 4, 4);
                y += 66;
            }
            g2.dispose();
        }
    }
}
