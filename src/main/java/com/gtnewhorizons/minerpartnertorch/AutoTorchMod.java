package com.gtnewhorizons.minerpartnertorch;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.EventHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.relauncher.Side;

@Mod(
    modid = AutoTorchMod.MODID,
    name = AutoTorchMod.NAME,
    version = AutoTorchMod.VERSION,
    acceptableRemoteVersions = "*"
)
public class AutoTorchMod {

    public static final String MODID = "minerpartnertorch";
    public static final String NAME = "MinerPartner Torch";
    public static final String VERSION = "1.2.2";

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        // 初始化配置
        AutoTorchHandler.INSTANCE.init(event.getModConfigurationDirectory());
        // 客户端快捷键注册
        if (event.getSide() == Side.CLIENT) {
            ClientProxy.init();
        }
    }

    @EventHandler
    public void init(FMLInitializationEvent event) {
        // 注册事件
        AutoTorchHandler.INSTANCE.registerEvents();
    }
}
