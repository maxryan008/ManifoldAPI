package dev.manifold.mass;

import com.google.gson.*;
import dev.manifold.ConstructManager;
import dev.manifold.Manifold;
import dev.manifold.api_implementations.MassAPI;
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
    private static final List<ChangedItem> changedItems = new ArrayList<>();
    private static final double DEFAULT_MASS = 1000.0;
    private static Path savePath;

    public record ChangedItem(Item item, Double oldMass, Double newMass) {}

    public static void init(Path configDir) {
        savePath = configDir.resolve("mass_data.json");
    }

    public static void setMass(Item item, double newMass, boolean isAuto) {
        double oldMass = getMassOrDefault(item);
        if (oldMass != newMass) {
            if (item instanceof BlockItem) {
                changedItems.add(new ChangedItem(item, oldMass, newMass));
            }
        }
        overriddenMasses.put(item, newMass);
        if (isAuto) autoMasses.add(item);
        else autoMasses.remove(item);
    }

    public static void setMass(Item item, double mass) {
        setMass(item, mass, false); // default to manual
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
                            if (!obj.has("mass")) {
                                System.out.println("test");
                            }
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

        recalculateMasses(server);
    }

    public static void recalculateMasses(MinecraftServer server) {
        Registry<Item> itemRegistry = server.registryAccess().registryOrThrow(Registries.ITEM);

        // Build forward deps (ingredient -> results) and map result -> its crafting recipes
        Map<Item, Set<Item>> forwardGraph = new HashMap<>();
        Map<Item, List<CraftingRecipe>> outputRecipes = new HashMap<>();
        for (RecipeHolder<? extends CraftingRecipe> holder : server.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING)) {
            CraftingRecipe recipe = holder.value();
            Item result = recipe.getResultItem(server.registryAccess()).getItem();
            outputRecipes.computeIfAbsent(result, r -> new ArrayList<>()).add(recipe);

            boolean isDye = isDyeRecipe(recipe, result, itemRegistry);
            for (Ingredient ing : recipe.getIngredients()) {
                for (ItemStack stack : ing.getItems()) {
                    Item ingItem = stack.getItem();
                    // Keep your special-case: skip dye edges into stems
                    if (!isDye || !stemItems.contains(result)) {
                        forwardGraph.computeIfAbsent(ingItem, k -> new HashSet<>()).add(result);
                    }
                }
            }
        }

        // Known-mass items (manual overrides or base/MassAPI)
        Set<Item> known = new HashSet<>();
        for (Item i : itemRegistry) {
            if (isOverridden(i) || isBase(i, server)) {
                known.add(i);
            }
        }

        // Auto items are the candidates we will compute
        Set<Item> candidates = new HashSet<>();
        for (Item i : itemRegistry) {
            if (!isOverridden(i) && !isBase(i, server)) {
                candidates.add(i);
                autoMasses.add(i); // keep your flag consistent
            }
        }

        // Helper: does this recipe have all ingredient masses known?
        java.util.function.Predicate<CraftingRecipe> recipeReady = (CraftingRecipe r) -> {
            for (Ingredient ing : r.getIngredients()) {
                if (ing.isEmpty()) continue;
                ItemStack[] options = ing.getItems();
                if (options.length == 0) return false;

                // Choose your “representative” ingredient consistently with your code:
                Optional<Item> stemMatch = Arrays.stream(options)
                        .map(ItemStack::getItem)
                        .filter(stemItems::contains)
                        .findFirst();
                Item chosen = stemMatch.orElse(options[0].getItem());

                if (!known.contains(chosen) && isAuto(chosen)) {
                    // ingredient mass not known yet
                    return false;
                }
            }
            return true;
        };

        // Preferred recipe chooser (same idea as before, but only among ready recipes)
        java.util.function.Function<Item, Optional<CraftingRecipe>> pickRecipe = (Item result) -> {
            List<CraftingRecipe> all = outputRecipes.getOrDefault(result, List.of());
            List<CraftingRecipe> ready = all.stream().filter(recipeReady).toList();
            if (ready.isEmpty()) return Optional.empty();

            // Keep your distance heuristic: prefer “closer to base” ingredients
            // We can approximate by counting how many ingredients are NOT auto (i.e., already known),
            // or reuse your old distance ranking if desired. Here’s a simple ranking to keep it deterministic:
            return ready.stream().min(Comparator.comparingInt(r -> {
                int score = 0;
                for (Ingredient ing : r.getIngredients()) {
                    ItemStack[] options = ing.getItems();
                    if (options.length == 0) continue;
                    Optional<Item> stemMatch = Arrays.stream(options)
                            .map(ItemStack::getItem)
                            .filter(stemItems::contains)
                            .findFirst();
                    Item chosen = stemMatch.orElse(options[0].getItem());
                    // prefer known ingredients
                    if (!known.contains(chosen)) score += 1;
                }
                return score;
            }));
        };

        // Worklist seeded with anything that already has a ready recipe
        Deque<Item> queue = new ArrayDeque<>();
        for (Item item : candidates) {
            if (pickRecipe.apply(item).isPresent()) queue.add(item);
        }

        List<ChangedItem> localChanges = new ArrayList<>();

        while (!queue.isEmpty()) {
            Item item = queue.poll();
            if (known.contains(item)) continue; // might have been set earlier via another path

            Optional<CraftingRecipe> opt = pickRecipe.apply(item);
            if (opt.isEmpty()) continue; // not ready anymore (unlikely), skip

            OptionalDouble massOpt = resolveRecipeMassUsingKnown(opt.get(), server, known);
            if (massOpt.isEmpty()) continue; // guard

            double newMass = massOpt.getAsDouble();
            double oldMass = getMassOrDefault(item);
            if (oldMass != newMass && item instanceof BlockItem) {
                localChanges.add(new ChangedItem(item, oldMass, newMass));
            }
            overriddenMasses.put(item, newMass); // set computed mass
            autoMasses.add(item);
            known.add(item);

            // Now that this is known, some dependents may have become ready
            for (Item out : forwardGraph.getOrDefault(item, Set.of())) {
                if (candidates.contains(out) && !known.contains(out) && pickRecipe.apply(out).isPresent()) {
                    queue.add(out);
                }
            }
        }

        // For any remaining candidates that never became ready (cycles/underdetermined),
        // keep their existing mass if any, otherwise fall back to DEFAULT_MASS just once.
        for (Item item : candidates) {
            if (!known.contains(item)) {
                if (!overriddenMasses.containsKey(item)) {
                    overriddenMasses.put(item, DEFAULT_MASS);
                }
            }
        }

        allChangedItems(localChanges);
    }

    private static OptionalDouble resolveRecipeMassUsingKnown(CraftingRecipe recipe, MinecraftServer server, Set<Item> known) {
        ItemStack resultStack = recipe.getResultItem(server.registryAccess());
        int outputCount = resultStack.getCount();
        if (outputCount <= 0) return OptionalDouble.empty();

        double total = 0.0;

        for (Ingredient ing : recipe.getIngredients()) {
            if (ing.isEmpty()) continue;

            ItemStack[] options = ing.getItems();
            if (options.length == 0) return OptionalDouble.empty();

            // Choose a deterministic representative (prefer stems)
            Optional<Item> stemMatch = Arrays.stream(options)
                    .map(ItemStack::getItem)
                    .filter(stemItems::contains)
                    .findFirst();
            Item chosen = stemMatch.orElse(options[0].getItem());

            // Must be known; caller ensures this via recipeReady()
            if (!known.contains(chosen) && isAuto(chosen)) {
                return OptionalDouble.empty();
            }
            total += getMassOrDefault(chosen);
        }

        if (total == 0.0) return OptionalDouble.empty();
        return OptionalDouble.of(Math.round(total / outputCount));
    }

    public static void recalculateMasses(ServerPlayer response, MinecraftServer server) {
        recalculateMasses(server);
        ServerPlayNetworking.send(response, new MassGuiDataRefreshS2CPacket(MassEntry.collect(server)));
    }

    public static boolean isBase(Item item, MinecraftServer server) {
        if (baseItems.isEmpty()) {
            recalculateBaseItems(server);
        }
        return baseItems.contains(item);
    }

    private static void recalculateBaseItems(MinecraftServer server) {
        baseItems.clear();
        stemItems.clear();

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
                    baseItems.contains(i) || isReachableFromBase(i, forwardGraph)
            );

            if (reachableFromBase) {
                autoMasses.add(scc.stream().toList().getLast());
            } else {
                // Prefer block-like base
                scc.stream().min(Comparator
                        .comparing((Item i) -> (i instanceof BlockItem) ? 0 : 1)
                        .thenComparing(i -> Objects.requireNonNull(itemRegistry.getKey(i)).toString())).ifPresent(baseItems::add);
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
            Manifold.LOGGER.error("No newMass found for recipe " + recipe);
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

    private static boolean isReachableFromBase(Item item, Map<Item, Set<Item>> graph) {
        Set<Item> visited = new HashSet<>();
        Deque<Item> stack = new ArrayDeque<>();
        stack.push(item);

        while (!stack.isEmpty()) {
            Item current = stack.pop();
            if (!visited.add(current)) continue;

            if (MassManager.baseItems.contains(current)) return true;

            for (Item next : graph.getOrDefault(current, Set.of())) {
                stack.push(next);
            }
        }

        return false;
    }

    public static void allChangedItems(List<ChangedItem> changedItems) {
        for (ChangedItem item : changedItems) {
            ConstructManager.INSTANCE.updateConstructCOMS(item);
        }
    }
}