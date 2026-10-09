package com.paulorchard.islandcraft.waterofarrakis;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;

import java.util.Locale;
import java.util.function.Supplier;

/**
 * /staminacurve: prints the stamina rework curves as tables (water 100 to 0 in steps of 10, against exposure 0, 25, 50,
 * 75 and 100) so they can be judged without playing. Works from the console. Values come from the config, so edit the
 * Pause and Regen constants and run it again after a restart.
 */
final class StaminaCurveCommand {

    private static final double[] EXPOSURES = {0, 25, 50, 75, 100};

    private StaminaCurveCommand() {
    }

    static WorldPositional build(Supplier<WaterOfArrakisConfig> config) {
        return WorldPositional.build("staminacurve", "server.commands.staminacurve.desc", (ctx, world, store, args) -> {
            WaterOfArrakisConfig cfg = config.get();
            say(ctx, "Pause multiplier P (1.0 = vanilla; every regeneration pause is multiplied by it). Columns: exposure %.");
            table(ctx, cfg, (w, e) -> StaminaCurves.pauseMultiplier(cfg, w, e), "%7.2f");
            say(ctx, "Regen speed multiplier R (1.0 = vanilla 3.0 stamina per second).");
            table(ctx, cfg, (w, e) -> StaminaCurves.regenMultiplier(cfg, w, e), "%7.2f");
            say(ctx, "A 0.5 s pause as the player lives it, in seconds (capped at MaxPauseSeconds).");
            table(ctx, cfg, (w, e) -> StaminaCurves.effectivePause(cfg, 0.5, w, e), "%7.2f");
            say(ctx, "Time to refill the whole bar once regeneration has started, in seconds (vanilla 3.3).");
            table(ctx, cfg, (w, e) -> StaminaCurves.refillSeconds(cfg, w, e), "%7.2f");
            say(ctx, "Water taken by one full refill (10 stamina). It depends on exposure only: x1.0 to x2.0.");
            table(ctx, cfg, (w, e) -> StaminaCurves.waterCostPerFullRefill(cfg, e), "%7.2f");
        });
    }

    private interface Curve {
        double at(double water, double exposure);
    }

    private static void table(CommandContext ctx, WaterOfArrakisConfig cfg, Curve curve, String format) {
        StringBuilder header = new StringBuilder("water   ");
        for (double e : EXPOSURES) {
            header.append(String.format(Locale.ROOT, "%6.0f%%", e));
        }
        say(ctx, header.toString());
        for (int w = 100; w >= 0; w -= 10) {
            StringBuilder row = new StringBuilder(String.format(Locale.ROOT, "%5d%%  ", w));
            for (double e : EXPOSURES) {
                row.append(String.format(Locale.ROOT, format, curve.at(w, e)));
            }
            say(ctx, row.toString());
        }
    }

    private static void say(CommandContext ctx, String text) {
        ctx.sendMessage(Message.raw(text));
    }
}
