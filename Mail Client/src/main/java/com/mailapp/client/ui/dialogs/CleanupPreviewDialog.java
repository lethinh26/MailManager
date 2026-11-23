package com.mailapp.client.ui.dialogs;

import com.mailapp.client.model.MailClientDtos.MailSummaryDto;
import com.mailapp.client.service.AttachmentCleanupService;
import com.mailapp.client.service.AttachmentCleanupService.MailCleanupResult;
import com.mailapp.client.ui.theme.AppIcons;
import com.mailapp.client.ui.theme.Theme;
import com.mailapp.client.util.ClientConfig;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.File;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

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
        super(owner, "Tải đính kèm & Xóa thư — " + AppIcons.BRAND_FULL, true);
        this.cleanupService = cleanupService;
        this.gmailId = gmailId;
        this.accountEmail = accountEmail;
        this.folder = folder;
        this.targets = targets;
        this.onFinishedCallback = onFinishedCallback;

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
        long totalEstBytes = targets.stream().mapToLong(MailSummaryDto::size).sum();
        File baseDir = new File(ClientConfig.getInstance().getDownloadDir());
        String destPreview = AttachmentCleanupService.buildTargetDirectory(baseDir, accountEmail, java.time.LocalDate.now()).toString();

        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));

        JLabel title = new JLabel("Xem trước: " + targets.size() + " thư được chọn");
        title.setFont(Theme.fontTitle());
        title.setForeground(Theme.textPrimary());

        JLabel info = new JLabel("Thư mục đích: " + destPreview + "   •   Tổng dung lượng ước tính: " + ComposeDialog.formatBytes(totalEstBytes));
        info.setFont(Theme.fontSmall());
        info.setForeground(Theme.textSecondary());

        JLabel guarantee = new JLabel("Cam kết an toàn: CHỈ xóa thư sau khi TẤT CẢ tệp đính kèm tải xuống & xác minh dung lượng + SHA-256 thành công.");
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
                    i + 1, m.uid(), m.subject(), ComposeDialog.formatBytes(m.size()), m.hasAttachment() ? "Có" : "Không"));
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
        cancelBtn.addActionListener(e -> {
            cancelled.set(true);
            dispose();
        });

        startBtn = Theme.primaryButton("Bắt đầu Tải & Xóa", AppIcons.vectorIcon(AppIcons.Glyph.DOWNLOAD, 16, Color.WHITE));
        startBtn.addActionListener(e -> runCleanup());

        bottom.add(cancelBtn);
        bottom.add(startBtn);
        root.add(bottom, BorderLayout.SOUTH);

        setContentPane(root);
    }

    private void runCleanup() {
        boolean permanent = permanentCheck.isSelected();
        boolean dryRun = dryRunCheck.isSelected();

        if (permanent && !dryRun) {
            int ok = JOptionPane.showConfirmDialog(this,
                    "CẢNH BÁO: Bạn đang chọn XÓA VĨNH VIỄN sau khi tải đính kèm.\nThư sẽ không thể khôi phục. Tiếp tục?",
                    "Xác nhận xóa vĩnh viễn", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (ok != JOptionPane.YES_OPTION) return;
        }

        cancelled.set(false);
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
                        permanent, dryRun, cancelled, this::publish);
            }

            @Override
            protected void process(List<String> chunks) {
                for (String msg : chunks) {
                    logArea.append("• " + msg + "\n");
                }
            }

            @Override
            protected void done() {
                progressBar.setIndeterminate(false);
                progressBar.setValue(100);
                cancelBtn.setText("Đóng");
                try {
                    List<MailCleanupResult> results = get();
                    logArea.append("\n=== KẾT QUẢ TỪNG THƯ ===\n");
                    int okCount = 0, skipCount = 0, errCount = 0;
                    for (MailCleanupResult r : results) {
                        logArea.append(String.format("[%s] UID %d — %s\n   -> %s\n",
                                r.status(), r.uid(), r.subject(), r.message()));
                        if (r.status() == AttachmentCleanupService.ItemStatus.SUCCESS || r.status() == AttachmentCleanupService.ItemStatus.DRY_RUN) okCount++;
                        else if (r.status() == AttachmentCleanupService.ItemStatus.SKIPPED) skipCount++;
                        else errCount++;
                    }
                    logArea.append(String.format("\nTỔNG KẾT: Thành công=%d | Bỏ qua=%d | Lỗi/Hủy=%d\n", okCount, skipCount, errCount));
                    if (onFinishedCallback != null && !dryRun && okCount > 0) {
                        onFinishedCallback.run();
                    }
                } catch (Exception ex) {
                    logArea.append("LỖI: " + ex.getMessage() + "\n");
                }
            }
        };
        worker.execute();
    }
}

