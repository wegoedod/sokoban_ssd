package sokoban.core;

/**
 * 규칙 엔진. Swing에 의존하지 않으며 상태를 바꾸지 않는 순수 함수다(NFR-1, NFR-2).
 *
 * <p>3D 격자 규칙: 중력·바닥이 없어 모든 빈 칸은 공중이며, 플레이어와 블록 모두 x, y, z 6방향으로 움직인다.
 * 턴 해석 순서(FR-2.6): 플레이어 이동 → 밀기 → 포탈 전송 → 구멍 채움 → 순서 열쇠 수집 → 클리어 판정.
 * 새 기믹은 이 사이에 단계로 끼워 넣는다.
 *
 * <p>순서 열쇠: 레벨에 열쇠(1..9)가 있으면 플레이어는 1, 2, 3 … 순서대로 밟아야 열쇠를 얻는다.
 * 순서가 아닌 열쇠 칸은 지나갈 수만 있고 얻어지지 않는다. 모든 열쇠를 얻기 전에는 목표(GOAL) 칸이 잠겨
 * 있어 플레이어가 들어가지 못하고(LOCKED_GOAL) 상자도 밀어 넣을 수 없다(PUSH_LOCKED_GOAL).
 * 마지막 열쇠를 얻는 순간 모든 목표가 활성화된다.
 *
 * <p>입력 하나가 성공하는지·막히는지와 그 이유는 {@link #resolve}가 한 곳에서 계산한다.
 * {@link #step}과 {@link #whyBlocked}는 같은 결과의 서로 다른 면이라 규칙이 늘어도 어긋나지 않는다.
 */
public final class Engine {
    private Engine() {
    }

    /** resolve의 결과: 성공이면 result, 막혔으면 reason (둘 중 하나만 있다). */
    private record Resolution(StepResult result, BlockReason reason) {
    }

    /**
     * 입력 1회를 처리한다. 플레이어는 6방향(x, y, z)으로 움직이고 블록도 같은 6방향으로 밀린다.
     *
     * @return 상태가 변했으면 결과, 막혀서 변화가 없으면 null (이동 횟수에 포함하지 않는다. FR-1.4)
     */
    public static StepResult step(Level lv, GameState s, Direction dir) {
        return resolve(lv, s, dir).result();
    }

    /**
     * 입력이 막힌 이유를 돌려준다. {@link #step}이 null일 때만 값이 있고, 아니면 null이다.
     * UI가 막은 칸을 강조하는 데 쓴다. 상태나 규칙에는 영향이 없다.
     */
    public static BlockReason whyBlocked(Level lv, GameState s, Direction dir) {
        return resolve(lv, s, dir).reason();
    }

