package sokoban.core;

import java.util.Map;

/**
 * 레벨 정의(불변). 3차원 격자 W×D×H + 메타데이터. z=0이 가장 아래 층이다.
 *
 * <p>순서 목표: {@code '1'}~{@code '9'} 칸은 {@link TileType#GOAL}이면서 {@link #goalOrder(int)}가 1~9인
 * <b>순서 목표</b>다. 번호가 없는 목표({@code '.'})는 순서 0인 일반 목표로 항상 활성이다. 어떤 목표가
 * 열려 있는지는 순수하게 {@link GameState}의 상자 배치로 계산되므로({@link GameState#unlockedGoalOrder}),
 * 되돌리기·다시 하기·리셋·솔버가 따로 기억할 상태가 없다.
 */
public final class Level {
    public final String id;
    public final String name;
    public final int w, d, h;
    private final TileType[] tiles;
    public final GameState initial;
    public final int[] goals;
    /** 솔버가 산출한 최적 이동 횟수. 미검증이면 -1. */
    public final int optimalMoves;
    /** 솔버 풀이 경로(U/R/D/L/T/B). 미검증이면 빈 문자열. */
    public final String solution;
    /** 별 기준 덮어쓰기. 없으면 null. */
    public final Double star3Ratio;
    public final Double star2Ratio;
    /** 사용된 기믹별 인스턴스 수. 기본 게임에는 기믹이 없어 비어 있다. */
    public final Map<String, Integer> gimmicks;
    public final String source;
    /** 상자 당기기 허용 여부(레벨 메타데이터 {@code pull}, 레벨 디자인 M-02). 기본은 false. */
    public final boolean pullAllowed;
    /** 포탈 칸 → 짝 칸. 포탈이 없으면 null, 포탈이 아닌 칸은 -1. */
    private final int[] portalPartner;
    /** 포탈 칸의 식별 문자('A', 'B'). 포탈이 없으면 null, 포탈이 아닌 칸은 0. */
    private final char[] portalChar;
    /** 셀 인덱스별 순서 목표 번호(1..9). 순서 목표가 아닌 칸은 0, 순서 목표가 없는 레벨은 null. */
    private final int[] goalOrder;
    /** 이 레벨의 순서 목표 수. 0이면 모든 목표가 처음부터 활성이다. */
    public final int sequenceGoalCount;
    /** 순서 번호 → 셀 인덱스(1..sequenceGoalCount). 순서 목표가 없으면 null. */
    private final int[] sequenceCell;
    public final java.util.List<Magnet> magnets;

    public Level(String id, String name, int w, int d, int h, TileType[] tiles, GameState initial,
                 int optimalMoves, String solution, Double star3Ratio, Double star2Ratio,
                 Map<String, Integer> gimmicks, String source) {
        this(id, name, w, d, h, tiles, initial, optimalMoves, solution, star3Ratio, star2Ratio,
                gimmicks, source, false, null, null);
    }

    /**
     * @param pullAllowed   상자 당기기 허용 여부
     * @param portalPartner 셀 인덱스별 짝 포탈 칸(없으면 -1). 포탈이 없으면 null 가능. 길이는 tiles와 같아야 한다.
     * @param portalChar    셀 인덱스별 포탈 식별 문자(없으면 0). portalPartner와 함께 주거나 함께 null.
     */
    public Level(String id, String name, int w, int d, int h, TileType[] tiles, GameState initial,
                 int optimalMoves, String solution, Double star3Ratio, Double star2Ratio,
                 Map<String, Integer> gimmicks, String source,
                 boolean pullAllowed, int[] portalPartner, char[] portalChar) {
        this(id, name, w, d, h, tiles, initial, optimalMoves, solution, star3Ratio, star2Ratio,
                gimmicks, source, pullAllowed, portalPartner, portalChar, null);
    }

