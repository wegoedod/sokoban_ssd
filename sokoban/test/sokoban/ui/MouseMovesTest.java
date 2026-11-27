package sokoban.ui;

import sokoban.config.GameConfig;
import sokoban.core.Direction;
import sokoban.core.GameState;
import sokoban.core.Level;
import sokoban.core.Session;
import sokoban.core.TileType;

import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import javax.imageio.ImageIO;

public final class MouseMovesTest {
    private static int at(int x, int y, int z) { return (z * 5 + y) * 5 + x; }

    private static Level level(boolean flat, int[] boxes, int wall, int hole) {
        TileType[] tiles = new TileType[flat ? 25 : 125];
        Arrays.fill(tiles, TileType.EMPTY);
        tiles[0] = TileType.GOAL;
        if (wall >= 0) tiles[wall] = TileType.WALL;
        if (hole >= 0) tiles[hole] = TileType.HOLE;
        return new Level("mouse-test", "클릭 이동", 5, 5, flat ? 1 : 5, tiles,
                GameState.of(at(2, 2, flat ? 0 : 2), boxes, hole < 0 ? new int[0] : new int[]{hole}),
                10, "", null, null, Map.of(), "test");
    }

    private static final Rectangle AREA = new Rectangle(20, 112, 960, 522);

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static MouseMoves.Target pick(MouseMoves mouse, IsoRenderer renderer, int x, int y, int z, boolean flat) {
        Point2D p = renderer.projectCell(x, y, z, flat).center();
        return mouse.pick((int) Math.round(p.getX()), (int) Math.round(p.getY()));
    }

    public static void flatNeighbourClicksAndIgnoredCells() {
        Level lv = level(true, new int[0], -1, -1);
        Camera cam = new Camera(); cam.snapToTopView();
        IsoRenderer renderer = new IsoRenderer();
        MouseMoves mouse = new MouseMoves();
        mouse.layout(lv, lv.initial, renderer, cam, AREA);
        for (Direction d : Direction.values()) {
            if (d.dz != 0) continue;
            var t = pick(mouse, renderer, 2 + d.dx, 2 + d.dy, 0, true);
            check(t != null && t.direction() == d && t.available(), "flat " + d);
        }
        check(pick(mouse, renderer, 2, 2, 0, true) == null, "clicking player must do nothing");
        check(pick(mouse, renderer, 0, 0, 0, true) == null, "distant cell must not pathfind");
        check(mouse.pick(5, 5) == null, "HUD must not move player");
        check(lv.initial.player == at(2, 2, 0), "layout changed state");
    }

    public static void allSixCellsAtEveryCameraAngle() {
        Level lv = level(false, new int[0], -1, -1);
        Camera cam = new Camera();
        IsoRenderer renderer = new IsoRenderer();
        MouseMoves mouse = new MouseMoves();
        for (int step = 0; step < 8; step++) {
            cam.yaw = cam.targetYaw = step * 45;
            mouse.layout(lv, lv.initial, renderer, cam, AREA);
            for (Direction d : Direction.values()) {
                var t = pick(mouse, renderer, 2 + d.dx, 2 + d.dy, 2 + d.dz, false);
                check(t != null && t.direction() == d && t.available(), "rotation " + step + " direction " + d);
                // Hit the actual vertical face away from its center, including axis-aligned views.
                if (d.dz != 0) {
                    Point2D center = renderer.projectCell(2, 2, 2 + d.dz, false).center();
                    var face = mouse.pick((int) center.getX() + 16, (int) center.getY());
                    check(face != null && face.direction() == d, "vertical face must be clickable: " + d);
                }
            }
        }
    }

    public static void blockedClicksAndVerticalPushes() {
        Camera cam = new Camera();
        IsoRenderer renderer = new IsoRenderer();
        MouseMoves mouse = new MouseMoves();
        for (Direction d : new Direction[]{Direction.ABOVE, Direction.BELOW}) {
            int box = at(2, 2, 2 + d.dz), destination = at(2, 2, 2 + 2 * d.dz);
            Level blocked = level(false, new int[]{box}, destination, -1);
            mouse.layout(blocked, blocked.initial, renderer, cam, AREA);
            var blockedTarget = pick(mouse, renderer, 2, 2, 2 + d.dz, false);
            check(blockedTarget != null && !blockedTarget.available(), "wall behind box must block click");
            Level lv = level(false, new int[]{box}, -1, destination);
            Session session = new Session(lv, GameConfig.defaults());
            mouse.layout(lv, session.state(), renderer, cam, AREA);
            var target = pick(mouse, renderer, 2, 2, 2 + d.dz, false);
            check(target != null && target.direction() == d && target.preview().blockVanished(), "vertical hole preview");
            check(session.moves() == 0 && session.state() == lv.initial, "preview cannot change history");
            session.move(target.direction());
            check(session.moves() == 1 && session.state().player == box && session.state().blockCount() == 0,
                    "one click must execute one normal engine move");
            session.undo();
            mouse.layout(lv, session.state(), renderer, cam, AREA);
            check(pick(mouse, renderer, 2, 2, 2 + d.dz, false).preview().blockTo() == destination,
                    "undo must refresh the click target");
        }
    }

