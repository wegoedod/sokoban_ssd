package sokoban.ui;

import sokoban.save.GameSettings;
import sokoban.save.SaveStore;

import java.awt.Component;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Esc 설정 창: 확인창 규칙(기본은 취소), 설정 저장·복원, 기록 초기화. */
public final class SettingsTest {
    private SettingsTest() {
    }

    private static void check(boolean c, String msg) {
        if (!c) throw new AssertionError(msg);
    }

    private static final Component SRC = new java.awt.Canvas();

    private static KeyEvent key(int code) {
        return new KeyEvent(SRC, KeyEvent.KEY_PRESSED, 0, 0, code, KeyEvent.CHAR_UNDEFINED);
    }

    private static SettingsOverlay overlay(int[] ran, int[] closed) {
        return new SettingsOverlay(List.of(
                SettingsOverlay.Row.action("계속", () -> ran[0]++),
                SettingsOverlay.separator(),
                SettingsOverlay.Row.danger("초기화", "경고", () -> ran[1]++)), () -> closed[0]++);
    }

    /** 위험 항목은 확인창을 거쳐야 실행되고, 기본 선택은 취소이며 Esc도 취소다. */
    public static void dangerNeedsConfirm() {
        int[] ran = new int[2], closed = new int[1];
        SettingsOverlay o = overlay(ran, closed);
        o.keyPressed(key(KeyEvent.VK_DOWN));          // 구분선을 건너뛰어 위험 항목으로
        o.keyPressed(key(KeyEvent.VK_ENTER));
        check(o.isConfirming(), "위험 항목은 확인창이 떠야 한다");
        check(ran[1] == 0, "확인 전에는 실행되면 안 된다");
        o.keyPressed(key(KeyEvent.VK_ENTER));         // 기본 선택 = 취소
        check(!o.isConfirming() && ran[1] == 0, "Enter 한 번(기본)은 취소여야 한다");
        o.keyPressed(key(KeyEvent.VK_ENTER));
        o.keyPressed(key(KeyEvent.VK_ESCAPE));
        check(!o.isConfirming() && ran[1] == 0 && closed[0] == 0, "Esc는 확인창만 닫는다");
        o.keyPressed(key(KeyEvent.VK_ENTER));
        o.keyPressed(key(KeyEvent.VK_RIGHT));         // 확인으로 이동
        o.keyPressed(key(KeyEvent.VK_ENTER));
        check(ran[1] == 1 && !o.isConfirming(), "확인을 눌러야 실행된다");
        o.keyPressed(key(KeyEvent.VK_ESCAPE));
        check(closed[0] == 1, "확인창이 없을 때 Esc는 설정 창을 닫는다");
    }

    /** 그려진 위치를 클릭해도 같은 규칙이 적용된다. */
    public static void mouseConfirmFlow() {
        int[] ran = new int[2], closed = new int[1];
        SettingsOverlay o = overlay(ran, closed);
        BufferedImage img = new BufferedImage(900, 700, BufferedImage.TYPE_INT_RGB);
        var g = img.createGraphics();
        o.paint(g, 900, 700);
        var row = o.rowBox(2);                         // 위험 항목
        o.mouseClicked(row.x + row.width / 2, row.y + row.height / 2);
        check(o.isConfirming() && ran[1] == 0, "클릭하면 확인창만 뜬다");
        o.paint(g, 900, 700);
        var cancel = o.confirmBox(0);
        o.mouseClicked(cancel.x + 5, cancel.y + 5);
        check(!o.isConfirming() && ran[1] == 0, "취소 버튼은 실행하지 않는다");
        o.mouseClicked(row.x + 5, row.y + 5);
        o.paint(g, 900, 700);
        var ok = o.confirmBox(1);
        o.mouseClicked(ok.x + 5, ok.y + 5);
        g.dispose();
        check(!o.isConfirming() && ran[1] == 1, "확인 버튼을 눌러야 실행된다");
    }

    /** 설정은 파일로 저장·복원되고, 손상된 파일은 기본값으로 시작한다. */
    public static void settingsPersist() throws Exception {
        Path f = Files.createTempFile("astro", ".properties");
        GameSettings s = new GameSettings(f);
        check(s.speed() == 1 && s.animMs() == 110 && s.blockFlash() && !s.layerFocusDefault(), "기본값");
        s.cycleSpeed();
        s.toggleBlockFlash();
        s.toggleLayerFocusDefault();
        s.save();
        GameSettings t = new GameSettings(f);
        check(t.speed() == 2 && t.animMs() == 70 && !t.blockFlash() && t.layerFocusDefault(), "저장 뒤 복원");
        t.cycleSpeed();
        check(t.speed() == 0 && t.animMs() == 170, "속도는 한 바퀴 돈다");
        t.resetToDefaults();
        check(t.speed() == 1 && t.blockFlash() && !t.layerFocusDefault(), "기본값 복원");
        Files.writeString(f, "animSpeed=zzz\nblockFlash=maybe\n");
        GameSettings bad = new GameSettings(f);
        check(bad.speed() == 1 && bad.blockFlash(), "잘못된 값은 기본값");
        Files.writeString(f, "animSpeed=9\n");
        check(new GameSettings(f).speed() == 1, "범위 밖 속도는 기본값");
        Files.delete(f);
        check(new GameSettings(f).speed() == 1, "파일이 없으면 기본값");
    }

    /** 진행 기록 초기화는 메모리와 파일을 모두 비운다. */
    public static void clearProgress() throws Exception {
        Path f = Files.createTempFile("astro", ".properties");
        SaveStore st = new SaveStore(f);
        st.update("L01", 900, 3, 5);
        st.update("L02", 500, 1, 9);
        st.save();
        check(new SaveStore(f).cleared("L02"), "저장 확인");
        st.clearAll();
        check(!st.cleared("L01") && st.get("L02").bestScore() == 0, "메모리에서 지워짐");
        st.save();
        SaveStore again = new SaveStore(f);
        check(!again.cleared("L01") && !again.cleared("L02"), "파일에서도 지워짐");
        Files.deleteIfExists(f);
    }
}
