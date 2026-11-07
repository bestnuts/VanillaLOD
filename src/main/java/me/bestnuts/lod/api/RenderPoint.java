package me.bestnuts.lod.api;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3d;

/**
 * An origin point and where it is drawn: shrunk towards the camera by scale, so its screen position and size stay the
 * same while its depth follows the LOD terrain.
 */
public record RenderPoint(Vector3d origin, Vector3d render, double scale) {

    public Vector3d toRenderWorld(Vector3d originWorld) {
        return new Vector3d(originWorld).sub(origin).mul(scale).add(render);
    }

    public Matrix4f toRenderLocal(Vector3d renderCarrier, Matrix4fc originLocal) {
        Vector3d offset = new Vector3d(render).sub(renderCarrier);
        return new Matrix4f()
                .translation((float) offset.x, (float) offset.y, (float) offset.z)
                .scale((float) scale)
                .mul(originLocal);
    }
}
