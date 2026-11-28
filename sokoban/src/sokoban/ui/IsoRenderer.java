package sokoban.ui;

import sokoban.core.GameState;
import sokoban.core.Engine;
import sokoban.core.Level;
import sokoban.core.StepResult;
import sokoban.core.TileType;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Area;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 3차원 격자(x, y, z)를 회전 가능한 아이소메트릭(직교 투영)으로 그린다 (FR-6.1).
 *
 * <p>카메라: 격자 중심을 축으로 z축 회전(yaw)과 기울기(pitch)를 준다. yaw 0, pitch 30°는
 * x+ = 화면 오른쪽 아래, y+ = 화면 왼쪽 아래, z+ = 위인 기본 시점이다.
 * yaw -45°, pitch 90° 는 2D 탑뷰로, z축이 화면에서 사라지고 격자가 화면 축과 나란한 정사각 타일로 그려진다.
 * 물체는 카메라 방향 깊이 순으로 정렬해 그리고(화가 알고리즘), 입체의 각 면은 법선으로 뒷면을 거른다.
 *
 * <p>공중에 떠 있는 물체의 높이를 읽을 수 있도록 바닥 격자(z=0), 뒤쪽 모서리 틀, 바닥 그림자,
 * 층 경계와 작은 층 눈금을 보조로 그린다. 층 강조 모드에서는 위층을 흐리게 그린다.
 */
final class IsoRenderer {
    /** 월드 1칸 = S 픽셀 (scale 1일 때). */
    private static final double S = 46;
    /** A small visual gap; game coordinates and cube sizes remain unchanged. */
    private static final double LAYER_GAP = .10;
    private static final double[] LIGHT = norm3(0.35, 0.25, 0.9);

    /** 진행 중 애니메이션. t = 0..1. */
    record Anim(StepResult step, double t) {
    }

    // 카메라 파생값
    private double cosPsi, sinPsi, cosP, sinP;
    private double cx0, cy0, cz0;
    private double scale, ox, oy;
    private double layerPitch = 1;
    private double layerZ = Double.MAX_VALUE; // 이 높이보다 위층은 흐리게 (MAX = 끔)
    private double layerBelow = -Double.MAX_VALUE; // 이 높이보다 아래층은 살짝 흐리게 (-MAX = 끔)
    private static final float ABOVE_ALPHA = 0.22f, BELOW_ALPHA = 0.7f;

    // 막힌 입력에서 막은 칸 강조(번쩍임). 격자 밖 칸일 수도 있다. alpha 0이면 그리지 않는다.
    private int flashX, flashY, flashZ;
    private float flashAlpha;
    private long effectMillis;

    /** 화면 효과용 시계. 턴·판정·상자 위치에는 영향을 주지 않는다. */
    /** 무대(테마)별 벽·격자 색. 기본은 {@link Theme}의 색이다. */
    private Color wallColor = Theme.WALL, gridColor = Theme.GRID;

    void setPalette(Color wall, Color grid) {
        wallColor = wall;
        gridColor = grid;
    }

    void tickEffects(long dt) {
        effectMillis = (effectMillis + Math.max(0, dt)) % 6600;
    }

    /** (x, y, z) 칸을 alpha(0~1) 세기로 번쩍이게 한다. 매 프레임 호출하고, 끝내려면 {@link #clearFlash()}. */
    void setFlash(int x, int y, int z, float alpha) {
        flashX = x;
        flashY = y;
        flashZ = z;
        flashAlpha = alpha;
    }

    void clearFlash() {
        flashAlpha = 0f;
    }

    /** Rendering and mouse picking must use exactly the same projection and viewport. */
    void configure(Level lv, Rectangle area, Camera cam) {
        setCamera(lv, cam.yaw, cam.pitch);
        fit(lv, area);
    }

    record CellProjection(Area shape, Path2D edges, Point2D.Double center) { }

    CellProjection projectCell(double x, double y, double z, boolean flat) {
        Area shape = new Area();
        Path2D edges = new Path2D.Double();
        if (flat) {
            Path2D face = square(x, y, 1, z + .5);
            shape.add(new Area(face));
            edges.append(face, false);
        } else {
            for (int[] n : FACE_N) {
                if (!facing(n[0], n[1], n[2])) continue;
                Path2D face = poly(faceCorners(x, y, z, n));
                shape.add(new Area(face));
                edges.append(face, false);
            }
        }
        double[] center = pt(x + .5, y + .5, layerBase(z) + .5);
        return new CellProjection(shape, edges, new Point2D.Double(center[0], center[1]));
    }

    private void setCamera(Level lv, double yawDeg, double pitchDeg) {
        double psi = Math.toRadians(45 + yawDeg);
        cosPsi = Math.cos(psi);
        sinPsi = Math.sin(psi);
        double p = Math.toRadians(pitchDeg);
        cosP = Math.cos(p);
        sinP = Math.sin(p);
        cx0 = lv.w / 2.0;
        cy0 = lv.d / 2.0;
        layerPitch = lv.h > 1 ? 1 + LAYER_GAP : 1;
        cz0 = sceneHeight(lv) / 2.0;
    }

    private double layerBase(double z) { return z * layerPitch; }

    private double sceneHeight(Level level) { return layerBase(level.h - 1) + 1; }

