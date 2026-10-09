package com.chaosthedude.explorerscompass.unlocking;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.chaosthedude.explorerscompass.util.VisitedStructures;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.advancement.Advancement;
import net.minecraft.advancement.AdvancementFrame;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.structure.StructureSet;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

/** Server-owned rule definitions. Reloading replaces definitions without touching player data. */
public final class UnlockingRules {
    private static final Logger LOGGER = LogManager.getLogger("explorerscompass");
    private static UnlockingRules active = new UnlockingRules();
    private boolean defaultUnlocked = true;
    private final Map<String, Rule> rules = new LinkedHashMap<>();
    private final Map<Identifier, List<Rule>> byStructure = new LinkedHashMap<>();

    private record Condition(Predicate<Progress> test, Function<Progress, RequirementNode> display,
                             Set<Identifier> visits, Set<Identifier> advancements) {}
    private record Rule(Set<Identifier> targets, Condition condition, String description) {}

    public static UnlockingRules get() { return active; }

    interface Catalog {
        Set<Identifier> structures();
        Set<Identifier> structureSet(Identifier id);
        boolean advancementExists(Identifier id);
        Text advancementTitle(Identifier id);
        Text advancementDescription(Identifier id);
        Formatting advancementColor(Identifier id);
    }

    interface Progress {
        boolean visited(Identifier id);
        boolean completed(Identifier id);
    }

    private static Catalog catalog(MinecraftServer server) {
        return new Catalog() {
            public Set<Identifier> structures() { return server.getRegistryManager().get(RegistryKeys.STRUCTURE).getIds(); }
            public Set<Identifier> structureSet(Identifier id) { return setMembers(server, id); }
            public boolean advancementExists(Identifier id) { return server.getAdvancementLoader().get(id) != null; }
            public Text advancementTitle(Identifier id) {
                Advancement advancement = server.getAdvancementLoader().get(id);
                return advancement != null && advancement.getDisplay() != null ? advancement.getDisplay().getTitle() : Text.literal(readable(id));
            }
            public Text advancementDescription(Identifier id) {
                Advancement advancement = server.getAdvancementLoader().get(id);
                return advancement != null && advancement.getDisplay() != null ? advancement.getDisplay().getDescription() : null;
            }
            public Formatting advancementColor(Identifier id) {
                Advancement advancement = server.getAdvancementLoader().get(id);
                AdvancementFrame frame = advancement != null && advancement.getDisplay() != null ? advancement.getDisplay().getFrame() : AdvancementFrame.TASK;
                return frame.getTitleFormat();
            }
        };
    }

    private static Progress progress(ServerPlayerEntity player) {
        return new Progress() {
            public boolean visited(Identifier id) { return VisitedStructures.get(player.getServer()).hasVisitedType(player.getUuid(), id); }
            public boolean completed(Identifier id) {
                Advancement advancement = player.getServer().getAdvancementLoader().get(id);
                return advancement != null && player.getAdvancementTracker().getProgress(advancement).isDone();
            }
        };
    }

    public static void clear() { active = new UnlockingRules(); }

    public static int reload(MinecraftServer server) {
        Path file = FabricLoader.getInstance().getConfigDir().resolve("explorerscompass/unlocking_rules.json");
        active = loadFile(file, catalog(server));
        LOGGER.info("Loaded {} structure unlocking rules", active.rules.size());
        return active.rules.size();
    }

    static UnlockingRules loadFile(Path file, Catalog catalog) {
        try {
            Files.createDirectories(file.getParent());
            if (!Files.exists(file)) Files.writeString(file, "{\n  \"default\": \"unlocked\",\n  \"rules\": {}\n}\n");
            try (Reader reader = Files.newBufferedReader(file)) {
                return parse(JsonParser.parseReader(reader).getAsJsonObject(), catalog);
            }
        } catch (IOException | RuntimeException e) {
            LOGGER.error("Cannot load {}. Using default unlocked with no rules.", file, e);
            return new UnlockingRules();
        }
    }

