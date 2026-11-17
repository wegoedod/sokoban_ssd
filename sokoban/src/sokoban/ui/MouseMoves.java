package sokoban.ui;

import sokoban.core.Direction;
import sokoban.core.Engine;
import sokoban.core.GameState;
import sokoban.core.Level;
import sokoban.core.StepResult;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

/** Click one of the six adjacent cells. No path finding or changes to game rules. */
final class MouseMoves {
    record Target(Direction direction, int cell, StepResult preview,
                  IsoRenderer.CellProjection projection) {
        boolean available() { return preview != null; }
    }

    private final List<Target> targets = new ArrayList<>();
    private Rectangle viewport = new Rectangle();
    private IsoRenderer.CellProjection player;

    void clear() { targets.clear(); }

    void layout(Level level, GameState state, IsoRenderer renderer, Camera camera, Rectangle area) {
        clear();
        viewport = new Rectangle(area);
        renderer.configure(level, area, camera);
        if (camera.isAnimating()) return;
        boolean flat = level.h == 1;
        int px = level.x(state.player), py = level.y(state.player), pz = level.z(state.player);
        player = renderer.projectCell(px, py, pz, flat);
        for (Direction direction : Direction.values()) {
            if (flat && direction.dz != 0) continue;
            int cell = level.neighbor(state.player, direction);
            var projection = renderer.projectCell(px + direction.dx, py + direction.dy, pz + direction.dz, flat);
            StepResult result = Engine.step(level, state, direction);
            // Keep blocked/outside neighbours in picking, so clicking them cannot select a cell behind them.
            targets.add(new Target(direction, cell, result, projection));
        }
    }

    Target pick(int x, int y) {
        if (!viewport.contains(x, y) || targets.isEmpty()) return null;
        Target nearest = null;
        double distance = Double.POSITIVE_INFINITY;
        boolean tied = false;
        for (Target target : targets) {
            if (!target.projection().shape().contains(x, y)) continue;
            double candidate = target.projection().center().distanceSq(x, y);
            if (candidate < distance - .01) {
                nearest = target;
                distance = candidate;
                tied = false;
            } else if (Math.abs(candidate - distance) <= .01) {
                tied = true;
            }
        }
        if (nearest == null || tied) return null;
        if (player.shape().contains(x, y) && player.center().distanceSq(x, y) <= distance) return null;
        return nearest;
    }

    void paint(Graphics2D original, IsoRenderer renderer, Level level, Target hover) {
        Graphics2D g = (Graphics2D) original.create();
        g.clip(viewport);
        for (Target target : targets) {
            boolean selected = target == hover;
            if (!target.available() && !selected) continue;
            Color color = color(target);
            g.setColor(alpha(color, selected ? 54 : 13));
            g.fill(target.projection().shape());
            g.setStroke(new BasicStroke(selected ? 2.3f : 1.3f));
            g.setColor(alpha(color, selected ? 255 : 145));
            g.draw(target.projection().edges());
            if (target.available() && target.direction().dz == 0) {
                Point2D p = target.projection().center();
                g.fillOval((int) p.getX() - 3, (int) p.getY() - 3, 6, 6);
            }
        }
        if (hover != null && hover.available() && hover.preview().pushed()) {
            int boxTo = hover.preview().blockTo();
            var arrival = renderer.projectCell(level.x(boxTo), level.y(boxTo), level.z(boxTo), level.h == 1);
            g.setColor(color(hover));
            g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                    10f, new float[]{5f, 4f}, 0));
            g.draw(arrival.edges());
        }
        g.dispose();
    }

    String description(Target target, Level level) {
        if (target == null) return "색칠된 인접 칸 클릭: 한 칸 이동 / 상자 밀기" + (level.h > 1 ? " · 위·아래층도 직접 클릭" : "");
        if (!target.available()) return "막힌 칸 · 이동할 수 없습니다";
        if (target.preview().blockVanished()) return "클릭: 상자로 구멍 메우기 · 상자는 소멸합니다";
        if (target.preview().pushed()) return "클릭: 상자 밀기 · 점선은 상자 도착 칸 (" + (level.z(target.preview().blockTo()) + 1) + "층)";
        return "클릭: " + (level.z(target.cell()) + 1) + "층의 이 칸으로 이동";
    }

    private static Color color(Target target) {
        if (!target.available()) return Theme.TEXT_DIM;
        return target.direction().dz == 0 ? Theme.PREVIEW_PLANAR : Theme.PREVIEW_VERTICAL;
    }

    private static Color alpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }
}
