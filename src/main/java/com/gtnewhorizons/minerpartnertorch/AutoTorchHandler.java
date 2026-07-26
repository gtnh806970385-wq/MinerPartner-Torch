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
import net.minecraft.inventory.IInventory;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.Vec3;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
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
    private boolean coverYellowZones = true;
    private boolean avoidMultiblocks = true;
    private Configuration config;

    // F3 debug info
    private String f3DebugInfo = "";

    // Cached reflection check to avoid repeated class lookups
    private static boolean gtReflectionChecked = false;
    private static Class<?> gtTileEntityInterface = null;

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
        coverYellowZones = config.getBoolean("coverYellowZones", Configuration.CATEGORY_GENERAL, true,
            "When true, uses block light only (covers both red and yellow NEI F7 zones). When false, uses effective light (red zones only).");
        avoidMultiblocks = config.getBoolean("avoidMultiblocks", Configuration.CATEGORY_GENERAL, true,
            "When true, avoids placing torches inside GregTech multiblock machine interiors.");
        // Force coverYellowZones to true to ensure yellow zones are always covered
        if (!coverYellowZones) {
            coverYellowZones = true;
            config.get(Configuration.CATEGORY_GENERAL, "coverYellowZones", true).set(true);
        }
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
                String mode = coverYellowZones ? "\u00a7a[\u7ea2+\u9ec4\u53c9]\u00a7f" : "\u00a7c[\u4ec5\u7ea2\u53c9]\u00a7f";
                String msg = enabled ? "\u00a7e[\u77ff\u5de5\u4f19\u4f34]\u00a7f \u706b\u628a\u81ea\u52a8\u63d2\u5165\u5df2\u5f00\u542f " + mode : "\u00a7e[\u77ff\u5de5\u4f19\u4f34]\u00a7f \u706b\u628a\u81ea\u52a8\u63d2\u5165\u5df2\u5173\u95ed";
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
        // Store F3 debug info
        int bl = getBlockLight(world, x, y, z);
        int el = getEffectiveLight(world, x, y, z);
        String xType = (el <= 7) ? "RED" : ((bl <= 7) ? "YEL" : "SAFE");
        f3DebugInfo = String.format("%sMode [%s] Last(%d,%d,%d) BL=%d EL=%d F7=%s",
            enabled ? "ON " : "OFF", coverYellowZones ? "R+Y" : "R  ",
            x, y, z, bl, el, xType);
        placeTorch(mc, player, world, x, y, z);
        lastPlaceX = x; lastPlaceY = y; lastPlaceZ = z;
        lastPlaceValid = true;
        cooldownTimer = placeCooldown;
    }
    /**
     * Gets the effective light (same as NEI F7 red X calculation).
     * Uses getSavedLightValue for direct chunk light array access.
     */
    private int getEffectiveLight(World world, int x, int y, int z) {
        int bl = world.getSavedLightValue(EnumSkyBlock.Block, x, y, z);
        int sl = world.getSavedLightValue(EnumSkyBlock.Sky, x, y, z);
        int sub = world.calculateSkylightSubtracted(1.0F);
        return Math.max(bl, sl - sub);
    }

    /**
     * Returns the pure block light at a position (from chunk light array).
     * This is the same value NEI F7 uses for block light.
     */
    private int getBlockLight(World world, int x, int y, int z) {
        return world.getSavedLightValue(EnumSkyBlock.Block, x, y, z);
    }

    /**
     * Checks if NEI F7 would show a red X or yellow X at this position.
     * 
     * F7 algorithm:
     *   effectiveLight = max(blockLight, skyLight - skylightSubtracted)
     *   RED X:   effectiveLight <= 7
     *   YELLOW X: effectiveLight > 7 && blockLight <= 7
     *   NO X:    blockLight > 7 (fully safe, no mob spawns)
     * 
     * When coverYellowZones=true:  place torch if red OR yellow X shown (blockLight <= 7)
     * When coverYellowZones=false: place torch only if red X shown (effectiveLight <= 7)
     */
    private boolean wouldF7ShowX(World world, int x, int y, int z) {
        int blockLight = getBlockLight(world, x, y, z);
        int effectiveLight = getEffectiveLight(world, x, y, z);

        if (coverYellowZones) {
            // Place torch if F7 would show red X OR yellow X
            // Mathematically: yellow X = (effectiveLight > 7 && blockLight <= 7)
            // red X = (effectiveLight <= 7) which implies blockLight <= 7
            // So combined: blockLight <= 7
            // But we compute explicitly for correctness:
            boolean redX = effectiveLight <= lightThreshold;
            boolean yellowX = effectiveLight > lightThreshold && blockLight <= lightThreshold;
            return redX || yellowX;
        } else {
            // Only red X: effectiveLight <= 7
            return effectiveLight <= lightThreshold;
        }
    }

    /**
     * Checks whether a position is inside a GregTech multiblock machine interior.
     * Uses reflection to detect GT tile entity interfaces without hard dependency.
     */
    private boolean isInsideMultiblock(World world, int x, int y, int z) {
        if (!avoidMultiblocks) return false;

        // Check the block below (where torch would be placed on)
        if (isGtMachineTile(world, x, y - 1, z)) return true;

        // Check the block above the torch position
        if (isGtMachineTile(world, x, y + 1, z)) return true;

        // Check the 4 horizontal neighbors at the torch position level
        if (isGtMachineTile(world, x + 1, y, z)) return true;
        if (isGtMachineTile(world, x - 1, y, z)) return true;
        if (isGtMachineTile(world, x, y, z + 1)) return true;
        if (isGtMachineTile(world, x, y, z - 1)) return true;

        // Also check horizontal neighbors at floor level (y-1) for wider multiblock detection
        if (isGtMachineTile(world, x + 1, y - 1, z)) return true;
        if (isGtMachineTile(world, x - 1, y - 1, z)) return true;
        if (isGtMachineTile(world, x, y - 1, z + 1)) return true;
        if (isGtMachineTile(world, x, y - 1, z - 1)) return true;

        return false;
    }

    /**
     * Checks if the tile entity at a position is a GregTech machine block.
     */
    private static boolean isGtMachineTile(World world, int x, int y, int z) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (te == null) return false;
        return implementsGtInterface(te);
    }

    /**
     * Reflection-based check for GregTech tile entity interfaces.
     * Caches the result of the class lookup for performance.
     */
    private static boolean implementsGtInterface(TileEntity te) {
        if (!gtReflectionChecked) {
            try {
                gtTileEntityInterface = Class.forName("gregtech.api.interfaces.tileentity.IGregTechTileEntity");
            } catch (ClassNotFoundException ignored) {
                // GregTech not loaded, no multiblock avoidance needed
            }
            gtReflectionChecked = true;
        }
        if (gtTileEntityInterface == null) return false;
        return gtTileEntityInterface.isAssignableFrom(te.getClass());
    }
    /**
     * Checks if the block at the given position is a container that would
     * open a GUI on right-click (chest, furnace, machine, etc.).
     */
    private static boolean isContainerBlock(World world, int x, int y, int z) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (te == null) return false;
        return te instanceof IInventory;
    }

    private int[] findDarkestPosition(World world, int px, int py, int pz) {
        List<int[]> candidates = new ArrayList<>();
        for (int dx = -scanRadius; dx <= scanRadius; dx++) {
            for (int dy = -verticalRange; dy <= verticalRange; dy++) {
                for (int dz = -scanRadius; dz <= scanRadius; dz++) {
                    int x = px + dx, y = py + dy, z = pz + dz;
                    if (!wouldF7ShowX(world, x, y, z)) continue;
                    Block below = world.getBlock(x, y - 1, z);
                    Block above = world.getBlock(x, y, z);
                    if (!below.isOpaqueCube()) continue;
                    if (!above.isAir(world, x, y, z) && !above.isReplaceable(world, x, y, z)) continue;
                    if (above.getMaterial().isLiquid()) continue;
                    if (above == Blocks.torch) continue;
                    // Skip positions inside GregTech multiblock machine interiors
                    if (isInsideMultiblock(world, x, y, z)) continue;
                    // Skip positions where the block below is a container (chest/machine)
                    if (isContainerBlock(world, x, y - 1, z)) continue;
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

    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Text event) {
        if (f3DebugInfo.isEmpty()) return;
        // Add to the right-side debug list (F3 screen)
        event.right.add(null); // blank line
        event.right.add("\u00a7e[MinerPartner Torch]\u00a7f");
        event.right.add(f3DebugInfo);
    }
}
