package me.bestnuts.lod.encode;

/**
 * Which wall facings of a tile can face the player. Walls are single sided, so a wall facing away from the player
 * is only needed once the player crosses the tile's span on that axis.
 */
public record WallView(int xSide, int zSide) {

    public static final WallView ALL = new WallView(0, 0);

    public static WallView of(double minX, double minZ, double span, double playerX, double playerZ) {
        return new WallView(side(playerX, minX, span), side(playerZ, minZ, span));
    }

    public boolean shows(boolean zAxis, boolean facesPositive) {
        int side = zAxis ? zSide : xSide;
        return side == 0 || (side > 0) == facesPositive;
    }

    private static int side(double player, double min, double span) {
        if (player < min) {
            return -1;
        }
        return player > min + span ? 1 : 0;
    }
}
