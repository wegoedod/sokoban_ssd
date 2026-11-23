package sokoban.ui;

import sokoban.core.Level;
import sokoban.save.SaveStore;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.awt.geom.Line2D;
import java.awt.geom.RoundRectangle2D;
import java.util.List;

/**
 * 레벨 선택 (FR-5.7): 지구에서 출발해 우주를 탐험하는 지도.
 *
 * <p>레벨은 행성이다. 레벨 1은 지구이고, 행성들은 지그재그로 이어진 길 위에 놓인다. 로켓은 선택한 행성까지
 * 한 행성씩 뛰어서 이동하고(좌우 키), 도착한 행성에서 Enter로 착륙(레벨 시작)한다. 해금·별·기록은 행성에 그대로 표시한다.
 * 카메라는 로켓을 따라 가로로 부드럽게 움직이고, 길이 화면보다 길어도 모든 행성에 닿을 수 있다.
 */
final class LevelSelectScreen implements Screen {
    /** 앱을 켠 뒤 처음 열 때만 지구에서 출발하는 장면을 보여 준다(레벨을 풀고 돌아올 때마다 반복하지 않는다). */
    private static boolean introPlayed = false;

    private final App app;
    private final List<Level> levels;
    private final SpaceMap map;

    private int selected;
    private int hover = -1;
    private double camX;
    private double timeMs;
    private String status = "";

    // 마지막 그리기에서 계산한 배치 (마우스 판정에 쓴다)
    private int lastW = 1000, lastH = 700, centerY = 300;
    private double amplitude = 60, planetR = 30;
    private final Rectangle landButton = new Rectangle();

    LevelSelectScreen(App app, int focus) {
        this.app = app;
        this.levels = app.repo.levels;
        this.map = new SpaceMap(levels.size());
        this.selected = Math.max(0, Math.min(focus, Math.max(0, levels.size() - 1)));
        // 처음 열 때는 지구(레벨 1)에서 출발해 선택된 행성까지 날아간다
        map.placeAt(introPlayed ? selected : 0);
        introPlayed = true;
        camX = map.rocketX();
    }

    // ---------------------------------------------------------------
    // 갱신
    // ---------------------------------------------------------------

    @Override
    public void tick(long dt) {
        timeMs += dt;
        map.step(dt, selected);
        camX = SpaceMap.cameraStep(camX, map.rocketX(), dt);
    }

    // ---------------------------------------------------------------
    // 그리기
    // ---------------------------------------------------------------

    @Override
    public void paint(Graphics2D g, int w, int h) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        lastW = w;
        lastH = h;
        centerY = (int) (h * 0.43);
        amplitude = Math.max(28, Math.min(78, h * 0.10));
        planetR = Math.max(20, Math.min(34, h * 0.05));

        PlanetArt.drawSpace(g, w, h, camX, timeMs);
        if (levels.isEmpty()) {
            g.setColor(Theme.TEXT_DIM);
            g.setFont(Theme.font(Font.PLAIN, 18f));
            Gfx.centered(g, "불러온 레벨이 없습니다.", w / 2, h / 2);
            return;
        }

        drawRoute(g, w);
        for (int i = 0; i < levels.size(); i++) {
            double sx = screenX(i);
            if (sx < -planetR * 4 || sx > w + planetR * 4) continue;
            drawNode(g, i, sx, screenY(i));
        }
        drawRocket(g);
        if (hover >= 0 && hover != selected) drawHoverLabel(g, hover);

