package com.mailapp.client.ui.theme;

import javax.swing.*;
import java.awt.*;

/**
 * FlowLayout có tính chiều cao nhiều hàng thật theo bề rộng hiện tại.
 * FlowLayout gốc luôn báo preferredSize bằng 1 hàng, nên khi bị ép hẹp các phần tử
 * tràn ra ngoài container (lỗi badge "App Password" bị cắt). Lớp này tính lại số hàng
 * theo width thật và tự invalidate khi container đổi kích thước.
 */
public final class WrapLayout extends FlowLayout {

    public WrapLayout(int align, int hgap, int vgap) {
        super(align, hgap, vgap);
    }

    @Override
    public Dimension preferredLayoutSize(Container target) {
        return layoutSize(target, true);
    }

    @Override
    public Dimension minimumLayoutSize(Container target) {
        Dimension d = layoutSize(target, false);
        return new Dimension(Math.max(d.width, 1), Math.max(d.height, 1));
    }

    private Dimension layoutSize(Container target, boolean preferred) {
        synchronized (target.getTreeLock()) {
            int targetWidth = target.getSize().width;
            if (targetWidth <= 0 && target.getParent() != null) targetWidth = target.getParent().getSize().width;
            if (targetWidth <= 0) targetWidth = Integer.MAX_VALUE;

            Insets insets = target.getInsets();
            int maxWidth = targetWidth - (insets.left + insets.right + getHgap() * 2);

            Dimension dim = new Dimension(0, 0);
            int rowWidth = 0;
            int rowHeight = 0;

            for (int i = 0; i < target.getComponentCount(); i++) {
                Component m = target.getComponent(i);
                if (!m.isVisible()) continue;
                Dimension d = preferred ? m.getPreferredSize() : m.getMinimumSize();
                if (rowWidth + d.width > maxWidth && rowWidth > 0) {
                    addRow(dim, rowWidth, rowHeight);
                    rowWidth = 0;
                    rowHeight = 0;
                }
                if (rowWidth != 0) rowWidth += getHgap();
                rowWidth += d.width;
                rowHeight = Math.max(rowHeight, d.height);
            }
            addRow(dim, rowWidth, rowHeight);

            dim.width += insets.left + insets.right + getHgap() * 2;
            dim.height += insets.top + insets.bottom + getVgap() * 2;
            return dim;
        }
    }

    private void addRow(Dimension dim, int rowWidth, int rowHeight) {
        dim.width = Math.max(dim.width, rowWidth);
        if (dim.height > 0) dim.height += getVgap();
        dim.height += rowHeight;
    }

    @Override
    public void layoutContainer(Container target) {
        synchronized (target.getTreeLock()) {
            Insets insets = target.getInsets();
            int maxWidth = target.getWidth() - (insets.left + insets.right + getHgap() * 2);
            int x = insets.left + getHgap();
            int y = insets.top + getVgap();
            int rowHeight = 0;

            for (int i = 0; i < target.getComponentCount(); i++) {
                Component m = target.getComponent(i);
                if (!m.isVisible()) continue;
                Dimension d = m.getPreferredSize();
                if (x + d.width > insets.left + maxWidth && x > insets.left + getHgap()) {
                    x = insets.left + getHgap();
                    y += rowHeight + getVgap();
                    rowHeight = 0;
                }
                // Phần tử rộng hơn cả hàng (nhãn dài, nút to) bị kẹp lại thay vì tràn ra ngoài panel.
                int w = Math.min(d.width, Math.max(1, maxWidth));
                m.setBounds(x, y, w, d.height);
                x += w + getHgap();
                rowHeight = Math.max(rowHeight, d.height);
            }
        }
    }
}
