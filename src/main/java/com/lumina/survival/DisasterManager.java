package com.lumina.survival;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.LightningBolt;
// Si estos dos imports fallan en 26.3, usa Alt+Enter en IntelliJ (el paquete cambio entre versiones)
import net.minecraft.world.entity.projectile.hurtingprojectile.LargeFireball;
import net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Tornados, terremotos, meteoritos, supertormentas (Overworld) y tornado de fuego / lluvia de lava (Nether). */
public final class DisasterManager {
    public enum Type { TORNADO, FIRE_TORNADO, EARTHQUAKE, METEORS, STORM, LAVA_RAIN }

    private static final Map<ResourceKey<Level>, Disaster> ACTIVE = new HashMap<>();
    private static final Map<ResourceKey<Level>, Integer> COOLDOWN = new HashMap<>();

    private DisasterManager() {}

    public static List<Type> allowed(ResourceKey<Level> key) {
        if (key == Level.OVERWORLD) return List.of(Type.TORNADO, Type.EARTHQUAKE, Type.METEORS, Type.STORM);
        if (key == Level.NETHER) return List.of(Type.FIRE_TORNADO, Type.EARTHQUAKE, Type.LAVA_RAIN);
        return List.of();
    }

    private static int rand(ServerLevel w, int min, int max) {
        return min + w.random.nextInt(Math.max(1, max - min));
    }

    public static void tick(MinecraftServer server) {
        if (!Config.DISASTERS_ENABLED) return;
        for (ServerLevel w : server.getAllLevels()) {
            ResourceKey<Level> key = w.dimension();
            List<Type> options = allowed(key);
            if (options.isEmpty()) continue;
            List<ServerPlayer> players = w.players();
            if (players.isEmpty()) continue;

            Disaster d = ACTIVE.get(key);
            if (d != null) {
                if (!d.tick(w, players)) {
                    ACTIVE.remove(key);
                    COOLDOWN.put(key, rand(w, Config.DISASTER_MIN, Config.DISASTER_MAX));
                }
                continue;
            }
            int cd = COOLDOWN.computeIfAbsent(key, k -> rand(w, Config.DISASTER_FIRST_MIN, Config.DISASTER_FIRST_MAX)) - 1;
            if (cd <= 0) start(w, options.get(w.random.nextInt(options.size())));
            else COOLDOWN.put(key, cd);
        }
    }

    public static boolean start(ServerLevel w, Type type) {
        if (!allowed(w.dimension()).contains(type)) return false;
        Disaster d = switch (type) {
            case TORNADO -> new Tornado(false);
            case FIRE_TORNADO -> new Tornado(true);
            case EARTHQUAKE -> new Earthquake();
            case METEORS -> new Meteors();
            case STORM -> new Storm();
            case LAVA_RAIN -> new LavaRain();
        };
        ACTIVE.put(w.dimension(), d);
        return true;
    }

    public static void stop(ServerLevel w) {
        ACTIVE.remove(w.dimension());
        COOLDOWN.put(w.dimension(), rand(w, Config.DISASTER_MIN, Config.DISASTER_MAX));
    }

    /** Altura del suelo en (x,z). NaN si el chunk no esta cargado o no hay suelo. */
    static double groundY(ServerLevel w, double x, double z, double refY, boolean nether) {
        int ix = Mth.floor(x), iz = Mth.floor(z);
        if (!w.hasChunkAt(new BlockPos(ix, 64, iz))) return Double.NaN;
        if (!nether) return w.getHeight(Heightmap.Types.MOTION_BLOCKING, ix, iz);
        int y = Mth.clamp((int) refY + 12, 6, 120);
        for (; y > 5; y--) {
            BlockPos p = new BlockPos(ix, y, iz);
            if (!w.getBlockState(p).getCollisionShape(w, p).isEmpty() && w.getBlockState(p.above()).isAir()) return y + 1;
        }
        return Double.NaN;
    }

    // ====================================================================
    private abstract static class Disaster {
        final String warning;
        final int warn;
        final int life;
        int age = 0;

        Disaster(String warning, int warn, int life) {
            this.warning = warning;
            this.warn = warn;
            this.life = life;
        }

