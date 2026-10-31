package com.lumina.survival;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public final class Util {
    private Util() {}

    public static ServerLevel level(ServerPlayer p) {
        return (ServerLevel) p.level();
    }

    /** Suma velocidad a una entidad; a los jugadores les avisa por paquete. */
    public static void push(Entity e, double x, double y, double z) {
        e.setDeltaMovement(e.getDeltaMovement().add(x, y, z));
        if (e instanceof ServerPlayer sp) {
            sp.connection.send(new ClientboundSetEntityMotionPacket(sp));
        }
    }

    public static void bar(ServerPlayer p, String msg, ChatFormatting color) {
        p.displayClientMessage(Component.literal(msg).withStyle(color), true);
    }

    /** Direccion (mundo) del agujero negro; misma formula que bh.glsl con orbita 0. */
    public static Vec3 bhDirection() {
        double a = Config.BH_AZIMUTH, e = Config.BH_ELEVATION;
        return new Vec3(Math.cos(a) * Math.cos(e), Math.sin(e), Math.sin(a) * Math.cos(e)).normalize();
    }

    /** Direccion del sol (formula vanilla del angulo del cielo + inclinacion del shader). */
    public static Vec3 sunDirection(ServerLevel level) {
        double t = level.getDayTime() / 24000.0 - 0.25;   // <-- si no compila en 26.3, ver LEEME
        double d = t - Math.floor(t);
        double e = 0.5 - Math.cos(d * Math.PI) / 2.0;
        double angle = (d * 2.0 + e) / 3.0;
        double phi = angle * 2.0 * Math.PI;
        double g = Math.toRadians(Config.SUN_TILT_DEG);
        return new Vec3(-Math.sin(phi), Math.cos(phi) * Math.cos(g), -Math.cos(phi) * Math.sin(g)).normalize();
    }

    public static void particle(ServerLevel level, ParticleOptions fx, double x, double y, double z,
                                int count, double spread, double speed) {
        level.sendParticles(fx, x, y, z, count, spread, spread, spread, speed);
    }
}
