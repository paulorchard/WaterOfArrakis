package com.paulorchard.islandcraft.waterofarrakis;

import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.Anchor;
import com.hypixel.hytale.server.core.ui.Value;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import java.util.Locale;

/**
 * The two vertical bars on the screen edges. Read only: it shows what the simulation holds and never changes it. The
 * layout is in {@code Common/UI/Custom/Hud/Water_of_Arrakis/Arrakis_Bars.ui}; its comments explain the scaling.
 *
 * <p>Water is blue on the LEFT edge, exposure orange to red on the RIGHT edge. Each fills from the bottom up and has
 * its icon in the bottom corner. A bar is a column of two parts that share its length by FlexWeight: the empty part
 * (1 - v) above and the fill part (v) below, so the fill is exactly v of the bar at any screen size. The fill is a
 * white texture tinted with {@code Background.Color}.
 *
 * <p>Sizes (edge margin, thickness, the 3 / 89 / 8 split, icon size) come from the config and are set when the
 * document is built, so they can be changed without editing the .ui file.
 */
final class WaterHud extends CustomUIHud {

    static final String KEY = "WaterOfArrakis";
    private static final String DOCUMENT = "Hud/Water_of_Arrakis/Arrakis_Bars.ui";
    /**
     * FlexWeight is an integer in the client (a float is refused: "An element of type Number cannot be converted to
     * System.Int32"), so a fill is split into SCALE parts: 1000 gives 0.1% steps. A part with weight 0 has no length.
     */
    static final int SCALE = 1000;
    static final int NONE = 0;

    private final WaterOfArrakisConfig config;
    private double sentWater = Double.NaN;
    private double sentExposure = Double.NaN;
    private String sentExposureColor = "";

    WaterHud(PlayerRef playerRef, WaterOfArrakisConfig config) {
        super(playerRef, KEY);
        this.config = config;
    }

    @Override
    protected void build(UICommandBuilder ui) {
        ui.append(DOCUMENT);
        // A fresh document starts at its defaults on the client: forget what was sent before.
        sentWater = Double.NaN;
        sentExposure = Double.NaN;
        sentExposureColor = "";

        layout(ui, "Water", true);
        layout(ui, "Exposure", false);
        ui.set("#WaterFill.Background.Color", config.getWaterColor());
        ui.set("#ExposureFill.Background.Color", exposureColor(0));
        sentExposureColor = exposureColor(0);
        // Both bars start empty until the first refresh.
        ui.set("#WaterEmpty.FlexWeight", SCALE);
        ui.set("#WaterFill.FlexWeight", NONE);
        ui.set("#ExposureEmpty.FlexWeight", SCALE);
        ui.set("#ExposureFill.FlexWeight", NONE);
    }

    /** Places one bar column and its icon from the config. */
    private void layout(UICommandBuilder ui, String name, boolean leftEdge) {
        Weights w = weights(config);
        int edge = (int) Math.round(config.getHudEdgeMargin());
        int size = (int) Math.round(config.getHudIconSize());
        int iconBottom = (int) Math.round(config.getHudIconBottomMargin());

        Anchor column = new Anchor();
        if (leftEdge) {
            column.setLeft(Value.of(edge));
        } else {
            column.setRight(Value.of(edge));
        }
        column.setWidth(Value.of((int) Math.round(config.getHudBarThickness())));
        if (config.getHudMode().equalsIgnoreCase("fixed")) {
            column.setTop(Value.of((int) Math.round(config.getHudTopMargin())));
            column.setHeight(Value.of((int) Math.round(config.getHudFixedHeight())));
        } else {
            // The column ends HudIconGap above the icon, so that gap is the same on every screen.
            column.setTop(Value.of(config.getHudMode().equalsIgnoreCase("margins") ? (int) Math.round(config.getHudTopMargin()) : 0));
            column.setBottom(Value.of(columnBottom(config)));
        }
        ui.setObject("#" + name + "Column.Anchor", column);
        ui.set("#" + name + "Top.FlexWeight", w.top);
        ui.set("#" + name + "Track.FlexWeight", w.bar);
        ui.set("#" + name + "Bottom.FlexWeight", w.bottom);

        // The icon is centred on the bar, in the bottom corner, at a fixed size.
        int inset = (int) Math.round(config.getHudEdgeMargin() + config.getHudBarThickness() / 2.0 - size / 2.0);
        Anchor icon = new Anchor();
        if (leftEdge) {
            icon.setLeft(Value.of(inset));
        } else {
            icon.setRight(Value.of(inset));
        }
        icon.setBottom(Value.of(iconBottom));
        icon.setWidth(Value.of(size));
        icon.setHeight(Value.of(size));
        ui.setObject("#" + name + "Icon.Anchor", icon);
    }

    /** Distance from the bottom of the screen to the bottom of the bar: icon margin + icon size + gap. */
    static int columnBottom(WaterOfArrakisConfig config) {
        return (int) Math.round(config.getHudIconBottomMargin() + config.getHudIconSize() + config.getHudIconGap());
    }

    /** The shares of the column above the bar, the bar, and below it (always 0). */
    record Weights(int top, int bar, int bottom) {
    }

    /**
     * Mode "percent": HudTopPercent of the column above the bar, in tenths of a percent (6.5 gives 65 : 935). Mode
     * "margins": the bar starts HudTopMargin pixels down (the column is anchored there by the .ui through the top weight
     * being 0 and the margin set below). Mode "fixed": the column has its own height, so the weights only hold the bar.
     */
    static Weights weights(WaterOfArrakisConfig config) {
        if (config.getHudMode().equalsIgnoreCase("percent")) {
            int top = (int) Math.round(Math.max(0, Math.min(99, config.getHudTopPercent())) * 10);
            return new Weights(top, SCALE - top, 0);
        }
        return new Weights(0, 1, 0);
    }

    /** The FlexWeights of (empty part, fill part) for a value in percent 0..100. They always add up to SCALE. */
    static int[] split(double percent) {
        double v = Math.max(0.0, Math.min(1.0, percent / 100.0));
        int fill = (int) Math.round(v * SCALE);
        return new int[] {SCALE - fill, fill};
    }

    /** Sends the new fill lengths and the exposure colour, but only what changed by enough to be seen. */
    void refresh(double water, double exposure) {
        double min = config.getHudMinPercentChange();
        boolean waterChanged = changed(sentWater, water, min);
        boolean exposureChanged = changed(sentExposure, exposure, min);
        String color = exposureColor(exposure);
        boolean colorChanged = !color.equals(sentExposureColor);
        if (!waterChanged && !exposureChanged && !colorChanged) {
            return;
        }

        UICommandBuilder ui = new UICommandBuilder();
        if (waterChanged) {
            int[] s = split(water);
            ui.set("#WaterEmpty.FlexWeight", s[0]);
            ui.set("#WaterFill.FlexWeight", s[1]);
            sentWater = water;
        }
        if (exposureChanged) {
            int[] s = split(exposure);
            ui.set("#ExposureEmpty.FlexWeight", s[0]);
            ui.set("#ExposureFill.FlexWeight", s[1]);
            sentExposure = exposure;
        }
        if (colorChanged) {
            ui.set("#ExposureFill.Background.Color", color);
            sentExposureColor = color;
        }
        update(false, ui);
    }

    /** True the first time, when the value moved by at least {@code min} percent, or when it reaches 0 or 100. */
    static boolean changed(double sent, double now, double min) {
        if (Double.isNaN(sent)) {
            return true;
        }
        if (now == sent) {
            return false;
        }
        return Math.abs(now - sent) >= min || now <= 0.0 || now >= 100.0;
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
