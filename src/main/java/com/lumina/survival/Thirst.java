package com.lumina.survival;

import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Sistema de sed (0-20). Se guarda como una etiqueta "lumina_thirst_<decimas>" en el propio jugador,
 * asi persiste con el mundo. Para beber: agachate (shift) mirando al agua.
 */
public final class Thirst {
    private static final String PREFIX = "lumina_thirst_";
    private static final Map<UUID, Float> VALUE = new HashMap<>();
    private static final Map<UUID, Integer> DRINK = new HashMap<>();

    private Thirst() {}

    public static float get(ServerPlayer p) {
        return VALUE.computeIfAbsent(p.getUUID(), k -> load(p));
    }

    public static void set(ServerPlayer p, float v) {
        VALUE.put(p.getUUID(), Math.max(0f, Math.min(Config.THIRST_MAX, v)));
        save(p);
    }

    // En 26.3 las etiquetas del jugador cambiaron; por ahora la sed no se guarda al cerrar el mundo
    // (se reinicia a 20 al entrar).
    private static float load(ServerPlayer p) {
        return Config.THIRST_MAX;
    }

    private static void save(ServerPlayer p) {
    }

    public static void onLeave(ServerPlayer p) {
        save(p);
        VALUE.remove(p.getUUID());
        DRINK.remove(p.getUUID());
    }

    public static void onRespawn(ServerPlayer p) {
        set(p, Config.THIRST_MAX);
    }

    public static void tick(ServerPlayer p) {
        if (!Config.THIRST_ENABLED || p.isCreative() || p.isSpectator() || !p.isAlive()) return;
        ServerLevel level = Util.level(p);
        UUID id = p.getUUID();
        float t = get(p);

        // ---- gasto ----
        float drain = Config.THIRST_DRAIN_PER_TICK;
        if (p.isSprinting()) drain *= 3f;
        if (level.dimension() == Level.NETHER) drain *= 2.5f;
        else if (level.dimension() == Level.OVERWORLD && Util.sunDirection(level).y > 0 && isHot(level, p)) drain *= 2f;
        if (p.isInWater()) drain *= 0.3f;
        t = Math.max(0f, t - drain);

        // ---- beber: shift + mirar al agua ----
        boolean drinking = false;
        if (p.isShiftKeyDown() && p.getXRot() > 15f) {
            HitResult hit = p.pick(3.0, 1.0f, true);
            if (hit instanceof BlockHitResult bhr && level.getFluidState(bhr.getBlockPos()).is(FluidTags.WATER)) {
                drinking = true;
                int c = DRINK.merge(id, 1, Integer::sum);
                if (c >= 25) {
                    DRINK.put(id, 0);
                    t = Math.min(Config.THIRST_MAX, t + Config.DRINK_AMOUNT);
                    level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.GENERIC_DRINK,
                            SoundSource.PLAYERS, 0.8f, 1.0f);
                    var biome = level.getBiome(bhr.getBlockPos());
                    if (biome.is(BiomeTags.IS_OCEAN)) {
                        t = Math.max(0f, t - Config.DRINK_AMOUNT - 1.5f); // agua salada deshidrata
                        p.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 120, 0));
                        Util.bar(p, "Agua salada: te deshidrata.", ChatFormatting.RED);
                    } else if (biome.is(BiomeTags.IS_RIVER) || biome.is(Biomes.SWAMP)) {
                        if (level.getRandom().nextFloat() < 0.25f) {
                            p.addEffect(new MobEffectInstance(MobEffects.HUNGER, 200, 0));
                            Util.bar(p, "Agua sucia: te sienta mal.", ChatFormatting.GOLD);
                        }
                    }
                }
            }
        }
        if (!drinking) DRINK.remove(id);

        VALUE.put(id, t);

        // ---- efectos de la sed ----
        long tick = level.getGameTime();
        if (t <= 6f && p.isSprinting()) p.setSprinting(false);
        if (tick % 20 == 0) {
            if (t <= 2f) {
                p.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 1, false, false, true));
                p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 0, false, false, true));
            } else if (t <= 6f) {
                p.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 0, false, false, true));
            }
            if (t <= 10f) {
                Util.bar(p, "Sed " + Math.round(t) + "/20 (agachate junto al agua para beber)",
                        t <= 4f ? ChatFormatting.RED : ChatFormatting.AQUA);
            }
            if (tick % 100 == 0) save(p);
        }
        if (t <= 0f && tick % 80 == 0) {
            p.hurtServer(level, level.damageSources().starve(), 1.0f);
        }
    }

    private static boolean isHot(ServerLevel level, ServerPlayer p) {
        var biome = level.getBiome(p.blockPosition());
        return biome.is(Biomes.DESERT) || biome.is(Biomes.BADLANDS) || biome.is(Biomes.ERODED_BADLANDS)
                || biome.is(Biomes.WOODED_BADLANDS) || biome.is(Biomes.SAVANNA) || biome.is(Biomes.SAVANNA_PLATEAU);
    }
}
