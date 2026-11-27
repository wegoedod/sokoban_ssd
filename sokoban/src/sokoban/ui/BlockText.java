package sokoban.ui;

import sokoban.core.BlockReason;
import sokoban.core.GameState;
import sokoban.core.Level;

/** 막힌 입력의 한국어 안내 문구. 플레이 화면과 마우스 미리보기가 같은 문구를 쓴다. */
final class BlockText {
    private BlockText() {
    }

    static String text(Level level, GameState state, BlockReason reason) {
        int left = Math.max(0, level.keyCount - state.keyProgress());
        return switch (reason.kind()) {
            case WALL -> "벽이 막고 있습니다";
            case EDGE -> "격자 밖으로는 나갈 수 없습니다";
            case HOLE -> "구멍은 지나갈 수 없습니다 · 상자로 메워야 합니다";
            case PUSH_WALL, PUSH_EDGE, PUSH_BOX -> "상자를 밀 수 없습니다 · 상자 뒤가 막혀 있습니다";
            case PORTAL, PUSH_PORTAL -> "포탈 짝 칸이 막혀 있습니다";
            case LOCKED_GOAL -> "목표가 잠겨 있습니다 · 순서 열쇠 " + left + "개를 1번부터 순서대로 모아야 열립니다";
            case PUSH_LOCKED_GOAL -> "목표가 잠겨 상자를 넣을 수 없습니다 · 순서 열쇠 " + left + "개를 순서대로 모으세요";
        };
    }
}
