package sokoban.ui;

import sokoban.config.GameConfig;
import sokoban.level.LevelRepository;
import sokoban.save.GameSettings;
import sokoban.save.SaveStore;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.Timer;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.file.Path;

/** 애플리케이션 루트: 창, 화면 전환, 공용 데이터(설정·레벨·기록). 모든 UI 갱신은 EDT에서 한다 (NFR-5). */
public final class App {
    private final Path home;
    public final GameConfig config;
    public final LevelRepository repo;
    public final SaveStore save;
    public final GameSettings settings;
    private SettingsOverlay overlay;
    final JFrame frame = new JFrame("2.5D 소코반");
    private final Canvas canvas = new Canvas();
    private Screen screen;
    private String notice;
    private long noticeUntil;

    public App(Path home) {
        this.home = home;
        // UC-01: 설정 → 레벨 목록 → 기록 순으로 읽는다.
        this.config = GameConfig.load(home.resolve("config").resolve("game.properties"));
        this.repo = LevelRepository.load(home.resolve("levels"), config);
        this.save = new SaveStore(SaveStore.defaultFile());
        this.settings = new GameSettings(GameSettings.defaultFile());
        config.warnings.forEach(w -> System.err.println("[설정 경고] " + w));
        repo.errors.forEach(e -> System.err.println("[레벨 오류] " + e));
        if (save.loadWarning() != null) System.err.println("[저장 경고] " + save.loadWarning());
    }

