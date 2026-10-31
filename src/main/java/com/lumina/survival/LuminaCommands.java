package com.lumina.survival;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;

/**
 * /lumina disaster <tornado|fire_tornado|earthquake|meteors|storm|lava_rain|stop>
 * /lumina thirst <0-20>
 */
public final class LuminaCommands {
    private LuminaCommands() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) -> {
            // Permisos: en 26.x se usa Commands.hasPermission(...). Si da error aqui, ver LEEME.
            LiteralArgumentBuilder<CommandSourceStack> root =
                    Commands.literal("lumina").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS));

            LiteralArgumentBuilder<CommandSourceStack> dis = Commands.literal("disaster");
            for (DisasterManager.Type type : DisasterManager.Type.values()) {
                dis.then(Commands.literal(type.name().toLowerCase(Locale.ROOT)).executes(ctx -> {
                    boolean ok = DisasterManager.start(Util.level(ctx.getSource().getPlayerOrException()), type);
                    ctx.getSource().sendSuccess(() -> Component.literal(ok
                            ? "Desastre iniciado."
                            : "Ese desastre no es posible en esta dimension."), false);
                    return ok ? 1 : 0;
                }));
            }
            dis.then(Commands.literal("stop").executes(ctx -> {
                DisasterManager.stop(Util.level(ctx.getSource().getPlayerOrException()));
                ctx.getSource().sendSuccess(() -> Component.literal("Desastre detenido."), false);
                return 1;
            }));
            root.then(dis);

            root.then(Commands.literal("thirst")
                    .then(Commands.argument("valor", IntegerArgumentType.integer(0, 20)).executes(ctx -> {
                        ServerPlayer p = ctx.getSource().getPlayerOrException();
                        int v = IntegerArgumentType.getInteger(ctx, "valor");
                        Thirst.set(p, v);
                        ctx.getSource().sendSuccess(() -> Component.literal("Sed = " + v), false);
                        return 1;
                    })));

            dispatcher.register(root);
        });
    }
}
