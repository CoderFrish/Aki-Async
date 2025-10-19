package org.virgil.akiasync.mixin.mixins.brain;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.virgil.akiasync.mixin.brain.core.AsyncBrainExecutor;
import org.virgil.akiasync.mixin.brain.piglin.PiglinCpuCalculator;
import org.virgil.akiasync.mixin.brain.piglin.PiglinDiff;
import org.virgil.akiasync.mixin.brain.piglin.PiglinSnapshot;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.piglin.Piglin;

/**
 * Piglin + PiglinBrute async AI optimization.
 * 
 * @author Virgil
 */
@SuppressWarnings("unused")
@Mixin(value = {Piglin.class, net.minecraft.world.entity.monster.piglin.PiglinBrute.class}, priority = 998)
public abstract class PiglinBrainMixin {
    
    @Unique private static volatile boolean cached_enabled;
    @Unique private static volatile long cached_timeoutMicros;
    @Unique private static volatile int cached_tickInterval;
    @Unique private static volatile int cached_lookDistance;
    @Unique private static volatile int cached_barterDistance;
    @Unique private static volatile boolean initialized = false;
    
    @Unique private PiglinSnapshot aki$snapshot;
    @Unique private long aki$nextAsyncTick = 0;
    
    @Unique private static int executionCount = 0;
    @Unique private static int successCount = 0;
    @Unique private static int timeoutCount = 0;
    
    @Inject(method = "customServerAiStep", at = @At("HEAD"))
    private void aki$takeSnapshot(CallbackInfo ci) {
        if (!initialized) { aki$initPiglinAsync(); }
        if (!cached_enabled) return;
        
        net.minecraft.world.entity.monster.piglin.AbstractPiglin abstractPiglin = 
            (net.minecraft.world.entity.monster.piglin.AbstractPiglin) (Object) this;
        
        ServerLevel level = (ServerLevel) abstractPiglin.level();
        if (level == null) return;
        
        Piglin piglin = abstractPiglin instanceof Piglin ? (Piglin) abstractPiglin : null;
        net.minecraft.world.entity.monster.piglin.PiglinBrute brute = 
            abstractPiglin instanceof net.minecraft.world.entity.monster.piglin.PiglinBrute ? 
            (net.minecraft.world.entity.monster.piglin.PiglinBrute) abstractPiglin : null;
        
        if (level.getGameTime() < this.aki$nextAsyncTick) {
            return;
        }
        this.aki$nextAsyncTick = level.getGameTime() + cached_tickInterval;
        
        try {
            if (piglin != null) {
                this.aki$snapshot = PiglinSnapshot.capture(piglin, level);
            } else if (brute != null) {
                this.aki$snapshot = PiglinSnapshot.captureSimple(brute, level);
            }
        } catch (Exception e) {
            this.aki$snapshot = null;
        }
    }
    
    @Inject(method = "customServerAiStep", at = @At("RETURN"))
    private void aki$offloadBrain(CallbackInfo ci) {
        if (!cached_enabled) return;
        if (this.aki$snapshot == null) return;
        
        executionCount++;
        
        net.minecraft.world.entity.monster.piglin.AbstractPiglin abstractPiglin = 
            (net.minecraft.world.entity.monster.piglin.AbstractPiglin) (Object) this;
        
        ServerLevel level = (ServerLevel) abstractPiglin.level();
        if (level == null) return;
        
        final PiglinSnapshot snapshot = this.aki$snapshot;
        
        try {
            CompletableFuture<PiglinDiff> future = AsyncBrainExecutor.runSync(() -> {
                return PiglinCpuCalculator.runCpuOnly(abstractPiglin, level, snapshot);
            }, cached_timeoutMicros, TimeUnit.MICROSECONDS);
            
            PiglinDiff diff = AsyncBrainExecutor.getWithTimeoutOrRunSync(
                future,
                cached_timeoutMicros,
                TimeUnit.MICROSECONDS,
                () -> new PiglinDiff()
            );
            
            if (diff != null && diff.hasChanges()) {
                diff.applyTo(abstractPiglin.getBrain(), level, cached_lookDistance, cached_barterDistance);
                successCount++;
                
                if (executionCount % 1000 == 0) {
                    double successRate = (successCount * 100.0) / executionCount;
                    double timeoutRate = (timeoutCount * 100.0) / executionCount;
                    System.out.println(String.format(
                        "[AkiAsync-PiglinAI] Stats: %d execs | %.1f%% success | %.1f%% timeout",
                        executionCount, successRate, timeoutRate
                    ));
                }
            } else {
                timeoutCount++;
            }
            
        } catch (Exception e) {
            if (executionCount <= 3) {
                System.err.println("[AkiAsync-PiglinAI] Error: " + e.getClass().getSimpleName());
            }
        } finally {
            this.aki$snapshot = null;
        }
    }
    
    @Unique
    private static synchronized void aki$initPiglinAsync() {
        if (initialized) return;
        
        org.virgil.akiasync.mixin.bridge.Bridge bridge = 
            org.virgil.akiasync.mixin.bridge.BridgeManager.getBridge();
        
        if (bridge != null) {
            cached_enabled = bridge.isPiglinOptimizationEnabled();
            cached_timeoutMicros = bridge.getAsyncAITimeoutMicros();
            cached_tickInterval = 3;
            cached_lookDistance = bridge.getPiglinLookDistance();
            cached_barterDistance = bridge.getPiglinBarterDistance();
            
            AsyncBrainExecutor.setExecutor(bridge.getGeneralExecutor());
        } else {
            cached_enabled = false;
            cached_timeoutMicros = 100;
            cached_tickInterval = 3;
            cached_lookDistance = 16;
            cached_barterDistance = 16;
        }
        
        initialized = true;
        System.out.println(String.format(
            "[AkiAsync] PiglinBrainMixin initialized: enabled=%s, timeout=%dμs, interval=%d tick, entities=[Piglin, PiglinBrute]",
            cached_enabled, cached_timeoutMicros, cached_tickInterval
        ));
    }
}

