package sokoban.core;

import java.util.Arrays;

/**
 * 불변 게임 상태: 플레이어 위치, 블록 위치 집합, 아직 메워지지 않은 구멍 집합.
 * 위치는 {@link Level#idx(int, int, int)} 인덱스. 되돌리기와 솔버가 공통으로 쓴다(NFR-3).
 *
 * <p>순서 목표의 잠금은 상태에 따로 저장하지 않는다. <b>상자 배치에서 순수하게 계산</b>되기 때문이다:
 * 1번부터 연속으로 상자가 올라가 있는 순서 목표 수를 k라 하면 활성화된 최대 번호는 k+1이고,
 * 그보다 뒤 번호의 목표는 잠겨 있다({@link #unlockedGoalOrder}, {@link #goalUnlocked}).
 * 따라서 되돌리기(Z)·다시 하기(Y)·리셋·솔버가 잠금 상태까지 저절로 함께 복원한다.
 */
public final class GameState {
    public final int player;
    private final int[] blocks; // 오름차순
    private final int[] holes;  // 오름차순, 남은 구멍
    private final int hash;

    private GameState(int player, int[] sortedBlocks, int[] sortedHoles) {
        this.player = player;
        this.blocks = sortedBlocks;
        this.holes = sortedHoles;
        this.hash = 31 * (31 * (31 * player + Arrays.hashCode(sortedBlocks))
                + Arrays.hashCode(sortedHoles));
    }

    public static GameState of(int player, int[] blocks, int[] holes) {
        int[] b = blocks.clone();
        int[] h = holes.clone();
        Arrays.sort(b);
        Arrays.sort(h);
        return new GameState(player, b, h);
    }

    public int blockCount() {
        return blocks.length;
    }

    public int holeCount() {
        return holes.length;
    }

    public int block(int i) {
        return blocks[i];
    }

    public int hole(int i) {
        return holes[i];
    }

    public boolean hasBlock(int idx) {
        return Arrays.binarySearch(blocks, idx) >= 0;
    }

    public boolean isHole(int idx) {
        return Arrays.binarySearch(holes, idx) >= 0;
    }

    /** 1번부터 연속으로 상자가 올라가 있는 순서 목표 수(k). */
    public int filledSequenceGoals(Level lv) {
        int k = 0;
        while (k < lv.sequenceGoalCount && hasBlock(lv.sequenceGoalCell(k + 1))) k++;
        return k;
    }

    /**
     * 지금 활성인 최대 순서 번호(k+1). 이보다 뒤 번호의 순서 목표는 잠겨 있다.
     * 순서 목표가 없는 레벨은 1이다(일반 목표는 언제나 활성).
     */
    public int unlockedGoalOrder(Level lv) {
        return filledSequenceGoals(lv) + 1;
    }

    /** 그 칸이 지금 들어갈 수 있는 목표인가. 일반 목표(순서 0)와 목표가 아닌 칸은 항상 true. */
    public boolean goalUnlocked(Level lv, int idx) {
        int order = lv.goalOrder(idx);
        return order == 0 || order <= unlockedGoalOrder(lv);
    }

    public GameState withPlayer(int newPlayer) {
        return new GameState(newPlayer, blocks, holes);
    }

    /** 플레이어를 옮기면서 블록 {@code from}을 {@code to}로 옮긴다. */
    public GameState withPush(int newPlayer, int from, int to) {
        int[] nb = blocks.clone();
        int pos = Arrays.binarySearch(nb, from);
        nb[pos] = to;
        Arrays.sort(nb);
        return new GameState(newPlayer, nb, holes);
    }

    /** 플레이어를 옮기면서 블록 {@code from}을 구멍 {@code hole}에 밀어 넣는다(블록·구멍 모두 소멸). */
    public GameState withFill(int newPlayer, int from, int hole) {
        int[] nb = new int[blocks.length - 1];
        int k = 0;
        for (int b : blocks) {
            if (b != from) nb[k++] = b;
        }
        int[] nh = new int[holes.length - 1];
        k = 0;
        for (int h : holes) {
            if (h != hole) nh[k++] = h;
        }
        return new GameState(newPlayer, nb, nh);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GameState other)) return false;
        return hash == other.hash && player == other.player
                && Arrays.equals(blocks, other.blocks) && Arrays.equals(holes, other.holes);
    }

    @Override
    public int hashCode() {
        return hash;
    }

    @Override
    public String toString() {
        return "GameState{player=" + player + ", blocks=" + Arrays.toString(blocks)
                + ", holes=" + Arrays.toString(holes) + "}";
    }
}
