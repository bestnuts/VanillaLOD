package me.bestnuts.lod.view;

import me.bestnuts.lod.api.LodProjection;
import me.bestnuts.lod.api.RenderPoint;
import me.bestnuts.lod.encode.LodBits;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.joml.Vector3d;

/**
 * Applies the depth compression of cell_lib.glsl's cellDepthPosition on the server, without the LOD depth push, so a
 * proxy lands just in front of the terrain at its spot.
 */
record ViewerProjection(World world, Vector3d camera, double renderDistance, int horizonSteps, Vector3d anchor) implements LodProjection {

    @Override
    public Vector3d camera() {
        return new Vector3d(camera);
    }

    @Override
    public Vector3d anchor() {
        return new Vector3d(anchor);
    }

    @Override
    public double horizon() {
        return (double) horizonSteps * LodBits.HORIZON_STEP;
    }

    @Override
    public boolean covers(Vector3d originWorld) {
        return camera.distance(originWorld) <= horizon();
    }

    @Override
    public Display.Brightness brightness() {
        int index = horizonSteps - 1;
        return new Display.Brightness(index % LodBits.PROXY_LIGHT_LEVELS, index / LodBits.PROXY_LIGHT_LEVELS);
    }

    @Override
    public RenderPoint project(Vector3d originWorld) {
        Vector3d ray = new Vector3d(originWorld).sub(camera);
        double length = ray.length();
        double scale = length > 0.0 ? compressed(length) / length : 1.0;
        return new RenderPoint(new Vector3d(originWorld), ray.mul(scale).add(camera), scale);
    }

    private double compressed(double length) {
        double shaderRenderDistance = Math.max(renderDistance, LodBits.MIN_DEPTH_RENDER_DISTANCE);
        double keep = shaderRenderDistance * LodBits.DEPTH_KEEP_SCALE;
        double limit = shaderRenderDistance * LodBits.DEPTH_LIMIT_SCALE;
        double horizon = horizon();
        if (length <= keep || horizon <= limit) {
            return length;
        }
        return Math.min(keep + (length - keep) * (limit - keep) / (horizon - keep), limit);
    }
}