    private static Resolution resolve(Level lv, GameState s, Direction dir) {
        int from = s.player;
        int to = lv.neighbor(from, dir);
        if (to < 0) {
            return blocked(BlockReason.Kind.EDGE, lv.x(from) + dir.dx, lv.y(from) + dir.dy, lv.z(from) + dir.dz);
        }
        if (!walkable(lv, s, to)) {
            return blocked(lv.tile(to) == TileType.WALL ? BlockReason.Kind.WALL : BlockReason.Kind.HOLE, lv, to);
        }
        // 0) 잠긴 목표: 열쇠를 다 모으기 전에는 목표 칸에 들어갈 수 없다(상자를 밀어 넣는 경우도 아래에서 막는다).
        if (lv.tile(to) == TileType.GOAL && !s.goalsUnlocked(lv)) {
            return blocked(BlockReason.Kind.LOCKED_GOAL, lv, to);
        }

        GameState cur = s;
        int blockFrom = -1, blockTo = -1, blockVia = -1;
        boolean vanished = false;

        // 1) 블록 밀기: 한 번에 1개만, 뒤가 막히면 불가 (FR-1.3)
        if (s.hasBlock(to)) {
            int behind = lv.neighbor(to, dir);
            if (behind < 0) {
                return blocked(BlockReason.Kind.PUSH_EDGE, lv.x(to) + dir.dx, lv.y(to) + dir.dy, lv.z(to) + dir.dz);
            }
            if (s.hasBlock(behind)) return blocked(BlockReason.Kind.PUSH_BOX, lv, behind);
            TileType t = lv.tile(behind);
            if (t == TileType.WALL) return blocked(BlockReason.Kind.PUSH_WALL, lv, behind);
            if (t == TileType.GOAL && !s.goalsUnlocked(lv)) {
                return blocked(BlockReason.Kind.PUSH_LOCKED_GOAL, lv, behind);
            }

            blockFrom = to;
            if (t == TileType.PORTAL) {
                // 2) 상자가 포탈에 들어가면 짝 포탈 칸으로 전송된다. 짝 칸이 막혔거나, 상자가 방금 떠난 칸으로
                //    되돌아오는 경우(플레이어가 그 칸에 들어가므로)는 성립하지 않는다.
                int exit = lv.portalPartner(behind);
                if (exit < 0 || exit == to || s.hasBlock(exit)) {
                    return blocked(BlockReason.Kind.PUSH_PORTAL, lv, exit < 0 ? behind : exit);
                }
                if (lv.tile(exit) == TileType.GOAL && !s.goalsUnlocked(lv)) {
                    return blocked(BlockReason.Kind.PUSH_LOCKED_GOAL, lv, exit);
                }
                blockVia = behind;
                blockTo = exit;
                cur = s.withPush(from, to, exit);
            } else if (t == TileType.HOLE && s.isHole(behind)) {
                vanished = true;
                blockTo = behind;
                cur = s.withFill(from, to, behind);
            } else {
                blockTo = behind;
                cur = s.withPush(from, to, behind);
            }
        }

        // 3) 플레이어 이동. 포탈 칸에 들어가면 짝 포탈 칸으로 전송된다(도착한 칸에서는 다시 전송하지 않는다).
        int dest = to, via = -1;
        if (lv.tile(to) == TileType.PORTAL) {
            int exit = lv.portalPartner(to);
            if (exit < 0 || (exit == from && blockFrom < 0)) {
                return blocked(BlockReason.Kind.PORTAL, lv, exit < 0 ? to : exit);
            }
            // 도착하는 칸이 잠긴 목표면 나올 수 없다(짝 칸에 상자가 있어 밀어내는 경우도 마찬가지다).
            if (lv.tile(exit) == TileType.GOAL && !s.goalsUnlocked(lv)) {
                return blocked(BlockReason.Kind.LOCKED_GOAL, lv, exit);
            }
            // 짝 칸에 상자가 있으면 플레이어가 나오면서 바라보는 방향으로 그 상자를 민다.
            // (이미 상자를 밀고 온 턴이거나, 상자 뒤가 막혔거나, 뒤가 또 포탈이면 막힌다)
            if (cur.hasBlock(exit)) {
                int behind = lv.neighbor(exit, dir);
                if (blockFrom >= 0 || behind < 0 || cur.hasBlock(behind)
                        || lv.tile(behind) == TileType.WALL || lv.tile(behind) == TileType.PORTAL) {
                    return blocked(BlockReason.Kind.PORTAL, lv, behind < 0 ? exit : behind);
                }
                if (lv.tile(behind) == TileType.GOAL && !s.goalsUnlocked(lv)) {
                    return blocked(BlockReason.Kind.PUSH_LOCKED_GOAL, lv, behind);
                }
                blockFrom = exit;
                blockTo = behind;
                if (lv.tile(behind) == TileType.HOLE && cur.isHole(behind)) {
                    vanished = true;
                    cur = cur.withFill(from, exit, behind);
                } else {
                    cur = cur.withPush(from, exit, behind);
                }
            }
            via = to;
            dest = exit;
        }

        // 4) 순서 열쇠 수집: 플레이어가 최종 도착한 칸의 열쇠가 다음 번호일 때만 얻어진다.
        int collected = 0;
        int order = lv.keyOrder(dest);
        if (order > 0 && order == s.keyProgress() + 1 && !cur.hasBlock(dest)) {
            collected = order;
        }
        GameState next = cur.withPlayer(dest);
        if (collected > 0) next = next.withKeyProgress(collected);
        boolean unlockedNow = !s.goalsUnlocked(lv) && next.goalsUnlocked(lv);
        return new Resolution(new StepResult(next, dir, from, dest, blockFrom, blockTo, vanished,
                isCleared(lv, next), via, blockVia, collected, unlockedNow), null);
    }

    private static Resolution blocked(BlockReason.Kind kind, Level lv, int cell) {
        return blocked(kind, lv.x(cell), lv.y(cell), lv.z(cell));
    }

    private static Resolution blocked(BlockReason.Kind kind, int x, int y, int z) {
        return new Resolution(null, new BlockReason(kind, x, y, z));
    }

    /** 플레이어가 들어갈 수 있는 칸인가(블록 점유와 잠긴 목표는 여기서 따지지 않는다). */
    private static boolean walkable(Level lv, GameState s, int idx) {
        return switch (lv.tile(idx)) {
            // ICE: 플레이어는 평범하게 걷는다(빙판 미끄러짐은 상자에만 적용하며 후속 PR에서 구현한다).
            case EMPTY, GOAL, ICE, PORTAL -> true;
            case HOLE -> !s.isHole(idx); // 메워진 구멍은 평범한 빈 칸
            case WALL -> false;
        };
    }

    /** 모든 목표 지점에 블록이 놓였는가 (FR-1.5). 잠긴 목표(열쇠를 다 모으기 전)는 클리어가 아니다. */
    public static boolean isCleared(Level lv, GameState s) {
        if (lv.goals.length == 0) return false;
        if (!s.goalsUnlocked(lv)) return false;
        for (int g : lv.goals) {
            if (!s.hasBlock(g)) return false;
        }
        return true;
    }
}
