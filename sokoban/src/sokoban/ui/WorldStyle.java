package sokoban.ui;

import sokoban.core.LevelTheme;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.Random;

/**
 * 플레이 화면의 무대 연출: 레벨 테마({@link LevelTheme})에 맞춘 배경 그림과 벽·격자 색.
 *
 * <ul>
 *   <li>행성: 별이 뜬 하늘 아래로 그 행성의 지평선이 보인다. 벽은 행성 색이 섞인 암석색.</li>
 *   <li>웜홀: 보랏빛 공간에 천천히 도는 소용돌이.</li>
 *   <li>우주정거장: 금속 선체 패널, 관측창 너머의 별, 깜빡이는 표시등, 경고 띠. 벽은 강철색.</li>
 * </ul>
 * 배경은 게임판보다 먼저 그리며 낮은 대비로 두어 게임판을 가리지 않는다. 외부 이미지는 쓰지 않는다(FR-6.6).
 */
final class WorldStyle {
    final LevelTheme theme;
    /** 목적지 고유색(행성 색, 정거장 표시등 색 등). */
    final Color accent;
    final Color wall, grid;
    private final Color bgTop, bgBottom;

    private static final int STAR_COUNT = 140;
    private static final double[][] STARS = new double[STAR_COUNT][3]; // x, y (0~1), 크기

    static {
        Random rnd = new Random(20261128L);
        for (double[] s : STARS) {
            s[0] = rnd.nextDouble();
            s[1] = rnd.nextDouble();
            s[2] = 0.7 + rnd.nextDouble() * 1.5;
        }
    }

    private WorldStyle(LevelTheme theme, Color accent, Color wall, Color grid, Color bgTop, Color bgBottom) {
        this.theme = theme;
        this.accent = accent;
        this.wall = wall;
        this.grid = grid;
        this.bgTop = bgTop;
        this.bgBottom = bgBottom;
    }

    static WorldStyle of(LevelTheme theme, Color accent) {
        // 블랙홀처럼 색이 거의 없는 목적지는 테마 기본색으로 대신한다
        if (accent.getRed() + accent.getGreen() + accent.getBlue() < 60) accent = new Color(0xff9a4a);
        return switch (theme) {
            case PLANET -> new WorldStyle(theme, accent, mix(Theme.WALL, accent, 0.35), mix(Theme.GRID, accent, 0.25),
                    new Color(0x080c22), mix(new Color(0x0e1224), accent, 0.16));
            case WORMHOLE -> new WorldStyle(theme, accent, new Color(0x737aa8), new Color(0xa894e6),
                    new Color(0x07041a), new Color(0x170b33));
            case STATION -> new WorldStyle(theme, accent, new Color(0x6c7a92), new Color(0x9fb4cc),
                    new Color(0x161c27), new Color(0x0a0e15));
        };
    }

    /** 게임판 뒤 배경 전체를 그린다. timeMs는 애니메이션(소용돌이·표시등)에 쓴다. */
    void paintBackdrop(Graphics2D g, int w, int h, double timeMs) {
        g.setPaint(new GradientPaint(0, 0, bgTop, 0, h, bgBottom));
        g.fillRect(0, 0, w, h);
        g.setPaint(null);
        switch (theme) {
            case PLANET -> planet(g, w, h, timeMs);
            case WORMHOLE -> wormhole(g, w, h, timeMs);
            case STATION -> station(g, w, h, timeMs);
        }
    }

    // ---------- 행성: 하늘 + 지평선 ----------

