package sokoban.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.util.Random;

/** 우주 지도에 쓰는 그림: 행성, 로켓, 별 배경. 외부 이미지 없이 Graphics2D 도형만 쓴다 (FR-6.6). */
final class PlanetArt {
    private PlanetArt() {
    }

    enum Kind { EARTH, MOON, ROCKY, BANDED, RINGED, ICE, BLACKHOLE }

    /** 행성 하나의 모양. blobs는 단위 원 안의 무늬 {x, y, 반지름}. */
    record Planet(String name, Color base, Kind kind, double[][] blobs) {
    }

    private static final Planet[] PLANETS = {
        make("지구", 0x2f6fd8, Kind.EARTH),
        make("달", 0xb9b9c0, Kind.MOON),
        make("화성", 0xc4553a, Kind.ROCKY),
        make("세레스", 0x8d8579, Kind.MOON),
        make("목성", 0xd9a066, Kind.BANDED),
        make("유로파", 0xe4d3b0, Kind.ICE),
        make("토성", 0xe0c68a, Kind.RINGED),
        make("타이탄", 0xd19a3a, Kind.ROCKY),
        make("천왕성", 0x7fe0e0, Kind.ICE),
        make("해왕성", 0x3d62d6, Kind.ICE),
        make("명왕성", 0xb89c88, Kind.MOON),
        make("프록시마 b", 0xe0703a, Kind.ROCKY),
        make("트라피스트-1e", 0x2fb0a0, Kind.ROCKY),
        make("케플러-452b", 0x3aa05a, Kind.EARTH),
        make("페가수스 51b", 0xe0905a, Kind.BANDED),
        make("에리스", 0xdde8f4, Kind.ICE),
        make("안드로메다 관문", 0xa07ae0, Kind.RINGED),
        make("블랙홀", 0x000000, Kind.BLACKHOLE),
        make("웜홀", 0x8a63d2, Kind.RINGED),
        make("쌍성", 0xd08a4a, Kind.BANDED),
        make("카론", 0xa8b2bd, Kind.MOON),
    };

    private static Planet make(String name, int rgb, Kind kind) {
        Random rnd = new Random(name.hashCode() * 31L + 7);
        int n = switch (kind) {
            case MOON -> 7;
            case EARTH -> 6;
            case ROCKY -> 8;
            default -> 3;
        };
        double[][] blobs = new double[n][3];
        for (double[] b : blobs) {
            double ang = rnd.nextDouble() * Math.PI * 2, dist = Math.sqrt(rnd.nextDouble()) * 0.75;
            b[0] = Math.cos(ang) * dist;
            b[1] = Math.sin(ang) * dist;
            b[2] = 0.12 + rnd.nextDouble() * 0.2;
        }
        return new Planet(name, new Color(rgb), kind, blobs);
    }

    /** 레벨 i(0부터)의 행성. 준비된 행성보다 레벨이 많으면 "미지의 행성 N"을 만들어 쓴다. */
    static Planet planet(int i) {
        if (i < PLANETS.length) return PLANETS[i];
        float hue = (i * 0.137f) % 1f;
        return make("미지의 행성 " + (i + 1), Color.HSBtoRGB(hue, 0.55f, 0.85f) & 0xFFFFFF, Kind.ROCKY);
    }

    static int namedPlanets() {
        return PLANETS.length;
    }

    // ---------------------------------------------------------------
    // 행성
    // ---------------------------------------------------------------

    /**
     * 행성을 그린다.
     *
     * @param glow   0~1, 선택·호버했을 때 바깥 후광 세기
     * @param locked true면 흑백으로 가라앉히고 자물쇠를 얹는다
     */
    static void drawPlanet(Graphics2D g, Planet p, double cx, double cy, double r, double glow, boolean locked) {
        if (glow > 0.01) halo(g, cx, cy, r * 2.1, new Color(120, 200, 255), (float) (0.55 * glow));
        Color base = locked ? gray(p.base()) : p.base();

        if (p.kind() == Kind.BLACKHOLE) {
            blackHole(g, cx, cy, r, locked);
            if (locked) lock(g, cx, cy, r);
            return;
        }
        if (p.kind() == Kind.RINGED) ring(g, cx, cy, r, base, false);

        Ellipse2D disc = new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r);
        Shape oldClip = g.getClip();
        g.clip(disc);
        g.setPaint(new RadialGradientPaint((float) (cx - r * 0.35), (float) (cy - r * 0.4), (float) (r * 1.5),
                new float[]{0f, 0.55f, 1f},
                new Color[]{Theme.shade(base, 1.35), base, Theme.shade(base, 0.45)}));
        g.fill(disc);
        features(g, p, base, cx, cy, r);
        // 빛이 오른쪽 아래로 갈수록 어두워지는 그림자
        g.setPaint(new RadialGradientPaint((float) (cx - r * 0.45), (float) (cy - r * 0.5), (float) (r * 1.9),
                new float[]{0.45f, 1f}, new Color[]{new Color(0, 0, 0, 0), new Color(0, 0, 0, 150)}));
        g.fill(disc);
        g.setClip(oldClip);
        g.setPaint(null);

