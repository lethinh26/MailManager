package com.mailapp.client.ui.dialogs;

import com.mailapp.client.model.GmailAccountDto;
import com.mailapp.client.model.MailClientDtos.*;
import com.mailapp.client.model.PublicConfigDto;
import com.mailapp.client.net.ApiClient;
import com.mailapp.client.net.ApiException;
import com.mailapp.client.ui.theme.AppIcons;
import com.mailapp.client.ui.theme.Theme;
import com.mailapp.client.util.ClientConfig;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Hộp thoại Soạn thư (Compose / Reply / Reply All / Forward) thuần Swing:
 * - Nhập Đến / Cc / Bcc dạng chip có kiểm tra định dạng email.
 * - Soạn Văn bản hoặc HTML đơn giản (Đậm / Nghiêng / Chèn liên kết).
 * - Chọn tài khoản Gmail gửi đi.
 * - Kéo-thả & chọn tệp đính kèm, hiển thị tổng dung lượng và chặn vượt MAX_ATTACHMENT_SIZE_MB.
 * - Nhắc xác nhận khi đóng mà chưa gửi; gửi bất đồng bộ bằng SwingWorker.
 */
public class ComposeDialog extends JDialog {

    private static final Pattern EMAIL_REGEX = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final ApiClient apiClient;
    private final List<GmailAccountDto> accounts;
    private final int maxAttachmentMb;
    private final Runnable onSentCallback;

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
        this.accounts = accounts != null ? accounts : List.of();
        this.maxAttachmentMb = (publicConfig != null && publicConfig.maxAttachmentSizeMb() > 0)
                ? publicConfig.maxAttachmentSizeMb() : 25;
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

    /**
     * Tự điền nội dung cho Trả lời / Trả lời tất cả / Chuyển tiếp.
     */
    public void prefillReplyOrForward(MailDetailResponse original, String mode) {
        if (original == null) return;
        String origSubj = original.subject() != null ? original.subject() : "";
        String senderAddr = original.from() != null ? original.from().address() : "";

        if ("REPLY".equals(mode) || "REPLY_ALL".equals(mode)) {
            setTitle(("REPLY_ALL".equals(mode) ? "Trả lời tất cả" : "Trả lời") + " — " + AppIcons.BRAND_FULL);
            if (!senderAddr.isBlank()) toInput.addChip(senderAddr);
            if ("REPLY_ALL".equals(mode)) {
                GmailAccountDto current = (GmailAccountDto) fromCombo.getSelectedItem();
                String myEmail = current != null ? current.email() : "";
                if (original.to() != null) {
                    for (MailAddressDto a : original.to()) {
                        if (a.address() != null && !a.address().equalsIgnoreCase(myEmail) && !a.address().equalsIgnoreCase(senderAddr)) {
                            ccInput.addChip(a.address());
                        }
                    }
                }
                if (original.cc() != null) {
                    for (MailAddressDto a : original.cc()) {
                        if (a.address() != null && !a.address().equalsIgnoreCase(myEmail)) {
                            ccInput.addChip(a.address());
                        }
                    }
                }
            }
            subjectField.setText(origSubj.toLowerCase().startsWith("re:") ? origSubj : "Re: " + origSubj);
            this.inReplyTo = original.messageId();
            this.references = original.references() != null && !original.references().isBlank()
                    ? original.references() + " " + original.messageId()
                    : original.messageId();
            bodyArea.setText("\n\n--- Vào lúc " + (original.date() != null ? original.date() : "")
                    + ", " + (original.from() != null ? original.from().displayText() : "") + " đã viết ---\n> "
                    + (original.textBody() != null ? original.textBody().replace("\n", "\n> ") : ""));
            bodyArea.setCaretPosition(0);
        } else if ("FORWARD".equals(mode)) {
            setTitle("Chuyển tiếp thư — " + AppIcons.BRAND_FULL);
            subjectField.setText(origSubj.toLowerCase().startsWith("fwd:") ? origSubj : "Fwd: " + origSubj);
            bodyArea.setText("\n\n---------- Thư đã chuyển tiếp ----------\nTừ: "
                    + (original.from() != null ? original.from().displayText() : "")
                    + "\nNgày: " + (original.date() != null ? original.date() : "")
                    + "\nTiêu đề: " + origSubj + "\n\n"
                    + (original.textBody() != null ? original.textBody() : ""));
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
            if (url != null && !url.isBlank()) {
                htmlModeToggle.setSelected(true);
                wrapSelection("<a href=\"" + url.trim() + "\">", "</a>");
            }
        });

