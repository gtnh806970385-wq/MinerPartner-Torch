package com.gtnewhorizons.minerpartnertorch;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

@Mod(
    modid = AutoTorchMod.MODID,
    version = AutoTorchMod.VERSION,
    name = "MinerPartner Torch",
    acceptedMinecraftVersions = "[1.7.10]"
)
public class AutoTorchMod {

    public static final String MODID = "minerpartnertorch";
    public static final String VERSION = "1.0.0";

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        AutoTorchHandler.init(event.getSuggestedConfigurationFile());
    }
}
