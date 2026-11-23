package sokoban.ui;

/**
 * 레벨 선택 우주 지도의 좌표와 로켓 이동 규칙. Swing에 의존하지 않아 단위 테스트가 가능하다.
 *
 * <p>레벨 i는 i번째 행성이다. 행성은 가로로 일정한 간격으로 놓이고, 세로로는 위·아래를 번갈아 지그재그로
 * 놓인다. 로켓은 선택한 행성까지 <b>한 행성씩</b> 뛰어서 이동한다(한 번에 한 칸, 도착하면 잠깐 쉰다).
 * 멀리 가야 하면(처음 지구에서 출발해 먼 레벨로 갈 때 등) 뛰는 속도를 높여 너무 오래 걸리지 않게 한다.
 */
final class SpaceMap {
    /** 이웃한 행성 사이의 가로 간격(px). */
    static final double SPACING = 124;
    /** 한 칸을 뛰는 시간, 도착 뒤 쉬는 시간(ms). */
    static final double HOP_MS = 170, PAUSE_MS = 45;
    /** 목표까지 이 칸수보다 멀면 빠르게 뛴다. */
    static final int FAST_DISTANCE = 6;
    static final double FAST_HOP_MS = 80;

    private final int count;

    // 로켓 상태: atNode에 서 있거나, atNode -> toNode로 뛰는 중(hop 0..1)
    private int atNode;
    private int toNode = -1;
    private double hop;
    private double pauseLeft;

    SpaceMap(int count) {
        this.count = Math.max(1, count);
    }

    int count() {
        return count;
    }

    // ---------- 좌표 ----------

    /** 행성 i의 세계 좌표 x. 레벨 1(지구)이 0이다. */
    static double nodeX(int i) {
        return i * SPACING;
    }

    /** 행성 i의 세계 좌표 y(지도 중심선 기준). 짝수 번째는 위, 홀수 번째는 아래로 지그재그. */
    static double nodeY(int i, double amplitude) {
        return (i % 2 == 0 ? -amplitude : amplitude);
    }

    /** 로켓 위치(칸 단위 실수)에서의 세계 좌표 x. */
    double rocketX() {
        return nodeX(atNode) + (toNode < 0 ? 0 : (nodeX(toNode) - nodeX(atNode)) * ease(hop));
    }

    /** 로켓 위치에서의 세계 좌표 y (뛰는 중에는 두 행성 사이를 직선으로 지난다). */
    double rocketY(double amplitude) {
        return nodeY(atNode, amplitude)
                + (toNode < 0 ? 0 : (nodeY(toNode, amplitude) - nodeY(atNode, amplitude)) * ease(hop));
    }

    /** 뛰는 중 위로 살짝 떠오르는 높이 비율(0~1). 서 있으면 0. */
    double hopLift() {
        return toNode < 0 ? 0 : Math.sin(Math.PI * hop);
    }

    /** 로켓이 향하는 각도(라디안). 뛰는 중이면 진행 방향, 서 있으면 0. */
    double rocketAngle(double amplitude) {
        if (toNode < 0) return 0;
        return Math.atan2(nodeY(toNode, amplitude) - nodeY(atNode, amplitude), nodeX(toNode) - nodeX(atNode));
    }

    // ---------- 로켓 이동 ----------

    int atNode() {
        return atNode;
    }

    boolean isHopping() {
        return toNode >= 0;
    }

    /** 가만히 서서 쉬는 중이 아니라, 완전히 멈춰 선 상태인가. */
    boolean isIdleAt(int node) {
        return toNode < 0 && atNode == node && pauseLeft <= 0;
    }

    /** 애니메이션 없이 node 위에 놓는다. */
    void placeAt(int node) {
        atNode = clamp(node);
        toNode = -1;
        hop = 0;
        pauseLeft = 0;
    }

    /**
     * 시간을 dtMs만큼 진행한다. 로켓은 target을 향해 한 번에 한 칸씩만 이동한다.
     * 이동 중에 목표가 반대편으로 바뀌면 그 자리에서 되돌아 뛴다.
     */
    void step(double dtMs, int target) {
        target = clamp(target);
        double remaining = dtMs;
        // 한 프레임에 여러 칸을 건너뛰지 않도록 도착하면 남은 시간은 쉬는 시간으로 쓴다
        if (toNode >= 0) {
            int dirNow = Integer.signum(toNode - atNode);
            int dirWant = Integer.signum(target - atNode);
            if (target == atNode || (dirWant != 0 && dirWant != dirNow)) {
                // 되돌아 뛴다: 출발지와 도착지를 맞바꾸고 진행도를 뒤집는다
                int t = atNode;
                atNode = toNode;
                toNode = t;
                hop = 1 - hop;
            }
            hop += remaining / hopMs(target);
            if (hop >= 1) {
                atNode = toNode;
                toNode = -1;
                hop = 0;
                pauseLeft = Math.abs(target - atNode) > FAST_DISTANCE ? 0 : PAUSE_MS;
            }
            return;
        }
        if (pauseLeft > 0) {
            pauseLeft -= remaining;
            return;
        }
        if (atNode == target) return;
        toNode = atNode + Integer.signum(target - atNode);
        hop = Math.min(0.999, remaining / hopMs(target));
    }

    private double hopMs(int target) {
        return Math.abs(target - atNode) > FAST_DISTANCE ? FAST_HOP_MS : HOP_MS;
    }

    private int clamp(int node) {
        return Math.max(0, Math.min(count - 1, node));
    }

    private static double ease(double t) {
        double c = Math.max(0, Math.min(1, t));
        return c * c * (3 - 2 * c); // smoothstep: 출발과 도착에서 부드럽게
    }

    // ---------- 카메라, 선택 ----------

    /** 카메라 x를 목표 x로 부드럽게 따라가게 한다. 프레임 시간에 관계없이 같은 속도로 수렴한다. */
    static double cameraStep(double camX, double targetX, double dtMs) {
        double k = 1 - Math.exp(-dtMs / 140.0);
        return camX + (targetX - camX) * k;
    }

    /**
     * 세계 좌표 (wx, wy)에 있는 행성 번호. 반지름 안에 여러 개면 가장 가까운 것, 없으면 -1.
     *
     * @param amplitude 지그재그의 위아래 진폭(px)
     * @param radius    판정 반지름(px)
     */
    int nodeAt(double wx, double wy, double amplitude, double radius) {
        int best = -1;
        double bestD = radius * radius;
        for (int i = 0; i < count; i++) {
            double dx = wx - nodeX(i), dy = wy - nodeY(i, amplitude);
            double d = dx * dx + dy * dy;
            if (d <= bestD) {
                bestD = d;
                best = i;
            }
        }
        return best;
    }
}