    private double[] pt(double x, double y, double z) {
        double[] p = projectWith(cosPsi, sinPsi, cosP, sinP, x - cx0, y - cy0, z - cz0);
        return new double[]{ox + p[0] * S * scale, oy + p[1] * S * scale};
    }

    /**
     * 월드 오프셋(dx, dy, dz)을 화면 평면 좌표(단위: 칸)로 투영한다. {@link #pt}와 같은 식이며,
     * 시점 하나의 기하를 테스트에서 그대로 확인할 수 있도록 열어 둔다.
     *
     * <p>yaw -45°(psi 0°)·pitch 90° 이면 (dx, dy, 0) → (dx, dy) 가 되어 격자 축이 화면 축과
     * 나란한 정사각 타일이 된다. 이것이 2D 탑뷰다.
     */
    static double[] project(double dx, double dy, double dz, double yawDeg, double pitchDeg) {
        double psi = Math.toRadians(45 + yawDeg);
        double p = Math.toRadians(pitchDeg);
        return projectWith(Math.cos(psi), Math.sin(psi), Math.cos(p), Math.sin(p), dx, dy, dz);
    }

    private static double[] projectWith(double cosPsi, double sinPsi, double cosP, double sinP,
                                       double dx, double dy, double dz) {
        double x1 = dx * cosPsi - dy * sinPsi;
        double y1 = dx * sinPsi + dy * cosPsi;
        return new double[]{x1, y1 * sinP - dz * cosP};
    }

    /** 클수록 시점에 가깝다. */
    private double depth(double x, double y, double z) {
        double dx = x - cx0, dy = y - cy0, dz = z - cz0;
        double y1 = dx * sinPsi + dy * cosPsi;
        return y1 * cosP + dz * sinP;
    }

    /** 법선 n 면이 카메라를 향하는가. */
    private boolean facing(double nx, double ny, double nz) {
        return nx * sinPsi * cosP + ny * cosPsi * cosP + nz * sinP > 1e-9;
    }

    private record Item(double key, int order, Runnable draw) {
    }

    void render(Graphics2D g, Level lv, GameState st, Anim anim, Rectangle area, Camera cam, boolean layerFocus) {
        if (anim != null && !anim.step().phases().isEmpty()) {
            List<StepResult> phases = anim.step().phases();
            double progress = Math.max(0, Math.min(1, anim.t())) * phases.size();
            int index = Math.min(phases.size() - 1, (int) progress);
            StepResult phase = phases.get(index);
            renderScene(g, lv, phase.state(), new Anim(phase, Math.min(1, progress - index)), area, cam, layerFocus, -1);
            return;
        }
        renderScene(g, lv, st, anim, area, cam, layerFocus, -1);
    }

    void renderEditor(Graphics2D g, Level lv, Rectangle area, Camera cam, int activeLayer) {
        renderScene(g, lv, lv.initial, null, area, cam, true, activeLayer);
    }

    private void renderScene(Graphics2D g, Level lv, GameState st, Anim anim, Rectangle area, Camera cam, boolean layerFocus, int editorLayer) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        configure(lv, area, cam);

        // 애니메이션 상태
        int pushedFrom = -1, pushedTo = -1;
        boolean vanish = false;
        double t = 1;
        if (anim != null && anim.step().pushed()) {
            pushedFrom = anim.step().blockFrom();
            pushedTo = anim.step().blockTo();
            vanish = anim.step().blockVanished();
            t = anim.t();
        }
        boolean hasPlayer = st.player >= 0;
        double px = hasPlayer ? lv.x(st.player) : 0, py = hasPlayer ? lv.y(st.player) : 0, pz = hasPlayer ? lv.z(st.player) : 0;
        float playerAlpha = 1f;
        if (anim != null && anim.step().playerTeleported()) {
            // 포탈 전송: 전반에는 들어간 포탈 칸까지 걸어가며 사라지고, 후반에는 짝 포탈 칸에서 나타난다
            StepResult sr = anim.step();
            boolean first = anim.t() < 0.5;
            double u = first ? anim.t() * 2 : (anim.t() - 0.5) * 2;
            if (first) {
                px = lerp(lv.x(sr.playerFrom()), lv.x(sr.playerVia()), u);
                py = lerp(lv.y(sr.playerFrom()), lv.y(sr.playerVia()), u);
                pz = lerp(lv.z(sr.playerFrom()), lv.z(sr.playerVia()), u);
                playerAlpha = (float) (1 - u);
            } else {
                px = lv.x(sr.playerTo());
                py = lv.y(sr.playerTo());
                pz = lv.z(sr.playerTo());
                playerAlpha = (float) u;
            }
        } else if (anim != null) {
            px = lerp(lv.x(anim.step().playerFrom()), lv.x(anim.step().playerTo()), anim.t());
            py = lerp(lv.y(anim.step().playerFrom()), lv.y(anim.step().playerTo()), anim.t());
            pz = lerp(lv.z(anim.step().playerFrom()), lv.z(anim.step().playerTo()), anim.t());
        }
        double bx = 0, by = 0, bz = 0;
        float boxAlpha = 1f;
        if (pushedTo >= 0 && anim.step().blockTeleported()) {
            int via = anim.step().blockVia();
            boolean first = t < 0.5;
            double u = first ? t * 2 : (t - 0.5) * 2;
            double fx0 = first ? lerp(lv.x(pushedFrom), lv.x(via), u) : lv.x(pushedTo);
            double fy0 = first ? lerp(lv.y(pushedFrom), lv.y(via), u) : lv.y(pushedTo);
            double fz0 = first ? lerp(lv.z(pushedFrom), lv.z(via), u) : lv.z(pushedTo);
            bx = fx0;
            by = fy0;
            bz = fz0;
            boxAlpha = (float) (first ? 1 - u : u);
        } else if (pushedTo >= 0) {
            bx = lerp(lv.x(pushedFrom), lv.x(pushedTo), t);
            by = lerp(lv.y(pushedFrom), lv.y(pushedTo), t);
            bz = lerp(lv.z(pushedFrom), lv.z(pushedTo), t);
        }
        int activeLayer = editorLayer >= 0 ? editorLayer : hasPlayer ? lv.z(st.player) : 0;
        layerZ = layerFocus ? activeLayer + 0.5 : Double.MAX_VALUE;
        // 층 강조: 현재 층만 선명하게, 위층은 많이, 아래층은 조금 흐리게. 맨 위층에 서 있어도 아래층이 흐려져 달라진다.
        layerBelow = layerFocus ? activeLayer : -Double.MAX_VALUE;

