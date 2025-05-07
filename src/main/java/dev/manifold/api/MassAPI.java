package dev.manifold.api;

import dev.manifold.mass.MassManager;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.HashMap;
import java.util.Map;

public class MassAPI implements MassApiEntrypoint {
    private static final Map<Item, Double> defaultMasses = new HashMap<>();

    public static void registerDefaultMass(Item item, double mass) {
        defaultMasses.put(item, mass);
    }

    public static double getDefaultMass(Item item) {
        return defaultMasses.getOrDefault(item, 1000.0);
    }

    public static void loadAllApiEntrypoints() {
        FabricLoader.getInstance()
                .getEntrypoints("manifold:mass", MassApiEntrypoint.class)
                .forEach(entry -> entry.registerMasses(MassAPI::registerDefaultMass));
    }

    public static boolean contains(Item item) {
        return defaultMasses.containsKey(item);
    }

    @Override
    public void registerMasses(MassRegistry registry) { //todo Register all vanilla blocks
        // --- LEAVES ---
        double leavesMass = 25;
        registry.register(Items.ACACIA_LEAVES,leavesMass);
        registry.register(Items.AZALEA_LEAVES,leavesMass);
        registry.register(Items.BIRCH_LEAVES,leavesMass);
        registry.register(Items.CHERRY_LEAVES,leavesMass);
        registry.register(Items.DARK_OAK_LEAVES,leavesMass);
        registry.register(Items.FLOWERING_AZALEA_LEAVES,leavesMass);
        registry.register(Items.JUNGLE_LEAVES,leavesMass);
        registry.register(Items.MANGROVE_LEAVES,leavesMass);
        registry.register(Items.OAK_LEAVES,leavesMass);
        registry.register(Items.SPRUCE_LEAVES,leavesMass);

        // --- LOGS ---
        double logsMass = 200;
        registry.register(Items.ACACIA_LOG,logsMass);
        registry.register(Items.BIRCH_LOG,logsMass);
        registry.register(Items.CHERRY_LOG,logsMass);
        registry.register(Items.DARK_OAK_LOG,logsMass);
        registry.register(Items.JUNGLE_LOG,logsMass);
        registry.register(Items.MANGROVE_LOG,logsMass);
        registry.register(Items.SPRUCE_LOG,logsMass);
        registry.register(Items.STRIPPED_ACACIA_LOG,logsMass);
        registry.register(Items.STRIPPED_BIRCH_LOG,logsMass);
        registry.register(Items.STRIPPED_CHERRY_LOG,logsMass);
        registry.register(Items.STRIPPED_DARK_OAK_LOG,logsMass);
        registry.register(Items.STRIPPED_JUNGLE_LOG,logsMass);
        registry.register(Items.STRIPPED_MANGROVE_LOG,logsMass);
        registry.register(Items.STRIPPED_OAK_LOG,logsMass);
        registry.register(Items.STRIPPED_SPRUCE_LOG,logsMass);

        // --- SAPLINGS ---
        double saplingsMass = 25;
        registry.register(Items.ACACIA_SAPLING,saplingsMass);
        registry.register(Items.BIRCH_SAPLING,saplingsMass);
        registry.register(Items.CHERRY_SAPLING,saplingsMass);
        registry.register(Items.DARK_OAK_SAPLING,saplingsMass);
        registry.register(Items.JUNGLE_SAPLING,saplingsMass);
        registry.register(Items.OAK_SAPLING,saplingsMass);
        registry.register(Items.SPRUCE_SAPLING,saplingsMass);

        // --- ORES ---
        registry.register(Items.REDSTONE,50);
        registry.register(Items.GLOWSTONE,50);
        registry.register(Items.COAL_ORE,1700);
        registry.register(Items.COAL_BLOCK,900);
        registry.register(Items.CHARCOAL,100);
        registry.register(Items.COPPER_ORE,1700);

        // --- STONES ---
        registry.register(Items.STONE,1600);
        registry.register(Items.COBBLESTONE,1600);
        registry.register(Items.SMOOTH_STONE,1600);
        registry.register(Items.CRACKED_STONE_BRICKS,1600);
        registry.register(Items.BLACKSTONE,1800);
        registry.register(Items.CRACKED_POLISHED_BLACKSTONE_BRICKS,1800);
        registry.register(Items.END_STONE,200);
        registry.register(Items.GILDED_BLACKSTONE,3800);

        // --- DIRT'S ---
        registry.register(Items.DIRT,1250);
        registry.register(Items.DIRT_PATH,1100);
        registry.register(Items.ROOTED_DIRT,900);
        registry.register(Items.SAND,1650);
        registry.register(Items.RED_SAND,1650);
        registry.register(Items.SMOOTH_SANDSTONE,6600);
        registry.register(Items.SMOOTH_RED_SANDSTONE,6600);
        registry.register(Items.SOUL_SAND,600);
        registry.register(Items.SUSPICIOUS_SAND,1700);
        registry.register(Items.GRAVEL,1500);
        registry.register(Items.SUSPICIOUS_GRAVEL,1550);

    }
}
