package dev.manifold.mass;

import com.google.gson.*;
import dev.manifold.Manifold;
import dev.manifold.network.packets.MassGuiDataRefreshS2CPacket;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;

import java.io.*;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class MassManager {
    private static final Map<Item, Double> overriddenMasses = new HashMap<>();
    private static final Set<Item> baseItems = new HashSet<>();
    private static final Set<Item> autoMasses = new HashSet<>();
    private static final double DEFAULT_MASS = 1000.0;
    private static Path savePath;

    public static void init(Path configDir) {
        savePath = configDir.resolve("mass_data.json");
    }

    public static void setMass(Item item, double mass, boolean isAuto) {
        overriddenMasses.put(item, mass);
        if (isAuto) autoMasses.add(item);
        else autoMasses.remove(item);
    }

    public static void setMass(Item item, double mass) {
        setMass(item, mass, false); // default to manual
    }

    public static void unsetMass(Item item) {
        overriddenMasses.remove(item);
        autoMasses.remove(item);
    }

    public static boolean isAuto(Item item) {
        return autoMasses.contains(item);
    }

    public static OptionalDouble getMass(Item item) {
        if (overriddenMasses.containsKey(item)) {
            return OptionalDouble.of(overriddenMasses.get(item));
        }
        return OptionalDouble.empty();
    }

    public static double getMassOrDefault(Item item) {
        return getMass(item).orElse(DEFAULT_MASS);
    }

    public static boolean isOverridden(Item item) {
        return overriddenMasses.containsKey(item);
    }

    public static void save(MinecraftServer server) {
        JsonObject root = new JsonObject();
        Registry<Item> itemRegistry = server.registryAccess().registryOrThrow(Registries.ITEM);

        for (Map.Entry<Item, Double> entry : overriddenMasses.entrySet()) {
            ResourceLocation id = itemRegistry.getKey(entry.getKey());
            if (id != null) {
                JsonObject obj = new JsonObject();
                obj.addProperty("mass", entry.getValue());
                if (autoMasses.contains(entry.getKey())) {
                    obj.addProperty("auto", true);
                }
                root.add(id.toString(), obj);
            }
        }

        // ✅ Ensure parent directory exists
        File file = savePath.toFile();
        file.getParentFile().mkdirs(); // Creates config/manifold if needed

        try (Writer writer = new FileWriter(file)) {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            gson.toJson(root, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void load(MinecraftServer server) {
        overriddenMasses.clear();
        autoMasses.clear();

        Registry<Item> itemRegistry = server.registryAccess().registryOrThrow(Registries.ITEM);

        if (savePath.toFile().exists()) {
            try (Reader reader = new FileReader(savePath.toFile())) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
                    ResourceLocation id = ResourceLocation.tryParse(entry.getKey());
                    if (id != null && itemRegistry.containsKey(id)) {
                        Item item = itemRegistry.get(id);
                        JsonElement val = entry.getValue();
                        if (val.isJsonObject()) {
                            JsonObject obj = val.getAsJsonObject();
                            double mass = obj.get("mass").getAsDouble();
                            boolean isAuto = obj.has("auto") && obj.get("auto").getAsBoolean();
                            setMass(item, mass, isAuto);
                        } else {
                            double mass = val.getAsDouble();
                            setMass(item, mass, false); // legacy support
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        for (Item item : itemRegistry) {
            if (!overriddenMasses.containsKey(item) && !isBase(item, server)) {
                autoMasses.add(item);
            }
        }

        recalculateMasses(Optional.empty(),server);
    }

    public static void recalculateMasses(Optional<ServerPlayer> optionalResponse, MinecraftServer server) {
        Map<Item, Set<Item>> forwardGraph = new HashMap<>();
        Map<Item, List<CraftingRecipe>> outputRecipes = new HashMap<>();

        // Build dependency graph and recipe map
        for (RecipeHolder<? extends CraftingRecipe> holder : server.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING)) {
            CraftingRecipe recipe = holder.value();
            Item result = recipe.getResultItem(server.registryAccess()).getItem();
            outputRecipes.computeIfAbsent(result, r -> new ArrayList<>()).add(recipe);

            for (Ingredient ing : recipe.getIngredients()) {
                for (ItemStack stack : ing.getItems()) {
                    Item ingItem = stack.getItem();
                    forwardGraph.computeIfAbsent(ingItem, k -> new HashSet<>()).add(result);
                }
            }
        }

        // Perform topological sort
        Set<Item> visited = new HashSet<>();
        List<Item> sorted = new ArrayList<>();
        for (Item base : baseItems) {
            dfsTopo(base, forwardGraph, visited, sorted);
        }

        Collections.reverse(sorted);

        // Traverse items in topological order and compute auto masses
        for (Item item : sorted) {
            if (!isAuto(item)) continue;

            List<CraftingRecipe> recipes = outputRecipes.getOrDefault(item, List.of());
            for (CraftingRecipe recipe : recipes) {
                OptionalDouble result = resolveRecipeMass(recipe, server);
                if (result.isPresent()) {
                    overriddenMasses.put(item, result.getAsDouble());
                    break;
                }
            }
        }

        optionalResponse.ifPresent(serverPlayer -> ServerPlayNetworking.send(serverPlayer, new MassGuiDataRefreshS2CPacket(MassEntry.collect(server))));
    }

    public static boolean isBase(Item item, MinecraftServer server) {
        if (baseItems.isEmpty()) {
            recalculateBaseItems(server);
        }
        return baseItems.contains(item);
    }

    private static void recalculateBaseItems(MinecraftServer server) {
        baseItems.clear();

        Map<Item, Set<Item>> reverseGraph = new HashMap<>();
        RecipeManager manager = server.getRecipeManager();

        for (RecipeHolder<? extends CraftingRecipe> holder : manager.getAllRecipesFor(RecipeType.CRAFTING)) {
            CraftingRecipe recipe = holder.value();
            Item result = recipe.getResultItem(server.registryAccess()).getItem();

            for (Ingredient ing : recipe.getIngredients()) {
                for (ItemStack stack : ing.getItems()) {
                    Item ingItem = stack.getItem();
                    reverseGraph.computeIfAbsent(ingItem, k -> new HashSet<>()).add(result);
                }
            }
        }

        // Kosaraju’s algorithm to find strongly connected components (SCCs)
        Set<Item> visited = new HashSet<>();
        List<Item> order = new ArrayList<>();
        for (Item item : reverseGraph.keySet()) {
            dfs1(item, visited, reverseGraph, order);
        }

        Map<Item, Set<Item>> forwardGraph = new HashMap<>();
        for (Map.Entry<Item, Set<Item>> entry : reverseGraph.entrySet()) {
            for (Item dst : entry.getValue()) {
                forwardGraph.computeIfAbsent(dst, k -> new HashSet<>()).add(entry.getKey());
            }
        }

        visited.clear();
        Collections.reverse(order);
        for (Item item : order) {
            if (!visited.contains(item)) {
                Set<Item> scc = new HashSet<>();
                dfs2(item, visited, forwardGraph, scc);

                if (scc.size() > 1) {
                    // Loop found: pick preferred base (prefer blocks)
                    Item base = scc.stream()
                            .sorted(Comparator.comparing(i -> (i instanceof BlockItem) ? 0 : 1))
                            .findFirst()
                            .orElse(null);
                    if (base != null) baseItems.add(base);
                }
            }
        }

        // Add items that aren’t used in any crafting recipe (leaf nodes)
        Set<Item> craftable = manager.getAllRecipesFor(RecipeType.CRAFTING).stream()
                .map(r -> r.value().getResultItem(server.registryAccess()).getItem())
                .collect(Collectors.toSet());

        Registry<Item> itemRegistry = server.registryAccess().registryOrThrow(Registries.ITEM);
        for (Item item : itemRegistry) {
            if (!craftable.contains(item)) {
                baseItems.add(item);
            }
        }
    }

    private static void dfs1(Item item, Set<Item> visited, Map<Item, Set<Item>> graph, List<Item> order) {
        if (visited.add(item)) {
            for (Item next : graph.getOrDefault(item, Collections.emptySet())) {
                dfs1(next, visited, graph, order);
            }
            order.add(item);
        }
    }

    private static void dfs2(Item item, Set<Item> visited, Map<Item, Set<Item>> graph, Set<Item> component) {
        if (visited.add(item)) {
            component.add(item);
            for (Item next : graph.getOrDefault(item, Collections.emptySet())) {
                dfs2(next, visited, graph, component);
            }
        }
    }

    private static void dfsTopo(Item item, Map<Item, Set<Item>> graph, Set<Item> visited, List<Item> sorted) {
        if (!visited.add(item)) return;
        for (Item next : graph.getOrDefault(item, Set.of())) {
            dfsTopo(next, graph, visited, sorted);
        }
        sorted.add(item);
    }

    private static OptionalDouble resolveRecipeMass(CraftingRecipe recipe, MinecraftServer server) {
        ItemStack resultStack = recipe.getResultItem(server.registryAccess());
        int outputCount = resultStack.getCount();
        if (outputCount <= 0) return OptionalDouble.empty();

        double total = 0;
        for (Ingredient ing : recipe.getIngredients()) {
            if (ing.isEmpty()) continue;

            ItemStack[] stacks = ing.getItems();
            if (stacks.length == 0) return OptionalDouble.empty();

            Item ingItem = stacks[0].getItem();
            if (!isOverridden(ingItem) && !isAuto(ingItem)) return OptionalDouble.empty();

            total += getMassOrDefault(ingItem);
        }

        if (total == 0) {
            Manifold.LOGGER.error("No mass found for recipe " + recipe);
            return OptionalDouble.empty();
        }

        return OptionalDouble.of(Math.round(total / outputCount));
    }
}