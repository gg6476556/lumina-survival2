package com.lumina.survival;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Si miras el agujero negro del End mucho tiempo, te absorbe hasta matarte (tambien en creativo). */
public final class BlackHoleGaze {
    private static final Map<UUID, Integer> GAZE = new HashMap<>();
    private static final Vec3 DIR = Util.bhDirection();

    private BlackHoleGaze() {}

    public static void clear(UUID id) { GAZE.remove(id); }

    public static void tick(ServerPlayer p) {
        ServerLevel level = Util.level(p);
        UUID id = p.getUUID();
        if (level.dimension() != Level.END || !p.isAlive()) { GAZE.remove(id); return; }

        double dot = p.getViewVector(1.0f).dot(DIR);
        int g = GAZE.getOrDefault(id, 0);
        g = dot >= Config.BH_LOOK_DOT ? g + 1 : Math.max(0, g - 3);
        if (g == 0) { GAZE.remove(id); return; }
        GAZE.put(id, g);

        if (g == Config.BH_WARN_TICKS) Util.bar(p, "Algo te observa desde el vacio...", ChatFormatting.DARK_PURPLE);
        if (g == Config.BH_PULL_TICKS) Util.bar(p, "Te esta absorbiendo. Aparta la mirada.", ChatFormatting.DARK_RED);

        if (g >= Config.BH_WARN_TICKS && g % 2 == 0) {
            Util.particle(level, ParticleTypes.REVERSE_PORTAL, p.getX(), p.getY(0.5), p.getZ(), 3, 0.5, 0.15);
        }

        if (g >= Config.BH_PULL_TICKS) {
            double ramp = Mth.clamp((g - Config.BH_PULL_TICKS) / (double) Config.BH_RAMP_TICKS, 0.0, 1.0);
            Vec3 pull = DIR.add(0, 0.5, 0).normalize().scale(ramp * Config.BH_MAX_PULL + 0.01);
            Util.push(p, pull.x, pull.y, pull.z);
            Vec3 v = p.getDeltaMovement();
            if (v.length() > 0.7) p.setDeltaMovement(v.normalize().scale(0.7));
            p.fallDistance = 0;
        }

        if (g >= Config.BH_DAMAGE_TICKS && g % 10 == 0) {
            // fellOutOfWorld atraviesta la invulnerabilidad del modo creativo
            p.hurtServer(level, level.damageSources().fellOutOfWorld(), 2.0f);
        }
    }
}