        // 1) 바닥 격자와 뒤쪽 틀
        floorGrid(g, lv);
        backFrame(g, lv);
        layerGuides(g, lv, activeLayer);
        layerPlane(g, lv, editorLayer >= 0 ? editorLayer : pz);
        for (var magnet : lv.magnets) magneticBeam(g, lv, magnet, st.magnetOn);

        // 2) 바닥 그림자 / 목표 표시 (높이 단서). 아직 열리지 않은 순서 목표는 어두운 회색 테두리와 자물쇠로 구분한다.
        g.setStroke(new BasicStroke(1.4f));
        for (int gi : lv.goals) {
            boolean open = st.goalUnlocked(lv, gi);
            Color gc = open ? Theme.GOAL : Theme.GOAL_LOCKED;
            g.setColor(alpha(gc, open ? 0.55f : 0.62f));
            g.draw(square(lv.x(gi) + 0.12, lv.y(gi) + 0.12, 0.76, 0));
            if (!open) {
                // 잠긴 목표: 어두운 회색 테두리 + 자물쇠. 앞 번호 목표를 채우면 사라지고 금색 테두리만 남는다.
                lockMark(g, lv.x(gi) + 0.5, lv.y(gi) + 0.5, lv.z(gi));
            }
        }
        for (int i = 0; i < st.blockCount(); i++) {
            int b = st.block(i);
            if (b == pushedTo) continue;
            shadow(g, lv.x(b), lv.y(b), 0.7);
        }
        if (pushedTo >= 0) shadow(g, bx, by, 0.7, boxAlpha);
        if (hasPlayer) shadow(g, px, py, 0.45, playerAlpha);

