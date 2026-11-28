package sokoban.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 입력 하나에 딸린 철 상자 흡인. 결정적인 순서로 한 칸씩 이동하며 포탈 순환은 턴 전체를 거부한다. */
final class MagneticField {
    record Settled(GameState state, List<StepResult> phases) { }

    static Settled settle(Level lv, GameState start) {
        if (!start.magnetOn) return new Settled(start, List.of());
        GameState state = start;
        List<StepResult> phases = new ArrayList<>();
        Set<GameState> seen = new HashSet<>();
        seen.add(start);
        long limit = Math.min(4L * lv.cellCount() * (start.blockCount() + 1) + 32, 4096);
        while (true) {
            StepResult next = next(lv, state);
            if (next == null) return new Settled(state, List.copyOf(phases));
            if (phases.size() >= limit || !seen.add(next.state())) return null;
            phases.add(next);
            state = next.state();
        }
    }

    private static StepResult next(Level lv, GameState s) {
        for (int i = 0; i < s.blockCount(); i++) {
            int b = s.block(i);
            if (!s.isIron(b)) continue;
            Magnet magnet = attracting(lv, s, b);
            if (magnet != null) {
                StepResult r = move(lv, s, b, magnet.pullDirection());
                if (r != null) return r;
            }
        }
        return null;
    }

    /** 가까운 장치 우선, 동률이면 작은 셀 인덱스 우선. 벽·상자·플레이어는 자기장을 가린다. */
    static Magnet attracting(Level lv, GameState s, int block) {
        Magnet best = null;
        int distance = Integer.MAX_VALUE;
        for (Magnet magnet : lv.magnets) {
            int cell = magnet.cell();
            for (int n = 1; n <= magnet.range(); n++) {
                cell = lv.neighbor(cell, magnet.facing());
                if (cell < 0 || solid(lv.tile(cell)) || cell == s.player || !s.goalUnlocked(lv, cell)) break;
                if (cell == block) {
                    if (n < distance) { best = magnet; distance = n; }
                    break;
                }
                if (s.hasBlock(cell)) break;
            }
        }
        return best;
    }

    static boolean solid(TileType tile) { return tile == TileType.WALL || tile == TileType.MAGNET; }

    static boolean attached(Level lv, GameState s, int cell) {
        if (!s.magnetOn || !s.isIron(cell)) return false;
        Magnet magnet = attracting(lv, s, cell);
        return magnet != null && lv.neighbor(magnet.cell(), magnet.facing()) == cell;
    }

    private static StepResult move(Level lv, GameState s, int from, Direction dir) {
        int to = lv.neighbor(from, dir), via = -1;
        if (!free(lv, s, to)) return null;
        if (lv.tile(to) == TileType.PORTAL) {
            via = to;
            to = lv.portalPartner(to);
            if (to == from || !free(lv, s, to)) return null;
        }
        boolean vanished = lv.tile(to) == TileType.HOLE && s.isHole(to);
        GameState next = vanished ? s.withFill(s.player, from, to) : s.withPush(s.player, from, to);
        return new StepResult(next, dir, s.player, s.player, from, to, vanished, false, -1, via, 0);
    }

    private static boolean free(Level lv, GameState s, int cell) {
        return cell >= 0 && !solid(lv.tile(cell)) && cell != s.player && !s.hasBlock(cell)
                && s.goalUnlocked(lv, cell);
    }
}
