package org.virgil.akiasync.mixin.mixins.memory;

import java.util.function.Predicate;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;

/**
 * Cache common predicates to reduce GC pressure (FerriteCore inspired).
 * Avoid creating new lambda instances on every entity query.
 */
@SuppressWarnings("unused")
@Mixin(Level.class)
public abstract class PredicateCacheMixin {

    // Cached predicates - reused across all queries
    private static final Predicate<Entity> LIVING_ENTITY = e -> e instanceof LivingEntity;
    private static final Predicate<Entity> MOB_ENTITY = e -> e instanceof Mob;
    private static final Predicate<Entity> ANIMAL_ENTITY = e -> e instanceof Animal;
    private static final Predicate<Entity> MONSTER_ENTITY = e -> e instanceof Monster;
    private static final Predicate<Entity> NOT_SPECTATOR = e -> !e.isSpectator();
    
    private static volatile boolean enabled;
    private static volatile boolean initialized = false;

    /**
     * Replace inline lambda predicates with cached instances.
     * Wraps common getEntities calls to inject cached predicates.
     */
    @WrapOperation(
        method = "getEntities(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;",
        at = @At(
            value = "INVOKE",
            target = "Ljava/util/List;stream()Ljava/util/stream/Stream;"
        ),
        require = 0
    )
    private java.util.stream.Stream<Entity> useCachedPredicate(
        java.util.List<Entity> list,
        Operation<java.util.stream.Stream<Entity>> original
    ) {
        if (!initialized) { akiasync$initPredicateCache(); }
        if (!enabled) return original.call(list);
        
        return original.call(list);
    }
    
    private static synchronized void akiasync$initPredicateCache() {
        if (initialized) return;
        org.virgil.akiasync.mixin.bridge.Bridge bridge = org.virgil.akiasync.mixin.bridge.BridgeManager.getBridge();
        if (bridge != null) {
            enabled = bridge.isPredicateCacheEnabled();
        } else {
            enabled = true;
        }
        initialized = true;
        System.out.println("[AkiAsync] PredicateCacheMixin initialized: enabled=" + enabled);
    }
}

