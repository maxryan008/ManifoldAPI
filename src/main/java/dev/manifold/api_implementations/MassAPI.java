package dev.manifold.api_implementations;

import dev.manifold.api.MassApiEntrypoint;
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
        registry.register(Items.WARPED_WART_BLOCK, 1300);

        // --- LOGS ---
        double logsMass = 200;
        registry.register(Items.ACACIA_LOG,logsMass);
        registry.register(Items.BIRCH_LOG,logsMass);
        registry.register(Items.CHERRY_LOG,logsMass);
        registry.register(Items.DARK_OAK_LOG,logsMass);
        registry.register(Items.OAK_LOG,logsMass);
        registry.register(Items.JUNGLE_LOG,logsMass);
        registry.register(Items.MANGROVE_LOG,logsMass);
        registry.register(Items.SPRUCE_LOG,logsMass);
        registry.register(Items.CRIMSON_STEM,logsMass);
        registry.register(Items.WARPED_STEM,logsMass);
        registry.register(Items.STRIPPED_ACACIA_LOG,logsMass);
        registry.register(Items.STRIPPED_BIRCH_LOG,logsMass);
        registry.register(Items.STRIPPED_CHERRY_LOG,logsMass);
        registry.register(Items.STRIPPED_DARK_OAK_LOG,logsMass);
        registry.register(Items.STRIPPED_JUNGLE_LOG,logsMass);
        registry.register(Items.STRIPPED_MANGROVE_LOG,logsMass);
        registry.register(Items.STRIPPED_OAK_LOG,logsMass);
        registry.register(Items.STRIPPED_SPRUCE_LOG,logsMass);
        registry.register(Items.STRIPPED_CRIMSON_STEM,logsMass);
        registry.register(Items.STRIPPED_WARPED_STEM,logsMass);

        // --- SAPLINGS ---
        double saplingsMass = 25;
        registry.register(Items.ACACIA_SAPLING,saplingsMass);
        registry.register(Items.BIRCH_SAPLING,saplingsMass);
        registry.register(Items.CHERRY_SAPLING,saplingsMass);
        registry.register(Items.DARK_OAK_SAPLING,saplingsMass);
        registry.register(Items.JUNGLE_SAPLING,saplingsMass);
        registry.register(Items.OAK_SAPLING,saplingsMass);
        registry.register(Items.SPRUCE_SAPLING,saplingsMass);

        // --- MUSHROOMS ---
        double mushroomMass = 15;
        registry.register(Items.CRIMSON_FUNGUS,mushroomMass);
        registry.register(Items.WARPED_FUNGUS,mushroomMass);
        registry.register(Items.BROWN_MUSHROOM,mushroomMass);
        registry.register(Items.RED_MUSHROOM,mushroomMass);
        registry.register(Items.RED_MUSHROOM_BLOCK,mushroomMass*4);
        registry.register(Items.MUSHROOM_STEM, mushroomMass*2);
        registry.register(Items.BROWN_MUSHROOM_BLOCK, mushroomMass*4);

        // --- PLANTS ---
        registry.register(Items.CRIMSON_ROOTS, 10);
        registry.register(Items.WARPED_ROOTS, 10);
        registry.register(Items.NETHER_WART, 144);
        registry.register(Items.HANGING_ROOTS, 10);
        registry.register(Items.ALLIUM, 10);
        registry.register(Items.AZURE_BLUET, 10);
        registry.register(Items.BAMBOO, 12.5);
        registry.register(Items.LILY_PAD, 10);
        registry.register(Items.BLUE_ORCHID, 10);
        registry.register(Items.CACTUS, 50);
        registry.register(Items.CHORUS_FLOWER, 25);
        registry.register(Items.CHORUS_PLANT, 25);
        registry.register(Items.CORNFLOWER, 10);
        registry.register(Items.DANDELION, 10);
        registry.register(Items.DEAD_BUSH, 10);
        registry.register(Items.FERN, 10);
        registry.register(Items.FLOWERING_AZALEA, 15);
        registry.register(Items.GLOW_LICHEN, 10);
        registry.register(Items.KELP, 10);
        registry.register(Items.LARGE_FERN, 10);
        registry.register(Items.LILAC, 10);
        registry.register(Items.LILY_OF_THE_VALLEY, 10);
        registry.register(Items.LILY_PAD, 10);
        registry.register(Items.MANGROVE_PROPAGULE, 10);
        registry.register(Items.NETHER_SPROUTS, 10);
        registry.register(Items.ORANGE_TULIP, 10);
        registry.register(Items.OXEYE_DAISY, 10);
        registry.register(Items.PEONY, 10);
        registry.register(Items.PINK_PETALS, 10);
        registry.register(Items.PINK_TULIP, 10);
        registry.register(Items.PITCHER_PLANT, 10);
        registry.register(Items.POPPY, 10);
        registry.register(Items.RED_TULIP, 10);
        registry.register(Items.SEA_PICKLE, 10);
        registry.register(Items.SEAGRASS, 10);
        registry.register(Items.SHORT_GRASS, 10);
        registry.register(Items.SMALL_DRIPLEAF, 10);
        registry.register(Items.SPORE_BLOSSOM, 10);
        registry.register(Items.SUGAR_CANE, 20);
        registry.register(Items.SUNFLOWER, 10);
        registry.register(Items.TALL_GRASS, 10);
        registry.register(Items.TORCHFLOWER, 10);
        registry.register(Items.TWISTING_VINES, 15);
        registry.register(Items.VINE, 15);
        registry.register(Items.WEEPING_VINES, 15);
        registry.register(Items.WHITE_TULIP, 10);
        registry.register(Items.WITHER_ROSE, 10);
        registry.register(Items.GLOW_BERRIES, 10);

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
        registry.register(Items.NETHERRACK, 1200);

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
        registry.register(Items.CRIMSON_NYLIUM,1200);
        registry.register(Items.WARPED_NYLIUM,1200);

        // --- MISC ---
        registry.register(Items.BEDROCK, 1);
        registry.register(Items.BARRIER, 1);
        registry.register(Items.COMMAND_BLOCK, 1);
        registry.register(Items.CHAIN_COMMAND_BLOCK, 1);
        registry.register(Items.REPEATING_COMMAND_BLOCK, 1);
        registry.register(Items.FIREWORK_STAR, 10);
        registry.register(Items.FIREWORK_ROCKET, 10);

        // --- MOB SPAWN EGGS ---
        registry.register(Items.ALLAY_SPAWN_EGG, 1);
        registry.register(Items.ARMADILLO_SPAWN_EGG, 1);
        registry.register(Items.AXOLOTL_SPAWN_EGG, 1);
        registry.register(Items.BAT_SPAWN_EGG, 1);
        registry.register(Items.BEE_SPAWN_EGG, 1);
        registry.register(Items.BLAZE_SPAWN_EGG, 1);
        registry.register(Items.BOGGED_SPAWN_EGG, 1);
        registry.register(Items.BREEZE_SPAWN_EGG, 1);
        registry.register(Items.CAMEL_SPAWN_EGG, 1);
        registry.register(Items.CAT_SPAWN_EGG, 1);
        registry.register(Items.CAVE_SPIDER_SPAWN_EGG, 1);
        registry.register(Items.CHICKEN_SPAWN_EGG, 1);
        registry.register(Items.COD_SPAWN_EGG, 1);
        registry.register(Items.COW_SPAWN_EGG, 1);
        registry.register(Items.CREEPER_SPAWN_EGG, 1);
        registry.register(Items.DOLPHIN_SPAWN_EGG, 1);
        registry.register(Items.DONKEY_SPAWN_EGG, 1);
        registry.register(Items.DROWNED_SPAWN_EGG, 1);
        registry.register(Items.ELDER_GUARDIAN_SPAWN_EGG, 1);
        registry.register(Items.ENDER_DRAGON_SPAWN_EGG, 1);
        registry.register(Items.ENDERMAN_SPAWN_EGG, 1);
        registry.register(Items.ENDERMITE_SPAWN_EGG, 1);
        registry.register(Items.EVOKER_SPAWN_EGG, 1);
        registry.register(Items.FOX_SPAWN_EGG, 1);
        registry.register(Items.FROG_SPAWN_EGG, 1);
        registry.register(Items.GHAST_SPAWN_EGG, 1);
        registry.register(Items.GLOW_SQUID_SPAWN_EGG, 1);
        registry.register(Items.GOAT_SPAWN_EGG, 1);
        registry.register(Items.GUARDIAN_SPAWN_EGG, 1);
        registry.register(Items.HOGLIN_SPAWN_EGG, 1);
        registry.register(Items.HORSE_SPAWN_EGG, 1);
        registry.register(Items.HUSK_SPAWN_EGG, 1);
        registry.register(Items.IRON_GOLEM_SPAWN_EGG, 1);
        registry.register(Items.LLAMA_SPAWN_EGG, 1);
        registry.register(Items.MAGMA_CUBE_SPAWN_EGG, 1);
        registry.register(Items.MOOSHROOM_SPAWN_EGG, 1);
        registry.register(Items.MULE_SPAWN_EGG, 1);
        registry.register(Items.OCELOT_SPAWN_EGG, 1);
        registry.register(Items.PANDA_SPAWN_EGG, 1);
        registry.register(Items.PARROT_SPAWN_EGG, 1);
        registry.register(Items.PHANTOM_SPAWN_EGG, 1);
        registry.register(Items.PIG_SPAWN_EGG, 1);
        registry.register(Items.PIGLIN_BRUTE_SPAWN_EGG, 1);
        registry.register(Items.PIGLIN_SPAWN_EGG, 1);
        registry.register(Items.PILLAGER_SPAWN_EGG, 1);
        registry.register(Items.POLAR_BEAR_SPAWN_EGG, 1);
        registry.register(Items.PUFFERFISH_SPAWN_EGG, 1);
        registry.register(Items.RABBIT_SPAWN_EGG, 1);
        registry.register(Items.RAVAGER_SPAWN_EGG, 1);
        registry.register(Items.SALMON_SPAWN_EGG, 1);
        registry.register(Items.SHEEP_SPAWN_EGG, 1);
        registry.register(Items.SHULKER_SPAWN_EGG, 1);
        registry.register(Items.SILVERFISH_SPAWN_EGG, 1);
        registry.register(Items.SKELETON_HORSE_SPAWN_EGG, 1);
        registry.register(Items.SKELETON_SPAWN_EGG, 1);
        registry.register(Items.SLIME_SPAWN_EGG, 1);
        registry.register(Items.SNIFFER_SPAWN_EGG, 1);
        registry.register(Items.SNOW_GOLEM_SPAWN_EGG, 1);
        registry.register(Items.SPIDER_SPAWN_EGG, 1);
        registry.register(Items.SQUID_SPAWN_EGG, 1);
        registry.register(Items.STRAY_SPAWN_EGG, 1);
        registry.register(Items.STRIDER_SPAWN_EGG, 1);
        registry.register(Items.TADPOLE_SPAWN_EGG, 1);
        registry.register(Items.TRADER_LLAMA_SPAWN_EGG, 1);
        registry.register(Items.TROPICAL_FISH_SPAWN_EGG, 1);
        registry.register(Items.TURTLE_SPAWN_EGG, 1);
        registry.register(Items.VEX_SPAWN_EGG, 1);
        registry.register(Items.VILLAGER_SPAWN_EGG, 1);
        registry.register(Items.VINDICATOR_SPAWN_EGG, 1);
        registry.register(Items.WANDERING_TRADER_SPAWN_EGG, 1);
        registry.register(Items.WARDEN_SPAWN_EGG, 1);
        registry.register(Items.WITCH_SPAWN_EGG, 1);
        registry.register(Items.WITHER_SKELETON_SPAWN_EGG, 1);
        registry.register(Items.WITHER_SPAWN_EGG, 1);
        registry.register(Items.WOLF_SPAWN_EGG, 1);
        registry.register(Items.ZOGLIN_SPAWN_EGG, 1);
        registry.register(Items.ZOMBIE_HORSE_SPAWN_EGG, 1);
        registry.register(Items.ZOMBIE_SPAWN_EGG, 1);
        registry.register(Items.ZOMBIE_VILLAGER_SPAWN_EGG, 1);
        registry.register(Items.ZOMBIFIED_PIGLIN_SPAWN_EGG, 1);
        registry.register(Items.TRADER_LLAMA_SPAWN_EGG, 1);
        registry.register(Items.TRADER_LLAMA_SPAWN_EGG, 1);
        registry.register(Items.TRADER_LLAMA_SPAWN_EGG, 1);
    }
}
