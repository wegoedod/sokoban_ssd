package sokoban.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Esc로 여는 설정 창. 어느 화면 위에든 겹쳐 그려지고, 열려 있는 동안 입력을 모두 가져간다.
 * 되돌릴 수 없는 항목은 경고 문구와 [취소]/[확인] 확인창을 거쳐야 실행된다(기본 선택은 취소).
 * 코드 항목({@link Row#code})은 숫자를 입력받는 입력판을 띄우고, 제출 결과를 창 아래에 성공/오류로 보여 준다.
 */
final class SettingsOverlay implements Screen {
    /** 코드 입력 결과. ok면 성공(초록), 아니면 오류(빨강)로 표시한다. */
    record CodeResult(boolean ok, String message) {
        static CodeResult done(String message) {
            return new CodeResult(true, message);
        }

        static CodeResult error(String message) {
            return new CodeResult(false, message);
        }
    }

    /**
     * 한 줄. value가 있으면 값이 오른쪽에 표시되고, 실행하면 값이 바뀐다. confirm이 있으면 먼저 확인창을 띄우고,
     * code가 있으면 코드 입력판을 띄운다(제출하면 결과 메시지를 돌려받는다).
     */
    record Row(String label, Supplier<String> value, Runnable action, boolean danger, String confirm,
               Function<String, CodeResult> code) {
        static Row action(String label, Runnable action) {
            return new Row(label, null, action, false, null, null);
        }

        static Row option(String label, Supplier<String> value, Runnable action) {
            return new Row(label, value, action, false, null, null);
        }

        static Row danger(String label, String confirm, Runnable action) {
            return new Row(label, null, action, true, confirm, null);
        }

        /** 누르면 숫자 입력판이 열린다. Enter/확인으로 제출하고, 결과 메시지는 설정 창 아래에 남는다. */
        static Row code(String label, Function<String, CodeResult> onSubmit) {
            return new Row(label, null, null, false, null, onSubmit);
        }

        boolean isCode() {
            return code != null;
        }
    }

    /** 코드 자릿수. 입력판은 이 자릿수까지만 받는다. */
    static final int CODE_LENGTH = 6;

    /** 제목 줄 구분선처럼 쓰는 소제목. */
    private static final Row SEPARATOR = new Row(null, null, null, false, null, null);

    private final List<Row> rows;
    private final Runnable onClose;
    private int selected;
    private final Rectangle[] boxes;
    // 확인창
    private Row pending;
    private int confirmSel;
    private final Rectangle[] confirmBoxes = {new Rectangle(), new Rectangle()};
    // 코드 입력판
    private final int codeRow;
    private boolean typing;
    private final StringBuilder typed = new StringBuilder();
    private String status;
    private boolean statusOk;
    private final Rectangle[] codeBoxes = {new Rectangle(), new Rectangle()};

    SettingsOverlay(List<Row> rows, Runnable onClose) {
        this.rows = rows;
        this.onClose = onClose;
        this.boxes = new Rectangle[rows.size()];
        for (int i = 0; i < boxes.length; i++) boxes[i] = new Rectangle();
        int found = -1;
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).isCode()) {
                found = i;
                break;
            }
        }
        codeRow = found;
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

    /** 마지막으로 그린 코드 입력판 버튼(0=취소, 1=확인)의 영역(테스트용). */
    Rectangle codeBox(int i) {
        return new Rectangle(codeBoxes[i]);
    }

    boolean isConfirming() {
        return pending != null;
    }

    String confirmMessage() {
        return pending == null ? null : pending.confirm();
    }

    /** 코드 입력판이 열려 있는가(테스트용). */
    boolean isTypingCode() {
        return typing;
    }

    /** 지금까지 입력한 코드(테스트용). */
    String typedCode() {
        return typed.toString();
    }

    /** 마지막 코드 제출의 결과 메시지. 제출한 적이 없으면 null. */
    String statusMessage() {
        return status;
    }

    boolean statusOk() {
        return statusOk;
    }

    // ---------- 입력 ----------

    @Override
    public void keyPressed(KeyEvent e) {
        int k = e.getKeyCode();
        if (typing) {
            if (k == KeyEvent.VK_ESCAPE) {
                cancelTyping();
            } else if (k == KeyEvent.VK_ENTER) {
                submitCode();
            } else if (k == KeyEvent.VK_BACK_SPACE || k == KeyEvent.VK_DELETE) {
                if (typed.length() > 0) typed.deleteCharAt(typed.length() - 1);
            } else {
                int digit = digitOf(k);
                if (digit >= 0 && typed.length() < CODE_LENGTH) typed.append((char) ('0' + digit));
            }
            return;
        }
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

    /** 숫자 키(본판·숫자패드)면 0~9, 아니면 -1. */
    private static int digitOf(int keyCode) {
        if (keyCode >= KeyEvent.VK_0 && keyCode <= KeyEvent.VK_9) return keyCode - KeyEvent.VK_0;
        if (keyCode >= KeyEvent.VK_NUMPAD0 && keyCode <= KeyEvent.VK_NUMPAD9) return keyCode - KeyEvent.VK_NUMPAD0;
        return -1;
    }

    @Override
    public void mouseMoved(int x, int y) {
        if (typing) {
            if (codeBoxes[0].contains(x, y)) confirmSel = 0;
            else if (codeBoxes[1].contains(x, y)) confirmSel = 1;
            return;
        }
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
        if (typing) {
            if (codeBoxes[0].contains(x, y)) cancelTyping();
            else if (codeBoxes[1].contains(x, y)) submitCode();
            return;
        }
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
        if (r.isCode()) {
            typing = true;
            typed.setLength(0);
            confirmSel = 1;          // 기본 선택은 확인
            status = null;
            statusOk = false;
            return;
        }
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

    /** 입력한 코드를 제출한다. 성공·실패 모두 설정 창 아래에 결과를 남기고 입력판은 닫는다. */
    private void submitCode() {
        CodeResult result = rows.get(codeRow).code().apply(typed.toString());
        typing = false;
        typed.setLength(0);
        status = result == null ? null : result.message();
        statusOk = result != null && result.ok();
    }

    private void cancelTyping() {
        typing = false;
        typed.setLength(0);
    }

    // ---------- 그리기 ----------

    @Override
    public void paint(Graphics2D g, int w, int h) {
        g.setColor(new Color(0, 0, 0, 170));
        g.fillRect(0, 0, w, h);

        int pw = Math.min(w - 20, 540);
        int n = rows.size();
        int head = 70, foot = status == null ? 36 : 64, gap = 8;
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
            boolean sel = i == selected && pending == null && !typing;
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
            } else if (r.isCode() && i == codeRow) {
                g.setColor(Theme.TEXT_DIM);
                String v = "Enter";
                g.drawString(v, b.x + b.width - 16 - fm.stringWidth(v), base);
            }
            y += rowH + gap;
        }
        if (status != null) {
            g.setFont(Theme.font(Font.BOLD, 14f));
            g.setColor(statusOk ? Theme.GOOD : Theme.BLOCKED);
            Gfx.centered(g, status, w / 2, py + ph - 38);
        }
        g.setColor(Theme.TEXT_DIM);
        g.setFont(Theme.font(Font.PLAIN, 13f));
        Gfx.centered(g, "↑↓ 선택   Enter/클릭 실행   Esc 닫기", w / 2, py + ph - 14);

        if (pending != null) paintConfirm(g, w, h);
        if (typing) paintCodeEntry(g, w, h);
    }

    private void paintConfirm(Graphics2D g, int w, int h) {
        g.setColor(new Color(0, 0, 0, 120));
        g.fillRect(0, 0, w, h);
        int pw = Math.min(w - 20, 460), ph = 230;
        int px = (w - pw) / 2, py = Math.max(10, (h - ph) / 2);
        g.setColor(Theme.PANEL);
        g.fillRoundRect(px, py, pw, ph, 22, 22);
        g.setColor(Theme.BLOCKED);
        g.setStroke(new BasicStroke(2f));
        g.drawRoundRect(px, py, pw, ph, 22, 22);
        g.setStroke(new BasicStroke(1f));

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

    /** 숫자 입력판: 자릿수만큼 칸을 그려 지금까지 입력한 숫자를 보여 준다. */
    private void paintCodeEntry(Graphics2D g, int w, int h) {
        g.setColor(new Color(0, 0, 0, 140));
        g.fillRect(0, 0, w, h);
        int pw = Math.min(w - 20, 460), ph = 264;
        int px = (w - pw) / 2, py = Math.max(10, (h - ph) / 2);
        g.setColor(Theme.PANEL);
        g.fillRoundRect(px, py, pw, ph, 22, 22);
        g.setColor(Theme.ACCENT);
        g.setStroke(new BasicStroke(2f));
        g.drawRoundRect(px, py, pw, ph, 22, 22);
        g.setStroke(new BasicStroke(1f));

        g.setColor(Theme.TEXT);
        g.setFont(Theme.font(Font.BOLD, 24f));
        Gfx.centered(g, "테스트 코드", w / 2, py + 44);
        g.setColor(Theme.TEXT_DIM);
        g.setFont(Theme.font(Font.PLAIN, 14f));
        Gfx.centered(g, "숫자 " + CODE_LENGTH + "자리를 입력하고 Enter로 확인하세요 (Esc 취소)", w / 2, py + 70);

        int iw = pw - 80, ih = 58, ix = px + 40, iy = py + 88;
        g.setColor(Theme.shade(Theme.PANEL, 0.7));
        g.fillRoundRect(ix, iy, iw, ih, 16, 16);
        g.setColor(Theme.ACCENT);
        g.drawRoundRect(ix, iy, iw, ih, 16, 16);
        g.setFont(Theme.font(Font.BOLD, 28f));
        FontMetrics fm = g.getFontMetrics();
        double slot = (iw - 40) / (double) CODE_LENGTH;
        for (int i = 0; i < CODE_LENGTH; i++) {
            String s = i < typed.length() ? String.valueOf(typed.charAt(i)) : "·";
            int cx = (int) (ix + 20 + slot * (i + 0.5));
            g.setColor(i < typed.length() ? Theme.TEXT : Theme.TEXT_DIM);
            Gfx.centered(g, s, cx, iy + ih / 2 + fm.getAscent() / 2 - 2);
        }

        String[] names = {"취소", "확인"};
        int bw = 130, bh = 42, by = py + ph - bh - 22;
        for (int i = 0; i < 2; i++) {
            int bx = w / 2 + (i == 0 ? -bw - 10 : 10);
            codeBoxes[i].setBounds(bx, by, bw, bh);
            boolean sel = i == confirmSel;
            Color base = Theme.PANEL_SEL;
            g.setColor(sel ? base : Theme.shade(base, 0.6));
            g.fillRoundRect(bx, by, bw, bh, 14, 14);
            if (sel) {
                g.setColor(i == 1 ? Theme.ACCENT : Color.WHITE);
                g.drawRoundRect(bx, by, bw, bh, 14, 14);
            }
            g.setColor(Color.WHITE);
            g.setFont(Theme.font(Font.BOLD, 17f));
            Gfx.centered(g, names[i], bx + bw / 2, by + bh / 2 + 6);
        }
    }
}
