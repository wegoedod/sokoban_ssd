package sokoban.ui;

import sokoban.core.BlockReason;
import sokoban.core.Direction;
import sokoban.core.Engine;
import sokoban.core.Level;
import sokoban.core.Scoring;
import sokoban.core.Session;
import sokoban.core.StepResult;

import javax.swing.JOptionPane;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.io.IOException;

/** 게임 플레이 화면: 격자 렌더링, HUD, 일시정지, 클리어 결과. */
final class PlayScreen implements Screen {
    /** 막히면 막은 칸이 번쩍이는 시간(ms). */
    private static final long FLASH_MS = 520;

    private enum Mode { PLAYING, CLEARED }

    private final App app;
    private final int levelIndex;
    private final Level level;
    private final Session session;
    private final IsoRenderer renderer = new IsoRenderer();
    private final Camera cam = new Camera();
    private final MouseMoves mouseMoves = new MouseMoves();
    private int mouseX = -1, mouseY = -1;
    /** 한 층짜리 레벨(1~3단계): 2D 탑뷰로 고정하고 시점 회전·z축 이동을 막는다. */
    private final boolean flat;
    private boolean layerFocus;

    private Mode mode = Mode.PLAYING;
    private StepResult animStep;
    private long animElapsed;
    private Direction pending;
    /** 직전에 막힌 입력의 이유(없으면 null)와 그 뒤 경과 시간. */
    private BlockReason blocked;
    private long blockedElapsed;

    private MenuList clearMenu;
    private boolean hasNext;
    // 결과
    private int resScore, resStars;
    private boolean newRecord;
    private final Runnable editorReturn;

    PlayScreen(App app, int levelIndex) {
        this(app, levelIndex, app.repo.levels.get(levelIndex), null);
    }

    PlayScreen(App app, Level level, Runnable editorReturn) {
        this(app, -1, level, editorReturn);
    }

    private PlayScreen(App app, int levelIndex, Level level, Runnable editorReturn) {
        this.app = app;
        this.levelIndex = levelIndex;
        this.level = level;
        this.editorReturn = editorReturn;
        this.layerFocus = app.settings.layerFocusDefault();
        this.session = new Session(level, app.config);
        this.flat = level.h == 1;
        // 첫 3D 레벨(앞 레벨이 모두 한 층)이면 탑뷰에서 시작해 3D 시점으로 확장하는 연출을 건다.
        if (flat) {
            cam.snapToTopView();
        } else if (isFirstMultiLayerLevel()) {
            cam.expandFromTopView();
        } else {
            cam.snapToDefaultView();
        }
    }

    /** 이 레벨보다 앞선 레벨이 모두 한 층짜리(2D)라서, 이 레벨이 3D로 넘어가는 첫 레벨인가. */
    private boolean isFirstMultiLayerLevel() {
        for (int i = 0; i < levelIndex; i++) {
            if (app.repo.levels.get(i).h > 1) return false;
        }
        return true;
    }

    // ---------- 갱신 ----------

    @Override
    public void tick(long dt) {
        cam.update(dt);
        if (blocked != null) {
            blockedElapsed += dt;
            if (blockedElapsed >= FLASH_MS) blocked = null;
        }
        if (animStep == null) return;
        animElapsed += dt;
        if (animElapsed >= app.settings.animMs()) {
            animStep = null;
            if (session.isCleared()) {
                onCleared();
            } else if (pending != null) {
                Direction d = pending;
                pending = null;
                move(d);
            }
        }
    }

    private void move(Direction d) {
        if (animStep != null) {
            pending = d; // 애니메이션 중 입력은 마지막 하나만 큐잉
            return;
        }
        StepResult r = session.move(d);
        if (r != null) {
            blocked = null;
            animStep = r;
            animElapsed = 0;
            if (r.goalOpened()) {
                app.notify(r.openedGoalOrder() + "번 목표가 열렸습니다 · 상자를 넣으세요");
            }
        } else {
            // 막혔다: 이동 횟수에는 포함되지 않는다. 무엇이 막았는지 찾아 화면에 알린다.
            blocked = app.settings.blockFlash() ? Engine.whyBlocked(level, session.state(), d) : null;
            blockedElapsed = 0;
        }
    }

