package com.mailapp.client.ui.dialogs;

import com.mailapp.client.model.MailClientDtos.MailSummaryDto;
import com.mailapp.client.service.AttachmentCleanupService;
import com.mailapp.client.service.AttachmentCleanupService.MailCleanupResult;
import com.mailapp.client.ui.theme.AppIcons;
import com.mailapp.client.ui.theme.Theme;
import com.mailapp.client.util.ClientConfig;
import com.mailapp.client.util.MailSizeDisplay;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

/**
 * Hộp thoại xem trước, thực thi và ghi nhật ký kết quả cho tính năng:
 * "Tải đính kèm & Xóa thư" (chỉ xóa khi mọi tệp tải & xác minh thành công).
 */
public class CleanupPreviewDialog extends JDialog {

    private final AttachmentCleanupService cleanupService;
    private final Long gmailId;
    private final String accountEmail;
    private final String folder;
    private final List<MailSummaryDto> targets;
    private final Runnable onFinishedCallback;
    private final BooleanSupplier stillCurrent;
    private volatile boolean disposed;
    private boolean running;

    private JCheckBox permanentCheck;
    private JCheckBox dryRunCheck;
    private JTextArea logArea;
    private JProgressBar progressBar;
    private JButton startBtn;
    private JButton cancelBtn;
    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    public CleanupPreviewDialog(Frame owner,
                                AttachmentCleanupService cleanupService,
                                Long gmailId,
                                String accountEmail,
                                String folder,
                                List<MailSummaryDto> targets,
                                Runnable onFinishedCallback) {
        this(owner, cleanupService, gmailId, accountEmail, folder, targets, onFinishedCallback, () -> true);
    }

