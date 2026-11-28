package sokoban.core;

/**
 * 규칙 엔진. Swing에 의존하지 않으며 상태를 바꾸지 않는 순수 함수다(NFR-1, NFR-2).
 *
 * <p>플레이어와 상자는 x, y, z 6방향으로 움직인다(중력 없음).
 * 턴 순서: 이동·밀기·포탈 → 진입 스위치 반전 → 철 상자 흡인 → 클리어 판정.
 *
 * <p>순서 목표: 목표 칸에 {@code 1}~{@code 9} 번호가 있으면 그 목표는 순서대로 채워야 한다. 1번부터 연속으로
 * 상자가 올라가 있는 수를 k라 하면 활성 번호는 k+1뿐이고(1번이 비어 있으면 1번만 열려 있다), 그보다 뒤 번호의
 * 목표는 잠겨 있어 플레이어가 들어가지 못하며(LOCKED_GOAL) 상자도 밀어 넣을 수 없다(PUSH_LOCKED_GOAL,
 * 포탈 출구에서 미는 경우 포함). 1번 목표에 상자를 넣는 순간 2번이 열리고, 2번을 채우면 3번이 열린다.
 * 잠금은 {@link GameState}의 상자 배치에서 계산되므로 되돌리기·다시 하기·리셋·솔버가 함께 따라간다.
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

    public static boolean isMagnetHeld(Level lv, GameState s, int cell) {
        return MagneticField.attached(lv, s, cell);
    }

    private static Resolution resolve(Level lv, GameState s, Direction dir) {
        int from = s.player;
        int to = lv.neighbor(from, dir);
        if (to < 0) {
            return blocked(BlockReason.Kind.EDGE, lv.x(from) + dir.dx, lv.y(from) + dir.dy, lv.z(from) + dir.dz);
        }
        if (!walkable(lv, s, to)) {
            return blocked(MagneticField.solid(lv.tile(to)) ? BlockReason.Kind.WALL : BlockReason.Kind.HOLE, lv, to);
        }
        // 0) 잠긴 순서 목표: 아직 열리지 않은 목표 칸에는 들어갈 수 없다(상자를 밀어 넣는 경우도 아래에서 막는다).
        if (!s.goalUnlocked(lv, to)) {
            return blocked(BlockReason.Kind.LOCKED_GOAL, lv, to);
        }

        GameState cur = s;
        int blockFrom = -1, blockTo = -1, blockVia = -1;
        boolean vanished = false;

        // 1) 블록 밀기: 한 번에 1개만, 뒤가 막히면 불가 (FR-1.3)
        if (s.hasBlock(to)) {
            if (isMagnetHeld(lv, s, to)) return blocked(BlockReason.Kind.MAGNET_HELD, lv, to);
            int behind = lv.neighbor(to, dir);
            if (behind < 0) {
                return blocked(BlockReason.Kind.PUSH_EDGE, lv.x(to) + dir.dx, lv.y(to) + dir.dy, lv.z(to) + dir.dz);
            }
            if (s.hasBlock(behind)) return blocked(BlockReason.Kind.PUSH_BOX, lv, behind);
            TileType t = lv.tile(behind);
            if (MagneticField.solid(t)) return blocked(BlockReason.Kind.PUSH_WALL, lv, behind);
            if (!s.goalUnlocked(lv, behind)) {
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
                if (!s.goalUnlocked(lv, exit)) {
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
            if (!s.goalUnlocked(lv, exit)) {
                return blocked(BlockReason.Kind.LOCKED_GOAL, lv, exit);
            }
            // 짝 칸에 상자가 있으면 플레이어가 나오면서 바라보는 방향으로 그 상자를 민다.
            // (이미 상자를 밀고 온 턴이거나, 상자 뒤가 막혔거나, 뒤가 또 포탈이면 막힌다)
            if (cur.hasBlock(exit)) {
                if (isMagnetHeld(lv, cur, exit)) return blocked(BlockReason.Kind.MAGNET_HELD, lv, exit);
                int behind = lv.neighbor(exit, dir);
                if (blockFrom >= 0 || behind < 0 || cur.hasBlock(behind)
                        || MagneticField.solid(lv.tile(behind)) || lv.tile(behind) == TileType.PORTAL) {
                    return blocked(BlockReason.Kind.PORTAL, lv, behind < 0 ? exit : behind);
                }
                if (!s.goalUnlocked(lv, behind)) {
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

        // 4) 플레이어 이동을 확정하고, 이번 수로 새로 열린 순서 목표가 있는지 본다.
        //    상자를 k번 목표에 넣으면 k+1번 목표가 열린다(잠금은 상태에서 계산되므로 따로 저장하지 않는다).
        GameState next = cur.withPlayer(dest);
        // Only a fresh player entry toggles power. Boxes, standing still, and failed moves do not.
        if (dest != from) {
            if (lv.tile(dest) == TileType.MAGNET_SWITCH) next = next.withMagnet(!s.magnetOn);
        }
        StepResult manual = new StepResult(next, dir, from, dest, blockFrom, blockTo, vanished,
                false, via, blockVia, 0);
        MagneticField.Settled settled = MagneticField.settle(lv, next);
        if (settled == null) return blocked(BlockReason.Kind.FORCE_LOOP, lv, dest);
        next = settled.state();
        // Preview must show the pushed box's final destination, after all automatic motion.
        int finalBlockTo = blockTo;
        boolean finalVanished = vanished;
        for (StepResult phase : settled.phases()) {
            if (!finalVanished && finalBlockTo >= 0 && phase.blockFrom() == finalBlockTo) {
                finalBlockTo = phase.blockTo(); finalVanished = phase.blockVanished();
            }
        }
        java.util.List<StepResult> phases = java.util.List.of();
        if (!settled.phases().isEmpty()) {
            var all = new java.util.ArrayList<StepResult>();
            all.add(manual); all.addAll(settled.phases()); phases = java.util.List.copyOf(all);
        }
        int before = s.unlockedGoalOrder(lv);
        int after = next.unlockedGoalOrder(lv);
        int opened = after > before && after <= lv.sequenceGoalCount ? after : 0;
        return new Resolution(new StepResult(next, dir, from, dest, blockFrom, finalBlockTo, finalVanished,
                isCleared(lv, next), via, blockVia, opened, phases), null);
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
            case EMPTY, GOAL, ICE, PORTAL, MAGNET_SWITCH -> true;
            case HOLE -> !s.isHole(idx); // 메워진 구멍은 평범한 빈 칸
            case WALL, MAGNET -> false;
        };
    }

    /** 모든 목표(일반 목표 + 순서 목표)에 블록이 놓였는가 (FR-1.5). */
    public static boolean isCleared(Level lv, GameState s) {
        if (lv.goals.length == 0) return false;
        for (int g : lv.goals) {
            if (!s.hasBlock(g)) return false;
        }
        return true;
    }
}
