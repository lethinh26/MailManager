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
import com.mailapp.client.util.ClientConfig;
import com.mailapp.client.util.ClientSafetyUtil;
import com.mailapp.client.util.SessionStore;

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
    private boolean hasMoreMails = false;
    private long totalMails = 0;
    private long currentUidValidity = 0;
    private MailDetailResponse currentDetail;
    private boolean showHtmlMode = true;
    private boolean headerExpanded = false;
    private boolean mockupMode = false;

    // Auto-cleanup timer & active SwingWorkers (chống race condition & rò rỉ luồng khi đóng cửa sổ)
    private javax.swing.Timer autoCleanTimer;
    private final AtomicBoolean autoCleanRunning = new AtomicBoolean(false);
    private final AtomicBoolean autoCleanCancelled = new AtomicBoolean(false);
    private final MailboxViewModel.WorkerCoordinator workerCoordinator = new MailboxViewModel.WorkerCoordinator();

    // Top bar & menus
    private JMenu adminMenu;
    private JButton adminToolbarBtn;
    private JButton userProfileBtn;
    private JButton autoCleanStopBtn;
    private JLabel autoCleanBadge;
    private JLabel statusBarLabel;
    private JLabel serverBadgeLabel;

    // Left column
    private JPanel accountsListPanel;
    private final Map<Long, java.awt.image.BufferedImage> accountAvatars = new java.util.concurrent.ConcurrentHashMap<>();
    private final Set<Long> avatarFetchedIds = java.util.Collections.synchronizedSet(new HashSet<>());
    private DefaultListModel<MailFolderDto> folderListModel;
    private JList<MailFolderDto> folderList;

    // Middle column
    private JTextField searchField;
    private JToggleButton unreadFilterBtn;
    private JToggleButton attachFilterBtn;
    private CardLayout listCardLayout;
    private JPanel listCardPanel;
    private DefaultListModel<MailSummaryDto> mailListModel;
    private JList<MailSummaryDto> mailList;
    private JButton loadMoreBtn;
    private JLabel mailCountLabel;
    private JLabel listEmptyTitle;
    private JLabel listEmptySub;
    private JButton listEmptyActionBtn;

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
    private JToggleButton htmlToggleBtn;
    private JToggleButton textToggleBtn;
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

        if (autoLoad) {
            loadPublicConfig();
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

        // 3-Column Split Layout
        JPanel leftCol = buildLeftColumn();
        JPanel middleCol = buildMiddleColumn();
        JPanel rightCol = buildRightColumn();

        JSplitPane midRightSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, middleCol, rightCol);
        midRightSplit.setDividerLocation(340);
        midRightSplit.setDividerSize(5);
        midRightSplit.setBorder(null);
        midRightSplit.setResizeWeight(0.36);

        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftCol, midRightSplit);
        mainSplit.setDividerLocation(235);
        mainSplit.setDividerSize(5);
        mainSplit.setBorder(null);
        mainSplit.setResizeWeight(0.0);

        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                int cw = getWidth();
                if (cw <= 860) {
                    mainSplit.setDividerLocation(195);
                    midRightSplit.setDividerLocation(250);
                }
            }
        });

        outerPanel.add(mainSplit, BorderLayout.CENTER);
        outerPanel.add(buildStatusBar(), BorderLayout.SOUTH);

        setContentPane(outerPanel);
    }

    private JPanel buildToolbar() {
        JPanel bar = new JPanel() {
            @Override
            public Dimension getPreferredSize() {
                int availW = getParent() != null && getParent().getWidth() > 0 ? getParent().getWidth() : 1040;
                Insets ins = getInsets();
                int maxW = Math.max(200, availW - ins.left - ins.right);
                int hgap = Theme.SP_2;
                int vgap = Theme.SP_1;
                int x = 0, rowH = 0, totalH = ins.top + ins.bottom;
                for (Component c : getComponents()) {
                    if (!c.isVisible()) continue;
                    Dimension d = c.getPreferredSize();
                    if (x > 0 && x + d.width > maxW) {
                        totalH += rowH + vgap;
                        x = 0;
                        rowH = 0;
                    }
                    x += d.width + hgap;
                    rowH = Math.max(rowH, d.height);
                }
                totalH += rowH;
                return new Dimension(availW, Math.max(44, totalH));
            }
        };
        bar.setLayout(new FlowLayout(FlowLayout.LEFT, Theme.SP_2, Theme.SP_1));
        bar.setBackground(Theme.bgSurface());
        bar.setBorder(new CompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.border()),
                new EmptyBorder(Theme.SP_1, Theme.SP_3, Theme.SP_1, Theme.SP_3)
        ));

        JLabel brand = new JLabel(AppIcons.BRAND_NAME, AppIcons.logoIcon(28), SwingConstants.LEFT);
        brand.setFont(Theme.fontSubTitle());
        brand.setForeground(Theme.primaryText());
        brand.setIconTextGap(Theme.SP_2);
        bar.add(brand);

        JButton composeBtn = Theme.primaryButton("Soạn thư", AppIcons.vectorIcon(AppIcons.Glyph.COMPOSE, 16, Color.WHITE));
        composeBtn.setToolTipText("Soạn thư mới (Ctrl+N)");
        composeBtn.setMnemonic(KeyEvent.VK_S);
        composeBtn.addActionListener(e -> openComposeDialog(null, null));

        JButton refreshBtn = Theme.secondaryButton("Làm mới", AppIcons.vectorIcon(AppIcons.Glyph.REFRESH, 16, Theme.textPrimary()));
        refreshBtn.setToolTipText("Đồng bộ hộp thư hiện tại (Ctrl+R)");
        refreshBtn.addActionListener(e -> refreshCurrentMailbox());

        JButton cleanupBtn = Theme.successButton("Tải đính kèm & Xóa", AppIcons.vectorIcon(AppIcons.Glyph.DOWNLOAD, 16, Color.WHITE));
        cleanupBtn.setToolTipText("Tải xuống & xác minh toàn bộ tệp đính kèm của thư đã chọn rồi xóa thư");
        cleanupBtn.addActionListener(e -> openCleanupForSelectedMails());

        JButton gmailMgrBtn = Theme.secondaryButton("Tài khoản Gmail", AppIcons.vectorIcon(AppIcons.Glyph.SHIELD, 16, Theme.primaryText()));
        gmailMgrBtn.setToolTipText("Liên kết OAuth2 / App Password, kiểm tra kết nối, đổi tên, hủy liên kết");
        gmailMgrBtn.addActionListener(e -> openGmailManagerDialog());

        bar.add(composeBtn);
        bar.add(refreshBtn);
        bar.add(cleanupBtn);
        bar.add(gmailMgrBtn);

        adminToolbarBtn = Theme.secondaryButton("Quản trị", AppIcons.vectorIcon(AppIcons.Glyph.SHIELD, 16, Theme.warning()));
        adminToolbarBtn.setToolTipText("Mở Bảng điều khiển Quản trị Hệ thống (chỉ dành cho ADMIN)");
        adminToolbarBtn.setVisible(false);
        adminToolbarBtn.addActionListener(e -> openAdminFrame());
        bar.add(adminToolbarBtn);

        JButton themeBtn = Theme.secondaryButton(
                Theme.isDark() ? "Chế độ Sáng" : "Chế độ Tối",
                AppIcons.vectorIcon(Theme.isDark() ? AppIcons.Glyph.SUN : AppIcons.Glyph.MOON, 15, Theme.textPrimary())
        );
        themeBtn.setToolTipText("Chuyển đổi giao diện Sáng / Tối");
        themeBtn.addActionListener(e -> toggleTheme());

        JButton settingsBtn = Theme.secondaryButton("Cài đặt", AppIcons.vectorIcon(AppIcons.Glyph.SETTINGS, 16, Theme.textPrimary()));
        settingsBtn.setToolTipText("Cấu hình máy chủ, thư mục tải về và Tự động dọn");
        settingsBtn.addActionListener(e -> openSettingsDialog());

        userProfileBtn = Theme.secondaryButton("Tài khoản", AppIcons.vectorIcon(AppIcons.Glyph.USER, 16, Theme.primaryText()));
        userProfileBtn.setToolTipText("Xem hồ sơ cá nhân & đổi mật khẩu");
        userProfileBtn.addActionListener(e -> openProfileDialog());

        bar.add(themeBtn);
        bar.add(settingsBtn);
        bar.add(userProfileBtn);
        return bar;
    }

    private JPanel buildLeftColumn() {
        JPanel left = new JPanel(new BorderLayout(0, Theme.SP_2));
        left.setBackground(Theme.bgSurface());
        left.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, Theme.border()));

        // Top half: Linked Gmail Accounts
        JPanel accSection = new JPanel(new BorderLayout(0, Theme.SP_1));
        accSection.setOpaque(false);
        accSection.setBorder(new EmptyBorder(Theme.SP_3, Theme.SP_3, Theme.SP_2, Theme.SP_3));

        JPanel accHeader = new JPanel(new BorderLayout());
        accHeader.setOpaque(false);
        JLabel accTitle = new JLabel("TÀI KHOẢN GMAIL");
        accTitle.setFont(Theme.fontTiny());
        accTitle.setForeground(Theme.textMuted());

        JButton addAccBtn = new JButton("+ Thêm");
        addAccBtn.setFont(Theme.fontTiny());
        addAccBtn.setForeground(Theme.primary());
        addAccBtn.setContentAreaFilled(false);
        addAccBtn.setBorderPainted(false);
        addAccBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        addAccBtn.addActionListener(e -> openGmailManagerDialog());

        accHeader.add(accTitle, BorderLayout.WEST);
        accHeader.add(addAccBtn, BorderLayout.EAST);
        accSection.add(accHeader, BorderLayout.NORTH);

        accountsListPanel = new JPanel();
        accountsListPanel.setLayout(new BoxLayout(accountsListPanel, BoxLayout.Y_AXIS));
        accountsListPanel.setBackground(Theme.bgSurface());

        JScrollPane accScroll = new JScrollPane(accountsListPanel);
        accScroll.setBorder(null);
        accScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        accScroll.setPreferredSize(new Dimension(230, 128));
        accScroll.getViewport().setBackground(Theme.bgSurface());
        accSection.add(accScroll, BorderLayout.CENTER);

        // Bottom half: Folders Tree/List
        JPanel folderSection = new JPanel(new BorderLayout(0, Theme.SP_1));
        folderSection.setOpaque(false);
        folderSection.setBorder(new CompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.border()),
                new EmptyBorder(Theme.SP_3, Theme.SP_2, Theme.SP_2, Theme.SP_2)
        ));

        JLabel folderTitle = new JLabel("  THƯ MỤC HỘP THƯ");
        folderTitle.setFont(Theme.fontTiny());
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
                    selectedFolder = f;
                    loadMails(0, false);
                }
            }
        });

        JScrollPane folderScroll = new JScrollPane(folderList);
        folderScroll.setBorder(null);
        folderScroll.getViewport().setBackground(Theme.bgSurface());
        folderSection.add(folderScroll, BorderLayout.CENTER);

        left.add(accSection, BorderLayout.NORTH);
        left.add(folderSection, BorderLayout.CENTER);
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
                    g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
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
        };
        mailList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        mailList.setBackground(Theme.bgSurface());
        mailList.setCellRenderer(new MailSummaryCellRenderer());
        mailList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && !mockupMode) {
                MailSummaryDto sel = mailList.getSelectedValue();
                if (sel != null && mailList.getSelectedIndices().length == 1) {
                    loadMailDetail(sel, false);
                }
            }
        });
        mailList.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    MailSummaryDto sel = mailList.getSelectedValue();
                    if (sel != null) loadMailDetail(sel, false);
                }
            }
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

        listFooter.add(mailCountLabel, BorderLayout.WEST);
        listFooter.add(loadMoreBtn, BorderLayout.EAST);
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

        // Action row (gọn gàng, tự xuống dòng ở 800x600 để vẫn chừa đủ chiều cao đọc nội dung thư)
        JPanel actionBar = Theme.createWrapPanel(Theme.SP_1, Theme.SP_1);
        EmptyBorder compactBtnPad = new EmptyBorder(4, 8, 4, 8);

        replyBtn = Theme.secondaryButton("Trả lời", AppIcons.vectorIcon(AppIcons.Glyph.REPLY, 14, Theme.textPrimary()));
        replyBtn.setBorder(compactBtnPad);
        replyBtn.addActionListener(e -> openComposeDialog(currentDetail, "REPLY"));

        replyAllBtn = Theme.secondaryButton("Trả lời tất cả", AppIcons.vectorIcon(AppIcons.Glyph.REPLY_ALL, 14, Theme.textPrimary()));
        replyAllBtn.setBorder(compactBtnPad);
        replyAllBtn.addActionListener(e -> openComposeDialog(currentDetail, "REPLY_ALL"));

        forwardBtn = Theme.secondaryButton("Chuyển tiếp", AppIcons.vectorIcon(AppIcons.Glyph.FORWARD, 14, Theme.textPrimary()));
        forwardBtn.setBorder(compactBtnPad);
        forwardBtn.addActionListener(e -> openComposeDialog(currentDetail, "FORWARD"));

        starActionBtn = Theme.secondaryButton("Sao", AppIcons.vectorIcon(AppIcons.Glyph.STAR, 14, Theme.starGold()));
        starActionBtn.setBorder(compactBtnPad);
        starActionBtn.addActionListener(e -> toggleStarCurrentMail());

        unreadActionBtn = Theme.secondaryButton("Chưa đọc", AppIcons.vectorIcon(AppIcons.Glyph.UNREAD, 14, Theme.textPrimary()));
        unreadActionBtn.setBorder(compactBtnPad);
        unreadActionBtn.addActionListener(e -> markCurrentMailUnread());

        cleanupOneBtn = Theme.successButton("Tải & Xóa", AppIcons.vectorIcon(AppIcons.Glyph.DOWNLOAD, 14, Color.WHITE));
        cleanupOneBtn.setBorder(compactBtnPad);
        cleanupOneBtn.addActionListener(e -> openCleanupForCurrentMail());

        deleteBtn = Theme.dangerButton("Xóa", AppIcons.vectorIcon(AppIcons.Glyph.DELETE, 14, Color.WHITE));
        deleteBtn.setBorder(compactBtnPad);
        deleteBtn.addActionListener(e -> deleteSelectedMails());

        actionBar.add(replyBtn);
        actionBar.add(replyAllBtn);
        actionBar.add(forwardBtn);
        actionBar.add(starActionBtn);
        actionBar.add(unreadActionBtn);
        actionBar.add(cleanupOneBtn);
        actionBar.add(deleteBtn);

        // HTML / Plain text switcher (đồng bộ phong cách Light/Dark, không cắt chữ)
        ButtonGroup bg = new ButtonGroup();
        htmlToggleBtn = createFilterToggle("HTML sạch", null);
        htmlToggleBtn.setSelected(true);
        htmlToggleBtn.addActionListener(e -> { showHtmlMode = true; htmlToggleBtn.repaint(); textToggleBtn.repaint(); renderCurrentBody(); });
        textToggleBtn = createFilterToggle("Văn bản", null);
        textToggleBtn.setSelected(false);
        textToggleBtn.addActionListener(e -> { showHtmlMode = false; htmlToggleBtn.repaint(); textToggleBtn.repaint(); renderCurrentBody(); });
        bg.add(htmlToggleBtn);
        bg.add(textToggleBtn);
        actionBar.add(htmlToggleBtn);
        actionBar.add(textToggleBtn);

        // Subject (căn trái tuyệt đối trong BoxLayout)
        detailSubjectLabel = new JLabel("(Không có tiêu đề)");
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
        detailFromLabel = new JLabel();
        detailFromLabel.setFont(Theme.fontBold());
        detailFromLabel.setForeground(Theme.textPrimary());
        detailToDateLabel = new JLabel();
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

        // Blocked remote images banner
        remoteImgBanner = new JPanel(new BorderLayout(Theme.SP_2, 0));
        remoteImgBanner.setBackground(Theme.warningSoft());
        remoteImgBanner.setBorder(new EmptyBorder(Theme.SP_2, Theme.SP_3, Theme.SP_2, Theme.SP_3));
        JLabel remLbl = new JLabel("Ảnh từ xa đã bị chặn để bảo vệ quyền riêng tư của bạn.");
        remLbl.setFont(Theme.fontSmall());
        remLbl.setForeground(Theme.warning());
        JButton loadRemoteBtn = Theme.secondaryButton("Tải ảnh từ xa", null);
        loadRemoteBtn.addActionListener(e -> {
            if (currentDetail != null) {
                MailSummaryDto cur = mailList.getSelectedValue();
                if (cur != null) loadMailDetail(cur, true);
            }
        });
        remoteImgBanner.add(remLbl, BorderLayout.CENTER);
        remoteImgBanner.add(loadRemoteBtn, BorderLayout.EAST);
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
        attachmentsContainer.add(attachmentsStrip, BorderLayout.CENTER);
        attachmentsContainer.setVisible(false);

        detail.add(detailNorth, BorderLayout.NORTH);
        detail.add(bodyScroll, BorderLayout.CENTER);
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
        JMenuItem composeItem = new JMenuItem("Soạn thư mới (Ctrl+N)");
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
                if (!(KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner() instanceof JTextField)) {
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
        if (autoCleanTimer != null) autoCleanTimer.stop();
        autoCleanCancelled.set(true);
        workerCoordinator.cancelAll();
    }

    // ==================== DATA LOADING (SWINGWORKER) ====================

    private void loadPublicConfig() {
        new SwingWorker<PublicConfigDto, Void>() {
            @Override protected PublicConfigDto doInBackground() throws Exception {
                return apiClient.getPublicConfig();
            }
            @Override protected void done() {
                try {
                    SessionStore.getInstance().setPublicConfig(get());
                } catch (Exception ignored) {}
            }
        }.execute();
    }

    public void loadUserInfo() {
        new SwingWorker<UserDto, Void>() {
            @Override protected UserDto doInBackground() throws Exception {
                return apiClient.getCurrentUser();
            }
            @Override protected void done() {
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
        userProfileBtn.setIcon(AvatarUtil.createAvatarIcon(display, user.email(), 20));
        boolean isAdmin = "ADMIN".equalsIgnoreCase(user.role());
        adminMenu.setVisible(isAdmin);
        if (adminToolbarBtn != null) adminToolbarBtn.setVisible(isAdmin);
        if (user.mustChangePassword() && !mockupMode) {
            promptForcedPasswordChange();
        }
    }

    private void openAdminFrame() {
        UserDto user = SessionStore.getInstance().getCurrentUser();
        if (user == null || !"ADMIN".equalsIgnoreCase(user.role())) {
            JOptionPane.showMessageDialog(this, "Chỉ Quản trị viên (ADMIN) mới có quyền truy cập.",
                    "Không có quyền", JOptionPane.WARNING_MESSAGE);
            return;
        }
        new AdminFrame(apiClient, this::handleSessionExpired).setVisible(true);
    }

    public void promptForcedPasswordChange() {
        ChangePasswordDialog dialog = new ChangePasswordDialog(this, apiClient, true);
        dialog.setVisible(true);
        if (dialog.isPasswordChangedSuccessfully()) {
            loadUserInfo();
        }
    }

    public void loadGmailAccounts() {
        avatarFetchedIds.clear();
        setStatus("Đang tải danh sách tài khoản Gmail...");
        new SwingWorker<List<GmailAccountDto>, Void>() {
            @Override protected List<GmailAccountDto> doInBackground() throws Exception {
                return apiClient.getGmailAccounts();
            }
            @Override protected void done() {
                try {
                    List<GmailAccountDto> accounts = get();
                    displayGmailAccounts(accounts);
                    setStatus("Đã tải " + currentGmailAccounts.size() + " tài khoản Gmail.");
                    if (!currentGmailAccounts.isEmpty()) {
                        GmailAccountDto target = selectedAccount;
                        if (target == null || currentGmailAccounts.stream().noneMatch(a -> a.id().equals(target.id()))) {
                            selectGmailAccount(currentGmailAccounts.get(0));
                        } else {
                            // Cập nhật bản ghi mới nhất của tài khoản đang chọn
                            currentGmailAccounts.stream().filter(a -> a.id().equals(target.id())).findFirst()
                                    .ifPresent(a -> selectedAccount = a);
                            renderAccountCards();
                        }
                    } else {
                        selectedAccount = null;
                        folderListModel.clear();
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

        JLabel nameLbl = new JLabel(name);
        nameLbl.setFont(Theme.fontBold());
        nameLbl.setForeground(Theme.textPrimary());
        nameLbl.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel emailLbl = new JLabel(acc.email());
        emailLbl.setFont(Theme.fontTiny());
        emailLbl.setForeground(Theme.textSecondary());
        emailLbl.setAlignmentX(Component.LEFT_ALIGNMENT);

        // Huy hiệu Google & App Password gọn gàng kèm dấu sao ★ cho phương thức chính
        JPanel badgeRow = Theme.createWrapPanel(4, 2);
        badgeRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel gBadge = hasOAuth
                ? Theme.badge(oauthPrimary ? "★ Google" : "Google", Theme.bgSelected(), Theme.primaryText())
                : Theme.badge("Google —", Theme.bgHover(), Theme.textMuted());
        JLabel pBadge = hasAppPass
                ? Theme.badge(appPassPrimary ? "★ App Password" : "App Password", Theme.successSoft(), Theme.success())
                : Theme.badge("App Password —", Theme.bgHover(), Theme.textMuted());
        badgeRow.add(gBadge);
        badgeRow.add(pBadge);

        info.add(nameLbl);
        info.add(emailLbl);
        info.add(Box.createVerticalStrut(2));
        info.add(badgeRow);

        if (fallbackActive) {
            JPanel fbStrip = new JPanel(new BorderLayout(4, 2));
            fbStrip.setBackground(Theme.warningSoft());
            fbStrip.setBorder(new CompoundBorder(
                    BorderFactory.createLineBorder(Theme.warning(), 1),
                    new EmptyBorder(3, 6, 3, 6)
            ));
            fbStrip.setAlignmentX(Component.LEFT_ALIGNMENT);
            JLabel fbLbl = new JLabel("<html>Đang dùng App Password dự phòng - Google cần cấp lại quyền</html>");
            fbLbl.setFont(Theme.fontTiny().deriveFont(Font.BOLD));
            fbLbl.setForeground(Theme.warning());
            JButton reBtn = new JButton("Cấp lại quyền");
            reBtn.setFont(Theme.fontTiny().deriveFont(Font.BOLD));
            reBtn.setForeground(Theme.primaryText());
            reBtn.setContentAreaFilled(false);
            reBtn.setBorderPainted(false);
            reBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            reBtn.addActionListener(e -> initiateOAuth2Flow());
            fbStrip.add(fbLbl, BorderLayout.CENTER);
            fbStrip.add(reBtn, BorderLayout.SOUTH);
            info.add(Box.createVerticalStrut(3));
            info.add(fbStrip);
        } else if (needsReauth) {
            JPanel warnRow = Theme.createWrapPanel(4, 2);
            warnRow.setAlignmentX(Component.LEFT_ALIGNMENT);
            warnRow.add(Theme.badge("Cần cấp lại quyền", Theme.dangerSoft(), Theme.danger()));
            JButton reBtn = new JButton("Cấp lại");
            reBtn.setFont(Theme.fontTiny().deriveFont(Font.BOLD));
            reBtn.setForeground(Theme.primaryText());
            reBtn.setContentAreaFilled(false);
            reBtn.setBorderPainted(false);
            reBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            reBtn.addActionListener(e -> initiateOAuth2Flow());
            warnRow.add(reBtn);
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
        this.selectedAccount = acc;
        this.selectedFolder = null;
        this.currentDetail = null;
        renderAccountCards();
        detailCardLayout.show(detailCardPanel, "EMPTY_DETAIL");

        // Nếu NEEDS_REAUTH nhưng có App Password dự phòng (hoặc đang fallbackActive), vẫn cho phép đọc/gửi thư qua dự phòng
        if ("NEEDS_REAUTH".equalsIgnoreCase(acc.status()) && !acc.hasAppPassword() && !acc.isFallbackActive()) {
            folderListModel.clear();
            mailListModel.clear();
            showEmptyListState("Tài khoản cần xác thực lại",
                    acc.email() + " đã hết hạn hoặc bị thu hồi quyền. Hãy cấp lại quyền để tiếp tục.",
                    "Cấp lại quyền (OAuth2)");
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
        if (selectedAccount == null) return;
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
                    }
                } catch (Exception ex) {
                    if (!isCancelled()) handleCommonError(ex, "Không tải được danh sách thư mục");
                }
            }
        };
        workerCoordinator.attachFoldersTask(worker);
        worker.execute();
    }

    private void loadMails(int page, boolean append) {
        if (selectedAccount == null || selectedFolder == null) return;
        final Long gid = selectedAccount.id();
        final String folderName = selectedFolder.name();
        final String query = searchField.getText().trim();
        final Boolean unreadOnly = unreadFilterBtn.isSelected() ? Boolean.TRUE : null;
        final Boolean hasAttachOnly = attachFilterBtn.isSelected() ? Boolean.TRUE : null;
        final long gen = workerCoordinator.beginMailsLoad();

        if (!append) {
            listCardLayout.show(listCardPanel, "LOADING");
        } else {
            loadMoreBtn.setEnabled(false);
            loadMoreBtn.setText("Đang tải...");
        }
        setStatus("Đang tải thư trong " + selectedFolder.displayName() + "...");

        SwingWorker<MailListResponse, Void> worker = new SwingWorker<>() {
            @Override protected MailListResponse doInBackground() throws Exception {
                return apiClient.getMails(gid, folderName, page, 30, query, unreadOnly, hasAttachOnly, null, null);
            }
            @Override protected void done() {
                if (isCancelled() || !workerCoordinator.isMailsCurrent(gen)) return;
                loadMoreBtn.setEnabled(true);
                loadMoreBtn.setText("Tải thêm...");
                try {
                    MailListResponse resp = get();
                    List<MailSummaryDto> existingSnapshot = new ArrayList<>(mailListModel.size());
                    for (int i = 0; i < mailListModel.size(); i++) {
                        existingSnapshot.add(mailListModel.get(i));
                    }
                    MailboxViewModel.PageMergeResult merged = MailboxViewModel.mergeMailPage(
                            existingSnapshot, currentUidValidity, resp, append);
                    // Nếu hộp thư đổi UIDVALIDITY giữa lúc tải thêm -> đồng bộ lại từ trang 0
                    if (merged.uidValidityChanged()) {
                        loadMails(0, false);
                        return;
                    }
                    currentPage = resp.page();
                    totalMails = resp.total();
                    hasMoreMails = resp.hasMore();
                    currentUidValidity = merged.uidValidity();

                    mailListModel.clear();
                    for (MailSummaryDto item : merged.items()) {
                        mailListModel.addElement(item);
                    }

                    if (mailListModel.isEmpty()) {
                        showEmptyListState("Không có thư nào",
                                "Thư mục \"" + selectedFolder.displayName() + "\" hiện trống hoặc không khớp bộ lọc.",
                                "Làm mới");
                    } else {
                        listCardLayout.show(listCardPanel, "LIST");
                    }

                    mailCountLabel.setText("Hiển thị " + mailListModel.size() + " / " + totalMails + " thư");
                    loadMoreBtn.setVisible(hasMoreMails);
                    setStatus("Đã tải " + mailListModel.size() + " / " + totalMails + " thư (" + selectedFolder.displayName() + ").");
                } catch (Exception ex) {
                    if (!isCancelled()) {
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
        if (selectedAccount == null || selectedFolder == null || summary == null) return;
        final Long gid = selectedAccount.id();
        final String folderName = selectedFolder.name();
        final long gen = workerCoordinator.beginDetailLoad();
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
                    displayMailDetail(detail);
                    updateListItemSeenFlag(summary.uid(), true);
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

        detailSubjectLabel.setText((detail.subject() != null && !detail.subject().isBlank()) ? detail.subject() : "(Không có tiêu đề)");

        String senderName = detail.from() != null ? detail.from().displayText() : "(Không rõ người gửi)";
        String senderAddr = detail.from() != null ? detail.from().address() : "";
        detailAvatarLabel.setIcon(AvatarUtil.createAvatarIcon(senderName, senderAddr, 40));
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
        starActionBtn.setText(starred ? "Bỏ sao" : "Gắn sao");
        starActionBtn.setIcon(AppIcons.vectorIcon(starred ? AppIcons.Glyph.STAR_FILLED : AppIcons.Glyph.STAR, 14, Theme.starGold()));

        // Mặc định ưu tiên HTML nếu có, không thì văn bản thuần
        showHtmlMode = detail.htmlBody() != null && !detail.htmlBody().isBlank();
        htmlToggleBtn.setSelected(showHtmlMode);
        textToggleBtn.setSelected(!showHtmlMode);
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

        JLabel name = new JLabel(att.filename() + " (" + ComposeDialog.formatBytes(att.size()) + ")");
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
        if (selectedAccount == null || selectedFolder == null) return;
        File baseDir = new File(ClientConfig.getInstance().getDownloadDir());
        Path targetDir = AttachmentCleanupService.buildTargetDirectory(baseDir, selectedAccount.email(), LocalDate.now());
        String safeName = AttachmentCleanupService.sanitizeFilename(att.filename());

        File targetFile;
        if (!openAfter) {
            JFileChooser fc = new JFileChooser(targetDir.toFile());
            fc.setSelectedFile(new File(targetDir.toFile(), safeName));
            if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
            targetFile = fc.getSelectedFile();
        } else {
            targetDir.toFile().mkdirs();
            targetFile = AttachmentCleanupService.resolveUniqueFilePath(targetDir, safeName).toFile();
        }

        final Long gid = selectedAccount.id();
        final String folderName = selectedFolder.name();
        setStatus("Đang tải tệp " + att.filename() + "...");

        new SwingWorker<ApiClient.DownloadResult, Void>() {
            @Override protected ApiClient.DownloadResult doInBackground() throws Exception {
                targetFile.getParentFile().mkdirs();
                return apiClient.downloadAttachmentToFile(gid, folderName, detail.uid(), att.index(), detail.uidValidity(), targetFile);
            }
            @Override protected void done() {
                try {
                    ApiClient.DownloadResult res = get();
                    setStatus("Đã tải xong " + targetFile.getName() + " (" + ComposeDialog.formatBytes(res.bytesWritten()) + ")");
                    if (openAfter) {
                        if (ClientSafetyUtil.isDangerousExecutableExtension(targetFile.getName())) {
                            int warn = JOptionPane.showConfirmDialog(
                                    MainFrame.this,
                                    "CẢNH BÁO BẢO MẬT: Tệp '" + targetFile.getName()
                                            + "' có định dạng có thể thực thi mã độc trên máy tính.\n"
                                            + "Tệp đã được lưu an toàn tại: " + targetFile.getAbsolutePath()
                                            + "\n\nBạn có CHẮC CHẮN muốn mở tệp này không?",
                                    "Cảnh báo tệp đính kèm nguy hiểm",
                                    JOptionPane.YES_NO_OPTION,
                                    JOptionPane.WARNING_MESSAGE
                            );
                            if (warn != JOptionPane.YES_OPTION) {
                                return;
                            }
                        }
                        if (Desktop.isDesktopSupported()) {
                            Desktop.getDesktop().open(targetFile);
                        }
                    } else {
                        JOptionPane.showMessageDialog(MainFrame.this,
                                "Đã lưu tệp thành công:\n" + targetFile.getAbsolutePath() + "\nSHA-256: " + res.sha256Hex().substring(0, 16) + "...",
                                "Tải tệp thành công", JOptionPane.INFORMATION_MESSAGE);
                    }
                } catch (Exception ex) {
                    handleCommonError(ex, "Lỗi tải tệp đính kèm");
                }
            }
        }.execute();
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
                    + "body{font-family:'Segoe UI',sans-serif;font-size:13px;padding:18px 22px;background:" + bgHex + ";color:" + fgHex + ";line-height:1.58;margin:0;}"
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
        dlg.setVisible(true);
    }

    private void toggleStarCurrentMail() {
        if (currentDetail == null || selectedAccount == null || selectedFolder == null) return;
        boolean newFlagged = !(currentDetail.flags() != null && currentDetail.flags().flagged());
        final Long gid = selectedAccount.id();
        final String folderName = selectedFolder.name();
        final long uid = currentDetail.uid();

        new SwingWorker<MailFlagsDto, Void>() {
            @Override protected MailFlagsDto doInBackground() throws Exception {
                return apiClient.updateMailFlags(gid, folderName, uid, null, newFlagged, currentDetail.uidValidity());
            }
            @Override protected void done() {
                try {
                    MailFlagsDto updated = get();
                    currentDetail = new MailDetailResponse(
                            currentDetail.uid(), currentDetail.uidValidity(), currentDetail.from(), currentDetail.to(),
                            currentDetail.cc(), currentDetail.bcc(), currentDetail.replyTo(), currentDetail.date(),
                            currentDetail.messageId(), currentDetail.inReplyTo(), currentDetail.references(),
                            currentDetail.subject(), currentDetail.textBody(), currentDetail.htmlBody(),
                            currentDetail.truncated(), currentDetail.hasBlockedRemoteImages(),
                            currentDetail.attachments(), updated);
                    starActionBtn.setText(updated.flagged() ? "Bỏ sao" : "Gắn sao");
                    starActionBtn.setIcon(AppIcons.vectorIcon(updated.flagged() ? AppIcons.Glyph.STAR_FILLED : AppIcons.Glyph.STAR, 14, Theme.starGold()));
                    updateListItemFlags(uid, updated);
                } catch (Exception ex) {
                    handleCommonError(ex, "Không cập nhật được trạng thái sao");
                }
            }
        }.execute();
    }

    private void markCurrentMailUnread() {
        if (currentDetail == null || selectedAccount == null || selectedFolder == null) return;
        final Long gid = selectedAccount.id();
        final String folderName = selectedFolder.name();
        final long uid = currentDetail.uid();

        new SwingWorker<MailFlagsDto, Void>() {
            @Override protected MailFlagsDto doInBackground() throws Exception {
                return apiClient.updateMailFlags(gid, folderName, uid, false, null, currentDetail.uidValidity());
            }
            @Override protected void done() {
                try {
                    MailFlagsDto updated = get();
                    updateListItemFlags(uid, updated);
                    setStatus("Đã đánh dấu thư là Chưa đọc.");
                } catch (Exception ex) {
                    handleCommonError(ex, "Không cập nhật được trạng thái đọc");
                }
            }
        }.execute();
    }

    private void deleteSelectedMails() {
        if (selectedAccount == null || selectedFolder == null) return;
        List<MailSummaryDto> selected = mailList.getSelectedValuesList();
        if (selected.isEmpty()) return;

        boolean inTrash = "TRASH".equalsIgnoreCase(selectedFolder.name());
        String msg = inTrash
                ? "Bạn có chắc chắn muốn XÓA VĨNH VIỄN " + selected.size() + " thư trong Thùng rác?"
                : "Chuyển " + selected.size() + " thư đã chọn vào Thùng rác?";
        if (JOptionPane.showConfirmDialog(this, msg, "Xác nhận xóa thư", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE)
                != JOptionPane.YES_OPTION) {
            return;
        }

        final Long gid = selectedAccount.id();
        final String folderName = selectedFolder.name();
        setStatus("Đang xóa " + selected.size() + " thư...");

        new SwingWorker<Integer, Void>() {
            @Override protected Integer doInBackground() throws Exception {
                int ok = 0;
                for (MailSummaryDto m : selected) {
                    apiClient.deleteMail(gid, folderName, m.uid(), inTrash, m.uidValidity());
                    ok++;
                }
                return ok;
            }
            @Override protected void done() {
                try {
                    int ok = get();
                    setStatus("Đã xóa " + ok + " thư.");
                    detailCardLayout.show(detailCardPanel, "EMPTY_DETAIL");
                    currentDetail = null;
                    loadMails(0, false);
                } catch (Exception ex) {
                    handleCommonError(ex, "Lỗi khi xóa thư");
                }
            }
        }.execute();
    }

    // ==================== CORE FEATURE: DOWNLOAD ATTACHMENTS THEN DELETE ====================

    private void openCleanupForSelectedMails() {
        if (currentGmailAccounts.isEmpty()) {
            int opt = JOptionPane.showConfirmDialog(this,
                    "Bạn chưa liên kết tài khoản Gmail nào.\nMở Quản lý Tài khoản Gmail để liên kết ngay?",
                    "Chưa có tài khoản Gmail", JOptionPane.YES_NO_OPTION, JOptionPane.INFORMATION_MESSAGE);
            if (opt == JOptionPane.YES_OPTION) {
                openGmailManagerDialog();
            }
            return;
        }
        if (selectedAccount == null || selectedFolder == null) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn tài khoản Gmail và thư mục trước.", "Thông báo", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        List<MailSummaryDto> targets = mailList.getSelectedValuesList();
        if (targets.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Hãy chọn một hoặc nhiều thư trong danh sách (giữ Ctrl/Shift để chọn nhiều).",
                    "Chưa chọn thư", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        CleanupPreviewDialog dlg = new CleanupPreviewDialog(
                this, cleanupService, selectedAccount.id(), selectedAccount.email(),
                selectedFolder.name(), targets, this::refreshCurrentMailbox);
        dlg.setVisible(true);
    }

    private void openCleanupForCurrentMail() {
        MailSummaryDto sel = mailList.getSelectedValue();
        if (sel == null) {
            openCleanupForSelectedMails();
            return;
        }
        CleanupPreviewDialog dlg = new CleanupPreviewDialog(
                this, cleanupService, selectedAccount.id(), selectedAccount.email(),
                selectedFolder.name(), List.of(sel), this::refreshCurrentMailbox);
        dlg.setVisible(true);
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
        if (mockupMode || selectedAccount == null || autoCleanRunning.get()) return;
        if ("NEEDS_REAUTH".equalsIgnoreCase(selectedAccount.status())) {
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

        new SwingWorker<List<MailCleanupResult>, String>() {
            @Override protected List<MailCleanupResult> doInBackground() throws Exception {
                MailListResponse list = apiClient.getMails(gid, "INBOX", 0, maxPerRun, null, null, true,
                        senderFilter.isBlank() ? null : senderFilter, null);
                List<MailSummaryDto> candidates = new ArrayList<>();
                Instant cutoff = minAgeDays > 0 ? Instant.now().minus(Duration.ofDays(minAgeDays)) : null;
                if (list.items() != null) {
                    for (MailSummaryDto m : list.items()) {
                        if (cutoff != null && m.date() != null) {
                            try {
                                if (Instant.parse(m.date()).isAfter(cutoff)) continue;
                            } catch (Exception ignored) {}
                        }
                        candidates.add(m);
                    }
                }
                return cleanupService.processMails(gid, email, "INBOX", candidates, baseDir,
                        false, dryRun, autoCleanCancelled, this::publish);
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
                    if (cleanupService.getConsecutiveErrors() >= 3) {
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
        ClientConfig.getInstance().setAutoCleanEnabled(false);
        configureAutoCleanTimer();
        setStatus(reason);
        JOptionPane.showMessageDialog(this, reason, "Tự động dọn đã tắt", JOptionPane.WARNING_MESSAGE);
    }

    // ==================== DIALOGS & OAUTH2 ====================

    private void openGmailManagerDialog() {
        GmailManagerDialog dlg = new GmailManagerDialog(
                this, apiClient, SessionStore.getInstance().getPublicConfig(),
                this::initiateOAuth2Flow, this::loadGmailAccounts);
        dlg.setVisible(true);
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
        fresh.setVisible(true);
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
        PublicConfigDto config = SessionStore.getInstance().getPublicConfig();
        if (config != null && !config.oauth2LinkEnabled()) {
            JOptionPane.showMessageDialog(this,
                    "Tính năng liên kết Google OAuth2 hiện đang bị tắt bởi Quản trị viên.",
                    "Tính năng bị tắt", JOptionPane.WARNING_MESSAGE);
            return;
        }

        JDialog pollDialog = new JDialog(this, "Liên Kết Google OAuth2 — " + AppIcons.BRAND_FULL, true);
        pollDialog.setIconImages(AppIcons.getWindowIcons());
        pollDialog.setSize(490, 210);
        pollDialog.setLocationRelativeTo(this);
        pollDialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

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
        cancelButton.addActionListener(e -> pollDialog.dispose());
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        btnPanel.setOpaque(false);
        btnPanel.add(cancelButton);

        dialogPanel.add(statusLabel, BorderLayout.NORTH);
        dialogPanel.add(progressBar, BorderLayout.CENTER);
        dialogPanel.add(btnPanel, BorderLayout.SOUTH);
        pollDialog.setContentPane(dialogPanel);

        final int initialCount = currentGmailAccounts.size();
        final Set<Long> initialActiveOAuthIds = new HashSet<>();
        for (GmailAccountDto acc : currentGmailAccounts) {
            if ("OAUTH2".equalsIgnoreCase(acc.authType()) && "ACTIVE".equalsIgnoreCase(acc.status())) {
                initialActiveOAuthIds.add(acc.id());
            }
        }
        final AtomicBoolean pollCancelled = new AtomicBoolean(false);

        SwingWorker<Void, Integer> oauthWorker = new SwingWorker<>() {
            @Override protected Void doInBackground() throws Exception {
                String authUrl = apiClient.getOAuth2AuthorizeUrl();
                boolean opened = false;
                try {
                    if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                        Desktop.getDesktop().browse(new URI(authUrl));
                        opened = true;
                    }
                } catch (Exception ignored) {}

                final boolean isOpened = opened;
                SwingUtilities.invokeLater(() -> statusLabel.setText(isOpened
                        ? "<html><b>Trình duyệt đã mở.</b><br>Hãy chọn tài khoản Google và giữ nguyên dấu tick quyền Gmail.</html>"
                        : "<html><b>Hãy mở liên kết sau bằng trình duyệt:</b><br><small>" + authUrl + "</small></html>"));

                MailboxViewModel.OAuthPollStatus status = MailboxViewModel.pollForOAuthAccount(
                        apiClient::getGmailAccounts,
                        initialCount,
                        initialActiveOAuthIds,
                        90,
                        2000L,
                        pollCancelled,
                        this::publish
                );

                if (status == MailboxViewModel.OAuthPollStatus.LINKED) {
                    SwingUtilities.invokeLater(() -> {
                        pollDialog.dispose();
                        loadGmailAccounts();
                        JOptionPane.showMessageDialog(MainFrame.this,
                                "Liên kết tài khoản Google OAuth2 thành công!",
                                "Thành công", JOptionPane.INFORMATION_MESSAGE);
                    });
                } else if (status == MailboxViewModel.OAuthPollStatus.TIMEOUT && pollDialog.isVisible()) {
                    SwingUtilities.invokeLater(() -> {
                        pollDialog.dispose();
                        JOptionPane.showMessageDialog(MainFrame.this,
                                "Hết thời gian chờ xác thực Google (3 phút). Vui lòng thử lại.",
                                "Hết thời gian", JOptionPane.WARNING_MESSAGE);
                    });
                }
                return null;
            }
            @Override protected void process(List<Integer> chunks) {
                int v = chunks.get(chunks.size() - 1);
                progressBar.setValue(v);
                progressBar.setString("Đang chờ xác nhận (" + (v * 2) + "s / 180s)...");
            }
            @Override protected void done() {
                if (isCancelled() || pollCancelled.get()) return;
                try {
                    get();
                } catch (Exception ex) {
                    if (isCancelled() || pollCancelled.get()) return;
                    pollDialog.dispose();
                    handleCommonError(ex, "Lỗi liên kết Google OAuth2");
                }
            }
        };
        cancelButton.addActionListener(e -> {
            pollCancelled.set(true);
            oauthWorker.cancel(true);
        });
        pollDialog.addWindowListener(new WindowAdapter() {
            @Override public void windowClosed(WindowEvent e) {
                pollCancelled.set(true);
                oauthWorker.cancel(true);
            }
        });
        oauthWorker.execute();

        pollDialog.setVisible(true);
    }

    private boolean hasNewActiveOAuth2(List<GmailAccountDto> list) {
        for (GmailAccountDto acc : list) {
            if ("OAUTH2".equalsIgnoreCase(acc.authType()) && "ACTIVE".equalsIgnoreCase(acc.status())) {
                boolean wasActive = currentGmailAccounts.stream()
                        .anyMatch(o -> Objects.equals(o.id(), acc.id()) && "ACTIVE".equalsIgnoreCase(o.status()));
                if (!wasActive) return true;
            }
        }
        return false;
    }

    private void refreshCurrentMailbox() {
        if (selectedAccount == null) {
            loadGmailAccounts();
        } else {
            // Refresh chỉ cần tải lại danh sách thư. Gọi loadFolders() ở đây từng gây
            // thêm một request IMAP đắt đỏ (~8s với Gmail) cho mỗi lần bấm Làm mới.
            loadMails(0, false);
        }
    }

    private void handleLogout() {
        if (JOptionPane.showConfirmDialog(this, "Bạn có chắc chắn muốn đăng xuất?",
                "Đăng xuất", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
            cancelAllBackgroundTasks();
            SessionStore.getInstance().clearSession();
            apiClient.clearAccessToken();
            new LoginFrame(apiClient).setVisible(true);
            dispose();
        }
    }

    private void handleSessionExpired() {
        cancelAllBackgroundTasks();
        JOptionPane.showMessageDialog(this, "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.",
                "Hết phiên đăng nhập", JOptionPane.WARNING_MESSAGE);
        SessionStore.getInstance().clearSession();
        apiClient.clearAccessToken();
        new LoginFrame(apiClient).setVisible(true);
        dispose();
    }

    private void handleCommonError(Exception ex, String fallbackTitle) {
        Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
        if (cause instanceof ApiException apiEx) {
            if (apiEx.getStatusCode() == 401) {
                handleSessionExpired();
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
        }
        setStatus(fallbackTitle + ": " + extractMsg(ex));
    }

    private void showEmptyListState(String title, String sub, String ctaText) {
        listEmptyTitle.setText(title);
        listEmptySub.setText("<html><div style='text-align:center;width:210px;'>" + sub + "</div></html>");
        listEmptyActionBtn.setText(ctaText);
        for (ActionListener al : listEmptyActionBtn.getActionListeners()) {
            listEmptyActionBtn.removeActionListener(al);
        }
        listEmptyActionBtn.addActionListener(e -> {
            if (ctaText.contains("Cấp lại")) initiateOAuth2Flow();
            else if (ctaText.contains("Liên kết") || ctaText.contains("Quản lý")) openGmailManagerDialog();
            else refreshCurrentMailbox();
        });
        listCardLayout.show(listCardPanel, "EMPTY");
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
        mailListModel.clear();
        for (MailSummaryDto m : mails) mailListModel.addElement(m);
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

            JPanel topRow = new JPanel(new BorderLayout(Theme.SP_1, 0));
            topRow.setOpaque(false);
            topRow.setAlignmentX(Component.LEFT_ALIGNMENT);
            topRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
            JPanel rightTop = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
            rightTop.setOpaque(false);
            rightTop.add(iconsLbl);
            rightTop.add(dateLbl);
            topRow.add(senderLbl, BorderLayout.CENTER);
            topRow.add(rightTop, BorderLayout.EAST);

            subjectLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
            subjectLbl.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
            snippetLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
            snippetLbl.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));

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
            if (starred) {
                iconsLbl.setIcon(AppIcons.vectorIcon(AppIcons.Glyph.STAR_FILLED, 13, Theme.starGold()));
            } else if (m.hasAttachment()) {
                iconsLbl.setIcon(AppIcons.vectorIcon(AppIcons.Glyph.ATTACHMENT, 13, Theme.textSecondary()));
            }

            String rawSubj = (m.subject() != null && !m.subject().isBlank()) ? cleanDisplayText(m.subject()) : "(Không có tiêu đề)";
            subjectLbl.setText(rawSubj.isEmpty() ? "(Không có tiêu đề)" : rawSubj);
            subjectLbl.setFont(isUnreadRow ? Theme.fontBold() : Theme.fontSmall());
            subjectLbl.setForeground(isUnreadRow ? Theme.textPrimary() : Theme.textSecondary());

            String snip = (m.snippet() != null && !m.snippet().isBlank())
                    ? cleanDisplayText(m.snippet())
                    : (m.hasAttachment() ? "Có tệp đính kèm • " + ComposeDialog.formatBytes(m.size()) : ComposeDialog.formatBytes(m.size()));
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
