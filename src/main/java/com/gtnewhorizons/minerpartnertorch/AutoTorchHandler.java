package com.gtnewhorizons.minerpartnertorch;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Vec3;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;

@SideOnly(Side.CLIENT)
public class AutoTorchHandler {

    public static final AutoTorchHandler INSTANCE = new AutoTorchHandler();
    private static final org.apache.logging.log4j.Logger LOG = LogManager.getLogger("AutoTorch");

    private boolean enabled = true;
    private int lightThreshold = 7;
    private int scanRadius = 5;
    private int verticalRange = 2;
    private int placeCooldown = 20;
    private boolean includeYellowSpawns = true;
    private String[] blockBlacklist = new String[0];
    private Configuration config;
    private int cooldownTimer = 0;
    private int torchSlot = -1;
    private boolean lastPlaceValid = false;
    private int lastPlaceX, lastPlaceY, lastPlaceZ;

    private AutoTorchHandler() {}

    public void init(File configDir) {
        LOG.info("AutoTorch v1.2.3 initializing...");
        config = new Configuration(new File(configDir, "minerpartnertorch.cfg"));
        loadConfig();
        LOG.info("AutoTorch initialized, enabled={}, threshold={}, radius={}", enabled, lightThreshold, scanRadius);
    }

    public void registerEvents() {
        MinecraftForge.EVENT_BUS.register(this);
        FMLCommonHandler.instance().bus().register(this);
        LOG.info("AutoTorch events registered");
    }

    private void loadConfig() {
        config.load();
        enabled = config.getBoolean("enabled", "general", true, "enable");
        lightThreshold = config.getInt("lightThreshold", "general", 7, 0, 15, "light threshold");
        scanRadius = config.getInt("scanRadius", "general", 5, 2, 10, "scan radius");
        verticalRange = config.getInt("verticalRange", "general", 2, 1, 5, "vertical range");
        placeCooldown = config.getInt("placeCooldown", "general", 20, 5, 100, "cooldown ticks");
        includeYellowSpawns = config.getBoolean("includeYellowSpawns", "general", true, "place at yellow");
        String def = "minecraft:glass,minecraft:stained_glass,minecraft:ice," +
            "minecraft:glowstone,gregtech:gt.blockglass1,gregtech:gt.blocktintedglass," +
            "gregtech:gt.blockcasings1,gregtech:gt.blockcasings2,gregtech:gt.blockcasings3," +
            "gregtech:gt.blockcasings4,gregtech:gt.blockcasings5,gregtech:gt.blockcasings6," +
            "gregtech:gt.blockcasings7,gregtech:gt.blockcasings8,gregtech:gt.blockcasings9," +
            "gregtech:gt.blockcasings10,gregtech:gt.blockcasings11,gregtech:gt.blockcasings12," +
            "gregtech:gt.blockcasings13,gregtech:gt.blockcasings14,gregtech:gt.blockcasingsnh," +
            "gregtech:gt.blockreinforced,gregtech:gt.blockstorage,gregtech:gt.blockmetal," +
            "gregtech:gt.blockframe,gregtech:gt.blockconcretes";
        String raw = config.getString("blockBlacklist", "general", def, "block blacklist");
        blockBlacklist = raw.isEmpty() ? new String[0] : raw.split(",");
        if (config.hasChanged()) config.save();
    }

    private boolean isBlacklistedBlock(Block block) {
        if (blockBlacklist.length == 0 || block == null) return false;
        String id = Block.blockRegistry.getNameForObject(block);
        if (id == null) return false;
        for (String s : blockBlacklist) {
            if (s != null && id.equals(s.trim())) return true;
        }
        return false;
    }

    private int getEffectiveLight(World w, int x, int y, int z) {
        if (includeYellowSpawns && w.canBlockSeeTheSky(x, y, z)) {
            return w.getBlockLightValue(x, y, z);
        }
        int bl = w.getBlockLightValue(x, y, z);
        int sl = w.getSkyBlockTypeBrightness(EnumSkyBlock.Sky, x, y, z);
        int sub = w.calculateSkylightSubtracted(1.0F);
        return Math.max(bl, sl - sub);
    }

    private boolean shouldPlaceTorchAt(World w, int x, int y, int z) {
        Block below = w.getBlock(x, y - 1, z);
        if (below == null || below.isAir(w, x, y - 1, z)) return false;
        if (!below.canCreatureSpawn(EnumCreatureType.monster, w, x, y - 1, z)) return false;
        if (isBlacklistedBlock(below)) return false;
        Block above = w.getBlock(x, y, z);
        if (!above.isAir(w, x, y, z) && !above.isReplaceable(w, x, y, z)) return false;
        if (above.getMaterial().isLiquid()) return false;
        if (above == Blocks.torch || above == Blocks.redstone_torch) return false;
        BiomeGenBase biome = w.getBiomeGenForCoords(x, z);
        if (biome.getSpawnableList(EnumCreatureType.monster).isEmpty()) return false;
        if (biome.getSpawningChance() <= 0.0f) return false;
        return getEffectiveLight(w, x, y, z) <= lightThreshold;
    }