        // 3) 입체물 (깊이 정렬)
        List<Item> items = new ArrayList<>();
        for (int idx = 0; idx < lv.cellCount(); idx++) {
            TileType tt = lv.tile(idx);
            final double x = lv.x(idx), y = lv.y(idx), z = lv.z(idx);
            final double key = depth(x + 0.5, y + 0.5, layerBase(z) + 0.5);
            switch (tt) {
                case WALL -> items.add(new Item(key, 0, () -> cube(g, x, y, z, wallColor, 0.55f)));
                case HOLE -> {
                    boolean open = st.isHole(idx)
                            || (anim != null && vanish && idx == pushedTo && anim.t() < 1);
                    if (open) items.add(new Item(key, 0, () -> cube(g, x, y, z, Theme.HOLE, 0.85f)));
                }
                case GOAL -> {
                    if (!st.hasBlock(idx) && idx != pushedTo) {
                        final boolean open = st.goalUnlocked(lv, idx);
                        items.add(new Item(key, 0, () -> cube(g, x, y, z, open ? Theme.GOAL : Theme.GOAL_LOCKED,
                                open ? 0.22f : 0.20f)));
                    }
                }
                // 새 타일: 행동은 엔진 PR에서 구현하고, 여기서는 어느 칸인지 알아볼 수 있게 투명 색 상자만 그린다
                case ICE -> items.add(new Item(key, 0, () -> cube(g, x, y, z, Theme.ICE, 0.50f)));
                case MAGNET_SWITCH -> {
                    boolean on = st.magnetOn;
                    Color color = Theme.MAGNET;
                    items.add(new Item(key, 0, () -> {
                        cube(g, x, y, z, color, on ? .35f : .13f);
                        if (on) magneticGlow(g, x, y, z, false);
                    }));
                }
                case MAGNET -> items.add(new Item(key, 0, () -> {
                    cube(g, x, y, z, Theme.MAGNET, .85f);
                    if (st.magnetOn) magneticGlow(g, x, y, z, false);
                }));
                case PORTAL -> {
                    final Color pc = lv.portalId(idx) == 'B' ? Theme.PORTAL_B : Theme.PORTAL_A;
                    // 큐브는 흐리게, 그 위에 바닥 링을 겹쳐 "들어가는 문"으로 읽히게 한다.
                    // 같은 문자(A/A, B/B)끼리 같은 색이라 짝을 알아볼 수 있다.
                    items.add(new Item(key, 0, () -> {
                        cube(g, x, y, z, pc, 0.34f);
                        portalRing(g, x, y, z, pc);
                    }));
                }
                default -> {
                }
            }
        }
        // 순서 목표 번호: 칸 위에 번호 패를 띄운다. 상자가 올라간 목표는 초록(채움), 활성 목표는 금색, 잠긴 목표는 회색.
        if (lv.hasSequenceGoals()) {
            for (int gi : lv.goals) {
                final int order = lv.goalOrder(gi);
                if (order <= 0) continue;
                final double x = lv.x(gi), y = lv.y(gi), z = lv.z(gi);
                final boolean open = st.goalUnlocked(lv, gi);
                final boolean filled = st.hasBlock(gi) || gi == pushedTo;
                items.add(new Item(depth(x + 0.5, y + 0.5, layerBase(z) + 0.5) + 1e-4, 1,
                        () -> goalNumber(g, x, y, z, order, open, filled)));
            }
        }
        for (int i = 0; i < st.blockCount(); i++) {
            int b = st.block(i);
            if (b == pushedTo) continue;
            final double x = lv.x(b), y = lv.y(b), z = lv.z(b);
            final Color c = lv.tile(b) == TileType.GOAL ? Theme.BLOCK_ON_GOAL : st.isIron(b) ? Theme.IRON : Theme.BLOCK;
            final boolean held = Engine.isMagnetHeld(lv, st, b);
            items.add(new Item(depth(x + 0.5, y + 0.5, layerBase(z) + 0.5), 1, () -> {
                cube(g, x, y, z, c, 1f);
                if (held) magneticGlow(g, x, y, z, true);
            }));
        }
        if (pushedTo >= 0) {
            final double fx = bx, fy = by, fz = bz;
            final boolean fin = !vanish && lv.tile(pushedTo) == TileType.GOAL && t >= 1;
            final float a = vanish ? (float) Math.max(0, 1 - t) : boxAlpha;
            final boolean iron = st.isIron(pushedTo);
            // 흡인 중인 상자에 고정 표시가 먼저 붙지 않도록, 도착한 뒤에만 표시한다.
            final boolean held = !vanish && t >= .999 && Engine.isMagnetHeld(lv, st, pushedTo);
            items.add(new Item(depth(fx + 0.5, fy + 0.5, layerBase(fz) + 0.5), 1, () -> {
                cube(g, fx, fy, fz, fin ? Theme.BLOCK_ON_GOAL : iron ? Theme.IRON : Theme.BLOCK, a);
                if (held) magneticGlow(g, fx, fy, fz, true);
            }));
        }
        final double fpx = px, fpy = py, fpz = pz;
        final float fpa = playerAlpha;
        if (hasPlayer) items.add(new Item(depth(fpx + 0.5, fpy + 0.5, layerBase(fpz) + 0.5) + 1e-3, 2, () -> {
            if (fpa >= 0.999f) {
                tetra(g, fpx, fpy, fpz);
            } else {
                java.awt.Composite old = g.getComposite();
                g.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, Math.max(0f, fpa)));
                tetra(g, fpx, fpy, fpz);
                g.setComposite(old);
            }
        }));
        if (flashAlpha > 0f) {
            // 막은 칸: 그 칸의 벌마 바로 다음에 그려 벽·상자 위에 붉은 빛이 덮이게 한다
            final int fxc = flashX, fyc = flashY, fzc = flashZ;
            final float fa = flashAlpha;
            items.add(new Item(depth(fxc + 0.5, fyc + 0.5, layerBase(fzc) + 0.5) + 5e-4, 1, () -> {
                cube(g, fxc, fyc, fzc, Theme.BLOCKED, fa);
                g.setStroke(new BasicStroke(3f));
                g.setColor(alpha(Theme.BLOCKED, Math.min(1f, fa + 0.2f)));
                cubeEdges(g, fxc, fyc, fzc);
            }));
        }
        items.sort(Comparator.<Item>comparingDouble(Item::key).thenComparingInt(Item::order));
        for (Item it : items) it.draw().run();

        // 4) 가려져도 보이도록 목표·플레이어 윤곽 (FR-6.5)
        g.setStroke(new BasicStroke(1.8f));
        for (int gi : lv.goals) {
            if (st.hasBlock(gi)) continue;
            if (lv.z(gi) + 0.5 > layerZ) continue;
            boolean open = st.goalUnlocked(lv, gi);
            g.setColor(alpha(open ? Theme.GOAL : Theme.GOAL_LOCKED, open ? 0.7f : 0.5f));
            cubeEdges(g, lv.x(gi), lv.y(gi), lv.z(gi));
        }
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.35f));
        if (hasPlayer) tetra(g, fpx, fpy, fpz);
        g.setComposite(AlphaComposite.SrcOver);

    }

    void objectIcon(Graphics2D g, Level sample, Rectangle area, Color color, float opacity, boolean player, boolean outline) {
        configure(sample, area, new Camera());
        layerZ = Double.MAX_VALUE;
        layerBelow = -Double.MAX_VALUE;
        if (player) tetra(g, 0, 0, 0);
        else {
            cube(g, 0, 0, 0, color, opacity);
            if (outline) { g.setColor(color); g.setStroke(new BasicStroke(1.5f)); cubeEdges(g, 0, 0, 0); }
        }
    }

    /** 어떤 시점에서도 같은 배율이 되도록, 여러 yaw에서의 화면 범위 중 최대치로 맞춘다. */
    private void fit(Level lv, Rectangle area) {
        double savedCos = cosPsi, savedSin = sinPsi;
        ox = 0;
        oy = 0;
        scale = 1;
        double hw = 1, hh = 1;
        for (int k = 0; k < 8; k++) {
            double psi = Math.toRadians(k * 45.0);
            cosPsi = Math.cos(psi);
            sinPsi = Math.sin(psi);
            for (int cx = 0; cx <= 1; cx++) {
                for (int cy = 0; cy <= 1; cy++) {
                    for (int cz = 0; cz <= 1; cz++) {
                        double[] p = pt(cx * lv.w, cy * lv.d, cz * sceneHeight(lv));
                        hw = Math.max(hw, Math.abs(p[0]));
                        hh = Math.max(hh, Math.abs(p[1]));
                    }
                }
            }
        }
        cosPsi = savedCos;
        sinPsi = savedSin;
        double usableWidth = Math.max(1, area.width - (lv.h > 1 ? 44 : 0));
        scale = Math.min(Math.min(usableWidth / 2.0 / hw, area.height / 2.0 / hh) * 0.92, 1.8);
        ox = area.x + area.width / 2.0;
        oy = area.y + area.height / 2.0;
    }

    // ---------- 배경 보조선 ----------

    private void floorGrid(Graphics2D g, Level lv) {
        g.setColor(alpha(gridColor, 0.07f));
        g.fill(poly(pt(0, 0, 0), pt(lv.w, 0, 0), pt(lv.w, lv.d, 0), pt(0, lv.d, 0)));
        g.setColor(alpha(gridColor, 0.28f));
        g.setStroke(new BasicStroke(1f));
        for (int x = 0; x <= lv.w; x++) line(g, pt(x, 0, 0), pt(x, lv.d, 0));
        for (int y = 0; y <= lv.d; y++) line(g, pt(0, y, 0), pt(lv.w, y, 0));
    }

    /** 시점에서 가장 먼 모서리와 그 양옆을 잇는 뒤쪽 틀. */
    private void backFrame(Graphics2D g, Level lv) {
        double[][] c = {{0, 0}, {lv.w, 0}, {lv.w, lv.d}, {0, lv.d}};
        int far = 0;
        for (int i = 1; i < 4; i++) {
            if (depth(c[i][0], c[i][1], 0) < depth(c[far][0], c[far][1], 0)) far = i;
        }
        g.setColor(alpha(gridColor, 0.22f));
        g.setStroke(new BasicStroke(1f));
        for (int k = -1; k <= 1; k++) {
            double[] q = c[Math.floorMod(far + k, 4)];
            line(g, pt(q[0], q[1], 0), pt(q[0], q[1], sceneHeight(lv)));
            if (k != 0) {
                double[] f = c[far];
                line(g, pt(f[0], f[1], sceneHeight(lv)), pt(q[0], q[1], sceneHeight(lv)));
            }
        }
    }

    private void layerPlane(Graphics2D g, Level lv, double z) {
        z = layerBase(z);
        g.setColor(alpha(Theme.PLAYER, 0.025f));
        g.fill(poly(pt(0, 0, z), pt(lv.w, 0, z), pt(lv.w, lv.d, z), pt(0, lv.d, z)));
        g.setColor(alpha(Theme.PLAYER, 0.18f));
        g.setStroke(new BasicStroke(1f));
        for (int x = 0; x <= lv.w; x++) line(g, pt(x, 0, z), pt(x, lv.d, z));
        for (int y = 0; y <= lv.d; y++) line(g, pt(0, y, z), pt(lv.w, y, z));
    }

    /** Quiet perimeter lines and one floor ruler; no solid platforms or labels on objects. */
    private void layerGuides(Graphics2D g, Level lv, int currentLayer) {
        if (lv.h <= 1) return;
        double[][] corners = {{0, 0}, {lv.w, 0}, {lv.w, lv.d}, {0, lv.d}};
        double[] left = corners[0];
        for (double[] corner : corners) {
            if (pt(corner[0], corner[1], 0)[0] < pt(left[0], left[1], 0)[0]) left = corner;
        }
        g.setFont(Theme.font(Font.PLAIN, 11f));
        for (int layer = 0; layer < lv.h; layer++) {
            double z = layerBase(layer);
            g.setStroke(new BasicStroke(.8f));
            g.setColor(alpha(gridColor, layer == currentLayer ? .27f : .13f));
            if (layer > 0) g.draw(poly(pt(0, 0, z), pt(lv.w, 0, z), pt(lv.w, lv.d, z), pt(0, lv.d, z)));
            double[] mark = pt(left[0], left[1], z);
            line(g, new double[]{mark[0] - 5, mark[1]}, mark);
            g.setColor(alpha(Theme.TEXT_DIM, layer == currentLayer ? .95f : .65f));
            Gfx.right(g, (layer + 1) + "층", (int) mark[0] - 9, (int) mark[1] + 4);
        }
    }

    /** 잠긴 목표 칸 가운데의 작은 자물쇠(화면 기준 아이콘). 위치는 그 층 칸 중심 바닥 좌표. */
    private void lockMark(Graphics2D g, double cx, double cy, double z) {
        double[] c = pt(cx, cy, layerBase(z));
        double w = Math.max(9, Math.min(20, S * scale * 0.34));
        g.setColor(alpha(Theme.GOAL_LOCKED, 0.85f));
        g.setStroke(new BasicStroke(2f));
        g.draw(new java.awt.geom.Arc2D.Double(c[0] - w * 0.42, c[1] - w * 0.95, w * 0.84, w * 0.84,
                0, 180, java.awt.geom.Arc2D.OPEN));
        g.fill(new java.awt.geom.RoundRectangle2D.Double(c[0] - w * 0.62, c[1] - w * 0.52,
                w * 1.24, w * 0.98, 3, 3));
        g.setColor(alpha(Theme.PANEL_LOCK, 0.9f));
        g.fill(new java.awt.geom.Ellipse2D.Double(c[0] - w * 0.10, c[1] - w * 0.24, w * 0.20, w * 0.20));
    }

    /**
     * 순서 목표 번호 표시: 칸 위에 뜬 둥근 패에 번호를 쓴다.
     * 채운 목표는 초록(상자 위에 올라앉는다), 지금 채울 수 있는 목표는 금색, 잠긴 목표는 어두운 회색이다.
     */
    private void goalNumber(Graphics2D g, double x, double y, double z, int order, boolean open, boolean filled) {
        float la = layerAlpha(z);
        if (la <= 0.06f) return;
        double[] c = pt(x + .5, y + .5, layerBase(z) + (filled ? 1.02 : .80));
        int r = (int) Math.max(8, Math.min(17, S * scale * 0.30));
        Color base = filled ? Theme.BLOCK_ON_GOAL : open ? Theme.GOAL : Theme.GOAL_LOCKED;
        float strength = filled ? 1f : open ? 0.95f : 0.7f;
        java.awt.geom.Ellipse2D disc = new java.awt.geom.Ellipse2D.Double(c[0] - r, c[1] - r, r * 2, r * 2);
        g.setColor(alpha(base, strength * la));
        g.fill(disc);
        g.setStroke(new BasicStroke(open || filled ? 2.2f : 1.3f));
        g.setColor(alpha(Theme.shade(base, open || filled ? 1.2 : 1.35), la));
        g.draw(disc);
        g.setFont(Theme.font(Font.BOLD, Math.max(9f, r * 1.15f)));
        g.setColor(alpha(open || filled ? Theme.GOAL_NUMBER : Theme.TEXT, la));
        Gfx.centered(g, Integer.toString(order), (int) Math.round(c[0]), (int) Math.round(c[1] + r * 0.38));
    }

    /** 칸 안쪽 바닥(그 층의 윗면)에 그리는 포탈 링. 투영을 거치므로 카메라 각도에 맞게 기울어진다. */
    private void portalRing(Graphics2D g, double x, double y, double z, Color c) {
        double zz = layerBase(z) + 0.5;      // 칸 중앙 높이에 띄워서 큐브와 겹쳐 보이게 한다
        double cx = x + 0.5, cy = y + 0.5;
        float la = layerAlpha(z);
        double[] radii = {0.36, 0.22};
        for (int r = 0; r < radii.length; r++) {
            Path2D ring = new Path2D.Double();
            for (int i = 0; i <= 24; i++) {
                double a = i * 2 * Math.PI / 24;
                double[] p = pt(cx + radii[r] * Math.cos(a), cy + radii[r] * Math.sin(a), zz);
                if (i == 0) ring.moveTo(p[0], p[1]); else ring.lineTo(p[0], p[1]);
            }
            ring.closePath();
            g.setStroke(new BasicStroke(r == 0 ? 3.2f : 2f));
            g.setColor(alpha(Theme.shade(c, r == 0 ? 1.25 : 1.5), Math.min(1f, (r == 0 ? 0.95f : 0.8f) * la)));
            g.draw(ring);
        }
        // 가운데 점: 안쪽으로 빨려 들어가는 느낌
        double[] m = pt(cx, cy, zz);
        g.setColor(alpha(Theme.shade(c, 1.7), 0.9f * la));
        g.fill(new java.awt.geom.Ellipse2D.Double(m[0] - 3, m[1] - 3, 6, 6));
    }

    private void shadow(Graphics2D g, double x, double y, double size) {
        shadow(g, x, y, size, 1f);
    }

    /** 포탈 전송 중에는 개체가 투명해지는 만큼 그림자도 옅게 한다. */
    private void shadow(Graphics2D g, double x, double y, double size, float strength) {
        double m = (1 - size) / 2;
        g.setColor(new Color(0, 0, 0, Math.round(70 * Math.max(0f, Math.min(1f, strength)))));
        g.fill(square(x + m, y + m, size, 0));
    }

    // ---------- 도형 ----------

    private static final int[][] FACE_N = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

    /** 층 강조 상태에서 z층 물체에 곱할 불투명도. 강조를 꺼 두면 항상 1. */
    private float layerAlpha(double z) {
        if (z + 0.5 > layerZ) return ABOVE_ALPHA;
        if (z + 0.5 < layerBelow) return BELOW_ALPHA;
        return 1f;
    }

    /** 셀 (x,y,z)를 채우는 단위 정육면체. alpha < 1이면 반투명(뒤가 비친다). 카메라를 향한 면만 그린다. */
    private void cube(Graphics2D g, double x, double y, double z, Color base, float alpha) {
        alpha *= layerAlpha(z); // 층 강조: 위층은 많이, 아래층은 조금 흐리게
        for (int[] n : FACE_N) {
            if (!facing(n[0], n[1], n[2])) continue;
            Path2D face = poly(faceCorners(x, y, z, n));
            double lum = 0.45 + 0.6 * Math.max(0, n[0] * LIGHT[0] + n[1] * LIGHT[1] + n[2] * LIGHT[2]);
            g.setColor(alpha(Theme.shade(base, lum), alpha));
            g.fill(face);
            g.setStroke(new BasicStroke(1f));
            g.setColor(alpha(Theme.shade(base, 0.4), Math.min(1f, alpha + 0.25f)));
            g.draw(face);
        }
    }

    private void cubeEdges(Graphics2D g, double x, double y, double z) {
        for (int[] n : FACE_N) {
            if (facing(n[0], n[1], n[2])) g.draw(poly(faceCorners(x, y, z, n)));
        }
    }

    /** 셀 면(법선 n)의 네 꼭짓점의 화면 좌표. */
    private double[][] faceCorners(double x, double y, double z, int[] n) {
        z = layerBase(z);
        double fx = n[0] > 0 ? x + 1 : x, fy = n[1] > 0 ? y + 1 : y, fz = n[2] > 0 ? z + 1 : z;
        if (n[0] != 0) {
            return new double[][]{pt(fx, y, z), pt(fx, y + 1, z), pt(fx, y + 1, z + 1), pt(fx, y, z + 1)};
        } else if (n[1] != 0) {
            return new double[][]{pt(x, fy, z), pt(x + 1, fy, z), pt(x + 1, fy, z + 1), pt(x, fy, z + 1)};
        }
        return new double[][]{pt(x, y, fz), pt(x + 1, y, fz), pt(x + 1, y + 1, fz), pt(x, y + 1, fz)};
    }

    /**
     * 정사면체 플레이어. 한 모서리 0.9, 꼭대기가 위, 밑면은 정삼각형, 무게중심이 셀 중심.
     * 카메라를 향한 면만 평면 음영으로 칠한다.
     */
    private void tetra(Graphics2D g, double x, double y, double z) {
        double cx = x + 0.5, cy = y + 0.5, cz = layerBase(z) + 0.5;
        double edge = 0.9, base = edge / Math.sqrt(3), hgt = edge * Math.sqrt(2.0 / 3.0), rot = Math.toRadians(20);
        double[][] v = new double[4][];
        v[0] = new double[]{0, 0, 0.75 * hgt};
        for (int i = 0; i < 3; i++) {
            double ang = rot + i * 2 * Math.PI / 3;
            v[i + 1] = new double[]{base * Math.cos(ang), base * Math.sin(ang), -0.25 * hgt};
        }
        int[][] faces = {{0, 1, 2}, {0, 2, 3}, {0, 3, 1}, {1, 2, 3}};
        List<double[]> drawn = new ArrayList<>(); // {깊이, 면 번호}
        for (int f = 0; f < faces.length; f++) {
            double[] n = normal(v, faces[f]);
            if (!facing(n[0], n[1], n[2])) continue;
            double d = 0;
            for (int i : faces[f]) d += depth(cx + v[i][0], cy + v[i][1], cz + v[i][2]);
            drawn.add(new double[]{d, f});
        }
        drawn.sort(Comparator.comparingDouble(a -> a[0]));
        for (double[] d : drawn) {
            int[] fi = faces[(int) d[1]];
            double[] n = normal(v, fi);
            double lum = 0.5 + 0.6 * Math.max(0, n[0] * LIGHT[0] + n[1] * LIGHT[1] + n[2] * LIGHT[2]);
            double[][] pts = new double[3][];
            for (int i = 0; i < 3; i++) pts[i] = pt(cx + v[fi[i]][0], cy + v[fi[i]][1], cz + v[fi[i]][2]);
            Path2D p = poly(pts);
            g.setColor(Theme.shade(Theme.PLAYER, lum));
            g.fill(p);
            g.setStroke(new BasicStroke(1f));
            g.setColor(Theme.shade(Theme.PLAYER, 0.45));
            g.draw(p);
        }
    }

    /** 면의 바깥쪽 단위 법선 (정사면체 중심이 원점). */
    private static double[] normal(double[][] v, int[] f) {
        double[] a = v[f[0]], b = v[f[1]], c = v[f[2]];
        double[] u = {b[0] - a[0], b[1] - a[1], b[2] - a[2]};
        double[] w = {c[0] - a[0], c[1] - a[1], c[2] - a[2]};
        double nx = u[1] * w[2] - u[2] * w[1], ny = u[2] * w[0] - u[0] * w[2], nz = u[0] * w[1] - u[1] * w[0];
        double dot = nx * (a[0] + b[0] + c[0]) + ny * (a[1] + b[1] + c[1]) + nz * (a[2] + b[2] + c[2]);
        if (dot < 0) {
            nx = -nx;
            ny = -ny;
            nz = -nz;
        }
        return norm3(nx, ny, nz);
    }

    private static double[] norm3(double x, double y, double z) {
        double l = Math.sqrt(x * x + y * y + z * z);
        return new double[]{x / l, y / l, z / l};
    }

    /** 높이 z 평면 위의 한 변 len 정사각형(화면에선 마름모). */
    private Path2D square(double x, double y, double len, double z) {
        return poly(pt(x, y, z), pt(x + len, y, z), pt(x + len, y + len, z), pt(x, y + len, z));
    }

    private static void line(Graphics2D g, double[] a, double[] b) {
        g.draw(new Line2D.Double(a[0], a[1], b[0], b[1]));
    }

    private static Path2D poly(double[]... pts) {
        Path2D p = new Path2D.Double();
        p.moveTo(pts[0][0], pts[0][1]);
        for (int i = 1; i < pts.length; i++) p.lineTo(pts[i][0], pts[i][1]);
        p.closePath();
        return p;
    }

    private static Color alpha(Color c, float a) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.round(255 * Math.max(0f, Math.min(1f, a))));
    }

    /** 전원 ON은 부드러운 발광 윤곽, 고정된 철 상자는 밝은 테두리와 자물쇠로 구분한다. */
    private void magneticGlow(Graphics2D original, double x, double y, double z, boolean held) {
        Graphics2D g = (Graphics2D) original.create();
        float strength = layerAlpha(z) * (float) (.8 + .2 * Math.sin(effectMillis * Math.PI / 1100));
        Color light = Theme.shade(Theme.MAGNET, 1.4);
        for (int i = 0; i < 3; i++) {
            float width = i == 0 ? 10f : i == 1 ? 5f : 1.8f;
            float opacity = i == 0 ? .09f : i == 1 ? .20f : .85f;
            g.setStroke(new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(alpha(light, strength * opacity));
            cubeEdges(g, x, y, z);
        }
        if (held) {
            // 텍스트를 띄우지 않고 큐브 중앙에 작은 고정 아이콘을 얹는다.
            double[] p = pt(x + .5, y + .5, layerBase(z) + .66);
            double size = Math.max(10, Math.min(17, 13 * scale));
            g.setColor(alpha(Theme.BG_BOTTOM, .88f * layerAlpha(z)));
            g.fill(new java.awt.geom.Ellipse2D.Double(p[0] - size, p[1] - size, size * 2, size * 2));
            g.setColor(alpha(light, .98f * layerAlpha(z)));
            g.setStroke(new BasicStroke(2f));
            g.draw(new java.awt.geom.Arc2D.Double(p[0] - size * .40, p[1] - size * .65,
                    size * .8, size * .9, 0, 180, java.awt.geom.Arc2D.OPEN));
            g.fill(new java.awt.geom.RoundRectangle2D.Double(p[0] - size * .6, p[1] - size * .2,
                    size * 1.2, size * .85, 3, 3));
            g.setColor(alpha(Theme.BG_BOTTOM, layerAlpha(z)));
            g.fill(new java.awt.geom.Ellipse2D.Double(p[0] - 1.5, p[1] + size * .05, 3, 4));
        }
        g.dispose();
    }

    private void magneticBeam(Graphics2D original, Level lv, sokoban.core.Magnet magnet, boolean on) {
        int end = magnet.cell();
        for (int n=0;n<magnet.range();n++) {
            int next=lv.neighbor(end,magnet.facing());
            if(next<0)break;
            end=next;
            if(lv.tile(end)==TileType.WALL || lv.tile(end)==TileType.MAGNET)break;
        }
        if(end==magnet.cell())return;
        Graphics2D g=(Graphics2D)original.create();
        g.setColor(alpha(Theme.MAGNET,on?.7f:.22f));
        g.setStroke(new BasicStroke(on?2f:1f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND,10,new float[]{5,5},
                on ? (float) ((effectMillis / 55.0) % 10) : 0));
        int c=magnet.cell();
        double[] a=pt(lv.x(end)+.5,lv.y(end)+.5,layerBase(lv.z(end))+.5);
        double[] b=pt(lv.x(c)+.5,lv.y(c)+.5,layerBase(lv.z(c))+.5);
        line(g,a,b);
        if (on) {
            g.setStroke(new BasicStroke(7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(alpha(Theme.MAGNET, .12f));
            line(g, a, b);
            // 먼 곳에서 장치 쪽으로 흐르는 입자가 실제 흡인 방향을 보여 준다.
            for (int i = 0; i < 4; i++) {
                double progress = (effectMillis / 1650.0 + i / 4.0) % 1;
                double px = lerp(a[0], b[0], progress), py = lerp(a[1], b[1], progress);
                g.setColor(alpha(Theme.shade(Theme.MAGNET, 1.5), .82f));
                g.fill(new java.awt.geom.Ellipse2D.Double(px - 2.4, py - 2.4, 4.8, 4.8));
            }
        }
        g.setColor(alpha(Theme.MAGNET, on ? .85f : .22f));
        double dx=b[0]-a[0],dy=b[1]-a[1],len=Math.hypot(dx,dy);
        if(len>1) {
            double x=lerp(a[0],b[0],.55),y=lerp(a[1],b[1],.55),ux=dx/len,uy=dy/len;
            g.fill(poly(new double[]{x,y},new double[]{x-10*ux+5*uy,y-10*uy-5*ux},new double[]{x-10*ux-5*uy,y-10*uy+5*ux}));
        }
        g.dispose();
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }
}
