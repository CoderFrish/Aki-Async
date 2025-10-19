package org.virgil.akiasync.mixin.mixins.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Consumer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntityTickList;

@SuppressWarnings({"unused", "CatchMayIgnoreException"})
@Mixin(value = EntityTickList.class, priority = 1100)
public abstract class EntityTickChunkParallelMixin {

    private static volatile boolean enabled;
    private static volatile int threads;
    private static volatile int minEntities;
    private static volatile int batchSize;
    private static volatile boolean initialized = false;
    private static volatile java.util.concurrent.ExecutorService dedicatedPool;
    private static int executionCount = 0;
    private static long lastMspt = 20;
    
    private static final java.lang.reflect.Field ACTIVE_FIELD_CACHE;
    
    static {
        java.lang.reflect.Field tempField = null;
        try {
            for (String fieldName : new String[]{"active", "f", "b", "entries", "activeEntities"}) {
                try {
                    tempField = EntityTickList.class.getDeclaredField(fieldName);
                    tempField.setAccessible(true);
                    System.out.println("[AkiAsync] EntityTickList field cached successfully: " + fieldName);
                    break;
                } catch (NoSuchFieldException ignored) {
                }
            }
            if (tempField == null) {
                System.out.println("[AkiAsync] EntityTickList field not found, will use reflection fallback");
            }
        } catch (Exception e) {
            System.err.println("[AkiAsync] Failed to cache field: " + e.getMessage());
        }
        ACTIVE_FIELD_CACHE = tempField;
    }
    
    private java.util.List<EntityAccess> cachedList;
    private long lastCacheTick;

    @Inject(method = "forEach", at = @At("HEAD"), cancellable = true)
    private void entityBatchedParallel(Consumer<EntityAccess> action, CallbackInfo ci) {
        if (!initialized) { akiasync$initEntityTickParallel(); }
        if (!enabled) return;
        
        if (cachedList == null || System.currentTimeMillis() - lastCacheTick > 50) {
            cachedList = getActiveEntities();
            lastCacheTick = System.currentTimeMillis();
        }
        
        if (cachedList == null || cachedList.size() < minEntities) return;
        
        ci.cancel();
        executionCount++;
        
        List<List<EntityAccess>> batches = partition(cachedList, batchSize);
        
        long adaptiveTimeout = calculateAdaptiveTimeout(lastMspt);
        
        try {
            List<java.util.concurrent.CompletableFuture<Void>> futures = batches.stream()
                .map(batch -> java.util.concurrent.CompletableFuture.runAsync(() -> {
                    batch.forEach(entity -> {
                        try {
                            action.accept(entity);
                        } catch (Throwable t) {
                        }
                    });
                }, dedicatedPool != null ? dedicatedPool : ForkJoinPool.commonPool()))
                .collect(java.util.stream.Collectors.toList());
            
            java.util.concurrent.CompletableFuture.allOf(futures.toArray(java.util.concurrent.CompletableFuture[]::new))
                .get(adaptiveTimeout, java.util.concurrent.TimeUnit.MILLISECONDS);
            
            if (executionCount % 100 == 0) {
                System.out.println(String.format(
                    "[AkiAsync-Parallel] Processed %d entities in %d batches (timeout: %dms)",
                    cachedList.size(), batches.size(), adaptiveTimeout
                ));
            }
            
        } catch (Throwable t) {
            if (executionCount <= 3) {
                System.err.println("[AkiAsync-Parallel] Timeout/Error, fallback to sequential: " + t.getMessage());
            }
            for (EntityAccess entity : cachedList) {
                try { action.accept(entity); } catch (Throwable ignored) {}
            }
        }
    }
    
    private List<List<EntityAccess>> partition(List<EntityAccess> list, int size) {
        List<List<EntityAccess>> result = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            result.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return result;
    }
    
    private long calculateAdaptiveTimeout(long mspt) {
        if (mspt < 20) return 100;
        if (mspt <= 30) return 50;
        return 25;
    }

    private List<EntityAccess> getActiveEntities() {
        if (ACTIVE_FIELD_CACHE == null) return null;
        
        try {
            Object map = ACTIVE_FIELD_CACHE.get(this);
            if (map instanceof Map) {
                return new ArrayList<>(((Map<?, EntityAccess>) map).values());
            }
        } catch (IllegalAccessException | ClassCastException e) {
        } catch (Exception e) {
        }
        return null;
    }
    
    private static synchronized void akiasync$initEntityTickParallel() {
        if (initialized) return;
        org.virgil.akiasync.mixin.bridge.Bridge bridge = org.virgil.akiasync.mixin.bridge.BridgeManager.getBridge();
        if (bridge != null) {
            enabled = bridge.isEntityTickParallel();
            threads = bridge.getEntityTickThreads();
            minEntities = bridge.getMinEntitiesForParallel();
            batchSize = bridge.getEntityTickBatchSize();
            dedicatedPool = bridge.getGeneralExecutor();
        } else {
            enabled = false;
            threads = 4;
            minEntities = 100;
            batchSize = 8;
            dedicatedPool = null;
        }
        initialized = true;
        System.out.println("[AkiAsync] EntityTickParallelMixin initialized (entity-batched): enabled=" + enabled + 
            ", batchSize=" + batchSize + ", minEntities=" + minEntities + 
            ", pool=" + (dedicatedPool != null ? "dedicated" : "commonPool"));
    }
}


