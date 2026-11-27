package sokoban.ui;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.function.Supplier;

/**
 * Esc로 여는 설정 창. 어느 화면 위에든 겹쳐 그려지고, 열려 있는 동안 입력을 모두 가져간다.
 * 되돌릴 수 없는 항목은 경고 문구와 [취소]/[확인] 확인창을 거쳐야 실행된다(기본 선택은 취소).
 */
final class SettingsOverlay implements Screen {
    /** 한 줄. value가 있으면 값이 오른쪽에 표시되고, 실행하면 값이 바뀐다. confirm이 있으면 먼저 확인창을 띄운다. */
    record Row(String label, Supplier<String> value, Runnable action, boolean danger, String confirm) {
        static Row action(String label, Runnable action) {
            return new Row(label, null, action, false, null);
        }

        static Row option(String label, Supplier<String> value, Runnable action) {
            return new Row(label, value, action, false, null);
        }

        static Row danger(String label, String confirm, Runnable action) {
            return new Row(label, null, action, true, confirm);
        }
    }

    /** 제목 줄 구분선처럼 쓰는 소제목. */
    private static final Row SEPARATOR = new Row(null, null, null, false, null);

    private final List<Row> rows;
    private final Runnable onClose;
    private int selected;
    private final Rectangle[] boxes;
    // 확인창
    private Row pending;
    private int confirmSel;
    private final Rectangle[] confirmBoxes = {new Rectangle(), new Rectangle()};

    SettingsOverlay(List<Row> rows, Runnable onClose) {
        this.rows = rows;
        this.onClose = onClose;
        this.boxes = new Rectangle[rows.size()];
        for (int i = 0; i < boxes.length; i++) boxes[i] = new Rectangle();
        selected = firstSelectable(0, 1);
    }

    /** 구분선은 건너뛰고 고를 수 있는 행의 번호를 찾는다. */
    private int firstSelectable(int from, int step) {
        int n = rows.size();
        for (int k = 0; k < n; k++) {
            int i = Math.floorMod(from + k * step, n);
            if (rows.get(i) != SEPARATOR) return i;
        }
        return 0;
    }

    static Row separator() {
        return SEPARATOR;
    }

    /** 마지막으로 그린 i번째 행의 영역(테스트용). */
    Rectangle rowBox(int i) {
        return new Rectangle(boxes[i]);
    }

    /** 마지막으로 그린 확인창 버튼(0=취소, 1=확인)의 영역(테스트용). */
    Rectangle confirmBox(int i) {
        return new Rectangle(confirmBoxes[i]);
    }

    boolean isConfirming() {
        return pending != null;
    }

    String confirmMessage() {
        return pending == null ? null : pending.confirm();
    }

    // ---------- 입력 ----------

    @Override
    public void keyPressed(KeyEvent e) {
        int k = e.getKeyCode();
        if (pending != null) {
            switch (k) {
                case KeyEvent.VK_ESCAPE -> pending = null;
                case KeyEvent.VK_LEFT, KeyEvent.VK_RIGHT, KeyEvent.VK_TAB, KeyEvent.VK_A, KeyEvent.VK_D ->
                        confirmSel = 1 - confirmSel;
                case KeyEvent.VK_ENTER, KeyEvent.VK_SPACE -> answer(confirmSel == 1);
                default -> {
                }
            }
            return;
        }
        switch (k) {
            case KeyEvent.VK_ESCAPE -> onClose.run();
            case KeyEvent.VK_UP, KeyEvent.VK_W -> selected = firstSelectable(selected - 1, -1);
            case KeyEvent.VK_DOWN, KeyEvent.VK_S -> selected = firstSelectable(selected + 1, 1);
            case KeyEvent.VK_ENTER, KeyEvent.VK_SPACE -> activate(selected);
            case KeyEvent.VK_LEFT, KeyEvent.VK_RIGHT -> {
                if (rows.get(selected).value() != null) activate(selected);
            }
            default -> {
            }
        }
    }

    @Override
    public void mouseMoved(int x, int y) {
        if (pending != null) {
            for (int i = 0; i < 2; i++) if (confirmBoxes[i].contains(x, y)) confirmSel = i;
            return;
        }
        for (int i = 0; i < boxes.length; i++) {
            if (rows.get(i) != SEPARATOR && boxes[i].contains(x, y)) selected = i;
        }
    }

    @Override
    public void mouseClicked(int x, int y) {
        if (pending != null) {
            for (int i = 0; i < 2; i++) {
                if (confirmBoxes[i].contains(x, y)) {
                    confirmSel = i;
                    answer(i == 1);
                    return;
                }
            }
            return;
        }
        for (int i = 0; i < boxes.length; i++) {
            if (rows.get(i) != SEPARATOR && boxes[i].contains(x, y)) {
                selected = i;
                activate(i);
                return;
            }
        }
    }

    private void activate(int i) {
        Row r = rows.get(i);
        if (r == SEPARATOR) return;
        if (r.confirm() != null) {
            pending = r;
            confirmSel = 0; // 실수로 Enter를 눌러도 취소가 되게 한다
        } else {
            r.action().run();
        }
    }

    private void answer(boolean yes) {
        Row r = pending;
        pending = null;
        if (yes && r != null) r.action().run();
    }

