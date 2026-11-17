package sokoban.ui;

import sokoban.core.Direction;
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
    private static final long ANIM_MS = 110;

    private enum Mode { PLAYING, PAUSED, CLEARED }

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

    private final MenuList pauseMenu = new MenuList("계속", "다시 시작", "레벨 선택", "메인 메뉴");
    private MenuList clearMenu;
    private boolean hasNext;
    // 결과
    private int resScore, resStars;
    private boolean newRecord;

    PlayScreen(App app, int levelIndex) {
        this.app = app;
        this.levelIndex = levelIndex;
        this.level = app.repo.levels.get(levelIndex);
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
        if (animStep == null) return;
        animElapsed += dt;
        if (animElapsed >= ANIM_MS) {
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
            animStep = r;
            animElapsed = 0;
        }
    }

    private void finishAnim() {
        animStep = null;
        pending = null;
        if (session.isCleared() && mode == Mode.PLAYING) onCleared();
    }

    private void onCleared() {
        mode = Mode.CLEARED;
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
            case PAUSED -> {
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    mode = Mode.PLAYING;
                } else {
                    pauseAction(pauseMenu.key(e));
                }
            }
            case CLEARED -> {
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE) app.showLevelSelect(levelIndex);
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
                session.undo();
            }
            case KeyEvent.VK_Y -> {
                finishAnim();
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
                if (mode == Mode.PLAYING) mode = Mode.PAUSED;
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
        session.reset();
    }

    @Override
    public void mouseMoved(int x, int y) {
        mouseX = x;
        mouseY = y;
        if (mode == Mode.PAUSED) pauseMenu.hover(x, y);
        else if (mode == Mode.CLEARED) clearMenu.hover(x, y);
    }

    @Override
    public void mouseExited() {
        mouseX = mouseY = -1;
    }

    @Override
    public void mouseClicked(int x, int y) {
        if (mode == Mode.PAUSED) pauseAction(pauseMenu.click(x, y));
        else if (mode == Mode.CLEARED) clearAction(clearMenu.click(x, y));
        else if (canClickMove()) {
            // Rebuild at event time: resizing, undo and keyboard moves must not leave stale hit boxes.
            var size = app.canvasSize();
            mouseMoves.layout(level, session.state(), renderer, cam, boardArea(size.width, size.height));
            MouseMoves.Target target = mouseMoves.pick(x, y);
            if (target != null && target.available()) move(target.direction());
        }
    }

    private boolean canClickMove() {
        return mode == Mode.PLAYING && animStep == null && !cam.isAnimating();
    }

    private static Rectangle boardArea(int w, int h) {
        return new Rectangle(20, 112, Math.max(1, w - 40), Math.max(1, h - 178));
    }

    private void pauseAction(int i) {
        switch (i) {
            case 0 -> mode = Mode.PLAYING;
            case 1 -> {
                mode = Mode.PLAYING;
                reset();
            }
            case 2 -> app.showLevelSelect(levelIndex);
            case 3 -> app.showMainMenu();
            default -> {
            }
        }
    }

    private void clearAction(int i) {
        if (i < 0) return;
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
                : new IsoRenderer.Anim(animStep, Math.min(1.0, (double) animElapsed / ANIM_MS));
        renderer.render(g, level, session.state(), anim, area, cam, layerFocus);
        if (canClickMove()) {
            mouseMoves.layout(level, session.state(), renderer, cam, area);
            MouseMoves.Target hover = mouseMoves.pick(mouseX, mouseY);
            mouseMoves.paint(g, renderer, level, hover);
            helpLine(g, w, mouseMoves.description(hover, level), 94);
        } else {
            mouseMoves.clear();
        }
        hud(g, w, h);
        if (mode == Mode.PAUSED) pauseOverlay(g, w, h);
        if (mode == Mode.CLEARED) clearOverlay(g, w, h);
    }

    private void hud(Graphics2D g, int w, int h) {
        int idx = levelIndex + 1;
        g.setColor(Theme.TEXT);
        g.setFont(Theme.font(Font.BOLD, 24f));
        g.drawString(idx + ". " + level.name, 24, 40);

        double r3 = level.star3Ratio != null ? level.star3Ratio : app.config.star3Ratio;
        double r2 = level.star2Ratio != null ? level.star2Ratio : app.config.star2Ratio;
        g.setFont(Theme.font(Font.PLAIN, 14f));
        g.setColor(Theme.TEXT_DIM);
        g.drawString("★★★ " + Scoring.limit(level.optimalMoves, r3) + "수 이하   ★★ "
                + Scoring.limit(level.optimalMoves, r2) + "수 이하", 24, 64);

        g.setFont(Theme.font(Font.BOLD, 20f));
        g.setColor(Theme.TEXT);
        Gfx.right(g, "이동 " + session.moves() + " / 최적 " + level.optimalMoves, w - 24, 40);
        g.setFont(Theme.font(Font.PLAIN, 15f));
        g.setColor(Theme.TEXT_DIM);
        Gfx.right(g, flat
                ? "2D 탑뷰   되돌리기 " + session.undoCount() + "회"
                : "층 " + (level.z(session.state().player) + 1) + " / " + level.h
                        + "   되돌리기 " + session.undoCount() + "회", w - 24, 64);

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
        float size = 14f;
        g.setFont(Theme.font(Font.PLAIN, size));
        while (size > 9f && g.getFontMetrics().stringWidth(text) > w - 48) {
            size -= 1f;
            g.setFont(Theme.font(Font.PLAIN, size));
        }
        g.setColor(Theme.TEXT_DIM);
        Gfx.centered(g, text, w / 2, y);
    }

    private void dim(Graphics2D g, int w, int h) {
        g.setColor(new Color(0, 0, 0, 160));
        g.fillRect(0, 0, w, h);
    }

    private void pauseOverlay(Graphics2D g, int w, int h) {
        dim(g, w, h);
        g.setColor(Theme.TEXT);
        g.setFont(Theme.font(Font.BOLD, 36f));
        Gfx.centered(g, "일시정지", w / 2, h / 2 - 150);
        pauseMenu.paint(g, w / 2, h / 2 - 110, 260, 50, 12);
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
        Gfx.stars(g, w / 2.0, py + 96, 20, resStars);

        g.setFont(Theme.font(Font.PLAIN, 18f));
        int y = py + 150;
        String[][] rows = {
                {"이동 횟수", String.valueOf(session.moves())},
                {"최적 횟수", String.valueOf(level.optimalMoves)},
                {"되돌리기", session.undoCount() + "회"},
                {"점수", String.valueOf(resScore)},
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
        if (!hasNext) {
            g.setColor(Theme.ACCENT);
            g.setFont(Theme.font(Font.BOLD, 16f));
            Gfx.centered(g, "모든 레벨을 완주했습니다. 축하합니다!", w / 2, y + 30);
        }
        int n = hasNext ? 3 : 2;
        clearMenu.paint(g, w / 2, py + ph - 20 - n * 48, 240, 40, 8);
    }
}