    public static void resizeAnimationAndClearedTargets() {
        Level lv = level(false, new int[0], -1, -1);
        Camera cam = new Camera();
        IsoRenderer renderer = new IsoRenderer();
        MouseMoves mouse = new MouseMoves();
        mouse.layout(lv, lv.initial, renderer, cam, new Rectangle(20, 112, 600, 302));
        check(pick(mouse, renderer, 2, 2, 3, false).direction() == Direction.ABOVE, "small window vertical click");
        cam.rotateStep(1);
        mouse.layout(lv, lv.initial, renderer, cam, AREA);
        check(mouse.pick(500, 350) == null, "moving camera must not expose stale click targets");
        for (int i = 0; i < 200; i++) cam.update(16);
        mouse.layout(lv, lv.initial, renderer, cam, AREA);
        check(pick(mouse, renderer, 2, 2, 1, false).direction() == Direction.BELOW, "settled camera click");
        mouse.clear();
        check(pick(mouse, renderer, 2, 2, 1, false) == null, "cleared targets must not respond");
    }

    /** 막힘 안내: 잠긴 순서 목표를 짚어 "먼저 N번 목표에 상자를 넣으세요"로 안내한다. */
    private static void lockedGoalGuidance() throws Exception {
        Level lv = sokoban.level.LevelParser.parse("guide", java.util.List.of(
                "id: G", "layer 0", "#######", "#@1-2-#", "#-$-$-#", "#-----#", "#######"));
        var why = new sokoban.core.BlockReason(sokoban.core.BlockReason.Kind.LOCKED_GOAL, 4, 1, 0);
        String text = BlockText.text(lv, lv.initial, why);
        check(text.contains("2번 목표"), "잠긴 목표 번호를 알려 준다: " + text);
        check(text.contains("먼저 1번 목표에 상자를 넣으세요"), "앞 번호 목표를 안내한다: " + text);
        var push = new sokoban.core.BlockReason(sokoban.core.BlockReason.Kind.PUSH_LOCKED_GOAL, 4, 1, 0);
        check(BlockText.text(lv, lv.initial, push).contains("상자를 넣을 수 없습니다"), "밀기 안내");
        // 순서 목표가 아닌 칸을 가리키면 일반 문구로 물러난다(격자 밖 포함)
        var odd = new sokoban.core.BlockReason(sokoban.core.BlockReason.Kind.LOCKED_GOAL, -1, 0, 0);
        check(BlockText.text(lv, lv.initial, odd).contains("잠겨"), "fallback");
    }

    /** Generate review images without a window or changing the user's save. */
    public static void main(String[] args) throws Exception {
        flatNeighbourClicksAndIgnoredCells();
        allSixCellsAtEveryCameraAngle();
        blockedClicksAndVerticalPushes();
        resizeAnimationAndClearedTargets();
        lockedGoalGuidance();
        System.out.println("5 focused mouse checks passed.");
        if (args.length == 0) return;
        Path dir = Path.of(args[0]);
        Files.createDirectories(dir);
        for (int sample = 0; sample < 4; sample++) {
            boolean flat = sample == 0;
            Level lv = level(flat, new int[]{flat ? at(3, 2, 0) : at(2, 2, 3)}, -1, -1);
            if (sample == 1) lv = sokoban.level.LevelRepository.load(Path.of("levels"), GameConfig.defaults()).levels.get(7);
            Camera cam = new Camera();
            if (flat) cam.snapToTopView();
            else cam.yaw = cam.targetYaw = (sample - 1) * 45;
            int w = sample == 3 ? 640 : 1000, h = sample == 3 ? 480 : 700;
            Rectangle area = new Rectangle(20, 112, w - 40, h - 178);
            IsoRenderer renderer = new IsoRenderer();
            MouseMoves mouse = new MouseMoves();
            BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = image.createGraphics();
            g.setColor(Theme.BG_BOTTOM); g.fillRect(0, 0, w, h);
            renderer.render(g, lv, lv.initial, null, area, cam, false);
            mouse.layout(lv, lv.initial, renderer, cam, area);
            var hover = sample == 1 ? null : flat ? pick(mouse, renderer, 3, 2, 0, true) : pick(mouse, renderer, 2, 2, 3, false);
            mouse.paint(g, renderer, lv, hover);
            g.setFont(Theme.font(Font.PLAIN, 14)); g.setColor(Theme.TEXT);
            Gfx.centered(g, mouse.description(hover, lv), w / 2, 88);
            g.dispose();
            ImageIO.write(image, "png", dir.resolve("click-" + sample + ".png").toFile());
        }
        System.out.println("Rendered mouse control examples to " + dir.toAbsolutePath());
    }
}
