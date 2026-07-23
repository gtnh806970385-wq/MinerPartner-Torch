package com.gtnewhorizons.minerpartnertorch;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.Vec3;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;
import org.lwjgl.input.Keyboard;

@SideOnly(Side.CLIENT)
public class AutoTorchHandler {

    public static final AutoTorchHandler INSTANCE = new AutoTorchHandler();
    private boolean enabled = true;
    private boolean keyDebounce = false;
    private int lightThreshold = 7;
    private int scanRadius = 5;
    private int verticalRange = 2;
    private int placeCooldown = 20;
    private int cooldownTimer = 0;
    private int torchSlot = -1;
    private int lastPlaceX, lastPlaceY, lastPlaceZ;
    private boolean lastPlaceValid = false;
    private Configuration config;
    private AutoTorchHandler() {}
    public static void init(File configFile) {
        INSTANCE.config = new Configuration(configFile);
        INSTANCE.loadConfig();
        MinecraftForge.EVENT_BUS.register(INSTANCE);
        cpw.mods.fml.common.FMLCommonHandler.instance().bus().register(INSTANCE);
    }
    private void loadConfig() {
        config.load();
        enabled = config.getBoolean("enabled", Configuration.CATEGORY_GENERAL, true, "Enable auto torch placement");
        lightThreshold = config.getInt("lightThreshold", Configuration.CATEGORY_GENERAL, 7, 0, 15, "Light level threshold");
        scanRadius = config.getInt("scanRadius", Configuration.CATEGORY_GENERAL, 5, 2, 10, "Horizontal scan radius");
        verticalRange = config.getInt("verticalRange", Configuration.CATEGORY_GENERAL, 2, 1, 5, "Vertical scan range");
        placeCooldown = config.getInt("placeCooldown", Configuration.CATEGORY_GENERAL, 20, 5, 100, "Cooldown ticks");
        if (config.hasChanged()) config.save();
    }
    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null) return;
        if (Keyboard.isKeyDown(Keyboard.KEY_Y)) {
            if (!keyDebounce) {
                keyDebounce = true;
                enabled = !enabled;
                String msg = enabled ? "\u00a7e[\u77ff\u5de5\u4f19\u4f34]\u00a7f \u706b\u628a\u81ea\u52a8\u63d2\u5165\u5df2\u5f00\u542f" : "\u00a7e[\u77ff\u5de5\u4f19\u4f34]\u00a7f \u706b\u628a\u81ea\u52a8\u63d2\u5165\u5df2\u5173\u95ed";
                mc.thePlayer.addChatMessage(new ChatComponentText(msg));
                config.get(Configuration.CATEGORY_GENERAL, "enabled", true).set(enabled);
                config.save();
            }
        } else { keyDebounce = false; }
        if (!enabled) return;
        if (cooldownTimer > 0) { cooldownTimer--; return; }
        if (mc.currentScreen != null) return;
        doAutoTorch(mc);
    }
    private void doAutoTorch(Minecraft mc) {
        EntityPlayer player = mc.thePlayer;
        World world = mc.theWorld;
        torchSlot = findTorchInHotbar(player);
        if (torchSlot == -1) return;
        int[] bestPos = findDarkestPosition(world, (int)player.posX, (int)player.posY, (int)player.posZ);
        if (bestPos == null) return;
        int x = bestPos[0], y = bestPos[1], z = bestPos[2];
        if (lastPlaceValid && lastPlaceX == x && lastPlaceY == y && lastPlaceZ == z) return;
        placeTorch(mc, player, world, x, y, z);
        lastPlaceX = x; lastPlaceY = y; lastPlaceZ = z;
        lastPlaceValid = true;
        cooldownTimer = placeCooldown;
    }
    private int getEffectiveLight(World world, int x, int y, int z) {
        int bl = world.getBlockLightValue(x, y, z);
        int sl = world.getSkyBlockTypeBrightness(EnumSkyBlock.Sky, x, y, z);
        int sub = world.calculateSkylightSubtracted(1.0F);
        return Math.max(bl, sl - sub);
    }
    private int[] findDarkestPosition(World world, int px, int py, int pz) {
        List<int[]> candidates = new ArrayList<>();
        for (int dx = -scanRadius; dx <= scanRadius; dx++) {
            for (int dy = -verticalRange; dy <= verticalRange; dy++) {
                for (int dz = -scanRadius; dz <= scanRadius; dz++) {
                    int x = px + dx, y = py + dy, z = pz + dz;
                    if (getEffectiveLight(world, x, y, z) > lightThreshold) continue;
                    Block below = world.getBlock(x, y - 1, z);
                    Block above = world.getBlock(x, y, z);
                    if (!below.isOpaqueCube()) continue;
                    if (!above.isAir(world, x, y, z) && !above.isReplaceable(world, x, y, z)) continue;
                    if (above.getMaterial().isLiquid()) continue;
                    if (above == Blocks.torch) continue;
                    candidates.add(new int[]{x, y, z});
                }
            }
        }
        if (candidates.isEmpty()) return null;
        int[] best = null;
        double bestDist = Double.MAX_VALUE;
        for (int[] pos : candidates) {
            double d = (pos[0]-px)*(pos[0]-px)+(pos[1]-py)*(pos[1]-py)+(pos[2]-pz)*(pos[2]-pz);
            if (d < bestDist) { bestDist = d; best = pos; }
        }
        return best;
    }
    private int findTorchInHotbar(EntityPlayer player) {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = player.inventory.getStackInSlot(i);
            if (stack != null && stack.getItem() == Item.getItemFromBlock(Blocks.torch)) return i;
        }
        return -1;
    }
    private void placeTorch(Minecraft mc, EntityPlayer player, World world, int x, int y, int z) {
        int prevSlot = player.inventory.currentItem;
        player.inventory.currentItem = torchSlot;
        ItemStack torchStack = player.inventory.getCurrentItem();
        if (torchStack == null) { player.inventory.currentItem = prevSlot; return; }
        mc.playerController.onPlayerRightClick(player, world, torchStack, x, y - 1, z, 1, Vec3.createVectorHelper(x + 0.5, y, z + 0.5));
        if (!player.capabilities.isCreativeMode) {
            ItemStack updated = player.inventory.getCurrentItem();
            if (updated != null && updated.stackSize <= 0) player.inventory.setInventorySlotContents(torchSlot, null);
        }
        player.inventory.currentItem = prevSlot;
    }
}