        boolean tick(ServerLevel w, List<ServerPlayer> ps) {
            age++;
            if (age == 1) {
                for (ServerPlayer p : ps) {
                    p.sendSystemMessage(Component.literal(warning).withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
                    p.playSound(SoundEvents.ENDER_DRAGON_GROWL, 0.6f, 0.6f);
                }
                begin(w, ps);
            }
            if (age < warn) return true;
            if (age >= warn + life) { end(w, ps); return false; }
            return run(w, ps, age - warn);
        }

        void begin(ServerLevel w, List<ServerPlayer> ps) {}
        void end(ServerLevel w, List<ServerPlayer> ps) {}
        abstract boolean run(ServerLevel w, List<ServerPlayer> ps, int t);
    }

    // ---------------------------------------------------------------- TORNADO
    private static final class Tornado extends Disaster {
        static final double H = 26.0, R = 15.0;
        final boolean fire;
        Vec3 pos;
        double heading;

        Tornado(boolean fire) {
            super(fire ? "\u26A0 Un tornado de fuego se forma en el Nether. Corre!"
                       : "\u26A0 Se ha formado un tornado cerca. Busca refugio!", 100, 1800);
            this.fire = fire;
        }

        @Override
        void begin(ServerLevel w, List<ServerPlayer> ps) {
            for (int i = 0; i < 12 && pos == null; i++) {
                ServerPlayer p = ps.get(w.random.nextInt(ps.size()));
                double a = w.random.nextDouble() * Math.PI * 2;
                double dist = 22 + w.random.nextDouble() * 14; // dentro de los 32 bloques de particulas
                double x = p.getX() + Math.cos(a) * dist, z = p.getZ() + Math.sin(a) * dist;
                double y = groundY(w, x, z, p.getY(), fire);
                if (!Double.isNaN(y)) {
                    pos = new Vec3(x, y, z);
                    heading = a + Math.PI + (w.random.nextDouble() - 0.5) * 1.5; // se acerca al jugador
                }
            }
        }

        @Override
        boolean run(ServerLevel w, List<ServerPlayer> ps, int t) {
            if (pos == null) return false;

            heading += (w.random.nextDouble() - 0.5) * 0.08;
            double nx = pos.x + Math.cos(heading) * 0.22, nz = pos.z + Math.sin(heading) * 0.22;
            double ny = groundY(w, nx, nz, pos.y, fire);
            if (Double.isNaN(ny)) heading += Math.PI;
            else pos = new Vec3(nx, pos.y + (ny - pos.y) * 0.2, nz);

            for (int i = 0; i < 12; i++) {
                double h = w.random.nextDouble() * H;
                double r = 1.2 + h * 0.34;
                double a = t * 0.45 + h * 0.55 + w.random.nextDouble() * 0.6;
                ParticleOptions fx;
                if (fire) fx = w.random.nextInt(3) == 0 ? ParticleTypes.LARGE_SMOKE : ParticleTypes.FLAME;
                else fx = w.random.nextInt(4) == 0 ? ParticleTypes.SMOKE : ParticleTypes.CLOUD;
                Util.particle(w, fx, pos.x + Math.cos(a) * r, pos.y + h, pos.z + Math.sin(a) * r, 1, 0.05, 0.01);
            }
            if (t % 15 == 0) {
                w.playSound(null, pos.x, pos.y + 4, pos.z, SoundEvents.ENDER_DRAGON_FLAP,
                        SoundSource.WEATHER, 4.0f, 0.6f);
            }

            AABB box = new AABB(pos.x - R, pos.y - 2, pos.z - R, pos.x + R, pos.y + H, pos.z + R);
            List<Entity> list = w.getEntitiesOfClass(Entity.class, box, ent -> ent.isAlive() && !ent.isSpectator()
                    && (ent instanceof LivingEntity || ent instanceof ItemEntity || ent instanceof FallingBlockEntity));
            for (Entity e : list) {
                double dx = e.getX() - pos.x, dz = e.getZ() - pos.z;
                double dist = Math.max(0.3, Math.sqrt(dx * dx + dz * dz));
                if (dist > R) continue;
                double s = 1.0 - dist / R;
                double tx = -dz / dist, tz = dx / dist;
                double ix = -dx / dist, iz = -dz / dist;
                double spin = 0.02 + 0.08 * s;
                double lift = (e.getY() - pos.y < H + 4) ? 0.05 + 0.08 * s : 0.0;
                Util.push(e, tx * spin + ix * 0.05 * s, lift, tz * spin + iz * 0.05 * s);
                Vec3 v = e.getDeltaMovement();
                if (v.length() > 1.1) e.setDeltaMovement(v.normalize().scale(1.1));
                if (dist < 6 && t % 20 == 0 && e instanceof LivingEntity le) {
                    if (fire) le.igniteForSeconds(5.0f);
                    le.hurtServer(w, w.damageSources().generic(), 2.0f);
                }
            }

            if (Config.TORNADO_RIPS_BLOCKS && t % 2 == 0) {
                for (int i = 0; i < 4; i++) {
                    BlockPos bp = BlockPos.containing(pos.x + w.random.nextInt(15) - 7, pos.y + w.random.nextInt(5),
                            pos.z + w.random.nextInt(15) - 7);
                    if (!w.hasChunkAt(bp)) continue;
                    BlockState st = w.getBlockState(bp);
                    if (st.isAir() || !st.getFluidState().isEmpty()) continue;
                    float hd = st.getDestroySpeed(w, bp);
                    if (hd >= 0f && hd <= 0.4f) w.destroyBlock(bp, false);
                }
                if (fire && w.random.nextInt(8) == 0) {
                    BlockPos fp = BlockPos.containing(pos.x + w.random.nextInt(13) - 6, pos.y + 1, pos.z + w.random.nextInt(13) - 6);
                    if (w.hasChunkAt(fp) && w.getBlockState(fp).isAir()
                            && Blocks.FIRE.defaultBlockState().canSurvive(w, fp)) {
                        w.setBlockAndUpdate(fp, Blocks.FIRE.defaultBlockState());
                    }
                }
            }
            return true;
        }
    }