    static UnlockingRules parse(JsonObject root, Catalog catalog) {
        UnlockingRules result = new UnlockingRules();
        String mode = root.has("default") ? string(root, "default") : "unlocked";
        if (!mode.equals("unlocked") && !mode.equals("lockedUntilVisited")) throw invalid("Unknown default: " + mode);
        result.defaultUnlocked = mode.equals("unlocked");
        JsonObject entries = root.has("rules") ? root.getAsJsonObject("rules") : new JsonObject();
        for (Map.Entry<String, JsonElement> entry : entries.entrySet()) {
            try {
                JsonObject value = entry.getValue().getAsJsonObject();
                Set<Identifier> targets = targets(catalog, value.getAsJsonObject("unlocks"));
                Condition condition = condition(catalog, value.getAsJsonObject("condition"), 0);
                String description = value.has("description") ? string(value, "description") : null;
                Rule rule = new Rule(targets, condition, description);
                result.rules.put(entry.getKey(), rule);
                for (Identifier id : targets) result.byStructure.computeIfAbsent(id, ignored -> new ArrayList<>()).add(rule);
            } catch (RuntimeException e) {
                LOGGER.error("Skipping invalid rule '{}' in unlocking_rules.json: {}", entry.getKey(), e.getMessage());
            }
        }
        return result;
    }

    public Set<String> ruleNames() { return Set.copyOf(rules.keySet()); }
    public Set<Identifier> ruleTargets(String id) {
        Rule rule = rules.get(id);
        return rule == null ? null : rule.targets;
    }

    public static boolean isUnlocked(MinecraftServer server, UUID player, Identifier structure) {
        return UnlockedStructures.get(server).contains(player, structure);
    }

    /** Called on compass open, not by reload or search packets. */
    public void evaluateOnOpen(ServerPlayerEntity player) {
        int added = UnlockedStructures.get(player.getServer()).add(player.getUuid(),
                earnedOnOpen(catalog(player.getServer()).structures(), progress(player)));
        UnlockNotifications.newlyAdded(player, added);
    }

    Set<Identifier> earnedOnOpen(Set<Identifier> structures, Progress progress) {
        Set<Identifier> earned = new LinkedHashSet<>();
        for (Identifier id : structures) {
            if (!byStructure.containsKey(id) && (defaultUnlocked || progress.visited(id))) earned.add(id);
        }
        for (Rule rule : rules.values()) if (rule.condition.test.test(progress)) earned.addAll(rule.targets);
        return earned;
    }

    /** Evaluate only rules affected by newly recorded progress. */
    public void onProgress(ServerPlayerEntity player, Identifier visit, Identifier advancement) {
        MinecraftServer server = player.getServer();
        if (server == null || server.getOverworld() == null) return;
        int added = UnlockedStructures.get(server).add(player.getUuid(), earnedOnProgress(progress(player), visit, advancement));
        UnlockNotifications.newlyAdded(player, added);
    }

    Set<Identifier> earnedOnProgress(Progress progress, Identifier visit, Identifier advancement) {
        Set<Identifier> earned = new LinkedHashSet<>();
        if (visit != null && !byStructure.containsKey(visit)) earned.add(visit);
        for (Rule rule : rules.values()) {
            if (((visit != null && rule.condition.visits.contains(visit))
                    || (advancement != null && rule.condition.advancements.contains(advancement)))
                    && rule.condition.test.test(progress)) earned.addAll(rule.targets);
        }
        return earned;
    }

    public Map<Identifier, RequirementNode> lockedRequirements(ServerPlayerEntity player, List<Identifier> structures) {
        Map<Identifier, RequirementNode> result = new LinkedHashMap<>();
        Progress progress = progress(player);
        for (Identifier id : structures) {
            if (isUnlocked(player.getServer(), player.getUuid(), id)) continue;
            result.put(id, requirements(id, progress));
        }
        return result;
    }

