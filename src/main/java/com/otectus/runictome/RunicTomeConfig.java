package com.otectus.runictome;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

public final class RunicTomeConfig {

    public static final ForgeConfigSpec COMMON_SPEC;
    public static final Common COMMON;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        COMMON = new Common(b);
        COMMON_SPEC = b.build();
    }

    private RunicTomeConfig() {}

    public static final class Common {
        public final ForgeConfigSpec.BooleanValue absorbOnPickup;
        public final ForgeConfigSpec.BooleanValue absorbOnCraft;
        public final ForgeConfigSpec.BooleanValue verboseLogging;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> extraBookItemIds;
        public final ForgeConfigSpec.BooleanValue showUnlockToast;
        public final ForgeConfigSpec.IntValue sweepIntervalTicks;
        public final ForgeConfigSpec.BooleanValue grantTomeOnFirstJoin;

        Common(ForgeConfigSpec.Builder b) {
            b.push("absorption");
            absorbOnPickup = b.comment("Absorb guide books when picked up from the ground.")
                    .define("absorbOnPickup", true);
            absorbOnCraft = b.comment("Absorb guide books produced by crafting or smelting.")
                    .define("absorbOnCraft", true);
            b.pop();

            b.push("integrations");
            extraBookItemIds = b.comment(
                    "Extra item IDs to treat as single-item guide books (item is absorbed and tracked).",
                    "Use \"modid:item_path\" form. Unknown or malformed IDs are logged and skipped.",
                    "Example: [\"immersiveengineering:manual\", \"occultism:dictionary_of_spirits\"]")
                    .defineListAllowEmpty("extraBookItemIds", List.of(),
                            o -> o instanceof String s && s.contains(":"));
            b.pop();

            b.push("ui");
            showUnlockToast = b.comment("Show a toast notification when a new book is absorbed.")
                    .define("showUnlockToast", true);
            b.pop();

            b.push("performance");
            sweepIntervalTicks = b.comment(
                    "How often (in server ticks) to sweep player inventories for unabsorbed books.",
                    "Pickup, craft, and container-close events are handled separately and are unaffected.",
                    "20 = once per second. Set to 1 to restore legacy per-tick behavior.")
                    .defineInRange("sweepIntervalTicks", 20, 1, 1200);
            b.pop();

            b.push("items");
            grantTomeOnFirstJoin = b.comment(
                    "Give a Runic Tome to each player on their first login.",
                    "Disable this on servers that distribute the tome via quests or starting kits.")
                    .define("grantTomeOnFirstJoin", true);
            b.pop();

            b.push("debug");
            verboseLogging = b.comment("Extra logging for capability sync and adapter resolution.")
                    .define("verboseLogging", false);
            b.pop();
        }
    }
}