    // ---------- 그리기 ----------

    @Override
    public void paint(Graphics2D g, int w, int h) {
        g.setColor(new Color(0, 0, 0, 170));
        g.fillRect(0, 0, w, h);

        int pw = Math.min(w - 20, 540);
        int n = rows.size();
        int sepCount = 0;
        for (Row r : rows) if (r == SEPARATOR) sepCount++;
        int head = 70, foot = 36, gap = 8;
        int avail = Math.max(120, h - 20 - head - foot);
        int rowH = (int) Math.max(26, Math.min(46, (avail - (n - 1) * gap) / (double) n));
        int ph = head + n * rowH + (n - 1) * gap + foot;
        int px = (w - pw) / 2, py = Math.max(10, (h - ph) / 2);

        g.setColor(Theme.PANEL);
        g.fillRoundRect(px, py, pw, ph, 24, 24);
        g.setColor(Theme.ACCENT);
        g.drawRoundRect(px, py, pw, ph, 24, 24);
        g.setColor(Theme.TEXT);
        g.setFont(Theme.font(Font.BOLD, 30f));
        Gfx.centered(g, "설정", w / 2, py + 46);

        int y = py + head;
        for (int i = 0; i < n; i++) {
            Row r = rows.get(i);
            Rectangle b = boxes[i];
            if (r == SEPARATOR) {
                b.setBounds(0, 0, 0, 0);
                g.setColor(Theme.shade(Theme.PANEL, 1.6));
                g.drawLine(px + 24, y + rowH / 2, px + pw - 24, y + rowH / 2);
                y += rowH + gap;
                continue;
            }
            b.setBounds(px + 24, y, pw - 48, rowH);
            boolean sel = i == selected && pending == null;
            g.setColor(sel ? Theme.PANEL_SEL : Theme.shade(Theme.PANEL, 0.8));
            g.fillRoundRect(b.x, b.y, b.width, b.height, 14, 14);
            if (sel) {
                g.setColor(Theme.ACCENT);
                g.drawRoundRect(b.x, b.y, b.width, b.height, 14, 14);
            }
            g.setFont(Theme.font(Font.BOLD, Math.max(13f, rowH * 0.42f)));
            FontMetrics fm = g.getFontMetrics();
            int base = b.y + (rowH + fm.getAscent() - fm.getDescent()) / 2;
            g.setColor(r.danger() ? Theme.BLOCKED : (sel ? Color.WHITE : Theme.TEXT));
            g.drawString(r.label(), b.x + 16, base);
            if (r.value() != null) {
                String v = "◀ " + r.value().get() + " ▶";
                g.setColor(Theme.ACCENT);
                g.drawString(v, b.x + b.width - 16 - fm.stringWidth(v), base);
            }
            y += rowH + gap;
        }
        g.setColor(Theme.TEXT_DIM);
        g.setFont(Theme.font(Font.PLAIN, 13f));
        Gfx.centered(g, "↑↓ 선택   Enter/클릭 실행   Esc 닫기", w / 2, py + ph - 14);

        if (pending != null) paintConfirm(g, w, h);
    }

    private void paintConfirm(Graphics2D g, int w, int h) {
        g.setColor(new Color(0, 0, 0, 120));
        g.fillRect(0, 0, w, h);
        int pw = Math.min(w - 20, 460), ph = 230;
        int px = (w - pw) / 2, py = Math.max(10, (h - ph) / 2);
        g.setColor(Theme.PANEL);
        g.fillRoundRect(px, py, pw, ph, 22, 22);
        g.setColor(Theme.BLOCKED);
        g.setStroke(new java.awt.BasicStroke(2f));
        g.drawRoundRect(px, py, pw, ph, 22, 22);
        g.setStroke(new java.awt.BasicStroke(1f));

        g.setColor(Theme.BLOCKED);
        g.setFont(Theme.font(Font.BOLD, 24f));
        Gfx.centered(g, "⚠ 경고", w / 2, py + 44);
        g.setColor(Theme.TEXT);
        g.setFont(Theme.font(Font.PLAIN, 15f));
        int ly = py + 82;
        for (String line : pending.confirm().split("\n")) {
            Gfx.centered(g, line, w / 2, ly);
            ly += 24;
        }
        String[] names = {"취소", "확인"};
        int bw = 140, bh = 42, by = py + ph - bh - 22;
        for (int i = 0; i < 2; i++) {
            int bx = w / 2 + (i == 0 ? -bw - 12 : 12);
            confirmBoxes[i].setBounds(bx, by, bw, bh);
            boolean sel = i == confirmSel;
            Color base = i == 1 ? Theme.BLOCKED : Theme.PANEL_SEL;
            g.setColor(sel ? base : Theme.shade(base, 0.6));
            g.fillRoundRect(bx, by, bw, bh, 14, 14);
            if (sel) {
                g.setColor(Color.WHITE);
                g.drawRoundRect(bx, by, bw, bh, 14, 14);
            }
            g.setColor(Color.WHITE);
            g.setFont(Theme.font(Font.BOLD, 17f));
            Gfx.centered(g, names[i], bx + bw / 2, by + bh / 2 + 6);
        }
    }
}