    /**
     * @param goalOrder 셀 인덱스별 순서 목표 번호(1..9, 순서 목표가 아닌 칸은 0). 순서 목표가 없으면 null.
     *                  번호는 1부터 빠짐없이 이어져야 하며 중복될 수 없고, 그 칸은 {@link TileType#GOAL}이어야 한다.
     */
    public Level(String id, String name, int w, int d, int h, TileType[] tiles, GameState initial,
                 int optimalMoves, String solution, Double star3Ratio, Double star2Ratio,
                 Map<String, Integer> gimmicks, String source,
                 boolean pullAllowed, int[] portalPartner, char[] portalChar, int[] goalOrder) {
        this(id, name, w, d, h, tiles, initial, optimalMoves, solution, star3Ratio, star2Ratio,
                gimmicks, source, pullAllowed, portalPartner, portalChar, goalOrder, java.util.List.of());
    }

    public Level(String id, String name, int w, int d, int h, TileType[] tiles, GameState initial,
                 int optimalMoves, String solution, Double star3Ratio, Double star2Ratio,
                 Map<String, Integer> gimmicks, String source,
                 boolean pullAllowed, int[] portalPartner, char[] portalChar, int[] goalOrder,
                 java.util.List<Magnet> magnets) {
        if ((portalPartner == null) != (portalChar == null)) {
            throw new IllegalArgumentException("portalPartner와 portalChar는 함께 주거나 함께 null이어야 한다.");
        }
        if (portalPartner != null && (portalPartner.length != tiles.length || portalChar.length != tiles.length)) {
            throw new IllegalArgumentException("포탈 배열 길이가 셀 수와 다르다.");
        }
        if (goalOrder != null && goalOrder.length != tiles.length) {
            throw new IllegalArgumentException("순서 목표 배열 길이가 셀 수와 다르다.");
        }
        this.pullAllowed = pullAllowed;
        this.magnets = magnets.stream().sorted(java.util.Comparator.comparingInt(Magnet::cell)).toList();
        java.util.Set<Integer> devices = new java.util.HashSet<>();
        for (Magnet m : this.magnets) {
            if (m.cell() >= tiles.length || tiles[m.cell()] != TileType.MAGNET || !devices.add(m.cell()))
                throw new IllegalArgumentException("자기장 장치의 위치가 잘못되거나 중복되었습니다.");
        }
        this.portalPartner = portalPartner == null ? null : portalPartner.clone();
        this.portalChar = portalChar == null ? null : portalChar.clone();
        this.goalOrder = goalOrder == null ? null : goalOrder.clone();
        this.id = id;
        this.name = name;
        this.w = w;
        this.d = d;
        this.h = h;
        this.tiles = tiles.clone();
        this.initial = initial;
        this.optimalMoves = optimalMoves;
        this.solution = solution;
        this.star3Ratio = star3Ratio;
        this.star2Ratio = star2Ratio;
        this.source = source;
        int seq = 0;
        int[] cellOf = null;
        if (this.goalOrder != null) {
            boolean[] used = new boolean[10];
            for (int i = 0; i < this.goalOrder.length; i++) {
                int o = this.goalOrder[i];
                if (o == 0) continue;
                if (o < 1 || o > 9) throw new IllegalArgumentException("순서 목표 번호는 1~9여야 한다: " + o);
                if (used[o]) throw new IllegalArgumentException("순서 목표 " + o + "번이 두 번 나왔다.");
                if (this.tiles[i] != TileType.GOAL) throw new IllegalArgumentException("순서 목표 " + o + "번 칸이 GOAL이 아니다.");
                used[o] = true;
                seq++;
            }
            for (int o = 1; o <= seq; o++) {
                if (!used[o]) throw new IllegalArgumentException("순서 목표 번호는 1부터 " + seq + "까지 이어져야 한다 (없는 번호 " + o + ").");
            }
            if (seq > 0) {
                cellOf = new int[seq + 1];
                for (int i = 0; i < this.goalOrder.length; i++) {
                    if (this.goalOrder[i] > 0) cellOf[this.goalOrder[i]] = i;
                }
            }
        }
        this.sequenceGoalCount = seq;
        this.sequenceCell = cellOf;
        Map<String, Integer> g = new java.util.LinkedHashMap<>(gimmicks);
        if (seq > 0) g.put("sequence_goal", seq);
        this.gimmicks = Map.copyOf(g);
        int n = 0;
        for (TileType t : tiles) if (t == TileType.GOAL) n++;
        this.goals = new int[n];
        int k = 0;
        for (int i = 0; i < tiles.length; i++) if (tiles[i] == TileType.GOAL) goals[k++] = i;
    }

