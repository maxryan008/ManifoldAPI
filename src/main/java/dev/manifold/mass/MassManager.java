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
        return hasManualOverride(item) || MassAPI.contains(item);
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
        file.getParentFile().mkdirs();// Creates config/manifold if needed

        try (Writer writer = new FileWriter(file)) {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            gson.toJson(root, writer);
        } catch (IOException e) {
            Manifold.LOGGER.error("Couldn't save mass data to {}", file.getAbsolutePath(), e);
        }
    }

    private static boolean hasManualOverride(Item item) {
        return overriddenMasses.containsKey(item) && !autoMasses.contains(item);
    }

    private static boolean hasAutoComputedMass(Item item) {
        return overriddenMasses.containsKey(item) && autoMasses.contains(item);
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
                Manifold.LOGGER.error("Failed to load Mass Data", e);
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
                    // keep special-case: skip dye edges into stems
                    if (!isDye || !stemItems.contains(result)) {
                        forwardGraph.computeIfAbsent(ingItem, k -> new HashSet<>()).add(result);
                    }
                }
            }
        }

        // Known items: MANUAL overrides, MassAPI-provided, or base items
        Set<Item> known = new HashSet<>();
        for (Item i : itemRegistry) {
            if (hasManualOverride(i) || MassAPI.contains(i) || isBase(i, server)) {
                known.add(i);
            }
        }

        // Candidates: anything NOT manual-override, NOT MassAPI, NOT base
        Set<Item> candidates = new HashSet<>();
        for (Item i : itemRegistry) {
            if (!hasManualOverride(i) && !MassAPI.contains(i) && !isBase(i, server)) {
                candidates.add(i);
                autoMasses.add(i); // flag as auto-computed domain
            }
        }

        // Recipe readiness: all chosen ingredients must be known
        java.util.function.Predicate<CraftingRecipe> recipeReady = (CraftingRecipe r) -> {
            for (Ingredient ing : r.getIngredients()) {
                if (ing.isEmpty()) continue;
                Item choice = chooseIngredientItem(ing);
                if (choice == null) return false;
                if (!known.contains(choice) && isAuto(choice)) return false;
            }
            return true;
        };

        // Preferred recipe among ready ones (min unknown-score)
        java.util.function.Function<Item, Optional<CraftingRecipe>> pickRecipe = (Item result) -> {
            List<CraftingRecipe> all = outputRecipes.getOrDefault(result, List.of());
            List<CraftingRecipe> ready = all.stream().filter(recipeReady).toList();
            if (ready.isEmpty()) return Optional.empty();

            return ready.stream().min(Comparator.comparingInt(r -> {
                int score = 0;
                for (Ingredient ing : r.getIngredients()) {
                    Item choice = chooseIngredientItem(ing);
                    if (choice == null) continue;
                    if (!known.contains(choice)) score += 1; // prefer more-known inputs
                }
                return score;
            }));
        };

        // Initial worklist
        Deque<Item> queue = new ArrayDeque<>();
        for (Item item : candidates) {
            if (pickRecipe.apply(item).isPresent()) queue.add(item);
        }

        List<ChangedItem> localChanges = new ArrayList<>();

        // Worklist propagation (no defaults mid-pass)
        while (!queue.isEmpty()) {
            Item item = queue.poll();
            if (known.contains(item)) continue;

            Optional<CraftingRecipe> opt = pickRecipe.apply(item);
            if (opt.isEmpty()) continue;

            OptionalDouble massOpt = resolveRecipeMassUsingKnown(opt.get(), server, known);
            if (massOpt.isEmpty()) continue;

            double newMass = massOpt.getAsDouble();
            double oldMass = getMassOrDefault(item);
            if (oldMass != newMass && item instanceof BlockItem) {
                localChanges.add(new ChangedItem(item, oldMass, newMass));
            }
            overriddenMasses.put(item, newMass);
            // base items should never be auto
            if (baseItems.contains(item)) autoMasses.remove(item); else autoMasses.add(item);
            known.add(item);

            for (Item out : forwardGraph.getOrDefault(item, Set.of())) {
                if (candidates.contains(out) && !known.contains(out) && pickRecipe.apply(out).isPresent()) {
                    queue.add(out);
                }
            }
        }

        // Solve non-trivial cycles (SCCs) only; leave singletons to the worklist
        Set<Item> unresolved = candidates.stream().filter(i -> !known.contains(i)).collect(Collectors.toSet());
        if (!unresolved.isEmpty()) {
            List<Set<Item>> sccs = stronglyConnectedComponents(unresolved, forwardGraph);

            // 1) Solve true cycles
            for (Set<Item> scc : sccs) {
                if (scc.size() <= 1) continue; // skip singletons here
                boolean hasRecipe = scc.stream().anyMatch(i -> !outputRecipes.getOrDefault(i, List.of()).isEmpty());
                if (!hasRecipe) continue;
                solveSccMasses(scc, outputRecipes, known, server);
            }

            // 2) Re-run worklist to consume newly known cycle outputs (e.g., spyglass after copper)
            Deque<Item> q2 = new ArrayDeque<>();
            for (Item item : candidates) {
                if (!known.contains(item) && pickRecipe.apply(item).isPresent()) {
                    q2.add(item);
                }
            }
            while (!q2.isEmpty()) {
                Item item = q2.poll();
                if (known.contains(item)) continue;

                Optional<CraftingRecipe> opt = pickRecipe.apply(item);
                if (opt.isEmpty()) continue;

                OptionalDouble massOpt = resolveRecipeMassUsingKnown(opt.get(), server, known);
                if (massOpt.isEmpty()) continue;

                double newMass = massOpt.getAsDouble();
                double oldMass = getMassOrDefault(item);
                if (oldMass != newMass && item instanceof BlockItem) {
                    localChanges.add(new ChangedItem(item, oldMass, newMass));
                }
                overriddenMasses.put(item, newMass);
                if (baseItems.contains(item)) autoMasses.remove(item); else autoMasses.add(item);
                known.add(item);

                for (Item out : forwardGraph.getOrDefault(item, Set.of())) {
                    if (candidates.contains(out) && !known.contains(out) && pickRecipe.apply(out).isPresent()) {
                        q2.add(out);
                    }
                }
            }
        }

        // Final guard: anything still unknown gets a default once
        for (Item item : candidates) {
            if (!known.contains(item) && !overriddenMasses.containsKey(item)) {
                overriddenMasses.put(item, DEFAULT_MASS);
            }
        }

        // Ensure base items are never flagged auto
        for (Item b : baseItems) {
            autoMasses.remove(b);
        }

        allChangedItems(localChanges);
    }

    private static Item pickPivot(Set<Item> vars, MinecraftServer server) {
        Registry<Item> itemRegistry = server.registryAccess().registryOrThrow(Registries.ITEM);

        List<String> preferredSuffixes = List.of("_block", "_ingot", "_nugget", "_gem");

        return vars.stream()
                .min(Comparator
                        .comparingInt(i -> {
                            ResourceLocation id = itemRegistry.getKey((Item) i);
                            if (id == null) return Integer.MAX_VALUE;
                            String path = id.getPath();
                            for (int rank = 0; rank < preferredSuffixes.size(); rank++) {
                                if (path.endsWith(preferredSuffixes.get(rank))) return rank;
                            }
                            return preferredSuffixes.size();
                        })
                        .thenComparing(i -> {
                            ResourceLocation id = itemRegistry.getKey((Item) i);
                            return id != null ? id.toString() : "";
                        })
                )
                .orElseGet(() -> vars.iterator().next());
    }

    private static List<Set<Item>> stronglyConnectedComponents(Set<Item> nodes, Map<Item, Set<Item>> graph) {
        // Tarjan's algorithm
        Map<Item, Integer> index = new HashMap<>();
        Map<Item, Integer> low = new HashMap<>();
        Deque<Item> stack = new ArrayDeque<>();
        Set<Item> onStack = new HashSet<>();
        List<Set<Item>> sccs = new ArrayList<>();
        int[] time = {0};

        class Dfs {
            void run(Item v) {
                index.put(v, time[0]);
                low.put(v, time[0]);
                time[0]++;
                stack.push(v);
                onStack.add(v);

                for (Item w : graph.getOrDefault(v, Set.of())) {
                    if (!nodes.contains(w)) continue;
                    if (!index.containsKey(w)) {
                        run(w);
                        low.put(v, Math.min(low.get(v), low.get(w)));
                    } else if (onStack.contains(w)) {
                        low.put(v, Math.min(low.get(v), index.get(w)));
                    }
                }

                if (low.get(v).equals(index.get(v))) {
                    Set<Item> comp = new HashSet<>();
                    Item x;
                    do {
                        x = stack.pop();
                        onStack.remove(x);
                        comp.add(x);
                    } while (x != v);
                    sccs.add(comp);
                }
            }
        }
        Dfs dfs = new Dfs();
        for (Item v : nodes) {
            if (!index.containsKey(v)) dfs.run(v);
        }
        return sccs;
    }

    private static Item chooseIngredientItem(Ingredient ing) {
        ItemStack[] options = ing.getItems();
        if (options.length == 0) return null;
        Optional<Item> stemMatch = Arrays.stream(options)
                .map(ItemStack::getItem)
                .filter(stemItems::contains)
                .findFirst();
        return stemMatch.orElse(options[0].getItem());
    }

    private static void solveSccMasses(
            Set<Item> scc,
            Map<Item, List<CraftingRecipe>> outputRecipes,
            Set<Item> known,
            MinecraftServer server
    ) {
        // variables to solve
        List<Item> vars = new ArrayList<>(scc);
        Map<Item, Double> x = new HashMap<>();
        for (Item i : vars) x.put(i, overriddenMasses.getOrDefault(i, MassManager.DEFAULT_MASS));

        // Does SCC have any external known anchor?
        boolean hasExternalAnchor = false;
        for (Item r : vars) {
            for (CraftingRecipe recipe : outputRecipes.getOrDefault(r, List.of())) {
                boolean usesKnown = false;
                for (Ingredient ing : recipe.getIngredients()) {
                    if (ing.isEmpty()) continue;
                    Item choice = chooseIngredientItem(ing);
                    if (choice == null) continue;
                    if (!scc.contains(choice) && (isOverridden(choice) || !autoMasses.contains(choice) || known.contains(choice))) {
                        usesKnown = true; break;
                    }
                }
                if (usesKnown) { hasExternalAnchor = true; break; }
            }
            if (hasExternalAnchor) break;
        }

        // Gauss–Seidel iteration
        final int MAX_ITERS = 200;
        final double EPS = 1e-6;
        for (int it = 0; it < MAX_ITERS; it++) {
            double maxDelta = 0.0;

            for (Item r : vars) {
                List<CraftingRecipe> recs = outputRecipes.getOrDefault(r, List.of());
                if (recs.isEmpty()) continue;

                List<Double> candidates = new ArrayList<>();
                for (CraftingRecipe recipe : recs) {
                    ItemStack out = recipe.getResultItem(server.registryAccess());
                    int outCount = Math.max(1, out.getCount());

                    double total = 0.0;
                    boolean valid = true;
                    for (Ingredient ing : recipe.getIngredients()) {
                        if (ing.isEmpty()) continue;
                        Item choice = chooseIngredientItem(ing);
                        if (choice == null) { valid = false; break; }

                        if (scc.contains(choice)) {
                            Double mv = x.get(choice);
                            if (mv == null) { valid = false; break; }
                            total += mv;
                        } else {
                            total += getMassOrDefault(choice);
                        }
                    }
                    if (valid) candidates.add(total / outCount);
                }

                if (!candidates.isEmpty()) {
                    double newV = Collections.min(candidates); // keep ratios; no rounding inside loop
                    double oldV = x.get(r);
                    x.put(r, newV);
                    maxDelta = Math.max(maxDelta, Math.abs(newV - oldV));
                }
            }
            if (maxDelta < EPS) break;
        }

        // If there was no external anchor, scale SCC so pivot matches defaultMassAnchor
        if (!hasExternalAnchor) {
            Item pivot = pickPivot(scc, server);
            double pv = Math.max(1e-9, x.getOrDefault(pivot, MassManager.DEFAULT_MASS));
            double scale = MassManager.DEFAULT_MASS / pv;
            for (Item i : vars) {
                x.put(i, x.get(i) * scale);
            }
        }

        // Commit (rounding only at commit time for stability)
        for (Item i : vars) {
            double newMass = Math.round(x.get(i));
            double oldMass = getMassOrDefault(i);

            if (oldMass != newMass && i instanceof BlockItem) {
                changedItems.add(new ChangedItem(i, oldMass, newMass));
            }
            overriddenMasses.put(i, newMass);

            // IMPORTANT: if item is base, it's not auto
            if (baseItems.contains(i)) {
                autoMasses.remove(i);
            } else {
                autoMasses.add(i);
            }

            known.add(i);
        }
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

        // Build reverse graph: ingredient -> results
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

        // SCC detection and stem selection
        Set<Item> sccVisited = new HashSet<>();
        for (Item item : order) {
            if (sccVisited.contains(item)) continue;

            Set<Item> scc = new HashSet<>();
            dfs2(item, new HashSet<>(), forwardGraph, scc);
            sccVisited.addAll(scc);

            boolean reachableFromBase = scc.stream().anyMatch(i ->
                    baseItems.contains(i) || isReachableFromBase(i, forwardGraph)
            );

            if (reachableFromBase) {
                // treat as auto
                autoMasses.add(scc.stream().toList().getLast());
            } else {
                // this SCC is a base
                scc.stream().min(
                        Comparator
                                .comparing((Item i) -> (i instanceof BlockItem) ? 0 : 1)
                                .thenComparing(i -> Objects.requireNonNull(itemRegistry.getKey(i)).toString())
                ).ifPresent(baseItems::add);

            }
        }

        // Detect stems: items with both dye and non-dye recipes
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

        // Items not craftable at all are base items (true leaves)
        Set<Item> craftable = manager.getAllRecipesFor(RecipeType.CRAFTING).stream()
                .map(r -> r.value().getResultItem(server.registryAccess()).getItem())
                .collect(Collectors.toSet());

        for (Item item : itemRegistry) {
            if (!craftable.contains(item)) {
                baseItems.add(item);
            }
        }

        // Ensure consistency: no autoMasses flagged as base
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