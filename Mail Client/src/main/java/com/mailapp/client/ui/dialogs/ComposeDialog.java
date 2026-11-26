package com.mailapp.client.ui.dialogs;

import com.mailapp.client.model.GmailAccountDto;
import com.mailapp.client.model.MailClientDtos.*;
import com.mailapp.client.model.PublicConfigDto;
import com.mailapp.client.net.ApiClient;
import com.mailapp.client.net.ApiException;
import com.mailapp.client.ui.theme.AppIcons;
import com.mailapp.client.ui.theme.Theme;
import com.mailapp.client.util.ClientConfig;
import com.mailapp.client.util.ComposeValidation;
import com.mailapp.client.util.MailSizeDisplay;
import com.mailapp.client.util.SessionStore;
import com.mailapp.client.util.ClientSafetyUtil;
import com.mailapp.client.util.FeatureAdmission;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.event.ActionEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Hộp thoại Soạn thư (Compose / Reply / Reply All / Forward) thuần Swing:
 * - Nhập Đến / Cc / Bcc dạng chip có kiểm tra định dạng email.
 * - Soạn Văn bản hoặc HTML đơn giản (Đậm / Nghiêng / Chèn liên kết).
 * - Chọn tài khoản Gmail gửi đi.
 * - Kéo-thả & chọn tệp đính kèm, hiển thị tổng dung lượng và chặn vượt MAX_ATTACHMENT_SIZE_MB.
 * - Nhắc xác nhận khi đóng mà chưa gửi; gửi bất đồng bộ bằng SwingWorker.
 */
public class ComposeDialog extends JDialog {

    private final ApiClient apiClient;
    private final List<GmailAccountDto> accounts;
    private int maxAttachmentMb;
    private final FeatureAdmission sendAdmission;
    private boolean sending;
    private boolean uncertainSend;
    private final List<AbstractButton> editingButtons = new ArrayList<>();
    private static final String UNCERTAIN_SEND_MESSAGE =
            "Chưa xác định được kết quả gửi. Hãy kiểm tra thư đã gửi trước khi gửi lại.";
    private volatile boolean disposed;
    private final Runnable onSentCallback;
    private Runnable onSessionExpired;
    private final long dialogGeneration;

    private JComboBox<GmailAccountDto> fromCombo;
    private EmailChipInput toInput;
    private EmailChipInput ccInput;
    private EmailChipInput bccInput;
    private JTextField subjectField;
    private JToggleButton htmlModeToggle;
    private JTextArea bodyArea;
    private JPanel attachmentChipsPanel;
    private JLabel attachSummaryLabel;
    private JLabel statusLabel;
    private JProgressBar progressBar;
    private JButton sendBtn;

    private final List<File> attachmentFiles = new ArrayList<>();
    private final java.util.Map<File, ComposeValidation.AttachmentStamp> selectedStamps = new java.util.HashMap<>();
    private String inReplyTo;
    private String references;
    private boolean sentSuccessfully = false;