    private void planet(Graphics2D g, int w, int h, double t) {
        stars(g, w, (int) (h * 0.8), t, 0.75);
        // 화면 아래로 걸친 커다란 행성 표면
        double r = Math.max(w, h) * 1.1;
        double cx = w * 0.5, cy = h + r - h * 0.2;
        g.setPaint(new RadialGradientPaint((float) cx, (float) (cy - r * 0.4), (float) (r * 1.1),
                new float[]{0f, 0.7f, 1f},
                new Color[]{alpha(Theme.shade(accent, 0.9), 0.55f), alpha(Theme.shade(accent, 0.55), 0.5f),
                        alpha(Theme.shade(accent, 0.3), 0.45f)}));
        g.fill(new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r));
        g.setPaint(null);
        // 대기층 빛
        g.setStroke(new BasicStroke(6f));
        g.setColor(alpha(Theme.shade(accent, 1.4), 0.18f));
        g.draw(new Ellipse2D.Double(cx - r - 3, cy - r - 3, 2 * r + 6, 2 * r + 6));
        g.setStroke(new BasicStroke(2f));
        g.setColor(alpha(Theme.shade(accent, 1.6), 0.35f));
        g.draw(new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r));
    }

    // ---------- 웜홀: 소용돌이 ----------

    private void wormhole(Graphics2D g, int w, int h, double t) {
        stars(g, w, h, t, 0.6);
        double cx = w * 0.5, cy = h * 0.52;
        double max = Math.max(w, h) * 0.62;
        g.setPaint(new RadialGradientPaint((float) cx, (float) cy, (float) max, new float[]{0f, 0.25f, 1f},
                new Color[]{new Color(2, 1, 8, 230), alpha(accent, 0.20f), alpha(accent, 0f)}));
        g.fill(new Ellipse2D.Double(cx - max, cy - max, 2 * max, 2 * max));
        g.setPaint(null);
        double spin = t / 9000.0 * 360;
        for (int k = 0; k < 7; k++) {
            double f = 0.25 + k * 0.12;
            double rx = max * f, ry = max * f * 0.62;
            g.setStroke(new BasicStroke((float) (2.5 - k * 0.2)));
            g.setColor(alpha(Theme.shade(accent, 1.4), 0.16f - k * 0.015f));
            g.draw(new Arc2D.Double(cx - rx, cy - ry, 2 * rx, 2 * ry, spin * (1.4 - k * 0.12) + k * 47, 200, Arc2D.OPEN));
        }
    }

    // ---------- 우주정거장: 선체 패널 ----------

    private void station(Graphics2D g, int w, int h, double t) {
        int pw = 120, ph = 80;
        Random rnd = new Random(7);
        for (int y = 0; y < h; y += ph) {
            for (int x = -(y / ph % 2) * pw / 2; x < w; x += pw) {
                int shade = rnd.nextInt(10);
                g.setColor(new Color(30 + shade, 38 + shade, 52 + shade, 90));
                g.fill(new Rectangle2D.Double(x + 2, y + 2, pw - 4, ph - 4));
                g.setColor(new Color(0, 0, 0, 70));
                g.setStroke(new BasicStroke(2f));
                g.draw(new Rectangle2D.Double(x + 1, y + 1, pw - 2, ph - 2));
                g.setColor(new Color(170, 185, 210, 28));
                for (int[] c : new int[][]{{8, 8}, {pw - 10, 8}, {8, ph - 10}, {pw - 10, ph - 10}}) {
                    g.fill(new Ellipse2D.Double(x + c[0], y + c[1], 3, 3));
                }
            }
        }
        // 관측창: 위쪽에 둥근 창 세 개, 창 너머로 별이 보인다
        int ww = Math.min(220, w / 5), wh = 54, gap = ww / 3, total = 3 * ww + 2 * gap;
        int wx0 = (w - total) / 2, wy = 18;
        for (int i = 0; i < 3; i++) {
            int wx = wx0 + i * (ww + gap);
            RoundRectangle2D win = new RoundRectangle2D.Double(wx, wy, ww, wh, 26, 26);
            g.setColor(new Color(4, 6, 16, 200));
            g.fill(win);
            java.awt.Shape clip = g.getClip();
            g.clip(win);
            for (double[] s : STARS) {
                double sx = wx + s[0] * ww, sy = wy + s[1] * wh;
                g.setColor(new Color(220, 230, 255, 150));
                g.fill(new Ellipse2D.Double(sx, sy, s[2] * 0.8, s[2] * 0.8));
            }
            g.setClip(clip);
            g.setStroke(new BasicStroke(4f));
            g.setColor(new Color(90, 104, 128, 140));
            g.draw(win);
        }
        // 깜빡이는 표시등
        for (int i = 0; i < 6; i++) {
            double phase = Math.sin(t / 600.0 + i * 1.7);
            float a = (float) (0.15 + 0.35 * Math.max(0, phase));
            double lx = w * (0.08 + i * 0.168), ly = h * (i % 2 == 0 ? 0.3 : 0.7);
            g.setColor(alpha(accent, a));
            g.fill(new Ellipse2D.Double(lx - 4, ly - 4, 8, 8));
            g.setColor(alpha(accent, a * 0.35f));
            g.fill(new Ellipse2D.Double(lx - 11, ly - 11, 22, 22));
        }
        // 바닥 경고 띠
        int band = 14, by = h - band;
        g.setColor(new Color(20, 18, 10, 120));
        g.fillRect(0, by, w, band);
        g.setColor(new Color(240, 190, 60, 55));
        for (int x = -band; x < w; x += band * 2) {
            int[] xs = {x, x + band, x + band * 2, x + band};
            int[] ys = {h, by, by, h};
            g.fillPolygon(xs, ys, 4);
        }
    }

    // ---------- 공용 ----------

    private static void stars(Graphics2D g, int w, int h, double t, double strength) {
        for (int i = 0; i < STARS.length; i++) {
            double[] s = STARS[i];
            double tw = 0.6 + 0.4 * Math.sin(t / 700.0 + i);
            int a = (int) (200 * strength * tw);
            g.setColor(new Color(225, 235, 255, Math.max(0, Math.min(255, a))));
            g.fill(new Ellipse2D.Double(s[0] * w, s[1] * h, s[2], s[2]));
        }
    }

    static Color mix(Color a, Color b, double f) {
        return new Color((int) Math.round(a.getRed() * (1 - f) + b.getRed() * f),
                (int) Math.round(a.getGreen() * (1 - f) + b.getGreen() * f),
                (int) Math.round(a.getBlue() * (1 - f) + b.getBlue() * f));
    }

    private static Color alpha(Color c, float a) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.max(0, Math.min(255, Math.round(a * 255))));
    }
}
