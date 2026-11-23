package sokoban.core;

/**
 * 규칙 엔진. Swing에 의존하지 않으며 상태를 바꾸지 않는 순수 함수다(NFR-1, NFR-2).
 *
 * <p>3D 격자 규칙: 중력·바닥이 없어 모든 빈 칸은 공중이며, 플레이어와 블록 모두 x, y, z 6방향으로 움직인다.
 * 턴 해석 순서(FR-2.6, 기본 게임 부분): 플레이어 이동 → 밀기 → (구멍 채움) → 클리어 판정.
 * 추가 기믹(포탈 등)은 밀기와 클리어 판정 사이에 단계로 끼워 넣는다.
 */
public final class Engine {
    private Engine() {
    }

    /**
     * 입력 1회를 처리한다. 플레이어는 6방향(x, y, z)으로 움직이고 블록도 같은 6방향으로 밀린다.
     *
     * @return 상태가 변했으면 결과, 막혀서 변화가 없으면 null (이동 횟수에 포함하지 않는다. FR-1.4)
     */
    public static StepResult step(Level lv, GameState s, Direction dir) {
        int from = s.player;
        int to = lv.neighbor(from, dir);
        if (to < 0 || !walkable(lv, s, to)) return null;

        if (!s.hasBlock(to)) {
            GameState next = s.withPlayer(to);
            return new StepResult(next, dir, from, to, -1, -1, false, isCleared(lv, next));
        }

        // 블록 밀기: 한 번에 1개만, 뒤가 막히면 불가 (FR-1.3)
        int behind = lv.neighbor(to, dir);
        if (behind < 0 || s.hasBlock(behind)) return null;
        TileType t = lv.tile(behind);
        if (t == TileType.WALL) return null;

        if (t == TileType.HOLE && s.isHole(behind)) {
            GameState next = s.withFill(to, to, behind);
            return new StepResult(next, dir, from, to, to, behind, true, isCleared(lv, next));
        }
        GameState next = s.withPush(to, to, behind);
        return new StepResult(next, dir, from, to, to, behind, false, isCleared(lv, next));
    }

    /**
     * 입력이 막힌 이유를 돌려준다. {@link #step}이 null을 돌려줄 때만 값이 있고, 아니면 null이다.
     * UI가 막힌 칸을 강조하고 안내 문구를 띄우는 데 쓴다. 상태나 규칙에는 영향이 없다.
     *
     * <p>주의: 규칙이 늘어(빙판, 포탈, 당기기 등) {@code step}에 막히는 조건이 생기면 여기에도 사유를 추가해야 한다.
     * 두 함수가 어긋나지 않는지는 단위 테스트가 모든 출고 레벨에서 검사한다.
     */
    public static BlockReason whyBlocked(Level lv, GameState s, Direction dir) {
        int from = s.player;
        int to = lv.neighbor(from, dir);
        if (to < 0) {
            return new BlockReason(BlockReason.Kind.EDGE,
                    lv.x(from) + dir.dx, lv.y(from) + dir.dy, lv.z(from) + dir.dz);
        }
        if (!walkable(lv, s, to)) {
            BlockReason.Kind k = lv.tile(to) == TileType.WALL ? BlockReason.Kind.WALL : BlockReason.Kind.HOLE;
            return new BlockReason(k, lv.x(to), lv.y(to), lv.z(to));
        }
        if (!s.hasBlock(to)) return null;

        int behind = lv.neighbor(to, dir);
        if (behind < 0) {
            return new BlockReason(BlockReason.Kind.PUSH_EDGE,
                    lv.x(to) + dir.dx, lv.y(to) + dir.dy, lv.z(to) + dir.dz);
        }
        if (s.hasBlock(behind)) {
            return new BlockReason(BlockReason.Kind.PUSH_BOX, lv.x(behind), lv.y(behind), lv.z(behind));
        }
        if (lv.tile(behind) == TileType.WALL) {
            return new BlockReason(BlockReason.Kind.PUSH_WALL, lv.x(behind), lv.y(behind), lv.z(behind));
        }
        return null;
    }

    /** 플레이어가 들어갈 수 있는 칸인가(블록 점유는 여기서 따지지 않는다). */
    private static boolean walkable(Level lv, GameState s, int idx) {
        return switch (lv.tile(idx)) {
            // ICE·PORTAL: 플레이어는 평범하게 걷는다. 빙판 미끄러짐과 포탈 전송은 후속 엔진 PR에서 구현한다(지금은 빈 칸과 같다).
            case EMPTY, GOAL, ICE, PORTAL -> true;
            case HOLE -> !s.isHole(idx); // 메워진 구멍은 평범한 빈 칸
            case WALL -> false;
        };
    }

    /** 모든 목표 지점에 블록이 놓였는가 (FR-1.5). */
    public static boolean isCleared(Level lv, GameState s) {
        if (lv.goals.length == 0) return false;
        for (int g : lv.goals) {
            if (!s.hasBlock(g)) return false;
        }
        return true;
    }
}
