package me.bestnuts.lod.api;

import org.bukkit.World;
import org.bukkit.entity.Display;
import org.joml.Vector3d;

/**
 * Maps world points to where one viewer's client draws them among LOD terrain, captured at the time it was requested.
 * Matrices are in world axes; a proxy carrier must face yaw 0 and pitch 0.
 */
public interface LodProjection {

    World world();

    Vector3d camera();

    /**
     * The render distance in blocks the shaders compress depth against, the same as FogRenderDistanceEnd.
     */
    double renderDistance();

    /**
     * The distance in blocks where LOD and its fog end.
     */
    double horizon();

    boolean covers(Vector3d originWorld);

    /**
     * Where to stand a proxy carrier: inside the viewer's loaded chunks and display tracking range, and stable while the
     * viewer moves within a few chunks.
     */
    Vector3d anchor();

    /**
     * The brightness override every proxy display must use. It carries the horizon to the proxy fog shader, which lights
     * proxies as open sky in return.
     */
    Display.Brightness brightness();

    RenderPoint project(Vector3d originWorld);
}