    // ------------------------------------------------------------- TERREMOTO
    private static final class Earthquake extends Disaster {
        Earthquake() { super("\u26A0 Terremoto! El suelo empieza a temblar.", 60, 400); }

        @Override
        boolean run(ServerLevel w, List<ServerPlayer> ps, int t) {
            double k = Math.min(1.0, Math.min(t, life - t) / 60.0);
            for (ServerPlayer p : ps) {
                if (p.isSpectator()) continue;
                if (p.onGround() && t % 3 == 0) {
                    Util.push(p, (w.random.nextDouble() - 0.5) * 0.30 * k, 0.10 * k * w.random.nextDouble(),
                            (w.random.nextDouble() - 0.5) * 0.30 * k);
                }
                if (t % 8 == 0) {
                    w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENDER_DRAGON_GROWL,
                            SoundSource.AMBIENT, (float) (0.5 * k) + 0.05f, 0.3f);
                }
                if (t % 5 == 0) {
                    BlockState below = w.getBlockState(p.blockPosition().below());
                    if (!below.isAir()) {
                        Util.particle(w, new BlockParticleOption(ParticleTypes.BLOCK, below),
                                p.getX(), p.getY() + 0.2, p.getZ(), 8, 0.6, 0.05);
                    }
                    if (w.random.nextInt(3) == 0) {
                        BlockPos bp = p.blockPosition().offset(w.random.nextInt(17) - 8, w.random.nextInt(6) - 1, w.random.nextInt(17) - 8);
                        if (w.hasChunkAt(bp)) {
                            BlockState st = w.getBlockState(bp);
                            float hd = st.getDestroySpeed(w, bp);
                            if (!st.isAir() && hd > 0.25f && hd <= 0.3f) w.destroyBlock(bp, false); // vidrios
                        }
                    }
                }
            }
            return true;
        }
    }

    // ------------------------------------------------------------ METEORITOS
    private static final class Meteors extends Disaster {
        Meteors() { super("\u26A0 Lluvia de meteoritos! Mira al cielo.", 100, 700); }

        @Override
        boolean run(ServerLevel w, List<ServerPlayer> ps, int t) {
            if (t % 25 == 0) meteor(w, ps.get(w.random.nextInt(ps.size())));
            return true;
        }