        if (p.kind() == Kind.RINGED) ring(g, cx, cy, r, base, true);
        if (locked) lock(g, cx, cy, r);
    }

    private static void features(Graphics2D g, Planet p, Color base, double cx, double cy, double r) {
        switch (p.kind()) {
            case EARTH -> {
                for (int i = 0; i < p.blobs().length; i++) {
                    double[] b = p.blobs()[i];
                    // 대륙은 초록, 구름은 흰색. 잠겨서 회색이 된 행성은 대륙도 회색으로 맞춘다
                    g.setColor(i < 4 ? (isGray(base) ? Theme.shade(base, 1.5) : new Color(0x4cae5e))
                            : new Color(255, 255, 255, 120));
                    double w = r * b[2] * (i < 4 ? 2.2 : 3.0), h = r * b[2] * (i < 4 ? 1.5 : 0.8);
                    g.fill(new Ellipse2D.Double(cx + b[0] * r - w / 2, cy + b[1] * r - h / 2, w, h));
                }
            }
            case MOON -> {
                for (double[] b : p.blobs()) {
                    double rr = r * b[2] * 0.7;
                    double x = cx + b[0] * r, y = cy + b[1] * r;
                    g.setColor(new Color(0, 0, 0, 55));
                    g.fill(new Ellipse2D.Double(x - rr, y - rr, 2 * rr, 2 * rr));
                    g.setColor(new Color(255, 255, 255, 50));
                    g.setStroke(new BasicStroke(1f));
                    g.draw(new Ellipse2D.Double(x - rr, y - rr, 2 * rr, 2 * rr));
                }
            }
            case ROCKY -> {
                for (int i = 0; i < p.blobs().length; i++) {
                    double[] b = p.blobs()[i];
                    g.setColor(i % 2 == 0 ? new Color(0, 0, 0, 60) : new Color(255, 255, 255, 45));
                    double rr = r * b[2] * 1.2;
                    g.fill(new Ellipse2D.Double(cx + b[0] * r - rr, cy + b[1] * r - rr * 0.7, 2 * rr, 1.4 * rr));
                }
            }
            case BANDED, RINGED -> {
                for (int k = -4; k <= 4; k++) {
                    g.setColor(k % 2 == 0 ? new Color(120, 70, 30, 70) : new Color(255, 235, 200, 60));
                    double y = cy + k * r * 0.24;
                    g.fill(new java.awt.geom.Rectangle2D.Double(cx - r, y - r * 0.1, 2 * r, r * 0.2));
                }
                if (p.kind() == Kind.BANDED) {
                    g.setColor(new Color(190, 80, 50, 150));
                    g.fill(new Ellipse2D.Double(cx + r * 0.12, cy + r * 0.2, r * 0.5, r * 0.28));
                }
            }
            case ICE -> {
                g.setColor(new Color(255, 255, 255, 90));
                g.fill(new Ellipse2D.Double(cx - r * 0.7, cy - r * 0.75, r * 0.9, r * 0.45));
                g.setStroke(new BasicStroke(1f));
                g.setColor(new Color(255, 255, 255, 70));
                for (double[] b : p.blobs()) {
                    g.drawLine((int) (cx + b[0] * r - r * 0.3), (int) (cy + b[1] * r - r * 0.1),
                            (int) (cx + b[0] * r + r * 0.35), (int) (cy + b[1] * r + r * 0.2));
                }
            }
            default -> {
            }
        }
    }

    /** 토성형 고리. front=false면 행성 뒤쪽 절반, true면 앞쪽 절반만 그린다. */
    private static void ring(Graphics2D g, double cx, double cy, double r, Color base, boolean front) {
        AffineTransform old = g.getTransform();
        Shape oldClip = g.getClip();
        g.rotate(Math.toRadians(-16), cx, cy);
        if (front) g.clipRect((int) (cx - r * 3), (int) cy, (int) (r * 6), (int) (r * 3));
        else g.clipRect((int) (cx - r * 3), (int) (cy - r * 3), (int) (r * 6), (int) (r * 3));
        g.setStroke(new BasicStroke((float) Math.max(2, r * 0.28f)));
        g.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), 190));
        g.draw(new Ellipse2D.Double(cx - r * 1.85, cy - r * 0.5, r * 3.7, r * 1.0));
        g.setStroke(new BasicStroke((float) Math.max(1, r * 0.08f)));
        g.setColor(new Color(0, 0, 0, 90));
        g.draw(new Ellipse2D.Double(cx - r * 1.62, cy - r * 0.42, r * 3.24, r * 0.84));
        // 순서 주의: oldClip은 회전 전 좌표계 기준이므로 변환을 먼저 되돌린 뒤에 복원해야 한다.
        // 반대로 하면 클립이 회전된 채 남아 이후 그리는 화면이 대각선으로 잘린다.
        g.setTransform(old);
        g.setClip(oldClip);
    }

    private static void blackHole(Graphics2D g, double cx, double cy, double r, boolean locked) {
        Color hot = locked ? new Color(150, 150, 160) : new Color(255, 170, 70);
        AffineTransform old = g.getTransform();
        g.rotate(Math.toRadians(-14), cx, cy);
        g.setStroke(new BasicStroke((float) (r * 0.32f)));
        g.setColor(new Color(hot.getRed(), hot.getGreen(), hot.getBlue(), 170));
        g.draw(new Ellipse2D.Double(cx - r * 1.7, cy - r * 0.5, r * 3.4, r * 1.0));
        g.setTransform(old);
        g.setColor(Color.BLACK);
        g.fill(new Ellipse2D.Double(cx - r * 0.82, cy - r * 0.82, r * 1.64, r * 1.64));
        g.setStroke(new BasicStroke(2f));
        g.setColor(new Color(hot.getRed(), hot.getGreen(), hot.getBlue(), 210));
        g.draw(new Ellipse2D.Double(cx - r * 0.82, cy - r * 0.82, r * 1.64, r * 1.64));
    }

    private static void lock(Graphics2D g, double cx, double cy, double r) {
        double w = r * 0.72, h = r * 0.56;
        g.setColor(new Color(0, 0, 0, 120));
        g.fill(new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r));
        g.setStroke(new BasicStroke((float) Math.max(2f, r * 0.11f)));
        g.setColor(new Color(235, 238, 250));
        g.draw(new java.awt.geom.Arc2D.Double(cx - w * 0.34, cy - h * 1.05, w * 0.68, h * 1.0, 0, 180, java.awt.geom.Arc2D.OPEN));
        g.fill(new RoundRectangle2D.Double(cx - w / 2, cy - h * 0.35, w, h, 5, 5));
        g.setColor(new Color(40, 46, 80));
        g.fill(new Ellipse2D.Double(cx - w * 0.08, cy - h * 0.05, w * 0.16, w * 0.16));
    }

    private static void halo(Graphics2D g, double cx, double cy, double rad, Color c, float a) {
        g.setPaint(new RadialGradientPaint((float) cx, (float) cy, (float) rad, new float[]{0.35f, 1f},
                new Color[]{new Color(c.getRed(), c.getGreen(), c.getBlue(), (int) (255 * Math.min(1f, a))),
                        new Color(c.getRed(), c.getGreen(), c.getBlue(), 0)}));
        g.fill(new Ellipse2D.Double(cx - rad, cy - rad, 2 * rad, 2 * rad));
        g.setPaint(null);
    }

    private static boolean isGray(Color c) {
        return Math.abs(c.getRed() - c.getGreen()) < 14 && Math.abs(c.getGreen() - c.getBlue()) < 16;
    }

    private static Color gray(Color c) {
        int v = (int) (c.getRed() * 0.3 + c.getGreen() * 0.59 + c.getBlue() * 0.11);
        v = (int) (v * 0.7 + 18);
        return new Color(v, v, Math.min(255, v + 10));
    }

    // ---------------------------------------------------------------
    // 로켓
    // ---------------------------------------------------------------

    /**
     * 로켓을 그린다. 기본 방향은 오른쪽(+x)이고 angle(라디안)만큼 돈다. size = 몸 길이의 절반(px).
     *
     * @param flame 0~1 불꽃 크기(깜빡임)
     */
    static void drawRocket(Graphics2D g, double x, double y, double angle, double size, double flame) {
        AffineTransform old = g.getTransform();
        g.translate(x, y);
        g.rotate(angle);
        double s = size;

        // 불꽃
        Path2D fl = new Path2D.Double();
        fl.moveTo(-s * 0.95, -s * 0.28);
        fl.lineTo(-s * (1.25 + 0.9 * flame), 0);
        fl.lineTo(-s * 0.95, s * 0.28);
        fl.closePath();
        g.setPaint(new java.awt.GradientPaint((float) (-s * 0.95), 0, new Color(255, 250, 200),
                (float) (-s * (1.25 + 0.9 * flame)), 0, new Color(255, 110, 30, 0)));
        g.fill(fl);
        g.setPaint(null);

        // 날개
        Path2D fins = new Path2D.Double();
        fins.moveTo(-s * 0.85, 0);
        fins.lineTo(-s * 1.1, -s * 0.78);
        fins.lineTo(-s * 0.2, -s * 0.38);
        fins.lineTo(-s * 0.2, s * 0.38);
        fins.lineTo(-s * 1.1, s * 0.78);
        fins.closePath();
        g.setColor(new Color(0xe5564f));
        g.fill(fins);

        // 몸통과 앞부분
        Path2D body = new Path2D.Double();
        body.moveTo(s * 1.15, 0);
        body.curveTo(s * 0.7, -s * 0.5, s * 0.1, -s * 0.5, -s * 0.9, -s * 0.34);
        body.lineTo(-s * 0.9, s * 0.34);
        body.curveTo(s * 0.1, s * 0.5, s * 0.7, s * 0.5, s * 1.15, 0);
        body.closePath();
        g.setPaint(new java.awt.GradientPaint(0, (float) (-s * 0.5), new Color(0xf4f6ff), 0, (float) (s * 0.5), new Color(0xaeb6d6)));
        g.fill(body);
        g.setPaint(null);
        g.setStroke(new BasicStroke(1.2f));
        g.setColor(new Color(40, 46, 80, 200));
        g.draw(body);

        // 창
        g.setColor(new Color(0x4fc3f7));
        g.fill(new Ellipse2D.Double(s * 0.1, -s * 0.2, s * 0.42, s * 0.42));
        g.setColor(new Color(255, 255, 255, 170));
        g.fill(new Ellipse2D.Double(s * 0.18, -s * 0.15, s * 0.14, s * 0.14));
        g.setColor(new Color(40, 46, 80));
        g.draw(new Ellipse2D.Double(s * 0.1, -s * 0.2, s * 0.42, s * 0.42));

        g.setTransform(old);
    }

    // ---------------------------------------------------------------
    // 별 배경
    // ---------------------------------------------------------------

    private static final int STAR_COUNT = 260;
    private static final double STAR_PERIOD = 2600;
    private static final double[][] STARS = new double[STAR_COUNT][5]; // x, y(0~1), 크기, 위상, 시차층
    private static final double[] PARALLAX = {0.08, 0.2, 0.38};
    private static final double[][] NEBULAE = {
        {260, 0.3, 300, 110, 70, 200}, {1100, 0.72, 340, 60, 90, 210}, {1900, 0.35, 280, 150, 60, 170}, {2400, 0.78, 260, 70, 120, 190}};

    static {
        Random rnd = new Random(20261123L);
        for (double[] s : STARS) {
            s[0] = rnd.nextDouble() * STAR_PERIOD;
            s[1] = rnd.nextDouble();
            s[2] = 0.6 + rnd.nextDouble() * 1.7;
            s[3] = rnd.nextDouble() * Math.PI * 2;
            s[4] = rnd.nextInt(3);
        }
    }

    /** 우주 배경: 어두운 그라데이션 + 성운 + 시차를 두고 반짝이는 별. camX는 지도 카메라 위치. */
    static void drawSpace(Graphics2D g, int w, int h, double camX, double timeMs) {
        g.setPaint(new java.awt.GradientPaint(0, 0, new Color(0x050816), 0, h, new Color(0x0d1230)));
        g.fillRect(0, 0, w, h);
        g.setPaint(null);

        for (double[] n : NEBULAE) {
            double x = wrap(n[0] - camX * 0.12, STAR_PERIOD) - 200;
            double y = n[1] * h, rad = n[2];
            Color c = new Color((int) n[3], (int) n[4], 235);
            g.setPaint(new RadialGradientPaint((float) x, (float) y, (float) rad, new float[]{0f, 1f},
                    new Color[]{new Color(c.getRed(), c.getGreen(), c.getBlue(), (int) (n[5] * 0.28)),
                            new Color(c.getRed(), c.getGreen(), c.getBlue(), 0)}));
            g.fill(new Ellipse2D.Double(x - rad, y - rad, 2 * rad, 2 * rad));
        }
        g.setPaint(null);

        for (double[] s : STARS) {
            double sx = wrap(s[0] - camX * PARALLAX[(int) s[4]], STAR_PERIOD);
            if (sx > w + 4) continue;
            double tw = 0.55 + 0.45 * Math.sin(timeMs / (500 + 250 * s[4]) + s[3]);
            int a = (int) (255 * (0.35 + 0.65 * tw) * (0.55 + 0.15 * s[4]));
            g.setColor(new Color(225, 235, 255, Math.max(0, Math.min(255, a))));
            double d = s[2] * (0.8 + 0.25 * s[4]);
            g.fill(new Ellipse2D.Double(sx - d / 2, s[1] * h - d / 2, d, d));
        }
    }

    private static double wrap(double x, double period) {
        return ((x % period) + period) % period;
    }
}