    private int[] findDarkestPosition(World w, int cx, int cy, int cz) {
        List<int[]> candidates = new ArrayList<int[]>();
        for (int dx = -scanRadius; dx <= scanRadius; dx++) {
            for (int dy = -verticalRange; dy <= verticalRange; dy++) {
                for (int dz = -scanRadius; dz <= scanRadius; dz++) {
                    int x = cx + dx, y = cy + dy, z = cz + dz;
                    if (shouldPlaceTorchAt(w, x, y, z)) {
                        candidates.add(new int[]{x, y, z});
                    }
                }
            }
        }
        if (candidates.isEmpty()) return null;
        int[] best = null;
        double bestDist = Double.MAX_VALUE;
        for (int[] p : candidates) {
            double d = (p[0]-cx)*(p[0]-cx) + (p[1]-cy)*(p[1]-cy) + (p[2]-cz)*(p[2]-cz);
            if (d < bestDist) { bestDist = d; best = p; }
        }
        return best;
    }

    private int findTorchInHotbar(EntityPlayer player) {
        for (int i = 0; i < 9; i++) {
            ItemStack s = player.inventory.getStackInSlot(i);
            if (s != null && s.getItem() != null && Block.getBlockFromItem(s.getItem()) == Blocks.torch) {
                return i;
            }
        }
        return -1;
    }

    private void placeTorch(Minecraft mc, EntityPlayer player, World w, int x, int y, int z) {
        int prev = player.inventory.currentItem;
        player.inventory.currentItem = torchSlot;
        ItemStack s = player.inventory.getCurrentItem();
        if (s == null) { player.inventory.currentItem = prev; return; }
        Vec3 hit = Vec3.createVectorHelper(x + 0.5, y + 0.5, z + 0.5);
        boolean placed = mc.playerController.onPlayerRightClick(player, w, s, x, y - 1, z, 1, hit);
        if (!placed) {
            placed = mc.playerController.onPlayerRightClick(player, w, s, x, y, z, 1, hit);
        }
        if (!placed) {
            LOG.debug("placeTorch: Failed at ({},{},{})", x, y, z);
        }
        if (!player.capabilities.isCreativeMode) {
            ItemStack a = player.inventory.getCurrentItem();
            if (a != null && a.stackSize <= 0) player.inventory.setInventorySlotContents(torchSlot, null);
        }
        player.inventory.currentItem = prev;
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null || mc.isGamePaused()) return;
        if (ClientProxy.toggleKey.isPressed()) {
            enabled = !enabled;
            String status = enabled ? "\u00a7a\u5df2\u5f00\u542f" : "\u00a7c\u5df2\u5173\u95ed";
            mc.thePlayer.addChatMessage(new net.minecraft.util.ChatComponentText("\u00a7b[\u81ea\u52a8\u63d2\u706b\u628a] " + status));
            LOG.info("Toggle: enabled={}", enabled);
        }
        if (!enabled) return;
        if (cooldownTimer > 0) { cooldownTimer--; return; }
        torchSlot = findTorchInHotbar(mc.thePlayer);
        if (torchSlot == -1) {
            LOG.trace("No torch in hotbar");
            return;
        }
        int px = (int) Math.floor(mc.thePlayer.posX);
        int py = (int) Math.floor(mc.thePlayer.posY) - 1;
        int pz = (int) Math.floor(mc.thePlayer.posZ);
        int[] pos = findDarkestPosition(mc.theWorld, px, py, pz);
        if (pos == null) {
            lastPlaceValid = false;
            LOG.trace("No valid position found");
            return;
        }
        int x = pos[0], y = pos[1], z = pos[2];
        LOG.debug("Placing torch at ({},{},{})", x, y, z);
        if (lastPlaceValid && x == lastPlaceX && y == lastPlaceY && z == lastPlaceZ) {
            placeTorch(mc, mc.thePlayer, mc.theWorld, x, y, z);
            cooldownTimer = placeCooldown;
            return;
        }
        placeTorch(mc, mc.thePlayer, mc.theWorld, x, y, z);
        lastPlaceValid = true;
        lastPlaceX = x; lastPlaceY = y; lastPlaceZ = z;
        cooldownTimer = placeCooldown;
    }
}
