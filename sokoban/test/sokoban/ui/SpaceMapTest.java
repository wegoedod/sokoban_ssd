package sokoban.ui;

import sokoban.core.LevelTheme;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 우주 지도(레벨 선택)의 좌표·로켓 이동 규칙과, 행성 그리기가 그래픽 상태를 망가뜨리지 않는지 확인한다. */
public final class SpaceMapTest {
    private SpaceMapTest() {
    }

    private static void check(boolean c, String msg) {
        if (!c) throw new AssertionError(msg);
    }

    /** 행성이 위·아래를 번갈아 지그재그로 놓이고, 가로 간격이 일정하다. */
    public static void zigzagLayout() {
        double amp = 60;
        for (int i = 0; i < 12; i++) {
            check(Math.abs(SpaceMap.nodeY(i, amp)) == amp, "진폭이 일정해야 한다: " + i);
            if (i > 0) {
                check(SpaceMap.nodeY(i, amp) == -SpaceMap.nodeY(i - 1, amp), "이웃한 행성은 위·아래가 번갈아야 한다: " + i);
                check(Math.abs(SpaceMap.nodeX(i) - SpaceMap.nodeX(i - 1) - SpaceMap.SPACING) < 1e-9, "가로 간격이 일정해야 한다");
            }
        }
        check(SpaceMap.nodeX(0) == 0, "레벨 1(지구)은 지도의 시작점이다");
    }

    /** 로켓은 목표까지 한 행성씩만 지나간다(칸을 건너뛰지 않고, 뒤로 가지 않고, 목표를 넘지 않는다). */
    public static void rocketMovesOneNodeAtATime() {
        SpaceMap m = new SpaceMap(10);
        m.placeAt(0);
        List<Integer> arrivals = new ArrayList<>();
        int last = m.atNode();
        double prevX = m.rocketX();
        for (int t = 0; t < 4000; t++) {
            m.step(16, 4);
            if (m.atNode() != last) {
                check(m.atNode() == last + 1, "한 번에 한 칸만 가야 한다: " + last + " -> " + m.atNode());
                arrivals.add(m.atNode());
                last = m.atNode();
            }
            check(m.rocketX() >= prevX - 1e-9, "목표를 향해 앞으로만 간다");
            check(m.rocketX() <= SpaceMap.nodeX(4) + 1e-9, "목표를 넘어가면 안 된다");
            prevX = m.rocketX();
        }
        check(arrivals.equals(List.of(1, 2, 3, 4)), "1,2,3,4번 행성을 차례로 지나야 한다: " + arrivals);
        check(m.isIdleAt(4) && !m.isHopping(), "도착하면 멈춘다");
    }

    /** 뛰는 중에 목표가 반대편으로 바뀌면 그 자리에서 되돌아 뛰고, 범위를 벗어나지 않는다. */
    public static void rocketReversesMidHop() {
        SpaceMap m = new SpaceMap(10);
        m.placeAt(0);
        for (int i = 0; i < 6; i++) m.step(16, 3);       // 0 -> 1 로 뛰는 중
        check(m.isHopping(), "뛰는 중이어야 한다");
        for (int t = 0; t < 3000; t++) {
            m.step(16, 0);                                // 목표를 출발지로
            check(m.rocketX() >= -1e-9, "지도 시작보다 왼쪽으로 가면 안 된다");
        }
        check(m.isIdleAt(0), "출발지로 돌아와 멈춘다");
    }

    /** 멀리 가야 하면 빠르게 뛴다(처음에 지구에서 먼 레벨로 날아가는 장면이 너무 길지 않다). */
    public static void farTravelIsFaster() {
        SpaceMap far = new SpaceMap(30);
        far.placeAt(0);
        int farTicks = 0;
        while (!far.isIdleAt(20) && farTicks < 100000) {
            far.step(10, 20);
            farTicks++;
        }
        SpaceMap near = new SpaceMap(30);
        near.placeAt(0);
        int nearTicks = 0;
        while (!near.isIdleAt(3) && nearTicks < 100000) {
            near.step(10, 3);
            nearTicks++;
        }
        double perNodeFar = farTicks * 10.0 / 20, perNodeNear = nearTicks * 10.0 / 3;
        check(perNodeFar < perNodeNear * 0.7, "먼 이동이 칸당 더 빨라야 한다: " + perNodeFar + " vs " + perNodeNear);
    }