    private void finishAnim() {
        animStep = null;
        pending = null;
        if (session.isCleared() && mode == Mode.PLAYING) onCleared();
    }

    private void onCleared() {
        mode = Mode.CLEARED;
        if (editorReturn != null) {
            clearMenu = new MenuList("다시 도전", "편집으로 돌아가기");
            return;
        }
        resScore = session.score();
        resStars = session.stars();
        newRecord = app.save.update(level.id, resScore, resStars, session.moves());
        if (newRecord) {
            try {
                app.save.save();
            } catch (IOException e) {
                app.notify("기록 저장에 실패했다: " + e.getMessage());
            }
        }
        hasNext = levelIndex + 1 < app.repo.levels.size();
        clearMenu = hasNext
                ? new MenuList("다음 레벨", "다시 도전", "레벨 선택")
                : new MenuList("다시 도전", "레벨 선택");
    }

    // ---------- 입력 ----------

    @Override
    public void keyPressed(KeyEvent e) {
        switch (mode) {
            case PLAYING -> playKey(e);
            case CLEARED -> {
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    if (editorReturn != null) editorReturn.run(); else app.showLevelSelect(levelIndex);
                }
                else clearAction(clearMenu.key(e));
            }
        }
    }

    private void playKey(KeyEvent e) {
        int k = e.getKeyCode();
        switch (k) {
            // WASD: 화면 기준 평면 이동 (한글 입력기는 KeyNormalizer가 영문 키로 보정한다)
            case KeyEvent.VK_W -> move(cam.mapKey(Direction.UP));
            case KeyEvent.VK_A -> move(cam.mapKey(Direction.LEFT));
            case KeyEvent.VK_S -> move(cam.mapKey(Direction.DOWN));
            case KeyEvent.VK_D -> move(cam.mapKey(Direction.RIGHT));
            // ↑ / ↓: z축 위·아래. 한 층짜리 2D 레벨에서는 쓸 z가 없다
            case KeyEvent.VK_UP -> {
                if (!flat) move(Direction.ABOVE);
            }
            case KeyEvent.VK_DOWN -> {
                if (!flat) move(Direction.BELOW);
            }
            // ← / →: 시점 회전 (45°씩, 8방향). ← = 시점이 왼쪽으로 돈다. 2D 탑뷰에서는 고정
            case KeyEvent.VK_LEFT -> {
                if (!flat) cam.rotateStep(1);
            }
            case KeyEvent.VK_RIGHT -> {
                if (!flat) cam.rotateStep(-1);
            }
            case KeyEvent.VK_Z -> {
                finishAnim();
                blocked = null;
                session.undo();
            }
            case KeyEvent.VK_Y -> {
                finishAnim();
                blocked = null;
                session.redo();
            }
            case KeyEvent.VK_V -> {
                if (flat) cam.snapToTopView();
                else cam.resetView();
            }
            case KeyEvent.VK_TAB -> layerFocus = !layerFocus;
            case KeyEvent.VK_R -> reset();
            case KeyEvent.VK_ESCAPE -> {
                finishAnim();
                if (mode == Mode.PLAYING) openSettings();
            }
            default -> {
            }
        }
    }

    private void reset() {
        finishAnim();
        if (mode != Mode.PLAYING) return;
        if (session.moves() != 0) {
            int r = JOptionPane.showConfirmDialog(app.frame,
                    "처음부터 다시 시작할까요?\n이동·되돌리기 횟수가 0으로 돌아갑니다.",
                    "리셋", JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
            app.focusCanvas();
            if (r != JOptionPane.OK_OPTION) return;
        }
        blocked = null;
        session.reset();
    }

    @Override
    public void mouseMoved(int x, int y) {
        mouseX = x;
        mouseY = y;
        if (mode == Mode.CLEARED) clearMenu.hover(x, y);
    }

    @Override
    public void mouseExited() {
        mouseX = mouseY = -1;
    }

    @Override
    public void mouseClicked(int x, int y) {
        if (mode == Mode.CLEARED) clearAction(clearMenu.click(x, y));
        else if (canAcceptClick()) {
            // Rebuild at event time: resizing, undo and keyboard moves must not leave stale hit boxes.
            var size = app.canvasSize();
            mouseMoves.layout(level, session.state(), renderer, cam, boardArea(size.width, size.height));
            MouseMoves.Target target = mouseMoves.pick(x, y);
            if (target != null && target.available()) move(target.direction());
        }
    }

    /** 호버 미리보기를 그릴 수 있는가. 이동 애니메이션 중에는 플레이어가 움직이는 중이라 그리지 않는다. */
    private boolean canClickMove() {
        return mode == Mode.PLAYING && animStep == null && !cam.isAnimating();
    }

    /**
     * 클릭 입력을 받을 수 있는가. 이동 애니메이션(110ms) 중에도 받는다: 키보드와 똑같이 move()가
     * 마지막 입력 하나를 대기열에 넣었다가 애니메이션이 끝나면 실행하므로, 빠르게 클릭해도 입력이 씹히지 않는다.
     * 시점이 회전하는 중에는 화면 좌표와 칸의 대응이 계속 바뀌므로 받지 않는다.
     */
    private boolean canAcceptClick() {
        return mode == Mode.PLAYING && !cam.isAnimating();
    }

    private static Rectangle boardArea(int w, int h) {
        return new Rectangle(20, 112, Math.max(1, w - 40), Math.max(1, h - 178));
    }

    /** Esc: 설정 창. 위쪽에 이 화면에서 할 수 있는 일(계속·처음부터·나가기)을 함께 보여 준다. */
    private void openSettings() {
        var rows = new java.util.ArrayList<SettingsOverlay.Row>();
        rows.add(SettingsOverlay.Row.action("계속", app::closeSettings));
        rows.add(SettingsOverlay.Row.danger("현재 레벨 초기화",
                "이 레벨을 처음부터 다시 시작합니다.\n지금까지의 이동(" + session.moves() + "회)과 되돌리기 기록이 사라집니다.\n계속할까요?",
                () -> {
                    app.closeSettings();
                    blocked = null;
                    animStep = null;
                    pending = null;
                    session.reset();
                }));
        if (editorReturn != null) {
            rows.add(SettingsOverlay.Row.action("편집으로 돌아가기", () -> { app.closeSettings(); editorReturn.run(); }));
        } else {
            rows.add(SettingsOverlay.Row.action("레벨 선택", () -> { app.closeSettings(); app.showLevelSelect(levelIndex); }));
            rows.add(SettingsOverlay.Row.action("메인 메뉴", () -> { app.closeSettings(); app.showMainMenu(); }));
        }
        app.openSettings(rows);
    }

    private void clearAction(int i) {
        if (i < 0) return;
        if (editorReturn != null) {
            if (i == 0) { session.reset(); mode = Mode.PLAYING; animStep = null; pending = null; }
            else editorReturn.run();
            return;
        }
        int idx = hasNext ? i : i + 1; // 마지막 레벨에는 "다음 레벨"이 없다
        switch (idx) {
            case 0 -> app.startLevel(levelIndex + 1);
            case 1 -> app.startLevel(levelIndex);
            case 2 -> app.showLevelSelect(levelIndex);
            default -> {
            }
        }
    }

    // ---------- 그리기 ----------

    @Override
    public void paint(Graphics2D g, int w, int h) {
        Rectangle area = boardArea(w, h);
        IsoRenderer.Anim anim = animStep == null ? null
                : new IsoRenderer.Anim(animStep, Math.min(1.0, (double) animElapsed / app.settings.animMs()));
        if (blocked != null && mode == Mode.PLAYING) {
            renderer.setFlash(blocked.x(), blocked.y(), blocked.z(), flashAlpha());
        } else {
            renderer.clearFlash();
        }
        renderer.render(g, level, session.state(), anim, area, cam, layerFocus);
        if (canClickMove()) {
            mouseMoves.layout(level, session.state(), renderer, cam, area);
            MouseMoves.Target hover = mouseMoves.pick(mouseX, mouseY);
            mouseMoves.paint(g, renderer, level, hover);
            if (blocked != null) helpLine(g, w, BlockText.text(level, session.state(), blocked), 94, Theme.BLOCKED);
            else helpLine(g, w, mouseMoves.description(hover, level), 94);
        } else {
            mouseMoves.clear();
        }
        hud(g, w, h);
        if (mode == Mode.CLEARED) clearOverlay(g, w, h);
    }

    /** 막힌 칸 번쩍임 세기: 처음에 가장 강하고, 두 번 깜빡이며 점점 약해진다. */
    private float flashAlpha() {
        float e = Math.min(1f, blockedElapsed / (float) FLASH_MS);
        return 0.8f * (1f - e) * (0.5f + 0.5f * (float) Math.cos(4 * Math.PI * e));
    }

    private void hud(Graphics2D g, int w, int h) {
        int idx = levelIndex + 1;
        g.setColor(Theme.TEXT);
        g.setFont(Theme.font(Font.BOLD, 24f));
        g.drawString((editorReturn != null ? "테스트 · " : idx + ". ") + level.name, 24, 40);

        double r3 = level.star3Ratio != null ? level.star3Ratio : app.config.star3Ratio;
        double r2 = level.star2Ratio != null ? level.star2Ratio : app.config.star2Ratio;
        g.setFont(Theme.font(Font.PLAIN, 14f));
        g.setColor(Theme.TEXT_DIM);
        g.drawString(editorReturn != null ? "편집 맵 테스트 · Esc 메뉴에서 편집으로 복귀 · 기록 저장 없음"
                : "★★★ " + Scoring.limit(level.optimalMoves, r3) + "수 이하   ★★ "
                + Scoring.limit(level.optimalMoves, r2) + "수 이하", 24, 64);

        g.setFont(Theme.font(Font.BOLD, 20f));
        g.setColor(Theme.TEXT);
        Gfx.right(g, "이동 " + session.moves() + (editorReturn != null ? "" : " / 최적 " + level.optimalMoves), w - 24, 40);
        if (level.hasSequenceGoals()) {
            int done = session.state().filledSequenceGoals(level);
            g.setFont(Theme.font(Font.BOLD, 15f));
            g.setColor(done >= level.sequenceGoalCount ? Theme.GOOD : Theme.ACCENT);
            g.drawString("순서 목표 " + done + " / " + level.sequenceGoalCount
                    + (done >= level.sequenceGoalCount ? " · 모두 채움" : " · 다음 " + (done + 1) + "번"), 24, 90);
        }

        g.setFont(Theme.font(Font.PLAIN, 15f));
        g.setColor(Theme.TEXT_DIM);
        Gfx.right(g, flat
                ? "2D 탑뷰   되돌리기 " + session.undoCount() + "회"
                : "층 " + (level.z(session.state().player) + 1) + " / " + level.h
                        + "   되돌리기 " + session.undoCount() + "회", w - 24, 64);

        if (layerFocus && !flat) {
            g.setFont(Theme.font(Font.BOLD, 15f));
            g.setColor(Theme.ACCENT);
            Gfx.right(g, "층 강조 켜짐  (Tab으로 끄기)", w - 24, 90);
        }

        g.setFont(Theme.font(Font.PLAIN, 14f));
        g.setColor(Theme.TEXT_DIM);
        if (flat) {
            helpLine(g, w, "인접 칸 클릭 / W A S D 이동 (2D 탑뷰 고정)", h - 38);
            helpLine(g, w, "Z 되돌리기   Y 다시 하기   R 리셋   Esc 일시정지", h - 16);
            return;
        }
        helpLine(g, w, "인접 칸 클릭: 6방향 이동   W A S D 평면 이동   ↑ ↓ 위·아래   ← → 시점 회전(45°)", h - 38);
        helpLine(g, w, "Z 되돌리기   Y 다시 하기   R 리셋   Esc 일시정지   V 시점 초기화   Tab 층 강조"
                + (layerFocus ? "(켜짐)" : ""), h - 16);
    }

    /** 하단 도움말 한 줄. 창이 좁으면 글자를 줄여 화면 밖으로 나가지 않게 한다. */
    private void helpLine(Graphics2D g, int w, String text, int y) {
        helpLine(g, w, text, y, Theme.TEXT_DIM);
    }

    private void helpLine(Graphics2D g, int w, String text, int y, Color color) {
        float size = 14f;
        g.setFont(Theme.font(Font.PLAIN, size));
        while (size > 9f && g.getFontMetrics().stringWidth(text) > w - 48) {
            size -= 1f;
            g.setFont(Theme.font(Font.PLAIN, size));
        }
        g.setColor(color);
        Gfx.centered(g, text, w / 2, y);
    }

    private void dim(Graphics2D g, int w, int h) {
        g.setColor(new Color(0, 0, 0, 160));
        g.fillRect(0, 0, w, h);
    }

    private void clearOverlay(Graphics2D g, int w, int h) {
        dim(g, w, h);
        int pw = 420, ph = 470;
        int px = (w - pw) / 2, py = Math.max(10, (h - ph) / 2);
        g.setColor(Theme.PANEL);
        g.fillRoundRect(px, py, pw, ph, 24, 24);
        g.setColor(Theme.ACCENT);
        g.drawRoundRect(px, py, pw, ph, 24, 24);

        g.setColor(Theme.TEXT);
        g.setFont(Theme.font(Font.BOLD, 34f));
        Gfx.centered(g, "클리어!", w / 2, py + 52);
        if (editorReturn == null) Gfx.stars(g, w / 2.0, py + 96, 20, resStars);
        else { g.setFont(Theme.font(Font.PLAIN, 16f)); Gfx.centered(g, "테스트 성공 · 편집 중인 맵은 그대로 보존됩니다", w / 2, py + 96); }

        g.setFont(Theme.font(Font.PLAIN, 18f));
        int y = py + 150;
        String[][] rows = {
                {"이동 횟수", String.valueOf(session.moves())},
                {"최적 횟수", editorReturn != null ? "미검증" : String.valueOf(level.optimalMoves)},
                {"되돌리기", session.undoCount() + "회"},
                {"점수", editorReturn != null ? "기록하지 않음" : String.valueOf(resScore)},
        };
        for (String[] row : rows) {
            g.setColor(Theme.TEXT_DIM);
            g.drawString(row[0], px + 60, y);
            g.setColor(Theme.TEXT);
            Gfx.right(g, row[1], px + pw - 60, y);
            y += 30;
        }
        if (newRecord) {
            g.setColor(Theme.GOOD);
            g.setFont(Theme.font(Font.BOLD, 18f));
            Gfx.centered(g, "신기록!", w / 2, y + 6);
        }
        if (!hasNext && editorReturn == null) {
            g.setColor(Theme.ACCENT);
            g.setFont(Theme.font(Font.BOLD, 16f));
            Gfx.centered(g, "모든 레벨을 완주했습니다. 축하합니다!", w / 2, y + 30);
        }
        int n = hasNext ? 3 : 2;
        clearMenu.paint(g, w / 2, py + ph - 20 - n * 48, 240, 40, 8);
    }
}
