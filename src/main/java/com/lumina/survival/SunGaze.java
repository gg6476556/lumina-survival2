package com.lumina.survival;

import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Mirar el sol: la vista se oscurece, te quema y, si sigues, te deja ciego. */
public final class SunGaze {
    private static final Map<UUID, Integer> GAZE = new HashMap<>();

    private SunGaze() {}

    public static void clear(UUID id) { GAZE.remove(id); }

    public static void tick(ServerPlayer p) {
        ServerLevel level = Util.level(p);
        UUID id = p.getUUID();
        if (level.dimension() != Level.OVERWORLD || !p.isAlive()) { GAZE.remove(id); return; }

        boolean looking = false;
        Vec3 sun = Util.sunDirection(level);
        MobEffectInstance blind = p.getEffect(MobEffects.BLINDNESS);
        boolean alreadyBlind = blind != null && blind.getDuration() > 100;

        if (!alreadyBlind && sun.y > 0.1 && level.getRainLevel(1.0f) < 0.6f
                && p.getViewVector(1.0f).dot(sun) >= Config.SUN_LOOK_DOT) {
            Vec3 eye = p.getEyePosition();
            HitResult hit = level.clip(new ClipContext(eye, eye.add(sun.scale(96)),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
            looking = hit.getType() == HitResult.Type.MISS;
        }

        int g = GAZE.getOrDefault(id, 0);
        g = looking ? g + 1 : Math.max(0, g - 3);
        if (g == 0) { GAZE.remove(id); return; }
        GAZE.put(id, g);

        if (g == Config.SUN_DARKEN_TICKS) Util.bar(p, "El sol te deslumbra...", ChatFormatting.YELLOW);
        if (g >= Config.SUN_DARKEN_TICKS && g % 10 == 0) {
            p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60, 0, false, false, false));
        }
        if (g >= Config.SUN_BURN_TICKS && g % 20 == 0) {
            p.igniteForSeconds(2.0f);
        }
        if (g >= Config.SUN_BLACKOUT_TICKS && g % 10 == 0) {
            p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 30, 0, false, false, false));
        }
        if (g >= Config.SUN_BLIND_TICKS) {
            p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, Config.SUN_BLIND_DURATION, 0, false, true, true));
            Util.bar(p, "Tus ojos se quemaron. Estas ciego.", ChatFormatting.DARK_RED);
            GAZE.remove(id);
        }
    }
}