    public ComposeDialog(Frame owner,
                         ApiClient apiClient,
                         List<GmailAccountDto> accounts,
                         GmailAccountDto defaultAccount,
                         PublicConfigDto publicConfig,
                         Runnable onSentCallback) {
        super(owner, "Soạn thư mới — " + AppIcons.BRAND_FULL, true);
        this.apiClient = apiClient;
        this.dialogGeneration = SessionStore.getInstance().getGeneration();
        this.accounts = accounts != null ? accounts : List.of();
        this.maxAttachmentMb = (publicConfig != null && publicConfig.maxAttachmentSizeMb() > 0)
                ? publicConfig.maxAttachmentSizeMb() : 25;
        this.sendAdmission = new FeatureAdmission(publicConfig != null && publicConfig.sendMailEnabled());
        this.onSentCallback = onSentCallback;

        setIconImages(AppIcons.getWindowIcons());
        setSize(760, 620);
        setMinimumSize(new Dimension(640, 500));
        setLocationRelativeTo(owner);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                confirmClose();
            }
        });

        initUI(defaultAccount);
    }

    public void applyPublicConfig(PublicConfigDto config) {
        if (!currentDialog()) {
            sendBtn.setEnabled(false);
            return;
        }
        boolean wasAllowed = sendAdmission.isAllowed();
        sendAdmission.update(config != null && config.sendMailEnabled());
        if (config != null && config.maxAttachmentSizeMb() > 0 && config.maxAttachmentSizeMb() != maxAttachmentMb) {
            maxAttachmentMb = config.maxAttachmentSizeMb();
            refreshAttachmentChips();
        }
        sendBtn.setEnabled(sendAdmission.isAllowed() && !sending && !sentSuccessfully && fromCombo.getSelectedItem() != null);
        if (!sending) {
            if (!sendAdmission.isAllowed()) {
                statusLabel.setForeground(Theme.warning());
                statusLabel.setText(config == null ? "Chưa tải được cấu hình. Hãy kiểm tra kết nối."
                        : "Tính năng bị tắt bởi Admin");
            } else if (!wasAllowed) {
                statusLabel.setForeground(uncertainSend ? Theme.warning() : Theme.textSecondary());
                statusLabel.setText(uncertainSend ? UNCERTAIN_SEND_MESSAGE : " ");
            }
        }
    }

    /**
     * Tự điền nội dung cho Trả lời / Trả lời tất cả / Chuyển tiếp.
     */
    public void prefillReplyOrForward(MailDetailResponse original, String mode) {
        if (original == null || !currentDialog() || sending) return;
        String origSubj = original.subject() != null ? original.subject() : "";
        String quotedText = ComposeValidation.quoteText(original);

        if ("REPLY".equals(mode) || "REPLY_ALL".equals(mode)) {
            setTitle(("REPLY_ALL".equals(mode) ? "Trả lời tất cả" : "Trả lời") + " — " + AppIcons.BRAND_FULL);
            GmailAccountDto current = (GmailAccountDto) fromCombo.getSelectedItem();
            var plan = ComposeValidation.replyPlan(original, current != null ? current.email() : null, "REPLY_ALL".equals(mode));
            plan.to().forEach(toInput::addChip);
            plan.cc().forEach(ccInput::addChip);
            subjectField.setText(origSubj.toLowerCase(java.util.Locale.ROOT).startsWith("re:") ? origSubj : "Re: " + origSubj);
            this.inReplyTo = plan.inReplyTo();
            this.references = plan.references();
            bodyArea.setText("\n\n--- Vào lúc " + (original.date() != null ? original.date() : "")
                    + ", " + (original.from() != null ? original.from().displayText() : "") + " đã viết ---\n> "
                    + quotedText.replace("\n", "\n> "));
            bodyArea.setCaretPosition(0);
        } else if ("FORWARD".equals(mode)) {
            setTitle("Chuyển tiếp thư — " + AppIcons.BRAND_FULL);
            subjectField.setText(origSubj.toLowerCase(java.util.Locale.ROOT).startsWith("fwd:") ? origSubj : "Fwd: " + origSubj);
            bodyArea.setText("\n\n---------- Thư đã chuyển tiếp ----------\nTừ: "
                    + (original.from() != null ? original.from().displayText() : "")
                    + "\nNgày: " + (original.date() != null ? original.date() : "")
                    + "\nTiêu đề: " + origSubj + "\n\n"
                    + quotedText);
            bodyArea.setCaretPosition(0);
        }
    }

    private void initUI(GmailAccountDto defaultAccount) {
        JPanel outer = new JPanel(new BorderLayout());
        outer.setBackground(Theme.bgApp());
        if (ClientConfig.getInstance().isInsecureMode()) {
            outer.add(ClientConfig.createInsecureBanner(), BorderLayout.NORTH);
        }

        JPanel main = new JPanel(new BorderLayout(0, Theme.SP_3));
        main.setBackground(Theme.bgSurface());
        main.setBorder(new EmptyBorder(Theme.SP_4, Theme.SP_4, Theme.SP_3, Theme.SP_4));

        // === Header fields ===
        JPanel header = new JPanel(new GridBagLayout());
        header.setOpaque(false);
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(4, 4, 4, 4);
        g.fill = GridBagConstraints.HORIZONTAL;

        fromCombo = new JComboBox<>(accounts.toArray(new GmailAccountDto[0]));
        fromCombo.setFont(Theme.fontRegular());
        fromCombo.setRenderer((list, val, idx, sel, foc) -> {
            JLabel lbl = new JLabel(val != null ? (val.displayName() + " <" + val.email() + ">") : "(Chưa có Gmail)");
            lbl.setFont(Theme.fontRegular());
            lbl.setBorder(new EmptyBorder(4, 8, 4, 8));
            lbl.setOpaque(true);
            lbl.setBackground(sel ? Theme.bgSelected() : Theme.bgSurface());
            lbl.setForeground(Theme.textPrimary());
            return lbl;
        });
        if (defaultAccount != null) {
            for (int i = 0; i < accounts.size(); i++) {
                if (accounts.get(i).id().equals(defaultAccount.id())) {
                    fromCombo.setSelectedIndex(i);
                    break;
                }
            }
        }

        toInput = new EmailChipInput("Nhập email rồi nhấn Enter hoặc dấu phẩy...");
        ccInput = new EmailChipInput("Cc (tùy chọn)...");
        bccInput = new EmailChipInput("Bcc (tùy chọn)...");
        subjectField = new JTextField();
        Theme.styleTextField(subjectField);

        addRow(header, g, 0, "Từ:", fromCombo);
        addRow(header, g, 1, "Đến (*):", toInput);
        addRow(header, g, 2, "Cc:", ccInput);
        addRow(header, g, 3, "Bcc:", bccInput);
        addRow(header, g, 4, "Tiêu đề:", subjectField);

        main.add(header, BorderLayout.NORTH);

        // === Body + Formatting toolbar ===
        JPanel bodyWrap = new JPanel(new BorderLayout(0, Theme.SP_2));
        bodyWrap.setOpaque(false);

        JPanel fmtBar = new JPanel(new FlowLayout(FlowLayout.LEFT, Theme.SP_2, 0));
        fmtBar.setOpaque(false);

        htmlModeToggle = new JToggleButton("Chế độ HTML");
        htmlModeToggle.setFont(Theme.fontSmall());
        JButton boldBtn = Theme.secondaryButton("B", null);
        boldBtn.setToolTipText("In đậm (<b>...</b>)");
        boldBtn.addActionListener(e -> wrapSelection("<b>", "</b>"));

        JButton italicBtn = Theme.secondaryButton("I", null);
        italicBtn.setToolTipText("In nghiêng (<i>...</i>)");
        italicBtn.addActionListener(e -> wrapSelection("<i>", "</i>"));

        JButton linkBtn = Theme.secondaryButton("Chèn liên kết", null);
        linkBtn.addActionListener(e -> {
            String url = JOptionPane.showInputDialog(this, "Nhập đường dẫn URL (https://...):");
            if (!currentDialog() || sending) return;
            if (url != null && !url.isBlank()) {
                String href;
                try {
                    href = ComposeValidation.safeLinkHref(url);
                } catch (IllegalArgumentException ex) {
                    statusLabel.setForeground(Theme.danger());
                    statusLabel.setText("Liên kết phải là địa chỉ HTTP hoặc HTTPS hợp lệ.");
                    return;
                }
                htmlModeToggle.setSelected(true);
                wrapSelection("<a href=\"" + href + "\">", "</a>");
            }
        });

        JButton attachBtn = Theme.secondaryButton("Đính kèm tệp...", AppIcons.vectorIcon(AppIcons.Glyph.ATTACHMENT, 16, Theme.textPrimary()));
        attachBtn.addActionListener(e -> chooseAttachments());
        editingButtons.addAll(List.of(htmlModeToggle, boldBtn, italicBtn, linkBtn, attachBtn));

        fmtBar.add(htmlModeToggle);
        fmtBar.add(boldBtn);
        fmtBar.add(italicBtn);
        fmtBar.add(linkBtn);
        fmtBar.add(Box.createHorizontalStrut(Theme.SP_3));
        fmtBar.add(attachBtn);

        bodyArea = new JTextArea();
        bodyArea.setFont(Theme.fontRegular());
        bodyArea.setLineWrap(true);
        bodyArea.setWrapStyleWord(true);
        bodyArea.setBackground(Theme.bgSurface());
        bodyArea.setForeground(Theme.textPrimary());
        bodyArea.setCaretColor(Theme.textPrimary());
        bodyArea.setBorder(new EmptyBorder(Theme.SP_3, Theme.SP_3, Theme.SP_3, Theme.SP_3));

        JScrollPane bodyScroll = new JScrollPane(bodyArea);
        bodyScroll.setBorder(BorderFactory.createLineBorder(Theme.border(), 1));

        // Kéo-thả tệp đính kèm trực tiếp vào khung soạn thảo
        new DropTarget(bodyArea, DnDConstants.ACTION_COPY, new DropTargetAdapter() {
            @Override
            @SuppressWarnings("unchecked")
            public void drop(DropTargetDropEvent dtde) {
                if (sending || !currentDialog() || !dtde.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                    dtde.rejectDrop();
                    return;
                }
                boolean completed = false;
                try {
                    dtde.acceptDrop(DnDConstants.ACTION_COPY);
                    Object data = dtde.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                    if (data instanceof List<?> list) {
                        completed = addAttachmentFiles((List<File>) list) > 0;
                    }
                } catch (Exception ignored) {
                    statusLabel.setText("Không thể nhận tệp kéo-thả.");
                } finally {
                    dtde.dropComplete(completed);
                }
            }
        }, true);

        // Vùng hiển thị chip đính kèm
        JPanel attachSection = new JPanel(new BorderLayout(Theme.SP_2, Theme.SP_1));
        attachSection.setOpaque(false);
        attachSummaryLabel = new JLabel("Kéo-thả tệp vào khung soạn thảo hoặc bấm 'Đính kèm tệp...' (tối đa " + maxAttachmentMb + " MB)");
        attachSummaryLabel.setFont(Theme.fontTiny());
        attachSummaryLabel.setForeground(Theme.textSecondary());

        attachmentChipsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        attachmentChipsPanel.setOpaque(false);

        attachSection.add(attachSummaryLabel, BorderLayout.NORTH);
        attachSection.add(Theme.horizontalChipScroll(attachmentChipsPanel), BorderLayout.CENTER);

        bodyWrap.add(fmtBar, BorderLayout.NORTH);
        bodyWrap.add(bodyScroll, BorderLayout.CENTER);
        bodyWrap.add(attachSection, BorderLayout.SOUTH);

        main.add(bodyWrap, BorderLayout.CENTER);

        // === Bottom action bar ===
        JPanel bottom = new JPanel(new BorderLayout(Theme.SP_3, 0));
        bottom.setOpaque(false);

        JPanel statusWrap = new JPanel(new BorderLayout(Theme.SP_2, 0));
        statusWrap.setOpaque(false);
        statusLabel = new JLabel(" ");
        statusLabel.setFont(Theme.fontSmall());
        statusLabel.setForeground(Theme.textSecondary());
        progressBar = new JProgressBar();
        progressBar.setIndeterminate(true);
        progressBar.setVisible(false);
        progressBar.setPreferredSize(new Dimension(130, 14));
        statusWrap.add(progressBar, BorderLayout.WEST);
        statusWrap.add(statusLabel, BorderLayout.CENTER);

        JPanel btnWrap = new JPanel(new FlowLayout(FlowLayout.RIGHT, Theme.SP_2, 0));
        btnWrap.setOpaque(false);
        JButton cancelBtn = Theme.secondaryButton("Hủy", null);
        cancelBtn.addActionListener(e -> confirmClose());

        sendBtn = Theme.primaryButton("Gửi thư", AppIcons.vectorIcon(AppIcons.Glyph.SENT, 16, Color.WHITE));
        sendBtn.addActionListener(e -> performSend());
        sendBtn.setEnabled(sendAdmission.isAllowed() && fromCombo.getSelectedItem() != null);
        fromCombo.addActionListener(e -> sendBtn.setEnabled(currentDialog() && sendAdmission.isAllowed() && !sending
                && !sentSuccessfully && fromCombo.getSelectedItem() != null));
        if (!sendAdmission.isAllowed()) {
            statusLabel.setForeground(Theme.warning());
            statusLabel.setText("Tính năng bị tắt bởi Admin");
        }

        btnWrap.add(cancelBtn);
        btnWrap.add(sendBtn);

        bottom.add(statusWrap, BorderLayout.CENTER);
        bottom.add(btnWrap, BorderLayout.EAST);
        main.add(bottom, BorderLayout.SOUTH);

        outer.add(main, BorderLayout.CENTER);
        setContentPane(outer);
    }

    private void addRow(JPanel panel, GridBagConstraints g, int row, String label, Component comp) {
        g.gridy = row;
        g.gridx = 0; g.weightx = 0;
        JLabel lbl = new JLabel(label);
        lbl.setFont(Theme.fontBold());
        lbl.setForeground(Theme.textSecondary());
        lbl.setPreferredSize(new Dimension(76, 28));
        panel.add(lbl, g);
        g.gridx = 1; g.weightx = 1;
        panel.add(comp, g);
    }

    private void wrapSelection(String open, String close) {
        if (!currentDialog() || sending) return;
        htmlModeToggle.setSelected(true);
        int start = bodyArea.getSelectionStart();
        int end = bodyArea.getSelectionEnd();
        String sel = bodyArea.getSelectedText();
        if (sel == null) sel = "nội dung";
        bodyArea.replaceRange(open + sel + close, start, end);
    }

    private void chooseAttachments() {
        if (sending || !currentDialog()) return;
        File directory;
        try {
            directory = java.nio.file.Files.createDirectories(
                    java.nio.file.Path.of(ClientConfig.getInstance().getDownloadDir()).toAbsolutePath().normalize()).toFile();
        } catch (java.io.IOException | SecurityException | java.nio.file.InvalidPathException ex) {
            JOptionPane.showMessageDialog(this, "Không mở được thư mục chọn tệp đã cấu hình.",
                    "Lỗi chọn tệp", JOptionPane.ERROR_MESSAGE);
            return;
        }
        JFileChooser chooser = new JFileChooser(directory);
        chooser.setMultiSelectionEnabled(true);
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION && currentDialog() && !sending) {
            addAttachmentFiles(List.of(chooser.getSelectedFiles()));
        }
    }

    private int addAttachmentFiles(List<File> files) {
        if (sending || !currentDialog()) return 0;
        int added = 0;
        for (File selected : files) {
            if (!currentDialog() || sending) return added;
            File f;
            ComposeValidation.AttachmentStamp stamp;
            try {
                f = selected == null ? null : selected.toPath().toAbsolutePath().normalize().toFile();
                stamp = ComposeValidation.AttachmentStamp.inspect(f);
            } catch (java.io.IOException | RuntimeException ex) {
                statusLabel.setForeground(Theme.danger());
                statusLabel.setText("Tệp đính kèm không tồn tại, không đọc được hoặc là liên kết.");
                continue;
            }
            if (attachmentFiles.contains(f)) continue;
            long currentTotal = selectedStamps.values().stream().mapToLong(ComposeValidation.AttachmentStamp::size).sum();
            long maxBytes = (long) maxAttachmentMb * 1024L * 1024L;
            if (stamp.size() > maxBytes - currentTotal) {
                JOptionPane.showMessageDialog(this,
                        "Không thể thêm '" + f.getName() + "': tổng dung lượng vượt quá giới hạn " + maxAttachmentMb + " MB.",
                        "Vượt dung lượng đính kèm", JOptionPane.WARNING_MESSAGE);
                continue;
            }
            attachmentFiles.add(f);
            selectedStamps.put(f, stamp);
            added++;
        }
        refreshAttachmentChips();
        return added;
    }

    private void refreshAttachmentChips() {
        attachmentChipsPanel.removeAll();
        long totalBytes = 0;
        for (int i = 0; i < attachmentFiles.size(); i++) {
            File f = attachmentFiles.get(i);
            var stamp = selectedStamps.get(f);
            long size = stamp == null ? 0 : stamp.size();
            totalBytes += size;
            JPanel chip = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
            chip.setBackground(Theme.bgSurfaceAlt());
            chip.setBorder(BorderFactory.createLineBorder(Theme.border(), 1));
            JLabel name = new JLabel();
            name.putClientProperty("html.disable", Boolean.TRUE);
            name.setText(f.getName() + " (" + formatBytes(size) + ")");
            name.setFont(Theme.fontTiny());
            name.setForeground(Theme.textPrimary());
            JButton rm = new JButton("×");
            rm.setEnabled(!sending);
            rm.setMargin(new Insets(0, 4, 0, 4));
            rm.setBorderPainted(false);
            rm.setContentAreaFilled(false);
            rm.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            rm.addActionListener(e -> {
                if (sending || !currentDialog()) return;
                if (selectedStamps.get(f) == stamp && attachmentFiles.remove(f)) selectedStamps.remove(f);
                refreshAttachmentChips();
            });
            chip.add(name);
            chip.add(rm);
            attachmentChipsPanel.add(chip);
        }
        attachSummaryLabel.setText(attachmentFiles.isEmpty()
                ? "Kéo-thả tệp vào khung soạn thảo hoặc bấm 'Đính kèm tệp...' (tối đa " + maxAttachmentMb + " MB)"
                : "Đã đính kèm " + attachmentFiles.size() + " tệp — Tổng: " + formatBytes(totalBytes) + " / " + maxAttachmentMb + " MB");
        attachmentChipsPanel.revalidate();
        attachmentChipsPanel.repaint();
    }

    private void performSend() {
        if (!currentDialog() || sending || !sendAdmission.isAllowed() || sentSuccessfully) return;
        FeatureAdmission.Ticket admission = sendAdmission.capture();
        if (uncertainSend) {
            Object[] options = {"Gửi lại", "Hủy"};
            int answer = JOptionPane.showOptionDialog(this,
                    UNCERTAIN_SEND_MESSAGE + "\nBạn xác nhận muốn gửi lại?",
                    "Nguy cơ gửi trùng", JOptionPane.DEFAULT_OPTION,
                    JOptionPane.WARNING_MESSAGE, null, options, options[1]);
            if (answer != 0 || !currentDialog() || sending || !sendAdmission.isCurrent(admission) || sentSuccessfully) return;
        }
        if (!toInput.commitPending() || !ccInput.commitPending() || !bccInput.commitPending()) return;
        if (!currentDialog() || sending || !sendAdmission.isCurrent(admission)) return;

        GmailAccountDto fromAcc = (GmailAccountDto) fromCombo.getSelectedItem();
        if (fromAcc == null) {
            statusLabel.setForeground(Theme.danger());
            statusLabel.setText("Vui lòng liên kết ít nhất 1 tài khoản Gmail trước khi gửi thư.");
            return;
        }
        if (fromAcc.requiresReauthBeforeMail()) {
            statusLabel.setForeground(Theme.danger());
            statusLabel.setText("Tài khoản Gmail cần được xác thực lại trước khi gửi thư.");
            return;
        }
        List<String> toList = toInput.getEmails();
        if (toList.isEmpty()) {
            statusLabel.setForeground(Theme.danger());
            statusLabel.setText("Vui lòng nhập ít nhất một địa chỉ người nhận (Đến).");
            return;
        }

        String subject = subjectField.getText().trim();
        String rawBody = bodyArea.getText();
        List<String> ccList = ccInput.getEmails();
        List<String> bccList = bccInput.getEmails();
        List<File> files = List.copyOf(attachmentFiles);
        var stamps = java.util.Map.copyOf(selectedStamps);
        if (files.stream().anyMatch(f -> !stamps.containsKey(f) || !stamps.get(f).matches(f))) {
            statusLabel.setForeground(Theme.danger());
            statusLabel.setText("Tệp đính kèm đã thay đổi. Hãy xóa khỏi bản nháp và chọn lại.");
            return;
        }
        String invalid = ComposeValidation.validate(toList, ccList, bccList, subject, rawBody, files, maxAttachmentMb);
        if (invalid != null) {
            statusLabel.setForeground(Theme.danger());
            statusLabel.setText(invalid);
            return;
        }
        String textBody = htmlModeToggle.isSelected() ? null : rawBody;
        String htmlBody = htmlModeToggle.isSelected() ? rawBody : null;

        SendMailPayload payload = new SendMailPayload(
                fromAcc.id(), toList, ccList, bccList,
                subject, textBody, htmlBody, inReplyTo, references);

        sending = true;
        setEditingEnabled(false);
        SessionStore session = SessionStore.getInstance();
        long sessionGeneration = dialogGeneration;
        sendBtn.setEnabled(false);
        progressBar.setVisible(true);
        statusLabel.setForeground(Theme.primary());
        statusLabel.setText("Đang gửi thư qua SMTP bảo mật...");

        SwingWorker<SendMailResponse, Void> worker = new SwingWorker<>() {
            private boolean submissionStarted;
            @Override
            protected SendMailResponse doInBackground() throws Exception {
                PublicConfigDto latest = apiClient.getPublicConfig();
                if (disposed || sessionGeneration != session.getGeneration()) throw new InterruptedException();
                requireSendAdmission(admission);
                if (latest == null) {
                    throw new ApiException(503, "CONFIG_UNAVAILABLE", "Chưa tải được cấu hình gửi thư.");
                }
                if (!latest.sendMailEnabled()) {
                    throw new ApiException(403, "FEATURE_DISABLED", "Tính năng bị tắt bởi Admin");
                }
                String error = ComposeValidation.validate(toList, ccList, bccList, subject, rawBody,
                        files, latest.maxAttachmentSizeMb());
                if (error != null) throw new ApiException(400, "VALIDATION_ERROR", error);
                if (files.stream().anyMatch(f -> !stamps.get(f).matches(f)))
                    throw new ApiException(400, "VALIDATION_ERROR", "Tệp đính kèm đã thay đổi. Hãy chọn lại.");
                if (disposed || sessionGeneration != session.getGeneration()) throw new InterruptedException();
                requireSendAdmission(admission);
                submissionStarted = true;
                return apiClient.sendMailMultipart(payload, files);
            }

            @Override
            protected void done() {
                progressBar.setVisible(false);
                sending = false;
                if (disposed || sessionGeneration != session.getGeneration()) return;
                try {
                    SendMailResponse response = get();
                    if (response == null || !"SENT".equalsIgnoreCase(response.status())
                            || response.messageId() == null || response.messageId().isBlank()) {
                        throw new java.io.IOException("Missing send acknowledgement");
                    }
                    sentSuccessfully = true;
                    uncertainSend = false;
                    JOptionPane.showMessageDialog(ComposeDialog.this,
                            "Đã gửi thư thành công!", "Gửi thư", JOptionPane.INFORMATION_MESSAGE);
                    if (currentDialog() && onSentCallback != null) onSentCallback.run();
                    dispose();
                } catch (Exception ex) {
                    if (!currentDialog()) return;
                    // A callback/UI error after acknowledged delivery must not
                    // unlock the already sent draft or turn it into a retry.
                    if (sentSuccessfully) {
                        dispose();
                        return;
                    }
                    setEditingEnabled(true);
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    if (cause instanceof ApiException ae && ae.getStatusCode() == 401 && onSessionExpired != null) {
                        onSessionExpired.run();
                        return;
                    }
                    if (cause instanceof ApiException ae && sendAdmission.isCurrent(admission)
                            && ("FEATURE_DISABLED".equalsIgnoreCase(ae.getCode())
                            || "CONFIG_UNAVAILABLE".equalsIgnoreCase(ae.getCode()))) {
                        sendAdmission.update(false);
                    }
                    sendBtn.setEnabled(sendAdmission.isAllowed() && fromCombo.getSelectedItem() != null);
                    uncertainSend |= submissionStarted && ApiException.isMutationOutcomeUncertain(cause);
                    statusLabel.setForeground(uncertainSend ? Theme.warning() : Theme.danger());
                    statusLabel.setText(uncertainSend ? UNCERTAIN_SEND_MESSAGE
                            : ClientSafetyUtil.toFriendlyVietnameseError(cause));
                }
            }
        };
        worker.execute();
    }

    private boolean currentDialog() {
        return !disposed && dialogGeneration == SessionStore.getInstance().getGeneration();
    }

    private void requireSendAdmission(FeatureAdmission.Ticket admission) {
        if (!sendAdmission.isCurrent(admission)) {
            throw new ApiException(400, "FORM_INVALIDATED",
                    "Cấu hình gửi thư đã thay đổi. Bản nháp được giữ lại; hãy kiểm tra và bấm Gửi lại.");
        }
    }

    public void setOnSessionExpired(Runnable callback) {
        onSessionExpired = callback;
    }

    private void setEditingEnabled(boolean enabled) {
        fromCombo.setEnabled(enabled);
        subjectField.setEditable(enabled);
        bodyArea.setEditable(enabled);
        toInput.setEditingEnabled(enabled);
        ccInput.setEditingEnabled(enabled);
        bccInput.setEditingEnabled(enabled);
        editingButtons.forEach(button -> button.setEnabled(enabled));
        refreshAttachmentChips();
    }

    private void confirmClose() {
        if (sending) {
            JOptionPane.showMessageDialog(this, "Đang gửi thư. Vui lòng đợi kết quả trước khi đóng.",
                    "Đang gửi thư", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        if (hasDraft()) {
            int opt = JOptionPane.showConfirmDialog(this,
                    (uncertainSend ? UNCERTAIN_SEND_MESSAGE + "\n" : "Thư chưa được gửi. ")
                            + "Bạn có chắc chắn muốn đóng và hủy nội dung đang soạn?",
                    "Xác nhận đóng", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (opt != JOptionPane.YES_OPTION) return;
        }
        dispose();
    }

    private boolean hasDraft() {
        return !sentSuccessfully && (!subjectField.getText().isBlank() || !bodyArea.getText().isBlank()
                || toInput.hasInput() || ccInput.hasInput() || bccInput.hasInput() || !attachmentFiles.isEmpty());
    }

    @Override public void dispose() {
        disposed = true;
        super.dispose();
    }

    public static String formatBytes(long bytes) {
        return MailSizeDisplay.formatBytes(bytes);
    }

    /**
     * Component nhập danh sách email dạng chip có kiểm tra định dạng trực quan.
     */
    private static class EmailChipInput extends JPanel {
        private final List<String> emails = new ArrayList<>();
        private final JPanel chipsPanel;
        private final JScrollPane chipsScroll;
        private final JTextField inputField;
        private boolean editingEnabled = true;

        EmailChipInput(String placeholder) {
            super(new BorderLayout(4, 2));
            setBackground(Theme.bgSurface());
            setBorder(new CompoundBorder(
                    BorderFactory.createLineBorder(Theme.border(), 1),
                    new EmptyBorder(2, 6, 2, 6)
            ));

            chipsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
            chipsPanel.setOpaque(false);

            inputField = new JTextField(18);
            inputField.setFont(Theme.fontRegular());
            inputField.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
            inputField.setBackground(Theme.bgSurface());
            inputField.setForeground(Theme.textPrimary());
            inputField.setToolTipText(placeholder);
            inputField.addKeyListener(new KeyAdapter() {
                @Override
                public void keyPressed(KeyEvent e) {
                    if (editingEnabled && e.getKeyCode() == KeyEvent.VK_BACK_SPACE && inputField.getText().isEmpty() && !emails.isEmpty()) {
                        emails.remove(emails.size() - 1);
                        rebuildChips();
                    }
                }
            });
            inputField.addActionListener(e -> commitPending());
            inputField.getInputMap().put(KeyStroke.getKeyStroke(','), "commitRecipient");
            inputField.getInputMap().put(KeyStroke.getKeyStroke(';'), "commitRecipient");
            inputField.getActionMap().put("commitRecipient", new AbstractAction() {
                @Override public void actionPerformed(ActionEvent e) { commitPending(); }
            });

            // Bound the chip row independently of the text field. A long
            // address or many recipients must never consume the input area.
            chipsScroll = Theme.horizontalChipScroll(chipsPanel);
            chipsScroll.setPreferredSize(new Dimension(200, chipsScroll.getPreferredSize().height));
            chipsScroll.setVisible(false);
            add(chipsScroll, BorderLayout.NORTH);
            add(inputField, BorderLayout.CENTER);
        }

        boolean commitPending() {
            if (!editingEnabled) return false;
            String raw = inputField.getText();
            if (raw.isBlank()) return true;
            try {
                List<String> next = new ArrayList<>(emails);
                for (String token : ComposeValidation.parseRecipients(raw)) {
                    if (!next.contains(token)) next.add(token);
                }
                if (next.size() > 50) throw new IllegalArgumentException("Tối đa 50 địa chỉ người nhận.");
                emails.clear();
                emails.addAll(next);
                rebuildChips();
            } catch (IllegalArgumentException ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Lỗi định dạng email", JOptionPane.WARNING_MESSAGE);
                inputField.requestFocusInWindow();
                return false;
            }
            inputField.setText("");
            return true;
        }

        void addChip(String email) {
            if (email == null || email.isBlank()) return;
            if (!ComposeValidation.isEmail(email)) {
                JOptionPane.showMessageDialog(this, "Địa chỉ email không hợp lệ: " + email,
                        "Lỗi định dạng email", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (!emails.contains(email)) {
                emails.add(email);
                rebuildChips();
            }
        }

        List<String> getEmails() {
            return new ArrayList<>(emails);
        }

        boolean hasInput() {
            return !emails.isEmpty() || !inputField.getText().isBlank();
        }

        void setEditingEnabled(boolean enabled) {
            editingEnabled = enabled;
            inputField.setEnabled(enabled);
            rebuildChips();
        }

        private void rebuildChips() {
            chipsPanel.removeAll();
            for (int i = 0; i < emails.size(); i++) {
                final String address = emails.get(i);
                JPanel chip = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 1));
                chip.setBackground(Theme.bgSelected());
                chip.setBorder(BorderFactory.createLineBorder(Theme.primary(), 1));
                JLabel lbl = new JLabel(emails.get(i));
                lbl.setFont(Theme.fontTiny());
                lbl.setForeground(Theme.textPrimary());
                JButton del = new JButton("×");
                del.setEnabled(editingEnabled);
                del.setMargin(new Insets(0, 3, 0, 3));
                del.setBorderPainted(false);
                del.setContentAreaFilled(false);
                del.addActionListener(e -> { if (editingEnabled) { emails.remove(address); rebuildChips(); } });
                chip.add(lbl);
                chip.add(del);
                chipsPanel.add(chip);
            }
            chipsScroll.setPreferredSize(new Dimension(200, chipsPanel.getPreferredSize().height
                    + chipsScroll.getHorizontalScrollBar().getPreferredSize().height));
            chipsScroll.setVisible(!emails.isEmpty());
            revalidate();
            repaint();
        }
    }
}