    public CleanupPreviewDialog(Frame owner,
                                AttachmentCleanupService cleanupService,
                                Long gmailId,
                                String accountEmail,
                                String folder,
                                List<MailSummaryDto> targets,
                                Runnable onFinishedCallback,
                                BooleanSupplier stillCurrent) {
        super(owner, "Tải đính kèm & Xóa thư — " + AppIcons.BRAND_FULL, true);
        this.cleanupService = cleanupService;
        this.gmailId = gmailId;
        this.accountEmail = accountEmail;
        this.folder = folder;
        this.targets = List.copyOf(targets);
        this.onFinishedCallback = onFinishedCallback;
        this.stillCurrent = stillCurrent;

        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                stopOrClose();
            }
        });
        setIconImages(AppIcons.getWindowIcons());
        setSize(640, 480);
        setLocationRelativeTo(owner);
        initUI();
    }

    private void initUI() {
        JPanel root = new JPanel(new BorderLayout(0, Theme.SP_3));
        root.setBackground(Theme.bgSurface());
        root.setBorder(new EmptyBorder(Theme.SP_4, Theme.SP_4, Theme.SP_4, Theme.SP_4));

        // === Phần xem trước ===
        File baseDir = new File(ClientConfig.getInstance().getDownloadDir());
        String destPreview = AttachmentCleanupService.buildTargetDirectory(baseDir, accountEmail, java.time.LocalDate.now()).toString();

        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));

        JLabel title = new JLabel("Xem trước: " + targets.size() + " thư được chọn");
        title.setFont(Theme.fontTitle());
        title.setForeground(Theme.textPrimary());

        JLabel info = new JLabel("Thư mục đích: " + destPreview);
        info.setFont(Theme.fontSmall());
        info.setForeground(Theme.textSecondary());

        JLabel estimate = new JLabel("Tổng dung lượng thư (IMAP, không phải số byte tệp tải): " + MailSizeDisplay.messageTotal(targets));
        estimate.setFont(Theme.fontSmall());
        estimate.setForeground(Theme.textSecondary());

        JLabel guarantee = new JLabel("Chỉ xóa sau khi tải đủ tệp, kiểm tra số byte thực nhận/ghi và tính SHA-256.");
        guarantee.setFont(Theme.fontTiny());
        guarantee.setForeground(Theme.success());

        JPanel options = new JPanel(new FlowLayout(FlowLayout.LEFT, Theme.SP_3, 4));
        options.setOpaque(false);
        dryRunCheck = new JCheckBox("Chạy thử (chỉ báo cáo, không tải/xóa)", false);
        dryRunCheck.setOpaque(false);
        dryRunCheck.setFont(Theme.fontSmall());

        boolean inTrash = "TRASH".equalsIgnoreCase(folder);
        permanentCheck = new JCheckBox("Xóa vĩnh viễn (chỉ khả dụng trong Thùng rác)", false);
        permanentCheck.setOpaque(false);
        permanentCheck.setFont(Theme.fontSmall());
        permanentCheck.setEnabled(inTrash);
        if (!inTrash) {
            permanentCheck.setToolTipText("Mặc định chuyển thư vào Thùng rác. Xóa vĩnh viễn chỉ được phép khi thư đã nằm trong Thùng rác.");
        }

        options.add(dryRunCheck);
        options.add(permanentCheck);

        header.add(title);
        header.add(Box.createVerticalStrut(4));
        header.add(info);
        header.add(estimate);
        header.add(Box.createVerticalStrut(4));
        header.add(guarantee);
        header.add(Box.createVerticalStrut(6));
        header.add(options);
        root.add(header, BorderLayout.NORTH);

        // === Danh sách xem trước + Nhật ký kết quả ===
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        logArea.setBackground(Theme.bgSurfaceAlt());
        logArea.setForeground(Theme.textPrimary());
        StringBuilder preview = new StringBuilder("=== DANH SÁCH THƯ XEM TRƯỚC ===\n");
        for (int i = 0; i < targets.size(); i++) {
            MailSummaryDto m = targets.get(i);
            preview.append(String.format("%d. [UID %d] %s (%s, đính kèm: %s)\n",
                    i + 1, m.uid(), m.subject(), MailSizeDisplay.message(m.size()), m.hasAttachment() ? "Có" : "Không"));
        }
        logArea.setText(preview.toString());

        JPanel center = new JPanel(new BorderLayout(0, Theme.SP_2));
        center.setOpaque(false);
        progressBar = new JProgressBar();
        progressBar.setStringPainted(true);
        progressBar.setVisible(false);
        center.add(progressBar, BorderLayout.NORTH);
        center.add(new JScrollPane(logArea), BorderLayout.CENTER);
        root.add(center, BorderLayout.CENTER);

        // === Nút điều khiển ===
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT, Theme.SP_2, 0));
        bottom.setOpaque(false);
        cancelBtn = Theme.secondaryButton("Đóng", null);
        cancelBtn.addActionListener(e -> stopOrClose());

        startBtn = Theme.primaryButton("Bắt đầu Tải & Xóa", AppIcons.vectorIcon(AppIcons.Glyph.DOWNLOAD, 16, Color.WHITE));
        startBtn.addActionListener(e -> runCleanup());

        bottom.add(cancelBtn);
        bottom.add(startBtn);
        root.add(bottom, BorderLayout.SOUTH);

        setContentPane(root);
    }

    private void stopOrClose() {
        if (!running) {
            dispose();
            return;
        }
        cancelled.set(true);
        cancelBtn.setEnabled(false);
        logArea.append("\nĐang dừng; chờ kết quả thao tác đang thực hiện. Không gửi yêu cầu xóa tiếp theo.\n");
    }

    @Override
    public void dispose() {
        disposed = true;
        cancelled.set(true);
        super.dispose();
    }

    private void runCleanup() {
        if (disposed || running || !stillCurrent.getAsBoolean()) return;
        boolean permanent = permanentCheck.isSelected();
        boolean dryRun = dryRunCheck.isSelected();

        if (permanent && !dryRun) {
            int ok = JOptionPane.showConfirmDialog(this,
                    "CẢNH BÁO: Bạn đang chọn XÓA VĨNH VIỄN sau khi tải đính kèm.\nThư sẽ không thể khôi phục. Tiếp tục?",
                    "Xác nhận xóa vĩnh viễn", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (ok != JOptionPane.YES_OPTION) return;
        }

        if (disposed || running || !stillCurrent.getAsBoolean()) return;
        cancelled.set(false);
        running = true;
        startBtn.setEnabled(false);
        dryRunCheck.setEnabled(false);
        permanentCheck.setEnabled(false);
        cancelBtn.setText("Dừng giữa chừng");
        progressBar.setVisible(true);
        progressBar.setIndeterminate(true);
        logArea.append("\n=== BẮT ĐẦU THỰC THI ===\n");

        File baseDir = new File(ClientConfig.getInstance().getDownloadDir());

        SwingWorker<List<MailCleanupResult>, String> worker = new SwingWorker<>() {
            @Override
            protected List<MailCleanupResult> doInBackground() {
                return cleanupService.processMails(
                        gmailId, accountEmail, folder, targets, baseDir,
                        permanent, dryRun, cancelled, this::publish,
                        () -> !disposed && stillCurrent.getAsBoolean());
            }

            @Override
            protected void process(List<String> chunks) {
                if (disposed || !stillCurrent.getAsBoolean()) return;
                for (String msg : chunks) {
                    logArea.append("• " + msg + "\n");
                }
            }

            @Override
            protected void done() {
                running = false;
                try {
                    List<MailCleanupResult> results = get();
                    boolean uncertain = results.stream().anyMatch(r -> r.status() == AttachmentCleanupService.ItemStatus.DELETE_UNCERTAIN);
                    if (uncertain) ClientConfig.getInstance().setAutoCleanEnabled(false);
                    if (disposed) return;
                    progressBar.setIndeterminate(false);
                    progressBar.setValue(100);
                    cancelBtn.setText("Đóng");
                    cancelBtn.setEnabled(true);
                    if (!stillCurrent.getAsBoolean()) {
                        logArea.setText("Phiên hoặc lựa chọn đã thay đổi. Đã dừng lô; không tự thử lại.");
                        return;
                    }
                    logArea.append("\n=== KẾT QUẢ TỪNG THƯ ===\n");
                    int okCount = 0, skipCount = 0, errCount = 0;
                    for (MailCleanupResult r : results) {
                        logArea.append(String.format("[%s] UID %d — %s\n   -> %s\n",
                                r.status(), r.uid(), r.subject(), r.message()));
                        for (var file : r.downloadedFiles()) {
                            String size = MailSizeDisplay.attachment(file.bytes(), file.sizeAccuracy());
                            logArea.append("      " + file.filename() + " — " + size
                                    + (r.status() == AttachmentCleanupService.ItemStatus.DRY_RUN
                                    ? " (metadata, chưa tải)" : " (đã tải)") + "\n");
                        }
                        if (r.status() == AttachmentCleanupService.ItemStatus.SUCCESS || r.status() == AttachmentCleanupService.ItemStatus.DRY_RUN) okCount++;
                        else if (r.status() == AttachmentCleanupService.ItemStatus.SKIPPED) skipCount++;
                        else errCount++;
                    }
                    logArea.append(String.format("\nTỔNG KẾT: Thành công=%d | Bỏ qua=%d | Lỗi/Hủy=%d\n", okCount, skipCount, errCount));
                    if (uncertain) {
                        logArea.append("\nĐã tắt tự động dọn. Kiểm tra thư mục nguồn và Thùng rác trước khi thao tác lại.\n");
                    }
                    if (onFinishedCallback != null && !dryRun && (okCount > 0 || uncertain)) {
                        onFinishedCallback.run();
                    }
                } catch (Exception ex) {
                    if (!disposed) {
                        progressBar.setIndeterminate(false);
                        cancelBtn.setText("Đóng");
                        cancelBtn.setEnabled(true);
                        logArea.append("Không nhận được kết quả lô. Kiểm tra thư mục nguồn trước khi thao tác lại.\n");
                    }
                }
            }
        };
        worker.execute();
    }
}

