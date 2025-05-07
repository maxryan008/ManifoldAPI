package dev.manifold.mass;

import com.google.gson.*;
import dev.manifold.Manifold;
import dev.manifold.api.MassAPI;
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
    private static final Set<Item> stemItems = new HashSet<>();
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
        return getMass(item).orElse(MassAPI.getDefaultMass(item));
    }

    public static boolean isOverridden(Item item) {
        return overriddenMasses.containsKey(item) || MassAPI.contains(item);
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

        // Ensure parent directory exists
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
        // Load external API-provided default masses
        MassAPI.loadAllApiEntrypoints();

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

        recalculateBaseItems(server);

        for (Item item : itemRegistry) {
            ResourceLocation id = itemRegistry.getKey(item);
            if (!overriddenMasses.containsKey(item) && !isBase(item, server)) {
                autoMasses.add(item);
            }
            if (id != null && id.getNamespace().equals("minecraft") && id.getPath().endsWith("_dye")) {
                setMass(item, 0.0);
            }
        }

        recalculateMasses(Optional.empty(),server);
    }

    public static void recalculateMasses(Optional<ServerPlayer> optionalResponse, MinecraftServer server) {
        Map<Item, Set<Item>> forwardGraph = new HashMap<>();
        Map<Item, List<CraftingRecipe>> outputRecipes = new HashMap<>();
        Registry<Item> itemRegistry = server.registryAccess().registryOrThrow(Registries.ITEM);

        // Build dependency graph and recipe map
        for (RecipeHolder<? extends CraftingRecipe> holder : server.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING)) {
            CraftingRecipe recipe = holder.value();
            Item result = recipe.getResultItem(server.registryAccess()).getItem();
            outputRecipes.computeIfAbsent(result, r -> new ArrayList<>()).add(recipe);

            boolean isDye = isDyeRecipe(recipe, result, itemRegistry);

            for (Ingredient ing : recipe.getIngredients()) {
                for (ItemStack stack : ing.getItems()) {
                    Item ingItem = stack.getItem();

                    // Do NOT add edges from dye recipes if result is a stem item
                    if (!isDye || !stemItems.contains(result)) {
                        forwardGraph.computeIfAbsent(ingItem, k -> new HashSet<>()).add(result);
                    }
                }
            }
        }

        Set<Item> visited = new HashSet<>();
        List<Item> sorted = new ArrayList<>();

        // First pass: DFS from stems only
        for (Item stem : stemItems) {
            dfsTopo(stem, forwardGraph, visited, sorted);
        }

        // Second pass: DFS from all other auto items not yet visited
        for (Item auto : autoMasses) {
            if (!visited.contains(auto)) {
                dfsTopo(auto, forwardGraph, visited, sorted);
            }
        }

        // Precompute distance from each item to nearest base item
        Map<Item, Integer> distanceToBase = new HashMap<>();
        Deque<Item> queue = new ArrayDeque<>();

        for (Item base : baseItems) {
            distanceToBase.put(base, 0);
            queue.add(base);
        }

        while (!queue.isEmpty()) {
            Item current = queue.poll();
            int dist = distanceToBase.get(current);
            for (Item next : forwardGraph.getOrDefault(current, Set.of())) {
                if (!distanceToBase.containsKey(next) || distanceToBase.get(next) > dist + 1) {
                    distanceToBase.put(next, dist + 1);
                    queue.add(next);
                }
            }
        }

        // Sort items again by distance to base, so that closer-to-base items resolve first
        sorted.sort(Comparator.comparingInt(i -> distanceToBase.getOrDefault(i, 1000)));

        // Resolve masses
        for (Item item : sorted) {
            if (!isAuto(item)) continue;

            List<CraftingRecipe> allRecipes = outputRecipes.getOrDefault(item, List.of());

            Optional<CraftingRecipe> preferredRecipe = allRecipes.stream()
                    .min(Comparator.comparingInt(recipe -> {
                        int total = 0;
                        for (Ingredient ing : recipe.getIngredients()) {
                            int ingDist = Arrays.stream(ing.getItems())
                                    .map(stack -> distanceToBase.getOrDefault(stack.getItem(), 1000))
                                    .min(Integer::compareTo)
                                    .orElse(1000);
                            total += ingDist;
                        }
                        return total;
                    }));

            preferredRecipe.map(r -> resolveRecipeMass(r, server)).ifPresent(mass -> overriddenMasses.put(item, mass.orElse(DEFAULT_MASS)));
        }

        optionalResponse.ifPresent(serverPlayer ->
                ServerPlayNetworking.send(serverPlayer, new MassGuiDataRefreshS2CPacket(MassEntry.collect(server)))
        );
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
        Registry<Item> itemRegistry = server.registryAccess().registryOrThrow(Registries.ITEM);

        // Build reverse graph: ingredients -> results
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

        // Kosaraju’s algorithm — first pass
        Set<Item> visited = new HashSet<>();
        List<Item> order = new ArrayList<>();
        for (Item item : reverseGraph.keySet()) {
            dfs1(item, visited, reverseGraph, order);
        }

        // Transpose the graph
        Map<Item, Set<Item>> forwardGraph = new HashMap<>();
        for (Map.Entry<Item, Set<Item>> entry : reverseGraph.entrySet()) {
            for (Item dst : entry.getValue()) {
                forwardGraph.computeIfAbsent(dst, k -> new HashSet<>()).add(entry.getKey());
            }
        }

        visited.clear();
        Collections.reverse(order);

        // SCC detection and stem selection: items with both dye and non-dye recipes
        Set<Item> sccVisited = new HashSet<>();
        for (Item item : order) {
            if (sccVisited.contains(item)) continue;

            // Visit SCC from this root node
            Set<Item> scc = new HashSet<>();
            dfs2(item, new HashSet<>(), forwardGraph, scc); // local visited!

            sccVisited.addAll(scc); // mark all items in the SCC as globally visited

            boolean reachableFromBase = scc.stream().anyMatch(i ->
                    baseItems.contains(i) || isReachableFromBase(i, forwardGraph, baseItems)
            );

            if (reachableFromBase) {
                autoMasses.add(scc.stream().toList().getLast());
            } else {
                // Prefer block-like base
                Item base = scc.stream()
                        .sorted(Comparator
                                .comparing((Item i) -> (i instanceof BlockItem) ? 0 : 1)
                                .thenComparing(i -> itemRegistry.getKey(i).toString()))
                        .findFirst()
                        .orElse(null);
                if (base != null) baseItems.add(base);
            }
        }

        for (Item item : itemRegistry) {
            List<CraftingRecipe> recipes = manager.getAllRecipesFor(RecipeType.CRAFTING).stream()
                    .map(RecipeHolder::value)
                    .filter(r -> r.getResultItem(server.registryAccess()).getItem().equals(item))
                    .toList();

            boolean hasDye = recipes.stream().anyMatch(r -> isDyeRecipe(r, item, itemRegistry));
            boolean hasNonDye = recipes.stream().anyMatch(r -> !isDyeRecipe(r, item, itemRegistry));

            if (hasDye && hasNonDye) {
                stemItems.add(item);
            }
        }

        // Items not craftable at all are base items (leaf nodes)
        Set<Item> craftable = manager.getAllRecipesFor(RecipeType.CRAFTING).stream()
                .map(r -> r.value().getResultItem(server.registryAccess()).getItem())
                .collect(Collectors.toSet());

        for (Item item : itemRegistry) {
            if (!craftable.contains(item)) {
                baseItems.add(item);
            }
        }

        for (Item item : baseItems) {
            autoMasses.remove(item);
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

            // Try to match a stem item first
            Optional<Item> stemMatch = Arrays.stream(stacks)
                    .map(ItemStack::getItem)
                    .filter(stemItems::contains)
                    .findFirst();

            Item ingItem = stemMatch.orElse(stacks[0].getItem()); // fallback to first if no stem match

            total += getMassOrDefault(ingItem);
        }

        if (total == 0) {
            Manifold.LOGGER.error("No mass found for recipe " + recipe);
            return OptionalDouble.empty();
        }

        return OptionalDouble.of(Math.round(total / outputCount));
    }

    private static boolean isDyeRecipe(CraftingRecipe recipe, Item resultItem, Registry<Item> itemRegistry) {
        boolean containsDye = false;

        for (Ingredient ing : recipe.getIngredients()) {
            for (ItemStack stack : ing.getItems()) {
                Item ingItem = stack.getItem();
                ResourceLocation id = itemRegistry.getKey(ingItem);

                if (id != null && id.getPath().endsWith("_dye")) {
                    containsDye = true;
                }
            }
        }

        return containsDye;
    }

    private static boolean hasNonDyeRecipe(Item item, MinecraftServer server) {
        Registry<Item> itemRegistry = server.registryAccess().registryOrThrow(Registries.ITEM);
        List<CraftingRecipe> recipes = server.getRecipeManager()
                .getAllRecipesFor(RecipeType.CRAFTING).stream()
                .map(RecipeHolder::value)
                .filter(r -> r.getResultItem(server.registryAccess()).getItem().equals(item))
                .toList();

        return recipes.stream().anyMatch(r -> !isDyeRecipe(r, item, itemRegistry));
    }

    private static boolean isOnlyDyedVariant(Item item, MinecraftServer server) {
        Registry<Item> itemRegistry = server.registryAccess().registryOrThrow(Registries.ITEM);
        List<CraftingRecipe> recipes = server.getRecipeManager()
                .getAllRecipesFor(RecipeType.CRAFTING).stream()
                .map(RecipeHolder::value)
                .filter(r -> r.getResultItem(server.registryAccess()).getItem().equals(item))
                .toList();

        return !recipes.isEmpty() && recipes.stream().allMatch(r -> isDyeRecipe(r, item, itemRegistry));
    }

    private static boolean isReachableFromBase(Item item, Map<Item, Set<Item>> graph, Set<Item> baseItems) {
        Set<Item> visited = new HashSet<>();
        Deque<Item> stack = new ArrayDeque<>();
        stack.push(item);

        while (!stack.isEmpty()) {
            Item current = stack.pop();
            if (!visited.add(current)) continue;

            if (baseItems.contains(current)) return true;

            for (Item next : graph.getOrDefault(current, Set.of())) {
                stack.push(next);
            }
        }

        return false;
    }
}