        title(g, w);
        infoPanel(g, w, h);
        footer(g, w, h);
    }

    private double screenX(int i) {
        return lastW / 2.0 + SpaceMap.nodeX(i) - camX;
    }

    private double screenY(int i) {
        return centerY + SpaceMap.nodeY(i, amplitude);
    }

    /** 행성들을 잇는 지그재그 길. 갈 수 있는 구간은 밝게 흐르고, 잠긴 구간은 어둡게 점선으로 둔다. */
    private void drawRoute(Graphics2D g, int w) {
        // 대시 위상은 음수가 될 수 없다. 점이 앞으로 흐르는 것처럼 보이게 (18 - 위상)으로 거꾸로 돌린다
        float phase = 18f - (float) ((timeMs / 28.0) % 18);
        BasicStroke lit = new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f, new float[]{9f, 9f}, phase);
        BasicStroke dark = new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f, new float[]{2f, 11f}, 0f);
        for (int i = 0; i + 1 < levels.size(); i++) {
            double x1 = screenX(i), y1 = screenY(i), x2 = screenX(i + 1), y2 = screenY(i + 1);
            if (Math.max(x1, x2) < -10 || Math.min(x1, x2) > w + 10) continue;
            // 열린 행성에서 나가는 구간은 밝게(첫 번째 잠긴 행성까지: 다음 목적지가 보이도록), 그 너머는 어둡게
            boolean open = app.isUnlocked(i);
            g.setStroke(open ? lit : dark);
            g.setColor(open ? new Color(120, 220, 255, 190) : new Color(150, 160, 190, 90));
            g.draw(new Line2D.Double(x1, y1, x2, y2));
        }
    }

    private void drawNode(Graphics2D g, int i, double sx, double sy) {
        Level lv = levels.get(i);
        boolean unlocked = app.isUnlocked(i);
        boolean sel = i == selected;
        double glow = sel ? 1.0 : (i == hover ? 0.6 : 0.0);
        double r = planetR * (sel ? 1.2 : 1.0);
        PlanetArt.drawPlanet(g, PlanetArt.planet(i), sx, sy, r, glow, !unlocked);

        if (sel) {
            g.setStroke(new BasicStroke(2f));
            g.setColor(new Color(Theme.ACCENT.getRed(), Theme.ACCENT.getGreen(), Theme.ACCENT.getBlue(),
                    (int) (150 + 80 * Math.sin(timeMs / 220.0))));
            g.draw(new java.awt.geom.Ellipse2D.Double(sx - r - 7, sy - r - 7, 2 * r + 14, 2 * r + 14));
        }

        // 번호·이름·별
        int ty = (int) (sy + r + 20);
        g.setFont(Theme.font(Font.BOLD, 15f));
        g.setColor(unlocked ? Theme.TEXT : Theme.TEXT_DIM);
        Gfx.centered(g, String.valueOf(i + 1), (int) sx, ty);
        g.setFont(Theme.font(Font.PLAIN, 12f));
        g.setColor(Theme.TEXT_DIM);
        Gfx.centered(g, PlanetArt.planet(i).name(), (int) sx, ty + 16);
        if (unlocked) {
            SaveStore.Record rec = app.save.get(lv.id);
            if (rec.bestStars() > 0) Gfx.stars(g, sx, ty + 33, 5.5, rec.bestStars());
        }
    }

    /** 로켓: 선택한 행성 위에 서 있고, 이동 중에는 다음 행성을 향해 뛴다. */
    private void drawRocket(Graphics2D g) {
        double x = lastW / 2.0 + map.rocketX() - camX;
        double lift = map.hopLift();
        double y = centerY + map.rocketY(amplitude) - (planetR * 1.2 + 32) - lift * 16;   // 불꽃이 행성에 닿지 않게 띄운다
        double base = -Math.PI / 2;                                  // 서 있을 때는 위를 본다
        double delta = map.rocketAngle(amplitude) - base;
        delta = Math.atan2(Math.sin(delta), Math.cos(delta));        // 가장 짧은 회전 방향
        double angle = map.isHopping() ? base + delta * Math.sin(Math.PI * Math.min(1, lift * 1.2)) : base;
        double flame = map.isHopping() ? 0.7 + 0.3 * Math.sin(timeMs / 40.0) : 0.35 + 0.2 * Math.sin(timeMs / 90.0);
        PlanetArt.drawRocket(g, x, y, angle, 14, flame);
    }

    private void drawHoverLabel(Graphics2D g, int i) {
        String text = PlanetArt.planet(i).name() + " · " + levels.get(i).name;
        g.setFont(Theme.font(Font.BOLD, 14f));
        int tw = g.getFontMetrics().stringWidth(text);
        double sx = screenX(i), sy = screenY(i) - planetR - 34;
        g.setColor(new Color(10, 14, 36, 220));
        g.fill(new RoundRectangle2D.Double(sx - tw / 2.0 - 10, sy - 16, tw + 20, 26, 12, 12));
        g.setColor(Theme.TEXT);
        Gfx.centered(g, text, (int) sx, (int) sy + 3);
    }

    private void title(Graphics2D g, int w) {
        g.setColor(Theme.TEXT);
        g.setFont(Theme.font(Font.BOLD, 32f));
        Gfx.centered(g, "우주 탐험", w / 2, 58);
        g.setFont(Theme.font(Font.PLAIN, 15f));
        g.setColor(Theme.TEXT_DIM);
        Gfx.centered(g, "지구에서 출발해 한 행성씩 나아가세요 · 이전 레벨을 별 1개 이상으로 클리어하면 다음 행성이 열려요", w / 2, 84);
    }

    /** 아래쪽 정보 패널: 선택한 행성/레벨 정보와 착륙 버튼. */
    private void infoPanel(Graphics2D g, int w, int h) {
        int pw = Math.min(w - 60, 760), ph = 104;
        int px = (w - pw) / 2, py = h - ph - 48;
        Level lv = levels.get(selected);
        boolean unlocked = app.isUnlocked(selected);

        g.setColor(new Color(20, 26, 60, 215));
        g.fill(new RoundRectangle2D.Double(px, py, pw, ph, 20, 20));
        g.setStroke(new BasicStroke(1.5f));
        g.setColor(unlocked ? new Color(120, 200, 255, 140) : new Color(150, 160, 190, 90));
        g.draw(new RoundRectangle2D.Double(px, py, pw, ph, 20, 20));

        g.setFont(Theme.font(Font.BOLD, 24f));
        g.setColor(Theme.TEXT);
        g.drawString((selected + 1) + ". " + PlanetArt.planet(selected).name(), px + 24, py + 36);
        g.setFont(Theme.font(Font.PLAIN, 16f));
        g.setColor(Theme.ACCENT);
        g.drawString(lv.name + "   ·   최적 " + lv.optimalMoves + "수", px + 24, py + 62);

        g.setFont(Theme.font(Font.PLAIN, 14f));
        g.setColor(Theme.TEXT_DIM);
        if (!unlocked) {
            g.drawString("잠긴 행성 — 이전 레벨을 별 1개 이상으로 클리어하면 열려요.", px + 24, py + 88);
        } else {
            SaveStore.Record rec = app.save.get(lv.id);
            if (rec.bestStars() > 0) {
                g.drawString("최고 기록  " + rec.bestMoves() + "수 · " + rec.bestScore() + "점", px + 24, py + 88);
                Gfx.stars(g, px + 24 + 250, py + 83, 8, rec.bestStars());
            } else {
                g.drawString("아직 가 보지 않은 행성이에요.", px + 24, py + 88);
            }
        }

        // 착륙 버튼
        int bw = 150, bh = 52;
        landButton.setBounds(px + pw - bw - 24, py + (ph - bh) / 2, bw, bh);
        boolean ready = unlocked;
        g.setColor(ready ? Theme.PANEL_SEL : Theme.PANEL_LOCK);
        g.fill(new RoundRectangle2D.Double(landButton.x, landButton.y, bw, bh, 16, 16));
        g.setColor(ready ? Theme.ACCENT : Theme.TEXT_DIM);
        g.setStroke(new BasicStroke(1.6f));
        g.draw(new RoundRectangle2D.Double(landButton.x, landButton.y, bw, bh, 16, 16));
        g.setFont(Theme.font(Font.BOLD, 20f));
        g.setColor(ready ? Theme.TEXT : Theme.TEXT_DIM);
        Gfx.centered(g, ready ? "착륙  ▶" : "잠김", landButton.x + bw / 2, landButton.y + 33);

        if (!status.isEmpty()) {
            g.setFont(Theme.font(Font.PLAIN, 15f));
            g.setColor(Theme.ACCENT);
            Gfx.centered(g, status, w / 2, py - 10);
        }
    }

    private void footer(Graphics2D g, int w, int h) {
        g.setFont(Theme.font(Font.PLAIN, 14f));
        g.setColor(Theme.TEXT_DIM);
        Gfx.centered(g, "← → 한 칸 이동   Enter 착륙   Esc 메인 메뉴   ·   행성을 클릭하면 날아가고, 한 번 더 클릭하면 착륙", w / 2, h - 18);
    }

    // ---------------------------------------------------------------
    // 입력
    // ---------------------------------------------------------------

    @Override
    public void keyPressed(KeyEvent e) {
        int n = levels.size();
        if (n == 0) {
            if (e.getKeyCode() == KeyEvent.VK_ESCAPE) app.showMainMenu();
            return;
        }
        boolean moved = true;
        switch (e.getKeyCode()) {
            case KeyEvent.VK_LEFT, KeyEvent.VK_UP, KeyEvent.VK_A, KeyEvent.VK_W -> selected = Math.max(0, selected - 1);
            case KeyEvent.VK_RIGHT, KeyEvent.VK_DOWN, KeyEvent.VK_D, KeyEvent.VK_S -> selected = Math.min(n - 1, selected + 1);
            case KeyEvent.VK_HOME -> selected = 0;
            case KeyEvent.VK_END -> selected = n - 1;
            case KeyEvent.VK_ENTER, KeyEvent.VK_SPACE -> {
                moved = false;
                open(selected);
            }
            case KeyEvent.VK_ESCAPE -> {
                moved = false;
                app.showMainMenu();
            }
            default -> moved = false;
        }
        if (moved) status = "";
    }

    @Override
    public void mouseMoved(int x, int y) {
        hover = pick(x, y);
    }

    @Override
    public void mouseExited() {
        hover = -1;
    }

    @Override
    public void mouseClicked(int x, int y) {
        if (levels.isEmpty()) return;
        if (landButton.contains(x, y)) {
            open(selected);
            return;
        }
        int i = pick(x, y);
        if (i < 0) return;
        if (i == selected) {
            open(i);            // 이미 고른 행성을 한 번 더 누르면 착륙
        } else {
            selected = i;       // 처음 누르면 그 행성까지 날아간다
            status = "";
        }
    }

    /** 화면 좌표 (x, y) 아래에 있는 행성 번호, 없으면 -1. */
    private int pick(int x, int y) {
        if (levels.isEmpty()) return -1;
        double wx = x - lastW / 2.0 + camX;
        double wy = y - centerY;
        return map.nodeAt(wx, wy, amplitude, planetR * 1.35);
    }

    private void open(int i) {
        if (!app.isUnlocked(i)) {
            status = "잠긴 행성이에요. 이전 레벨을 별 1개 이상으로 클리어하세요.";
            return;
        }
        app.startLevel(i);
    }
}
