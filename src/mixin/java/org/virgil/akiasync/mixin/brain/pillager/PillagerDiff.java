package org.virgil.akiasync.mixin.brain.pillager;

import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.monster.AbstractIllager;

/**
 * Pillager family differential.
 * @author Virgil
 */
public final class PillagerDiff {
    
    private double chargeScore;
    private UUID attackTarget;
    private BlockPos raidTarget;
    private BlockPos patrolTarget;
    private int changeCount;
    
    public PillagerDiff() {}
    
    public void setChargeScore(double score) { this.chargeScore = score; changeCount++; }
    public void setAttackTarget(UUID id) { this.attackTarget = id; changeCount++; }
    public void setRaidTarget(BlockPos pos) { this.raidTarget = pos; changeCount++; }
    public void setPatrolTarget(BlockPos pos) { this.patrolTarget = pos; changeCount++; }
    
    public void applyTo(AbstractIllager illager, net.minecraft.server.level.ServerLevel level) {
        if (chargeScore > 0) {
            org.virgil.akiasync.mixin.util.REFLECTIONS.setField(
                illager, "crossbowChargedStartTime", 
                chargeScore > 0 ? level.getGameTime() : 0L
            );
        }
        
        if (attackTarget != null) {
            net.minecraft.world.entity.player.Player player = level.getPlayerByUUID(attackTarget);
            if (player != null && !player.isRemoved()) {
                org.virgil.akiasync.mixin.util.REFLECTIONS.setField(illager, "target", player);
            }
        }
        
        if (raidTarget != null) {
            org.virgil.akiasync.mixin.util.REFLECTIONS.setField(illager, "raidCenter", raidTarget);
        }
        
        if (patrolTarget != null) {
            org.virgil.akiasync.mixin.util.REFLECTIONS.setField(illager, "patrolTarget", patrolTarget);
        }
    }
    
    public boolean hasChanges() { return changeCount > 0; }
}

