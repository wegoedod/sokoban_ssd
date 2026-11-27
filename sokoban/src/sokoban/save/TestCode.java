package sokoban.save;

import sokoban.core.Level;

import java.io.IOException;
import java.util.List;

/**
 * 설정 창의 테스트 코드. 코드가 맞으면 출고된 모든 레벨을 최적 플레이 기록(★★★·1000점·최적 이동)으로
 * 클리어 처리해, 마지막 레벨까지 바로 열어 확인할 수 있게 한다 (시연·QA용).
 * 잘못된 코드는 기록을 하나도 건드리지 않는다.
 */
public final class TestCode {
    /** 테스트 코드(숫자 6자리). */
    public static final String CODE = "123456";
    public static final String DONE_MESSAGE = "테스트 코드 적용: 모든 레벨 완료 처리";
    public static final String WRONG_MESSAGE = "올바르지 않은 코드입니다";

    private TestCode() {
    }

    /** 적용 결과. ok=false면 아무 기록도 바꾸지 않았다. levels는 처리한 레벨 수. */
    public record Result(boolean ok, String message, int levels) {
    }

    /**
     * 코드를 확인하고, 맞으면 모든 레벨을 클리어 상태로 저장한다.
     *
     * @throws IOException 기록 파일을 쓰지 못했을 때 (메모리 기록은 이미 바뀐 상태다)
     */
    public static Result apply(String code, List<Level> levels, SaveStore save) throws IOException {
        if (!CODE.equals(code)) return new Result(false, WRONG_MESSAGE, 0);
        for (Level l : levels) save.markOptimal(l.id, l.optimalMoves);
        save.save();
        return new Result(true, DONE_MESSAGE, levels.size());
    }
}