    public int idx(int x, int y, int z) {
        return (z * d + y) * w + x;
    }

    public int x(int idx) {
        return idx % w;
    }

    public int y(int idx) {
        return (idx / w) % d;
    }

    public int z(int idx) {
        return idx / (w * d);
    }

    public int cellCount() {
        return tiles.length;
    }

    public TileType tile(int idx) {
        return tiles[idx];
    }

    /** idx가 포탈이면 짝 포탈 칸, 아니면 -1. */
    public int portalPartner(int idx) {
        return portalPartner == null ? -1 : portalPartner[idx];
    }

    /** idx가 포탈이면 식별 문자('A', 'B'), 아니면 0. */
    public char portalId(int idx) {
        return portalChar == null ? 0 : portalChar[idx];
    }

    /** idx가 순서 목표면 그 번호(1..9). 일반 목표·목표 아님은 0. */
    public int goalOrder(int idx) {
        return goalOrder == null ? 0 : goalOrder[idx];
    }

    /** idx가 순서 목표인가(번호가 매겨진 목표). */
    public boolean isSequenceGoal(int idx) {
        return goalOrder != null && goalOrder[idx] > 0;
    }

    /** 이 레벨에 순서 목표가 있는가. 없으면 모든 목표가 처음부터 활성이다. */
    public boolean hasSequenceGoals() {
        return sequenceGoalCount > 0;
    }

    /** order번 순서 목표의 셀 인덱스(1..sequenceGoalCount). */
    public int sequenceGoalCell(int order) {
        if (sequenceCell == null || order < 1 || order > sequenceGoalCount) {
            throw new IllegalArgumentException("없는 순서 목표 번호: " + order);
        }
        return sequenceCell[order];
    }

    /** idx에서 dir 방향 이웃 칸. 격자 밖이면 -1 (격자 밖은 고체로 취급한다). */
    public int neighbor(int idx, Direction dir) {
        int x = x(idx) + dir.dx;
        int y = y(idx) + dir.dy;
        int z = z(idx) + dir.dz;
        if (x < 0 || y < 0 || z < 0 || x >= w || y >= d || z >= h) return -1;
        return idx(x, y, z);
    }

    public int blockCount() {
        return initial.blockCount();
    }

    public int holeCount() {
        return initial.holeCount();
    }

    /** 벽·구멍·목표·블록·플레이어 중 무언가 있는 층(z)의 수. */
    public int usedLayers() {
        boolean[] used = new boolean[h];
        for (int i = 0; i < tiles.length; i++) {
            if (tiles[i] != TileType.EMPTY) used[z(i)] = true;
        }
        for (int i = 0; i < initial.blockCount(); i++) used[z(initial.block(i))] = true;
        used[z(initial.player)] = true;
        int n = 0;
        for (boolean u : used) if (u) n++;
        return n;
    }

    public Level withSolution(int optimalMoves, String solution) {
        return new Level(id, name, w, d, h, tiles, initial, optimalMoves, solution,
                star3Ratio, star2Ratio, gimmicks, source, pullAllowed, portalPartner, portalChar, goalOrder, magnets);
    }

    public boolean hasMagnets() {
        if (!magnets.isEmpty() || initial.magnetOn) return true;
        for (TileType t : tiles) if (t == TileType.MAGNET_SWITCH) return true;
        return false;
    }
}
