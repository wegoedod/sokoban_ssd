package sokoban.core;

import java.util.Arrays;

/**
 * 불변 게임 상태: 플레이어 위치, 블록 위치 집합, 아직 메워지지 않은 구멍 집합, 먹은 순서 열쇠 수.
 * 위치는 {@link Level#idx(int, int, int)} 인덱스. 되돌리기와 솔버가 공통으로 쓴다(NFR-3).
 *
 * <p>순서 열쇠 진행도({@link #keyProgress()})가 상태의 일부라서 되돌리기(Z)와 솔버가 열쇠까지 함께 되돌린다.
 * 어느 열쇠를 먹었는지는 {@code keyProgress} 하나로 정해진다: 열쇠 번호는 1부터 빠짐없이 이어지고
 * 다음에 먹을 번호는 항상 {@code keyProgress + 1}이기 때문이다.
 */
public final class GameState {
    public final int player;
    private final int[] blocks; // 오름차순
    private final int[] holes;  // 오름차순, 남은 구멍
    private final int keyProgress; // 먹은 순서 열쇠 수 (0..레벨 열쇠 수)
    private final int hash;

    private GameState(int player, int[] sortedBlocks, int[] sortedHoles, int keyProgress) {
        this.player = player;
        this.blocks = sortedBlocks;
        this.holes = sortedHoles;
        this.keyProgress = keyProgress;
        this.hash = 31 * (31 * (31 * player + Arrays.hashCode(sortedBlocks))
                + Arrays.hashCode(sortedHoles)) + keyProgress;
    }

    public static GameState of(int player, int[] blocks, int[] holes) {
        return of(player, blocks, holes, 0);
    }

    /** 순서 열쇠 진행도를 함께 주는 상태 생성. 되돌리기·솔버·테스트가 중간 상태를 만들 때 쓴다. */
    public static GameState of(int player, int[] blocks, int[] holes, int keyProgress) {
        int[] b = blocks.clone();
        int[] h = holes.clone();
        Arrays.sort(b);
        Arrays.sort(h);
        return new GameState(player, b, h, Math.max(0, keyProgress));
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

    /** 지금까지 먹은 순서 열쇠 수. 다음에 먹을 번호는 {@code keyProgress + 1}이다. */
    public int keyProgress() {
        return keyProgress;
    }

    /** 이 상태에서 목표(GOAL) 칸이 활성인가. 열쇠가 없는 레벨은 항상 활성이다. */
    public boolean goalsUnlocked(Level lv) {
        return lv.keyCount == 0 || keyProgress >= lv.keyCount;
    }

    public GameState withPlayer(int newPlayer) {
        return new GameState(newPlayer, blocks, holes, keyProgress);
    }

    /** 순서 열쇠 진행도만 바꾼 상태. */
    public GameState withKeyProgress(int progress) {
        return new GameState(player, blocks, holes, Math.max(0, progress));
    }

    /** 플레이어를 옮기면서 블록 {@code from}을 {@code to}로 옮긴다. */
    public GameState withPush(int newPlayer, int from, int to) {
        int[] nb = blocks.clone();
        int pos = Arrays.binarySearch(nb, from);
        nb[pos] = to;
        Arrays.sort(nb);
        return new GameState(newPlayer, nb, holes, keyProgress);
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
        return new GameState(newPlayer, nb, nh, keyProgress);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GameState other)) return false;
        return hash == other.hash && player == other.player && keyProgress == other.keyProgress
                && Arrays.equals(blocks, other.blocks) && Arrays.equals(holes, other.holes);
    }

    @Override
    public int hashCode() {
        return hash;
    }

    @Override
    public String toString() {
        return "GameState{player=" + player + ", blocks=" + Arrays.toString(blocks)
                + ", holes=" + Arrays.toString(holes) + ", keys=" + keyProgress + "}";
    }
}