        static void meteor(ServerLevel w, ServerPlayer target) {
            double tx = target.getX() + (w.random.nextDouble() - 0.5) * 70;
            double tz = target.getZ() + (w.random.nextDouble() - 0.5) * 70;
            if (!w.hasChunkAt(BlockPos.containing(tx, target.getY(), tz))) return;
            double ty = w.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(tx), Mth.floor(tz));
            double sx = tx + (w.random.nextDouble() - 0.5) * 50;
            double sz = tz + (w.random.nextDouble() - 0.5) * 50;
            double sy = Math.min(target.getY() + 70, 315);
            Vec3 dir = new Vec3(tx - sx, ty - sy, tz - sz).normalize();
            // Si el constructor no coincide en 26.3 (Vec3 vs 3 doubles), IntelliJ te lo marca aqui.
            LargeFireball f = new LargeFireball(w, target, dir, 2);
            f.setPos(sx, sy, sz);
            f.setDeltaMovement(dir.scale(0.8));
            w.addFreshEntity(f);
        }
    }

    // ------------------------------------------------------------- TORMENTA
    private static final class Storm extends Disaster {
        Storm() { super("\u26A0 Supertormenta electrica! Vientos huracanados.", 100, 2400); }

        @Override
        void begin(ServerLevel w, List<ServerPlayer> ps) {
            w.setWeatherParameters(0, 30000, true, true);
        }

        @Override
        void end(ServerLevel w, List<ServerPlayer> ps) {
            w.setWeatherParameters(12000, 0, false, false);
        }

        @Override
        boolean run(ServerLevel w, List<ServerPlayer> ps, int t) {
            if (t % 30 == 0) {
                ServerPlayer p = ps.get(w.random.nextInt(ps.size()));
                double a = w.random.nextDouble() * Math.PI * 2;
                double d = 10 + w.random.nextDouble() * 30;
                BlockPos g = BlockPos.containing(p.getX() + Math.cos(a) * d, p.getY(), p.getZ() + Math.sin(a) * d);
                if (w.hasChunkAt(g)) {
                    BlockPos top = w.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, g);
                    LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(w, EntitySpawnReason.TRIGGERED);
                    if (bolt != null) {
                        bolt.setPos(top.getX() + 0.5, top.getY(), top.getZ() + 0.5);
                        w.addFreshEntity(bolt);
                    }
                }
            }
            double s = 0.025 * (0.7 + 0.3 * Math.sin(t * 0.05));
            double ang = t * 0.0015;
            for (ServerPlayer p : ps) {
                if (!p.isSpectator() && w.canSeeSky(p.blockPosition())) {
                    Util.push(p, Math.cos(ang) * s, 0, Math.sin(ang) * s);
                }
            }
            return true;
        }
    }

    // ---------------------------------------------------------- LLUVIA DE LAVA
    private static final class LavaRain extends Disaster {
        LavaRain() { super("\u26A0 Erupcion! Llueve fuego desde el techo del Nether.", 100, 700); }

        @Override
        boolean run(ServerLevel w, List<ServerPlayer> ps, int t) {
            for (ServerPlayer p : ps) {
                if (p.isSpectator()) continue;
                if (t % 8 == 0) shower(w, p, false);
                if (t % 60 == 0) shower(w, p, true);
                if (t % 5 == 0) Util.particle(w, ParticleTypes.ASH, p.getX(), p.getY() + 3, p.getZ(), 15, 8, 0.0);
            }
            return true;
        }

        static void shower(ServerLevel w, ServerPlayer p, boolean big) {
            double sx = p.getX() + (w.random.nextDouble() - 0.5) * 26;
            double sz = p.getZ() + (w.random.nextDouble() - 0.5) * 26;
            double sy = p.getY() + 14;
            BlockPos from = BlockPos.containing(sx, sy, sz);
            if (!w.hasChunkAt(from) || !w.getBlockState(from).isAir()) return;
            Vec3 dir = new Vec3((w.random.nextDouble() - 0.5) * 0.15, -1, (w.random.nextDouble() - 0.5) * 0.15).normalize();
            Entity f = big ? new LargeFireball(w, p, dir, 1) : new SmallFireball(w, p, dir);
            f.setPos(sx, sy, sz);
            f.setDeltaMovement(dir.scale(0.5));
            w.addFreshEntity(f);
        }
    }
}