    /** 화면 좌표(세계 좌표)로 행성을 찾는다. 가까운 행성이 우선이고 멀리 떨어진 곳은 -1. */
    public static void hitTest() {
        SpaceMap m = new SpaceMap(8);
        double amp = 50, r = 30;
        check(m.nodeAt(SpaceMap.nodeX(3), SpaceMap.nodeY(3, amp), amp, r) == 3, "행성 중심");
        check(m.nodeAt(SpaceMap.nodeX(3) + 10, SpaceMap.nodeY(3, amp) - 8, amp, r) == 3, "행성 근처");
        check(m.nodeAt(SpaceMap.nodeX(3), SpaceMap.nodeY(3, amp) + 80, amp, r) == -1, "행성에서 먼 곳(이웃은 위로 지그재그)");
        check(m.nodeAt(-500, 0, amp, r) == -1, "지도 밖");
        // 지그재그라 같은 세로 위치라도 가로가 다르면 다른 행성
        check(m.nodeAt(SpaceMap.nodeX(4), SpaceMap.nodeY(4, amp), amp, r) == 4, "다음 행성");
    }

    /** 카메라는 목표로 수렴하고, 프레임을 잘게 나눠도 같은 결과(프레임 속도에 무관)다. */
    public static void cameraConverges() {
        double a = 0, b = 0;
        for (int i = 0; i < 100; i++) a = SpaceMap.cameraStep(a, 500, 10);   // 10ms x 100 = 1000ms
        for (int i = 0; i < 20; i++) b = SpaceMap.cameraStep(b, 500, 50);    // 50ms x 20  = 1000ms
        check(Math.abs(a - b) < 0.5, "프레임 길이와 무관하게 같은 위치: " + a + " vs " + b);
        for (int i = 0; i < 300; i++) a = SpaceMap.cameraStep(a, 500, 16);
        check(Math.abs(a - 500) < 0.5, "충분히 지나면 목표에 닿는다: " + a);
    }

    /** 이름이 있는 행성은 서로 달라야 하고, 모자라면 "미지의 행성 N"을 만들어 레벨이 몇 개든 처리한다. */
    public static void planetNames() {
        Set<String> names = new HashSet<>();
        for (int i = 0; i < PlanetArt.namedPlanets(); i++) check(names.add(PlanetArt.planet(i).name()), "행성 이름 중복: " + i);
        check(PlanetArt.planet(0).name().equals("지구"), "레벨 1은 지구");
        check(PlanetArt.namedPlanets() >= 18, "기본 목록에는 이름 있는 행성이 18개 이상 있어야 한다");
        check(PlanetArt.planet(40).name().equals("미지의 행성 41"), "목록을 넘으면 자동 이름: " + PlanetArt.planet(40).name());
    }

    /**
     * 목적지는 테마별로 차례대로 정해진다: 첫 행성 레벨은 지구, 첫 웜홀 레벨은 웜홀, 첫 정거장 레벨은 안드로메다 정거장.
     * 출고 캠페인에서는 이름이 모두 다르고, 정거장 레벨은 정거장 그림, 포탈 레벨은 웜홀(또는 블랙홀) 그림이다.
     */
    public static void themedDestinations() {
        List<sokoban.core.Level> levels = sokoban.level.LevelRepository.load(java.nio.file.Path.of("levels"),
                sokoban.config.GameConfig.defaults()).levels;
        List<PlanetArt.Planet> dest = PlanetArt.destinations(levels);
        check(dest.size() == levels.size(), "레벨마다 목적지");
        check(dest.get(0).name().equals("지구"), "첫 목적지는 지구");
        Set<String> names = new HashSet<>();
        boolean firstWormhole = true, firstStation = true;
        for (int i = 0; i < levels.size(); i++) {
            PlanetArt.Planet p = dest.get(i);
            check(names.add(p.name()), "목적지 이름 중복: " + p.name());
            switch (levels.get(i).theme) {
                case STATION -> {
                    check(p.kind() == PlanetArt.Kind.STATION, levels.get(i).id + " 는 정거장 그림");
                    if (firstStation) check(p.name().equals("안드로메다 정거장"), "첫 정거장: " + p.name());
                    firstStation = false;
                }
                case WORMHOLE -> {
                    check(p.kind() == PlanetArt.Kind.WORMHOLE || p.kind() == PlanetArt.Kind.BLACKHOLE,
                            levels.get(i).id + " 는 웜홀 그림");
                    if (firstWormhole) check(p.name().equals("웜홀"), "첫 웜홀: " + p.name());
                    firstWormhole = false;
                }
                case PLANET -> check(p.kind() != PlanetArt.Kind.STATION && p.kind() != PlanetArt.Kind.WORMHOLE,
                        levels.get(i).id + " 는 행성 그림");
            }
        }
        check(PlanetArt.destination(LevelTheme.STATION, 40).kind() == PlanetArt.Kind.STATION, "정거장 목록을 넘어도 정거장");
        check(PlanetArt.destination(LevelTheme.WORMHOLE, 40).kind() == PlanetArt.Kind.WORMHOLE, "웜홀 목록을 넘어도 웜홀");
    }

