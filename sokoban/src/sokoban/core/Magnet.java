package sokoban.core;

/** 장치 위치, 바라보는 6축 방향, 직선 유효 거리. 방향은 장치에서 바깥쪽이며 힘은 그 반대다. */
public record Magnet(int cell, Direction facing, int range) {
    public Magnet {
        if (cell < 0 || facing == null || range < 1 || range > 32)
            throw new IllegalArgumentException("자기장 방향과 범위(1~32)를 확인하세요.");
    }

    public static Direction direction(String value) {
        return switch (value.trim().toUpperCase(java.util.Locale.ROOT)) {
            case "+X" -> Direction.RIGHT; case "-X" -> Direction.LEFT;
            case "+Y" -> Direction.DOWN; case "-Y" -> Direction.UP;
            case "+Z" -> Direction.ABOVE; case "-Z" -> Direction.BELOW;
            default -> throw new IllegalArgumentException("방향은 +X, -X, +Y, -Y, +Z, -Z 중 하나여야 합니다.");
        };
    }

    public String directionText() {
        return switch (facing) {
            case RIGHT -> "+X"; case LEFT -> "-X"; case DOWN -> "+Y";
            case UP -> "-Y"; case ABOVE -> "+Z"; case BELOW -> "-Z";
        };
    }

    public Direction pullDirection() {
        return switch (facing) {
            case RIGHT -> Direction.LEFT; case LEFT -> Direction.RIGHT;
            case DOWN -> Direction.UP; case UP -> Direction.DOWN;
            case ABOVE -> Direction.BELOW; case BELOW -> Direction.ABOVE;
        };
    }
}
