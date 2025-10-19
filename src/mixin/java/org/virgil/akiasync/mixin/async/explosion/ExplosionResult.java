package org.virgil.akiasync.mixin.async.explosion;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/**
 * Explosion calculation result
 * 
 * @author Virgil
 */
public class ExplosionResult {
    private final List<BlockPos> toDestroy;
    private final Map<UUID, Vec3> toHurt;
    private final boolean fire;
    
    public ExplosionResult(List<BlockPos> toDestroy, Map<UUID, Vec3> toHurt, boolean fire) {
        this.toDestroy = new ArrayList<>(toDestroy);
        this.toHurt = new HashMap<>(toHurt);
        this.fire = fire;
    }
    
    public List<BlockPos> getToDestroy() {
        return toDestroy;
    }
    
    public Map<UUID, Vec3> getToHurt() {
        return toHurt;
    }
    
    public boolean isFire() {
        return fire;
    }
}

