package sokoban.ui;

import sokoban.config.GameConfig;
import sokoban.core.Level;
import sokoban.level.LevelRepository;
import sokoban.save.GameSettings;
import sokoban.save.SaveStore;
import sokoban.save.TestCode;

import java.awt.Component;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Esc 설정 창: 확인창 규칙(기본은 취소), 설정 저장·복원, 기록 초기화, 테스트 코드 입력. */
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

    // ---------- 테스트 코드 (설정 창) ----------

    /** 설정 창의 코드 항목 하나만 있는 오버레이. 입력한 코드는 실제 TestCode로 넘겨 저장까지 확인한다. */
    private static SettingsOverlay codeOverlay(SaveStore save, List<Level> levels) {
        return new SettingsOverlay(List.of(
                SettingsOverlay.Row.action("계속", () -> { }),
                SettingsOverlay.separator(),
                SettingsOverlay.Row.code("테스트 코드", code -> {
                    try {
                        TestCode.Result r = TestCode.apply(code, levels, save);
                        return r.ok()
                                ? SettingsOverlay.CodeResult.done(r.message())
                                : SettingsOverlay.CodeResult.error(r.message());
                    } catch (java.io.IOException e) {
                        return SettingsOverlay.CodeResult.error("기록 파일을 쓰지 못했습니다: " + e.getMessage());
                    }
                })), () -> { });
    }

    private static List<Level> shippedLevels() {
        LevelRepository repo = LevelRepository.load(Path.of("levels"), GameConfig.defaults());
        if (!repo.errors.isEmpty()) throw new AssertionError("레벨 오류: " + repo.errors);
        return repo.levels;
    }

    /** 코드 항목으로 내려가 Enter로 입력판을 연다(선택은 첫 행에서 시작한다). */
    private static void openCodeEntry(SettingsOverlay o) {
        o.keyPressed(key(KeyEvent.VK_DOWN));
        o.keyPressed(key(KeyEvent.VK_ENTER));
    }

    private static void typeCode(SettingsOverlay o, String digits) {
        for (char c : digits.toCharArray()) o.keyPressed(key(KeyEvent.VK_0 + (c - '0')));
    }

    /** 123456을 넣으면 21개 레벨 모두가 최적 기록(★★★·1000점·최적 이동)으로 저장된다. */
    public static void testCodeCompletesEveryLevel() throws Exception {
        Path f = Files.createTempFile("astro", ".properties");
        List<Level> levels = shippedLevels();
        check(levels.size() == 21, "출고 레벨 21개: " + levels.size());
        SaveStore save = new SaveStore(f);
        SettingsOverlay o = codeOverlay(save, levels);
        openCodeEntry(o);
        check(o.isTypingCode(), "Enter로 코드 입력판이 열린다");
        typeCode(o, TestCode.CODE);
        check(TestCode.CODE.equals(o.typedCode()), "입력한 코드: " + o.typedCode());
        o.keyPressed(key(KeyEvent.VK_ENTER));
        check(!o.isTypingCode(), "확인하면 입력판이 닫힌다");
        check(o.statusOk() && o.statusMessage().contains("모든 레벨 완료 처리"), "성공 안내: " + o.statusMessage());
        for (Level l : levels) {
            SaveStore.Record r = save.get(l.id);
            check(r.bestStars() == 3, l.id + " 별 3개");
            check(r.bestMoves() == l.optimalMoves, l.id + " 최적 이동 " + r.bestMoves());
            check(r.bestScore() == 1000, l.id + " 1000점: " + r.bestScore());
        }
        SaveStore reloaded = new SaveStore(f);
        for (Level l : levels) {
            check(reloaded.cleared(l.id) && reloaded.get(l.id).bestStars() == 3, l.id + " 파일에도 저장됨");
        }
        Files.deleteIfExists(f);
    }

    /** 잘못된 코드는 아무 기록도 바꾸지 않고 오류만 보여 준다. */
    public static void testCodeRejectsWrongCode() throws Exception {
        Path f = Files.createTempFile("astro", ".properties");
        List<Level> levels = shippedLevels();
        SaveStore save = new SaveStore(f);
        save.update(levels.get(0).id, 300, 1, 40);        // 이미 있던 기록
        SettingsOverlay o = codeOverlay(save, levels);
        openCodeEntry(o);
        typeCode(o, "654321");
        o.keyPressed(key(KeyEvent.VK_ENTER));
        check(!o.isTypingCode() && !o.statusOk(), "오답은 실패로 표시된다");
        check(o.statusMessage().contains("올바르지 않은 코드"), "안내: " + o.statusMessage());
        check(save.get(levels.get(0).id).bestStars() == 1 && save.get(levels.get(0).id).bestMoves() == 40,
                "기존 기록은 그대로");
        for (Level l : levels.subList(1, levels.size())) check(!save.cleared(l.id), l.id + " 변경 없음");
        check(!new SaveStore(f).cleared(levels.get(1).id), "파일도 그대로");
        Files.deleteIfExists(f);
    }

    /** 입력판: Backspace·Esc 취소·자릿수 제한. 취소는 기록을 건드리지 않는다. */
    public static void testCodeTypingKeys() throws Exception {
        Path f = Files.createTempFile("astro", ".properties");
        List<Level> levels = shippedLevels();
        SaveStore save = new SaveStore(f);
        SettingsOverlay o = codeOverlay(save, levels);
        openCodeEntry(o);
        typeCode(o, "123");
        o.keyPressed(key(KeyEvent.VK_BACK_SPACE));
        check("12".equals(o.typedCode()), "Backspace: " + o.typedCode());
        o.keyPressed(key(KeyEvent.VK_ESCAPE));
        check(!o.isTypingCode(), "Esc는 입력판만 닫는다");
        check(o.statusMessage() == null, "취소는 결과 메시지를 남기지 않는다");
        check(!save.cleared(levels.get(0).id), "취소는 기록을 건드리지 않는다");
        o.keyPressed(key(KeyEvent.VK_ENTER));           // 같은 항목에서 다시 열기
        check(o.isTypingCode() && o.typedCode().isEmpty(), "다시 열면 빈 입력란에서 시작");
        typeCode(o, "12345678");
        check(o.typedCode().equals(TestCode.CODE), "자릿수를 넘는 입력은 받지 않는다: " + o.typedCode());
        o.keyPressed(key(KeyEvent.VK_ESCAPE));
        Files.deleteIfExists(f);
    }

    /** 마우스로도 입력판을 열고, 취소·확인 버튼이 같은 일을 한다. */
    public static void mouseTestCodeEntry() throws Exception {
        Path f = Files.createTempFile("astro", ".properties");
        List<Level> levels = shippedLevels();
        SaveStore save = new SaveStore(f);
        SettingsOverlay o = codeOverlay(save, levels);
        BufferedImage img = new BufferedImage(900, 700, BufferedImage.TYPE_INT_RGB);
        var g = img.createGraphics();
        o.paint(g, 900, 700);
        var row = o.rowBox(2);                          // 코드 항목
        o.mouseClicked(row.x + row.width / 2, row.y + row.height / 2);
        check(o.isTypingCode(), "코드 항목을 클릭하면 입력판이 열린다");
        o.paint(g, 900, 700);
        var cancel = o.codeBox(0);
        o.mouseClicked(cancel.x + 5, cancel.y + 5);
        check(!o.isTypingCode(), "취소 버튼은 입력판만 닫는다");
        o.mouseClicked(row.x + 5, row.y + 5);
        typeCode(o, TestCode.CODE);
        o.paint(g, 900, 700);
        var ok = o.codeBox(1);
        o.mouseClicked(ok.x + 5, ok.y + 5);
        g.dispose();
        check(!o.isTypingCode() && o.statusOk(), "확인 버튼으로도 적용된다");
        check(save.get(levels.get(0).id).bestStars() == 3, "적용됨");
        Files.deleteIfExists(f);
    }
}