    /** 세 무대의 배경이 모두 그려지고(화면을 덮고), 그린 뒤 변환·클립이 그대로다. */
    public static void worldBackdrops() {
        for (LevelTheme theme : LevelTheme.values()) {
            BufferedImage img = new BufferedImage(640, 420, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            Rectangle clip = new Rectangle(0, 0, 640, 420);
            g.setClip(clip);
            AffineTransform identity = g.getTransform();
            WorldStyle style = WorldStyle.of(theme, PlanetArt.destination(theme, 0).base());
            style.paintBackdrop(g, 640, 420, 1234);
            check(g.getTransform().equals(identity), theme + " 배경 뒤 변환이 바뀌었다");
            check(clip.equals(g.getClipBounds()), theme + " 배경 뒤 클립이 바뀌었다");
            check((img.getRGB(5, 5) >>> 24) == 255 && (img.getRGB(635, 415) >>> 24) == 255, theme + " 배경이 화면을 덮는다");
            check(style.wall != null && style.grid != null, theme + " 팔레트");
            g.dispose();
        }
        // 블랙홀처럼 검은 목적지도 보이는 강조색을 쓴다
        WorldStyle dark = WorldStyle.of(LevelTheme.WORMHOLE, java.awt.Color.BLACK);
        check(dark.accent.getRed() + dark.accent.getGreen() + dark.accent.getBlue() > 60, "검은 목적지 강조색 대체");
    }

    /**
     * 행성을 그린 뒤에도 그래픽 상태(변환·클립)가 그대로여야 한다. 고리(토성형)를 그릴 때 클립을 회전된 좌표로
     * 복원하는 버그가 있어서, 고리 행성이 보이는 동안 화면 아래쪽이 대각선으로 잘렸다.
     */
    public static void drawingKeepsGraphicsState() {
        BufferedImage img = new BufferedImage(600, 400, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        Rectangle clip = new Rectangle(0, 0, 600, 400);
        g.setClip(clip);
        AffineTransform identity = g.getTransform();
        for (int i = 0; i < PlanetArt.namedPlanets(); i++) {
            for (boolean locked : new boolean[]{false, true}) {
                PlanetArt.drawPlanet(g, PlanetArt.planet(i), 300, 200, 30, 0.5, locked);
                check(g.getTransform().equals(identity), "변환이 바뀌었다: " + PlanetArt.planet(i).name());
                check(clip.equals(g.getClipBounds()) && g.getClip() instanceof Rectangle,
                        "클립이 바뀌었다: " + PlanetArt.planet(i).name() + " " + g.getClip());
            }
        }
        for (LevelTheme theme : LevelTheme.values()) {
            for (PlanetArt.Planet p : PlanetArt.named(theme)) {
                for (boolean locked : new boolean[]{false, true}) {
                    PlanetArt.drawPlanet(g, p, 300, 200, 30, 0.5, locked);
                    check(g.getTransform().equals(identity), "변환이 바뀌었다: " + p.name());
                    check(clip.equals(g.getClipBounds()) && g.getClip() instanceof Rectangle, "클립이 바뀌었다: " + p.name());
                }
            }
        }
        PlanetArt.drawRocket(g, 300, 200, 0.7, 14, 0.5);
        check(g.getTransform().equals(identity), "로켓을 그린 뒤 변환이 바뀌었다");
        g.dispose();
    }
}
