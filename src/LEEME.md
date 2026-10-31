# Lumina Survival (Fabric 26.3)

Mod acompanante de Lumina Shaders. Es solo logica de servidor: en un mundo local funciona solo,
y en un servidor dedicado se instala unicamente en el servidor.

## Que hace
- **Agujero negro (End):** ~2 s aviso, ~5 s empieza a jalarte (cada vez mas fuerte), ~25 s empieza a destruirte.
  Mata tambien en creativo. Apartar la mirada baja el contador.
- **Sol (Overworld):** oscurece la vista, luego quema, pulsos de negro y al final ceguera de 3 min.
  No cuenta si algo tapa el sol o llueve fuerte.
- **Desastres:** Overworld = tornado, terremoto, meteoritos, supertormenta. Nether = tornado de fuego, terremoto,
  lluvia de lava. Salen solos cada 10-20 min con 5 s de aviso.
- **Supervivencia:** sed (0-20). Agachate (shift) mirando al agua para beber. Oceano = agua salada, rio/pantano = riesgo.
- **Caminos:** pasto -> tierra -> sendero, nieve aplastada, polvo en arena/grava.
- **Agua:** corrientes fuertes, cascadas que jalan, oleaje en el oceano.

## Comandos (op)
- `/lumina disaster <tornado|fire_tornado|earthquake|meteors|storm|lava_rain|stop>`
- `/lumina thirst <0-20>`

## Compilar (Minecraft 26.3 = sin ofuscar, Java 25)
1. Instala JDK 25.
2. Copia `gradlew`, `gradlew.bat` y la carpeta `gradle/` del proyecto oficial `FabricMC/fabric-example-mod` (rama 26.x)
   y ejecuta `./gradlew wrapper --gradle-version latest`.
3. `./gradlew build` -> jar en `build/libs/`. Ponlo en `mods/` con Fabric API 0.161.0+26.3, Iris y Sodium para 26.3.
4. Versiones en `gradle.properties` (revisa https://fabricmc.net/develop por si hay mas nuevas).

## Si algo no compila (no pude probarlo)
Escribi el codigo con los nombres oficiales de Mojang. Los puntos mas probables de ajuste en 26.3 son:
- `Util.sunDirection`: usa `level.getDayTime()` (el reloj del mundo pudo cambiar).
- `LuminaCommands`: `Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)` (sistema de permisos nuevo).
- `DisasterManager`: imports y constructores de `LargeFireball` / `SmallFireball`.
- `Util.push`: usa `sp.connection.send(new ClientboundSetEntityMotionPacket(sp))`.
Pega el error exacto y se corrige en una linea.

## Ajustes
Todo en `src/main/java/com/lumina/survival/Config.java`.
El agujero negro usa la misma formula que el shader con `BLACK_HOLE_ORBIT = 0.0` (fijo).
