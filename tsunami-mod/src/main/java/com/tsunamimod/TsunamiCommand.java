package com.tsunamimod;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

/**
 * /tsunami start [height] [speed]   - wave appears where you are looking and rolls towards you
 * /tsunami stop                     - removes every running wave
 * /tsunami destroy <true|false>     - turn block destruction on or off for future waves
 */
final class TsunamiCommand {
    private static boolean destroy = true;

    private TsunamiCommand() {}

    static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("tsunami")
                .requires(src -> src.hasPermissionLevel(2))
                .then(CommandManager.literal("start")
                        .executes(c -> start(c, TsunamiSettings.defaults()))
                        .then(CommandManager.argument("height", IntegerArgumentType.integer(10, 200))
                                .executes(c -> start(c, TsunamiSettings.defaults()
                                        .withHeight(IntegerArgumentType.getInteger(c, "height"))))
                                .then(CommandManager.argument("speed", DoubleArgumentType.doubleArg(0.2, 3.0))
                                        .executes(c -> start(c, TsunamiSettings.defaults()
                                                .withHeight(IntegerArgumentType.getInteger(c, "height"))
                                                .withSpeed(DoubleArgumentType.getDouble(c, "speed")))))))
                .then(CommandManager.literal("stop")
                        .executes(c -> {
                            int n = TsunamiManager.stopAll();
                            c.getSource().sendFeedback(() -> Text.literal("หยุดสึนามิแล้ว (" + n + " ลูก)"), true);
                            return n;
                        }))
                .then(CommandManager.literal("destroy")
                        .then(CommandManager.argument("enabled", BoolArgumentType.bool())
                                .executes(c -> {
                                    destroy = BoolArgumentType.getBool(c, "enabled");
                                    c.getSource().sendFeedback(() -> Text.literal(
                                            "การทำลายบล็อก: " + (destroy ? "เปิด" : "ปิด")), true);
                                    return 1;
                                }))));
    }

    private static int start(CommandContext<ServerCommandSource> c, TsunamiSettings base) throws CommandSyntaxException {
        ServerCommandSource src = c.getSource();
        ServerPlayerEntity player = src.getPlayerOrThrow();
        TsunamiSettings settings = base.withDestroy(destroy);

        // horizontal direction the player is facing
        double yaw = Math.toRadians(player.getYaw());
        double lookX = -Math.sin(yaw);
        double lookZ = Math.cos(yaw);

        TsunamiManager.start(src.getWorld(), settings, player.getX(), player.getY(), player.getZ(), lookX, lookZ);
        src.sendFeedback(() -> Text.literal("สึนามิกำลังมา! หันหน้าไปทางที่มันมา แล้ววิ่งขึ้นที่สูง!"), true);
        return 1;
    }
}
