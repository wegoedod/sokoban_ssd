package sokoban.ui;

import sokoban.core.BlockReason;
import sokoban.core.GameState;
import sokoban.core.Level;

/** 막힌 입력의 한국어 안내 문구. 플레이 화면과 마우스 미리보기가 같은 문구를 쓴다. */
final class BlockText {
    private BlockText() {
    }

    static String text(Level level, GameState state, BlockReason reason) {
        return switch (reason.kind()) {
            case MAGNET_HELD -> "철 상자 고정됨";
            case FORCE_LOOP -> "";
            case WALL -> "벽이 막고 있습니다";
            case EDGE -> "격자 밖으로는 나갈 수 없습니다";
            case HOLE -> "구멍은 지나갈 수 없습니다 · 상자로 메워야 합니다";
            case PUSH_WALL, PUSH_EDGE, PUSH_BOX -> "상자를 밀 수 없습니다 · 상자 뒤가 막혀 있습니다";
            case PORTAL, PUSH_PORTAL -> "포탈 짝 칸이 막혀 있습니다";
            case LOCKED_GOAL -> lockedText(level, reason, "지나갈 수 없습니다");
            case PUSH_LOCKED_GOAL -> lockedText(level, reason, "상자를 넣을 수 없습니다");
        };
    }

    /** 잠긴 순서 목표 칸을 짚어 "먼저 N번 목표에 상자를 넣으세요"로 안내한다. */
    private static String lockedText(Level level, BlockReason reason, String what) {
        int order = orderAt(level, reason);
        if (order < 2) return "목표가 잠겨 있습니다 · 앞 번호 목표부터 상자를 넣어야 열립니다";
        return order + "번 목표는 아직 잠겨 " + what + " · 먼저 " + (order - 1) + "번 목표에 상자를 넣으세요";
    }

    /** 막은 칸이 순서 목표면 그 번호, 아니면 0. */
    private static int orderAt(Level level, BlockReason reason) {
        int x = reason.x(), y = reason.y(), z = reason.z();
        if (x < 0 || y < 0 || z < 0 || x >= level.w || y >= level.d || z >= level.h) return 0;
        return level.goalOrder(level.idx(x, y, z));
    }
}
