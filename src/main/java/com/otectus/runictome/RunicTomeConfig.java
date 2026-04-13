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

            b.push("debug");
            verboseLogging = b.comment("Extra logging for capability sync and adapter resolution.")
                    .define("verboseLogging", false);
            b.pop();
        }
    }
}
