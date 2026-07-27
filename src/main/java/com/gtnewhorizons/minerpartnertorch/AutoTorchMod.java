package com.gtnewhorizons.minerpartnertorch;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import net.minecraft.client.settings.KeyBinding;
import org.lwjgl.input.Keyboard;

@Mod(
    modid = AutoTorchMod.MODID,
    version = AutoTorchMod.VERSION,
    name = "MinerPartner Torch",
    acceptedMinecraftVersions = "[1.7.10]"
)
public class AutoTorchMod {

    public static final String MODID = "minerpartnertorch";
    public static final String VERSION = "1.1.0";
    public static KeyBinding toggleKey;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        toggleKey = new KeyBinding("key.minerpartnertorch.toggle", Keyboard.KEY_Y, "key.categories.minerpartnertorch");
        ClientRegistry.registerKeyBinding(toggleKey);
        AutoTorchHandler.init(event.getSuggestedConfigurationFile());
    }
}
