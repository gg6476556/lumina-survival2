package com.lumina.survival;

/** Todos los ajustes del mod. Cambia los numeros y vuelve a compilar. */
public final class Config {
    private Config() {}

    // ---------- Agujero negro (solo en The End) ----------
    // Debe coincidir con shaders/lib/bh.glsl con BLACK_HOLE_ORBIT = 0.0 (agujero fijo)
    public static final double BH_AZIMUTH = -0.8966;
    public static final double BH_ELEVATION = 0.35;
    public static final double BH_LOOK_DOT = 0.93;      // que tan de frente hay que mirarlo (1.0 = exacto)
    public static final int BH_WARN_TICKS = 40;         // 2 s: aviso
    public static final int BH_PULL_TICKS = 100;        // 5 s: empieza a jalarte
    public static final int BH_RAMP_TICKS = 400;        // 20 s hasta jalar al maximo
    public static final double BH_MAX_PULL = 0.09;      // fuerza maxima por tick
    public static final int BH_DAMAGE_TICKS = 500;      // 25 s: empieza a destruirte (mata incluso en creativo)

    // ---------- Sol (Overworld) ----------
    public static final double SUN_TILT_DEG = 20.0;     // = sunPathRotation del shader
    public static final double SUN_LOOK_DOT = 0.965;
    public static final int SUN_DARKEN_TICKS = 30;      // vista se oscurece
    public static final int SUN_BURN_TICKS = 70;        // empieza a quemar
    public static final int SUN_BLACKOUT_TICKS = 130;   // pulsos de negro total
    public static final int SUN_BLIND_TICKS = 220;      // ceguera larga
    public static final int SUN_BLIND_DURATION = 3600;  // 3 minutos ciego

    // ---------- Desastres ----------
    public static final int DISASTER_FIRST_MIN = 3000;      // primer desastre tras iniciar (2.5-5 min)
    public static final int DISASTER_FIRST_MAX = 6000;
    public static final int DISASTER_MIN = 12000;           // despues cada 10-20 min
    public static final int DISASTER_MAX = 24000;
    public static final boolean DISASTERS_ENABLED = true;
    public static final boolean TORNADO_RIPS_BLOCKS = true; // tornados rompen bloques debiles (hojas, plantas, vidrio)

    // ---------- Supervivencia: sed ----------
    public static final boolean THIRST_ENABLED = true;
    public static final float THIRST_MAX = 20f;
    public static final float THIRST_DRAIN_PER_TICK = 20f / 36000f; // 30 min de lleno a vacio
    public static final float DRINK_AMOUNT = 4f;

    // ---------- Huellas / caminos ----------
    public static final int WEAR_GRASS_TO_DIRT = 6;
    public static final int WEAR_DIRT_TO_PATH = 10;

    // ---------- Agua ----------
    public static final double CURRENT_STRENGTH = 0.035;
    public static final double WATERFALL_PULL = 0.06;
    public static final double OCEAN_WAVE_STRENGTH = 0.006;
}