    RequirementNode requirements(Identifier id, Progress progress) {
        List<Rule> matching = byStructure.get(id);
        if (matching == null) return visitNode(Set.of(id), true, Set.of(id), progress);
        List<RequirementNode> descriptions = matching.stream().map(rule -> rule.description == null
                ? rule.condition.display.apply(progress)
                : new RequirementNode(Text.literal(rule.description), rule.condition.test.test(progress), false, true, List.of())).toList();
        return descriptions.size() == 1 ? descriptions.get(0)
                : new RequirementNode(Text.literal("Do any of:"), descriptions.stream().anyMatch(RequirementNode::checked), true, descriptions);
    }

    private static Set<Identifier> targets(Catalog catalog, JsonObject json) {
        for (String key : json.keySet())
            if (!key.equals("structures") && !key.equals("structureSet")) throw invalid("Unknown unlock selector: " + key);
        selector(json, "structures", "structureSet");
        Set<Identifier> ids;
        if (json.has("structures")) ids = ids(json.get("structures"));
        else ids = catalog.structureSet(identifier(string(json, "structureSet")));
        Set<Identifier> known = catalog.structures();
        return ids.stream().filter(id -> {
            if (known.contains(id)) return true;
            warn("Unavailable structure target " + id);
            return false;
        }).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public static Set<Identifier> setMembers(MinecraftServer server, Identifier id) {
        StructureSet set = server.getRegistryManager().get(RegistryKeys.STRUCTURE_SET).get(id);
        if (set == null) { warn("Unavailable structure set " + id); return Set.of(); }
        return set.structures().stream().map(entry -> entry.structure().getKey().orElseThrow().getValue())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static Condition condition(Catalog catalog, JsonObject json, int depth) {
        if (json == null || depth > 64) throw invalid("Missing or excessively nested condition");
        selector(json, "type", "any", "all");
        if (!json.has("type")) {
            boolean all = json.has("all");
            var array = json.getAsJsonArray(all ? "all" : "any");
            if (array.isEmpty()) throw invalid("Condition groups cannot be empty");
            List<Condition> children = new ArrayList<>();
            for (JsonElement child : array) children.add(condition(catalog, child.getAsJsonObject(), depth + 1));
            Set<Identifier> visits = new LinkedHashSet<>(), advancements = new LinkedHashSet<>();
            children.forEach(child -> { visits.addAll(child.visits); advancements.addAll(child.advancements); });
            return new Condition(player -> all ? children.stream().allMatch(child -> child.test.test(player))
                    : children.stream().anyMatch(child -> child.test.test(player)),
                    player -> {
                        List<RequirementNode> nodes = children.stream().map(child -> child.display.apply(player)).toList();
                        return new RequirementNode(Text.literal(all ? "Do all of:" : "Do any of:"),
                                all ? nodes.stream().allMatch(RequirementNode::checked) : nodes.stream().anyMatch(RequirementNode::checked), true, nodes);
                    }, visits, advancements);
        }
        String type = string(json, "type");
        if (type.equals("advancement")) {
            if (!json.has("advancements") || !json.get("advancements").isJsonArray() || json.has("id"))
                throw invalid("Advancement conditions require an advancements array");
            Set<Identifier> required = ids(json.get("advancements"));
            String match = json.has("match") ? string(json, "match") : "any";
            if (!match.equals("any") && !match.equals("all")) throw invalid("Unknown advancement match " + match);
            boolean all = match.equals("all");
            for (Identifier id : required) if (!catalog.advancementExists(id)) warn("Unavailable advancement " + id);
            return new Condition(progress -> all ? required.stream().allMatch(id -> catalog.advancementExists(id) && progress.completed(id))
                    : required.stream().anyMatch(id -> catalog.advancementExists(id) && progress.completed(id)),
                    progress -> {
                        List<RequirementNode> leaves = required.stream().map(id -> {
                            Formatting color = catalog.advancementColor(id);
                            Text description = catalog.advancementDescription(id);
                            return new RequirementNode(catalog.advancementTitle(id).copy().formatted(color),
                                    catalog.advancementExists(id) && progress.completed(id), false, false, List.of(),
                                    description == null ? null : description.copy().formatted(color));
                        }).toList();
                        boolean checked = all ? leaves.stream().allMatch(RequirementNode::checked)
                                : leaves.stream().anyMatch(RequirementNode::checked);
                        String label = required.size() == 1 ? "Complete Advancement:"
                                : all ? "Complete each of these advancements:" : "Complete any of these advancements:";
                        return new RequirementNode(Text.literal(label), checked, leaves);
                    }, Set.of(), required);
        }
        if (!type.equals("visit")) throw invalid("Unknown condition type " + type);
        selector(json, "structure", "structures", "structureSet");
        String match = json.has("match") ? string(json, "match") : "any";
        if (!match.equals("any") && !match.equals("all")) throw invalid("Unknown visit match " + match);
        Set<Identifier> required = json.has("structure") ? Set.of(identifier(string(json, "structure")))
                : json.has("structures") ? ids(json.get("structures"))
                : catalog.structureSet(identifier(string(json, "structureSet")));
        Set<Identifier> known = catalog.structures();
        for (Identifier id : required) if (!known.contains(id)) warn("Unavailable visit requirement " + id);
        boolean all = match.equals("all");
        return new Condition(player -> {
            Predicate<Identifier> visited = id -> known.contains(id)
                    && player.visited(id);
            return !required.isEmpty() && (all ? required.stream().allMatch(visited) : required.stream().anyMatch(visited));
        }, player -> visitNode(required, all, known, player), required, Set.of());
    }

    private static RequirementNode visitNode(Set<Identifier> required, boolean all, Set<Identifier> known, Progress progress) {
        List<RequirementNode> children = required.stream().map(id -> new RequirementNode(
                Text.literal(readable(id)).formatted(Formatting.AQUA),
                known.contains(id) && progress.visited(id), List.of())).toList();
        boolean checked = !children.isEmpty() && (all ? children.stream().allMatch(RequirementNode::checked)
                : children.stream().anyMatch(RequirementNode::checked));
        return new RequirementNode(Text.literal(required.size() == 1 ? "Visit:" : all ? "Visit each of:" : "Visit any of:"), checked, children);
    }

    private static void selector(JsonObject json, String... keys) {
        if (json == null) throw invalid("Missing selector");
        int count = 0;
        for (String key : keys) if (json.has(key)) count++;
        if (count != 1) throw invalid("Choose exactly one of " + String.join(", ", keys));
    }
    private static Set<Identifier> ids(JsonElement json) {
        Set<Identifier> result = new LinkedHashSet<>();
        for (JsonElement value : json.getAsJsonArray()) {
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw invalid("IDs must be strings");
            result.add(identifier(value.getAsString()));
        }
        if (result.isEmpty()) throw invalid("ID list cannot be empty");
        return result;
    }
    private static String string(JsonObject json, String key) {
        JsonElement value = json.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw invalid("Expected string for " + key);
        return value.getAsString();
    }
    private static Identifier identifier(String value) {
        Identifier id = Identifier.tryParse(value);
        if (id == null) throw invalid("Invalid identifier " + value);
        return id;
    }
    private static String readable(Identifier id) {
        String path = id.getPath();
        path = path.substring(path.lastIndexOf('/') + 1).replace('_', ' ');
        return path.isEmpty() ? id.toString() : Character.toUpperCase(path.charAt(0)) + path.substring(1);
    }
    private static IllegalArgumentException invalid(String message) { return new IllegalArgumentException(message); }
    private static void warn(String message) { LOGGER.warn("Unlocking rules: {}", message); }
}