        JButton attachBtn = Theme.secondaryButton("Đính kèm tệp...", AppIcons.vectorIcon(AppIcons.Glyph.ATTACHMENT, 16, Theme.textPrimary()));
        attachBtn.addActionListener(e -> chooseAttachments());

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
                try {
                    dtde.acceptDrop(DnDConstants.ACTION_COPY);
                    Object data = dtde.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                    if (data instanceof List<?> list) {
                        addAttachmentFiles((List<File>) list);
                    }
                } catch (Exception ignored) {}
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
        attachSection.add(attachmentChipsPanel, BorderLayout.CENTER);

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
        htmlModeToggle.setSelected(true);
        int start = bodyArea.getSelectionStart();
        int end = bodyArea.getSelectionEnd();
        String sel = bodyArea.getSelectedText();
        if (sel == null) sel = "nội dung";
        bodyArea.replaceRange(open + sel + close, start, end);
    }

    private void chooseAttachments() {
        JFileChooser chooser = new JFileChooser();
        chooser.setMultiSelectionEnabled(true);
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            addAttachmentFiles(List.of(chooser.getSelectedFiles()));
        }
    }

    private void addAttachmentFiles(List<File> files) {
        long currentTotal = attachmentFiles.stream().mapToLong(File::length).sum();
        long maxBytes = (long) maxAttachmentMb * 1024L * 1024L;

        for (File f : files) {
            if (f == null || !f.exists() || !f.isFile()) continue;
            if (currentTotal + f.length() > maxBytes) {
                JOptionPane.showMessageDialog(this,
                        "Không thể thêm '" + f.getName() + "': tổng dung lượng vượt quá giới hạn " + maxAttachmentMb + " MB.",
                        "Vượt dung lượng đính kèm", JOptionPane.WARNING_MESSAGE);
                continue;
            }
            attachmentFiles.add(f);
            currentTotal += f.length();
        }
        refreshAttachmentChips();
    }

    private void refreshAttachmentChips() {
        attachmentChipsPanel.removeAll();
        long totalBytes = 0;
        for (int i = 0; i < attachmentFiles.size(); i++) {
            File f = attachmentFiles.get(i);
            totalBytes += f.length();
            final int idx = i;
            JPanel chip = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
            chip.setBackground(Theme.bgSurfaceAlt());
            chip.setBorder(BorderFactory.createLineBorder(Theme.border(), 1));
            JLabel name = new JLabel(f.getName() + " (" + formatBytes(f.length()) + ")");
            name.setFont(Theme.fontTiny());
            name.setForeground(Theme.textPrimary());
            JButton rm = new JButton("×");
            rm.setMargin(new Insets(0, 4, 0, 4));
            rm.setBorderPainted(false);
            rm.setContentAreaFilled(false);
            rm.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            rm.addActionListener(e -> {
                attachmentFiles.remove(idx);
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
        toInput.commitPending();
        ccInput.commitPending();
        bccInput.commitPending();

        GmailAccountDto fromAcc = (GmailAccountDto) fromCombo.getSelectedItem();
        if (fromAcc == null) {
            statusLabel.setForeground(Theme.danger());
            statusLabel.setText("Vui lòng liên kết ít nhất 1 tài khoản Gmail trước khi gửi thư.");
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
        String textBody = htmlModeToggle.isSelected() ? null : rawBody;
        String htmlBody = htmlModeToggle.isSelected() ? rawBody : null;

        SendMailPayload payload = new SendMailPayload(
                fromAcc.id(), toList, ccInput.getEmails(), bccInput.getEmails(),
                subject, textBody, htmlBody, inReplyTo, references);

        sendBtn.setEnabled(false);
        progressBar.setVisible(true);
        statusLabel.setForeground(Theme.primary());
        statusLabel.setText("Đang gửi thư qua SMTP bảo mật...");

        SwingWorker<SendMailResponse, Void> worker = new SwingWorker<>() {
            @Override
            protected SendMailResponse doInBackground() throws Exception {
                return apiClient.sendMailMultipart(payload, new ArrayList<>(attachmentFiles));
            }

            @Override
            protected void done() {
                progressBar.setVisible(false);
                try {
                    get();
                    sentSuccessfully = true;
                    JOptionPane.showMessageDialog(ComposeDialog.this,
                            "Đã gửi thư thành công!", "Gửi thư", JOptionPane.INFORMATION_MESSAGE);
                    if (onSentCallback != null) onSentCallback.run();
                    dispose();
                } catch (Exception ex) {
                    sendBtn.setEnabled(true);
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    statusLabel.setForeground(Theme.danger());
                    statusLabel.setText(cause instanceof ApiException ae ? ae.getMessage() : "Lỗi gửi thư: " + cause.getMessage());
                }
            }
        };
        worker.execute();
    }

    private void confirmClose() {
        boolean hasDraft = !sentSuccessfully && (!subjectField.getText().isBlank() || !bodyArea.getText().isBlank()
                || !toInput.getEmails().isEmpty() || !attachmentFiles.isEmpty());
        if (hasDraft) {
            int opt = JOptionPane.showConfirmDialog(this,
                    "Thư chưa được gửi. Bạn có chắc chắn muốn đóng và hủy nội dung đang soạn?",
                    "Xác nhận đóng", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (opt != JOptionPane.YES_OPTION) return;
        }
        dispose();
    }

    public static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
    }

    /**
     * Component nhập danh sách email dạng chip có kiểm tra định dạng trực quan.
     */
    private static class EmailChipInput extends JPanel {
        private final List<String> emails = new ArrayList<>();
        private final JPanel chipsPanel;
        private final JTextField inputField;

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
                    if (e.getKeyCode() == KeyEvent.VK_ENTER || e.getKeyChar() == ',' || e.getKeyChar() == ';') {
                        e.consume();
                        commitPending();
                    } else if (e.getKeyCode() == KeyEvent.VK_BACK_SPACE && inputField.getText().isEmpty() && !emails.isEmpty()) {
                        emails.remove(emails.size() - 1);
                        rebuildChips();
                    }
                }
            });

            add(chipsPanel, BorderLayout.WEST);
            add(inputField, BorderLayout.CENTER);
        }

        void commitPending() {
            String raw = inputField.getText().trim();
            if (raw.isEmpty()) return;
            for (String token : raw.split("[,;\\s]+")) {
                if (!token.isBlank()) addChip(token.trim());
            }
            inputField.setText("");
        }

        void addChip(String email) {
            if (email == null || email.isBlank()) return;
            if (!EMAIL_REGEX.matcher(email).matches()) {
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

        private void rebuildChips() {
            chipsPanel.removeAll();
            for (int i = 0; i < emails.size(); i++) {
                final int idx = i;
                JPanel chip = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 1));
                chip.setBackground(Theme.bgSelected());
                chip.setBorder(BorderFactory.createLineBorder(Theme.primary(), 1));
                JLabel lbl = new JLabel(emails.get(i));
                lbl.setFont(Theme.fontTiny());
                lbl.setForeground(Theme.textPrimary());
                JButton del = new JButton("×");
                del.setMargin(new Insets(0, 3, 0, 3));
                del.setBorderPainted(false);
                del.setContentAreaFilled(false);
                del.addActionListener(e -> { emails.remove(idx); rebuildChips(); });
                chip.add(lbl);
                chip.add(del);
                chipsPanel.add(chip);
            }
            revalidate();
            repaint();
        }
    }
}

