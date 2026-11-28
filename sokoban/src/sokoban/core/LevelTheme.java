package sokoban.core;

import java.util.Locale;

/**
 * 레벨의 무대(테마). 레벨 선택 지도에서 목적지 모양과 플레이 화면 배경·벽 색을 정한다.
 * 규칙에는 영향을 주지 않는다.
 *
 * <ul>
 *   <li>{@link #PLANET} 행성: 기본 밀기와 구멍(크레이터) 레벨.</li>
 *   <li>{@link #WORMHOLE} 웜홀: 포탈 레벨.</li>
 *   <li>{@link #STATION} 우주정거장: 자기장 장치·스위치·순서 목표(잠긴 문) 레벨.</li>
 * </ul>
 *
 * 레벨 파일의 {@code theme: planet|wormhole|station}으로 정하고, 없으면 쓰인 기믹으로 고른다({@link #infer}).
 */
public enum LevelTheme {
    PLANET("planet", "행성"),
    WORMHOLE("wormhole", "웜홀"),
    STATION("station", "우주정거장");

    /** 레벨 파일에 쓰는 이름. */
    public final String id;
    /** 화면에 보이는 이름. */
    public final String label;

    LevelTheme(String id, String label) {
        this.id = id;
        this.label = label;
    }

    /** 레벨 파일 값 → 테마. 알 수 없는 값이면 null. */
    public static LevelTheme parse(String value) {
        String v = value.trim().toLowerCase(Locale.ROOT);
        for (LevelTheme t : values()) if (t.id.equals(v)) return t;
        return null;
    }

    /**
     * 기믹으로 고르는 기본 테마. 정거장 장치(자기장·스위치·순서 목표)가 하나라도 있으면 우주정거장,
     * 포탈만 있으면 웜홀, 아무것도 없으면 행성이다.
     */
    public static LevelTheme infer(boolean stationDevices, boolean portals) {
        if (stationDevices) return STATION;
        if (portals) return WORMHOLE;
        return PLANET;
    }
}
