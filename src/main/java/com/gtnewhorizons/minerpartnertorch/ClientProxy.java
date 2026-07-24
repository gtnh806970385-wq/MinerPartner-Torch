package com.gtnewhorizons.minerpartnertorch;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.settings.KeyBinding;
import org.lwjgl.input.Keyboard;

@SideOnly(Side.CLIENT)
public class ClientProxy {

    /** 快捷键：默认 Y 键，可在"选项-控制"中修改 */
    public static final KeyBinding toggleKey = new KeyBinding(
        "自动插火把",
        Keyboard.KEY_Y,
        "矿工伙伴"
    );

    public static void init() {
        ClientRegistry.registerKeyBinding(toggleKey);
    }
}
