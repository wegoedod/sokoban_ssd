package sokoban.core;

/**
 * 유효한 입력 1회의 결과.
 *
 * @param state              처리 후 상태
 * @param dir                입력 방향
 * @param playerFrom         플레이어 이전 위치
 * @param playerTo           플레이어 새 위치 (포탈을 탔다면 도착한 짝 포탈 칸)
 * @param blockFrom          민 블록의 이전 위치(안 밀었으면 -1)
 * @param blockTo            민 블록의 새 위치(안 밀었으면 -1, 포탈을 탔다면 도착한 짝 포탈 칸)
 * @param blockVanished      블록이 구멍에 들어가 소멸했는가
 * @param cleared            이 턴을 끝낸 뒤 클리어 조건을 만족하는가
 * @param playerVia          플레이어가 들어간 포탈 칸(포탈을 안 탔으면 -1). 전송 연출에 쓴다
 * @param blockVia           블록이 들어간 포탈 칸(포탈을 안 탔으면 -1)
 * @param collectedKeyOrder  이 턴에 얻은 순서 열쇠 번호(얻지 못했으면 0). 연출에 쓴다
 * @param goalsJustUnlocked  이 턴에 마지막 열쇠를 얻어 목표가 활성화되었는가
 */
public record StepResult(GameState state, Direction dir, int playerFrom, int playerTo,
                         int blockFrom, int blockTo, boolean blockVanished, boolean cleared,
                         int playerVia, int blockVia, int collectedKeyOrder, boolean goalsJustUnlocked) {

    /** 순서 열쇠 정보 없이 만드는 편의 생성자. */
    public StepResult(GameState state, Direction dir, int playerFrom, int playerTo,
                      int blockFrom, int blockTo, boolean blockVanished, boolean cleared) {
        this(state, dir, playerFrom, playerTo, blockFrom, blockTo, blockVanished, cleared, -1, -1, 0, false);
    }

    /** 포탈 정보만 주는 편의 생성자. */
    public StepResult(GameState state, Direction dir, int playerFrom, int playerTo,
                      int blockFrom, int blockTo, boolean blockVanished, boolean cleared,
                      int playerVia, int blockVia) {
        this(state, dir, playerFrom, playerTo, blockFrom, blockTo, blockVanished, cleared,
                playerVia, blockVia, 0, false);
    }

    public boolean pushed() {
        return blockFrom >= 0;
    }

    /** 플레이어가 포탈로 전송됐는가. */
    public boolean playerTeleported() {
        return playerVia >= 0;
    }

    /** 밀린 블록이 포탈로 전송됐는가. */
    public boolean blockTeleported() {
        return blockVia >= 0;
    }

    /** 이 턴에 순서 열쇠를 얻었는가. */
    public boolean collectedKey() {
        return collectedKeyOrder > 0;
    }
}
