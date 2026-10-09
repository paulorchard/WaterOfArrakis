package com.paulorchard.islandcraft.waterofarrakis;

import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.Anchor;
import com.hypixel.hytale.server.core.ui.Value;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import java.util.Locale;

/**
 * The two bars drawn above the vanilla health and stamina bars. Read only: it shows what the simulation holds and
 * never changes it. The layout is in {@code Common/UI/Custom/Hud/Water_of_Arrakis/Arrakis_Bars.ui}.
 *
 * <p>Water is blue and sits above Health (the left half of the hotbar width); exposure is orange to red and sits
 * above Stamina (the right half). The fill of each is a plain coloured group whose width is set from the value, so
 * the colour can change smoothly (a progress bar takes its colour from a texture).
 */
final class WaterHud extends CustomUIHud {

    static final String KEY = "WaterOfArrakis";
    private static final String DOCUMENT = "Hud/Water_of_Arrakis/Arrakis_Bars.ui";
    static final int BAR_HEIGHT = 12;

    private final WaterOfArrakisConfig config;
    private int sentWaterPx = Integer.MIN_VALUE;
    private int sentExposurePx = Integer.MIN_VALUE;
    private String sentExposureColor = "";

    WaterHud(PlayerRef playerRef, WaterOfArrakisConfig config) {
        super(playerRef, KEY);
        this.config = config;
    }

    @Override
    protected void build(UICommandBuilder ui) {
        ui.append(DOCUMENT);
        // A fresh document starts empty on the client: forget what was sent before.
        sentWaterPx = Integer.MIN_VALUE;
        sentExposurePx = Integer.MIN_VALUE;
        sentExposureColor = "";
        ui.set("#WaterFill.Background.Color", config.getWaterColor());
    }

    /** Sends the new bar widths and the exposure colour, but only what changed by enough to be seen. */
    void refresh(double water, double exposure) {
        int width = (int) Math.round(config.getHudBarWidth());
        int waterPx = (int) Math.round(width * water / 100.0);
        int exposurePx = (int) Math.round(width * exposure / 100.0);
        String color = exposureColor(exposure);

        double min = config.getHudMinPixelChange();
        boolean waterChanged = sentWaterPx == Integer.MIN_VALUE || Math.abs(waterPx - sentWaterPx) >= min
                || (waterPx != sentWaterPx && (waterPx == 0 || waterPx == width));
        boolean exposureChanged = sentExposurePx == Integer.MIN_VALUE || Math.abs(exposurePx - sentExposurePx) >= min
                || (exposurePx != sentExposurePx && (exposurePx == 0 || exposurePx == width));
        boolean colorChanged = !color.equals(sentExposureColor);
        if (!waterChanged && !exposureChanged && !colorChanged) {
            return;
        }

        UICommandBuilder ui = new UICommandBuilder();
        if (waterChanged) {
            ui.setObject("#WaterFill.Anchor", fill(waterPx, true));
            ui.setObject("#WaterSheen.Anchor", fill(waterPx, true));
            sentWaterPx = waterPx;
        }
        if (exposureChanged) {
            ui.setObject("#ExposureFill.Anchor", fill(exposurePx, false));
            ui.setObject("#ExposureSheen.Anchor", fill(exposurePx, false));
            sentExposurePx = exposurePx;
        }
        if (colorChanged) {
            ui.set("#ExposureFill.Background.Color", color);
            sentExposureColor = color;
        }
        update(false, ui);
    }

    /** The fill grows from the icon side: from the left for water, from the right for exposure (like the vanilla bars). */
    private static Anchor fill(int widthPx, boolean fromLeft) {
        Anchor anchor = new Anchor();
        if (fromLeft) {
            anchor.setLeft(Value.of(0));
        } else {
            anchor.setRight(Value.of(0));
        }
        anchor.setWidth(Value.of(widthPx));
        anchor.setHeight(Value.of(BAR_HEIGHT));
        return anchor;
    }

    /**
     * Orange from 0 to ExposureRedStartPercent, a straight blend to red at 100, solid red at 100.
     */
    String exposureColor(double exposure) {
        double start = config.getExposureRedStartPercent();
        double t = exposure <= start ? 0.0 : Math.min(1.0, (exposure - start) / Math.max(1e-6, 100.0 - start));
        return blend(config.getExposureColorOrange(), config.getExposureColorRed(), t);
    }

    static String blend(String from, String to, double t) {
        int a = parse(from);
        int b = parse(to);
        int r = lerp((a >> 16) & 0xff, (b >> 16) & 0xff, t);
        int g = lerp((a >> 8) & 0xff, (b >> 8) & 0xff, t);
        int bl = lerp(a & 0xff, b & 0xff, t);
        return String.format(Locale.ROOT, "#%02x%02x%02x", r, g, bl);
    }

    private static int lerp(int a, int b, double t) {
        return (int) Math.round(a + (b - a) * t);
    }

    private static int parse(String hex) {
        String h = hex.startsWith("#") ? hex.substring(1) : hex;
        return Integer.parseInt(h.length() > 6 ? h.substring(0, 6) : h, 16);
    }
}
