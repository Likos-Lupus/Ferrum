package top.likoslupus.ferrum.noise;

import net.minecraft.world.level.levelgen.synth.NormalNoise;

import java.util.IdentityHashMap;

/**
 * Maps live vanilla {@code NormalNoise} objects to native handles, keyed by identity.
 *
 * <p>Handles are created once per noise object and released when the owning world/resource
 * generation ends. The loader glue calls {@link #closeAll()} on world unload and datapack reload so
 * stale handles are never reused across generations (ADR-0004).
 */
public final class NoiseHandleCache {

    private static final IdentityHashMap<NormalNoise, Long> HANDLES = new IdentityHashMap<>();

    private NoiseHandleCache() {
    }

    /**
     * Returns the existing handle for a noise object, creating one on first use.
     *
     * @param normal the noise object
     *
     * @return the handle, or {@code 0} when native is unavailable
     */
    public static long acquire(NormalNoise normal) {
        synchronized (HANDLES) {
            var existing = HANDLES.get(normal);
            if (existing != null) {
                return existing;
            }
            var created = NativeNoise.create(normal);
            if (created != 0L) {
                HANDLES.put(normal, created);
            }
            return created;
        }
    }

    /**
     * Releases the handle for a noise object, if present.
     *
     * @param normal the noise object
     */
    public static void release(NormalNoise normal) {
        synchronized (HANDLES) {
            var handle = HANDLES.remove(normal);
            if (handle != null) {
                NativeNoise.destroy(handle);
            }
        }
    }

    /**
     * Releases every cached handle. Called on world unload and datapack reload.
     */
    public static void closeAll() {
        synchronized (HANDLES) {
            HANDLES.values().forEach(NativeNoise::destroy);
            HANDLES.clear();
        }
    }

    /**
     * Returns the number of cached handles (diagnostics and leak tests).
     *
     * @return the live handle count
     */
    public static int liveCount() {
        synchronized (HANDLES) {
            return HANDLES.size();
        }
    }

}
