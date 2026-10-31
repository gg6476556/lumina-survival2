package com.lumina.survival;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

/** Corrientes fuertes, cascadas que te jalan y oleaje en el oceano. */
public final class WaterPhysics {
    private WaterPhysics() {}

    public static void tick(ServerPlayer p) {
        if (p.getAbilities().flying || !p.isInWater()) return;
        ServerLevel level = Util.level(p);

        BlockPos pos = p.blockPosition();
        FluidState fs = level.getFluidState(pos);
        if (!fs.is(FluidTags.WATER)) {
            pos = pos.above();
            fs = level.getFluidState(pos);
            if (!fs.is(FluidTags.WATER)) return;
        }

        Vec3 flow = fs.getFlow(level, pos);
        Util.push(p, flow.x * Config.CURRENT_STRENGTH, 0, flow.z * Config.CURRENT_STRENGTH);
        if (flow.y < -0.3) {
            Util.push(p, flow.x * 0.02, -Config.WATERFALL_PULL, flow.z * 0.02); // cascada
        }

        boolean surface = !level.getFluidState(pos.above()).is(FluidTags.WATER);
        if (surface && level.getBiome(pos).is(BiomeTags.IS_OCEAN)) {
            double storm = 1.0 + 2.0 * level.getRainLevel(1.0f) + level.getThunderLevel(1.0f);
            double t = level.getGameTime() * 0.07;
            double a = t + p.getX() * 0.15 + p.getZ() * 0.08;
            double s = Config.OCEAN_WAVE_STRENGTH * storm;
            Util.push(p, Math.sin(a) * s, Math.sin(t * 1.7 + p.getX() * 0.2) * s * 0.7, Math.cos(a) * s * 0.5);
        }

        if (surface && level.getGameTime() % 4 == 0 && p.getDeltaMovement().horizontalDistanceSqr() > 0.002) {
            level.sendParticles(ParticleTypes.SPLASH, p.getX(), p.getY() + 0.9, p.getZ(), 4, 0.25, 0.0, 0.25, 0.05);
        }
    }
}
