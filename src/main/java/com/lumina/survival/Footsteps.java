package com.lumina.survival;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Caminar deja camino: el pasto se desgasta, la nieve se aplasta, la arena levanta polvo. */
public final class Footsteps {
    private static final Map<UUID, Vec3> LAST = new HashMap<>();
    private static final Map<UUID, Double> DIST = new HashMap<>();
    private static final Map<Long, Integer> WEAR = new LinkedHashMap<>(512, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, Integer> eldest) { return size() > 8192; }
    };

    private Footsteps() {}

    public static void clear(UUID id) { LAST.remove(id); DIST.remove(id); }

    public static void tick(ServerPlayer p) {
        UUID id = p.getUUID();
        Vec3 now = p.position();
        Vec3 last = LAST.put(id, now);
        if (last == null || p.getAbilities().flying || p.isShiftKeyDown() || !p.onGround()) return;

        double dx = now.x - last.x, dz = now.z - last.z;
        double d = Math.sqrt(dx * dx + dz * dz);
        if (d > 3.0) return; // teletransporte
        double acc = DIST.merge(id, d, Double::sum);
        if (acc < 0.9) return;
        DIST.put(id, 0.0);
        step(p);
    }

    private static void step(ServerPlayer p) {
        ServerLevel level = Util.level(p);
        BlockPos feet = p.blockPosition();
        BlockPos below = feet.below();
        BlockState atFeet = level.getBlockState(feet);
        BlockState ground = level.getBlockState(below);

        if ((atFeet.is(Blocks.SHORT_GRASS) || atFeet.is(Blocks.FERN)) && level.random.nextFloat() < 0.5f) {
            level.destroyBlock(feet, false); // pasto pisado
        }
        if (atFeet.is(Blocks.SNOW)) {
            int layers = atFeet.getValue(SnowLayerBlock.LAYERS);
            if (layers > 1) level.setBlockAndUpdate(feet, atFeet.setValue(SnowLayerBlock.LAYERS, layers - 1));
            dust(level, p, atFeet);
        }

        long key = below.asLong();
        if (ground.is(Blocks.GRASS_BLOCK) || ground.is(Blocks.MYCELIUM) || ground.is(Blocks.PODZOL)) {
            int n = WEAR.merge(key, 1, Integer::sum);
            if (n >= Config.WEAR_GRASS_TO_DIRT) {
                level.setBlockAndUpdate(below, Blocks.DIRT.defaultBlockState());
                WEAR.remove(key);
            }
            dust(level, p, ground);
        } else if (ground.is(Blocks.DIRT) || ground.is(Blocks.COARSE_DIRT)) {
            int n = WEAR.merge(key, 1, Integer::sum);
            if (n >= Config.WEAR_DIRT_TO_PATH && level.getBlockState(feet).isAir()) {
                level.setBlockAndUpdate(below, Blocks.DIRT_PATH.defaultBlockState());
                WEAR.remove(key);
            }
            dust(level, p, ground);
        } else if (ground.is(BlockTags.SAND) || ground.is(Blocks.SNOW_BLOCK) || ground.is(Blocks.GRAVEL)) {
            dust(level, p, ground);
        }
    }

    private static void dust(ServerLevel level, ServerPlayer p, BlockState state) {
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state),
                p.getX(), p.getY() + 0.1, p.getZ(), 6, 0.2, 0.02, 0.2, 0.05);
    }
}
