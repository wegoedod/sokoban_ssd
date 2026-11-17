package sokoban.ui;

import sokoban.config.GameConfig;
import sokoban.level.LevelRepository;
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
    public final GameConfig config;
    public final LevelRepository repo;
    public final SaveStore save;
    final JFrame frame = new JFrame("2.5D 소코반");
    private final Canvas canvas = new Canvas();
    private Screen screen;
    private String notice;
    private long noticeUntil;

    public App(Path home) {
        // UC-01: 설정 → 레벨 목록 → 기록 순으로 읽는다.
        this.config = GameConfig.load(home.resolve("config").resolve("game.properties"));
        this.repo = LevelRepository.load(home.resolve("levels"), config);
        this.save = new SaveStore(SaveStore.defaultFile());
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
                    if (screen != null) screen.keyPressed(KeyNormalizer.normalize(e));
                }
            });
            MouseAdapter m = new MouseAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    if (screen != null) screen.mouseMoved(e.getX(), e.getY());
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    if (screen != null) screen.mouseExited();
                }

                @Override
                public void mouseDragged(MouseEvent e) {
                    if (screen != null) screen.mouseDragged(e.getX(), e.getY());
                }

                @Override
                public void mousePressed(MouseEvent e) {
                    if (screen != null) screen.mousePressed(e.getX(), e.getY());
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    if (screen != null) screen.mouseReleased(e.getX(), e.getY());
                }

                @Override
                public void mouseClicked(MouseEvent e) {
                    requestFocusInWindow();
                    if (screen != null && SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 1) {
                        screen.mouseClicked(e.getX(), e.getY());
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