    public void start() {
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setContentPane(canvas);
        canvas.setPreferredSize(new Dimension(1000, 700));
        frame.pack();
        frame.setMinimumSize(new Dimension(640, 480));
        frame.setLocationRelativeTo(null);
        showMainMenu();
        if (!repo.errors.isEmpty()) {
            notify("레벨 파일 오류 " + repo.errors.size() + "건: " + repo.errors.get(0));
        } else if (save.loadWarning() != null) {
            notify(save.loadWarning());
        }
        frame.setVisible(true);
        canvas.requestFocusInWindow();
        new Timer(15, new java.awt.event.ActionListener() {
            long last = System.nanoTime();

            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                long now = System.nanoTime();
                long dt = (now - last) / 1_000_000;
                last = now;
                if (screen != null) screen.tick(dt);
                canvas.repaint();
            }
        }).start();
        if (Boolean.getBoolean("sokoban.editor")) showLevelDesigner();
    }

    // ---------- 설정 창 (Esc) ----------

    /**
     * 설정 창을 연다. context는 그 화면에서만 할 수 있는 항목(계속·나가기 등)이고, 비어 있으면 "닫기"만 둔다.
     * 열려 있는 동안 키·마우스 입력은 설정 창이 모두 가져간다.
     */
    void openSettings(java.util.List<SettingsOverlay.Row> context) {
        var rows = new java.util.ArrayList<SettingsOverlay.Row>(context);
        if (!rows.isEmpty()) rows.add(SettingsOverlay.separator());
        rows.add(SettingsOverlay.Row.option("애니메이션 속도", () -> GameSettings.SPEED_NAMES[settings.speed()],
                () -> { settings.cycleSpeed(); persistSettings(); }));
        rows.add(SettingsOverlay.Row.option("막힌 칸 번쩍임", () -> settings.blockFlash() ? "켜짐" : "꺼짐",
                () -> { settings.toggleBlockFlash(); persistSettings(); }));
        rows.add(SettingsOverlay.Row.option("층 강조 기본값", () -> settings.layerFocusDefault() ? "켜짐" : "꺼짐",
                () -> { settings.toggleLayerFocusDefault(); persistSettings(); }));
        rows.add(SettingsOverlay.Row.option("창 크기", () -> maximized() ? "최대화" : "기본",
                () -> frame.setExtendedState(maximized() ? JFrame.NORMAL : JFrame.MAXIMIZED_BOTH)));
        rows.add(SettingsOverlay.Row.option("설정 기본값으로", () -> "실행",
                () -> { settings.resetToDefaults(); persistSettings(); }));
        rows.add(SettingsOverlay.separator());
        rows.add(SettingsOverlay.Row.danger("진행 기록 초기화",
                "모든 레벨의 클리어 기록(별·점수·최소 이동)이 삭제되고\n레벨 해금도 처음 상태로 돌아갑니다.\n이 작업은 되돌릴 수 없습니다. 계속할까요?",
                this::resetProgress));
        if (context.isEmpty()) rows.add(SettingsOverlay.Row.action("닫기", this::closeSettings));
        overlay = new SettingsOverlay(rows, this::closeSettings);
    }

    void closeSettings() {
        overlay = null;
        canvas.requestFocusInWindow();
    }

    boolean settingsOpen() {
        return overlay != null;
    }

    private boolean maximized() {
        return (frame.getExtendedState() & JFrame.MAXIMIZED_BOTH) == JFrame.MAXIMIZED_BOTH;
    }

    private void persistSettings() {
        try {
            settings.save();
        } catch (java.io.IOException e) {
            notify("설정을 저장하지 못했습니다: " + e.getMessage());
        }
    }

    private void resetProgress() {
        save.clearAll();
        try {
            save.save();
            notify("진행 기록을 초기화했습니다.");
        } catch (java.io.IOException e) {
            notify("기록 파일을 쓰지 못했습니다: " + e.getMessage());
        }
        closeSettings();
        // 레벨 선택·플레이 도중이면 해금 상태가 바뀌었으니 안전한 화면으로 돌아간다.
        if (!(screen instanceof MainMenuScreen)) showMainMenu();
    }

    // ---------- 화면 전환 ----------

    void setScreen(Screen s) {
        this.screen = s;
        canvas.requestFocusInWindow();
    }

    /** 모달 대화상자 등을 닫은 뒤 키 입력을 받는 캔버스로 포커스를 되돌린다. */
    void focusCanvas() {
        canvas.requestFocusInWindow();
    }

    Dimension canvasSize() {
        return canvas.getSize();
    }

    void showMainMenu() {
        setScreen(new MainMenuScreen(this));
    }

    void showLevelDesigner() {
        try {
            var editor = new sokoban.editor.LevelDesigner(home.resolve("config/editor-blocks.properties"));
            editor.setPlaytest(this::testEditorLevel);
            editor.openIn(frame, () -> { showMainMenu(); focusCanvas(); });
        } catch (Exception e) {
            notify("레벨 디자이너를 열 수 없습니다: " + e.getMessage());
        }
    }

    private void testEditorLevel(sokoban.core.Level level) {
        var editorContent = frame.getContentPane();
        String editorTitle = frame.getTitle();
        frame.setContentPane(canvas);
        frame.setTitle("레벨 테스트 — " + level.name);
        setScreen(new PlayScreen(this, level, () -> {
            showMainMenu();
            frame.setContentPane(editorContent);
            frame.setTitle(editorTitle);
            frame.revalidate(); frame.repaint();
        }));
        frame.revalidate(); frame.repaint(); focusCanvas();
    }

    void showLevelSelect(int focus) {
        setScreen(new LevelSelectScreen(this, focus));
    }

    void startLevel(int index) {
        setScreen(new PlayScreen(this, index));
    }

    /** 이전 레벨을 별 1개 이상으로 클리어하면 해금 (FR-5.7). */
    boolean isUnlocked(int index) {
        return index == 0 || save.cleared(repo.levels.get(index - 1).id);
    }

    int firstOpenLevel() {
        for (int i = 0; i < repo.levels.size(); i++) {
            if (isUnlocked(i) && !save.cleared(repo.levels.get(i).id)) return i;
        }
        return Math.max(0, repo.levels.size() - 1);
    }

    /** 잠깐 떠 있는 안내 메시지. */
    void notify(String msg) {
        notice = msg;
        noticeUntil = System.currentTimeMillis() + 5000;
    }

    // ---------- 화면 호스트 ----------

    private final class Canvas extends JPanel {
        Canvas() {
            setFocusable(true);
            setFocusTraversalKeysEnabled(false); // Tab을 게임 키로 쓴다
            enableInputMethods(false);          // 한글 입력기가 단축키를 가로채지 않게 한다
            setDoubleBuffered(true);
            addKeyListener(new KeyAdapter() {
                @Override
                public void keyPressed(KeyEvent e) {
                    if (overlay != null) overlay.keyPressed(KeyNormalizer.normalize(e));
                    else if (screen != null) screen.keyPressed(KeyNormalizer.normalize(e));
                }
            });
            MouseAdapter m = new MouseAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    if (overlay != null) overlay.mouseMoved(e.getX(), e.getY());
                    else if (screen != null) screen.mouseMoved(e.getX(), e.getY());
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    if (screen != null) screen.mouseExited();
                }

                @Override
                public void mouseDragged(MouseEvent e) {
                    if (overlay == null && screen != null) screen.mouseDragged(e.getX(), e.getY());
                }

                @Override
                public void mousePressed(MouseEvent e) {
                    if (overlay == null && screen != null) screen.mousePressed(e.getX(), e.getY());
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    if (overlay == null && screen != null) screen.mouseReleased(e.getX(), e.getY());
                }

                @Override
                public void mouseClicked(MouseEvent e) {
                    requestFocusInWindow();
                    if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 1) {
                        if (overlay != null) overlay.mouseClicked(e.getX(), e.getY());
                        else if (screen != null) screen.mouseClicked(e.getX(), e.getY());
                    }
                }
            };
            addMouseListener(m);
            addMouseMotionListener(m);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            g.setPaint(new GradientPaint(0, 0, Theme.BG_TOP, 0, h, Theme.BG_BOTTOM));
            g.fillRect(0, 0, w, h);
            if (screen != null) screen.paint(g, w, h);
            if (overlay != null) overlay.paint(g, w, h);
            if (notice != null) {
                if (System.currentTimeMillis() > noticeUntil) {
                    notice = null;
                } else {
                    g.setFont(Theme.font(Font.PLAIN, 15f));
                    int tw = g.getFontMetrics().stringWidth(notice);
                    int bw = Math.min(w - 20, tw + 30);
                    g.setColor(new Color(0, 0, 0, 200));
                    g.fillRoundRect((w - bw) / 2, h - 60, bw, 36, 12, 12);
                    g.setColor(Theme.ACCENT);
                    g.setClip((w - bw) / 2, h - 60, bw, 36);
                    g.drawString(notice, (w - tw) / 2 > 10 ? (w - tw) / 2 : (w - bw) / 2 + 15, h - 37);
                }
            }
            g.dispose();
        }
    }
}
