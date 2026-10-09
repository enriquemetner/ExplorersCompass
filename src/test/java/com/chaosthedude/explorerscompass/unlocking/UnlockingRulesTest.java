package com.chaosthedude.explorerscompass.unlocking;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import com.chaosthedude.explorerscompass.util.VisitedStructures;
import com.google.gson.JsonParser;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.Identifier;
import net.minecraft.util.Formatting;
import net.minecraft.text.Text;
import net.minecraft.network.PacketByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class UnlockingRulesTest {
    private static final Identifier A = new Identifier("minecraft:ancient_city");
    private static final Identifier B = new Identifier("minecraft:fortress");
    private static final Identifier C = new Identifier("minecraft:stronghold");
    private static final Identifier ADVANCEMENT = new Identifier("minecraft:story/mine_stone");
    private static final Identifier DRAGON_EGG = new Identifier("minecraft:end/dragon_egg");
    private static final Identifier SET = new Identifier("minecraft:test_set");
    private static final Set<Identifier> STRUCTURES = Set.of(A, B, C);
    private final UnlockingRules.Catalog catalog = new UnlockingRules.Catalog() {
        public Set<Identifier> structures() { return STRUCTURES; }
        public Set<Identifier> structureSet(Identifier id) { return id.equals(SET) ? Set.of(A, B) : Set.of(); }
        public boolean advancementExists(Identifier id) { return id.equals(ADVANCEMENT) || id.equals(DRAGON_EGG); }
        public Text advancementTitle(Identifier id) { return Text.literal(id.equals(DRAGON_EGG) ? "The Next Generation" : "Stone Age"); }
        public Text advancementDescription(Identifier id) { return Text.literal(id.equals(DRAGON_EGG) ? "Hold the Dragon Egg" : "Mine stone"); }
        public Formatting advancementColor(Identifier id) { return id.equals(DRAGON_EGG) ? Formatting.DARK_PURPLE : Formatting.GREEN; }
    };
    private static class Progress implements UnlockingRules.Progress {
        final Set<Identifier> visits = new HashSet<>(), advancements = new HashSet<>();
        public boolean visited(Identifier id) { return visits.contains(id); }
        public boolean completed(Identifier id) { return advancements.contains(id); }
    }
    private UnlockingRules rules(String json) { return UnlockingRules.parse(JsonParser.parseString(json).getAsJsonObject(), catalog); }
    private String rule(String condition) {
        return "{\"unlocks\":{\"structures\":[\"minecraft:fortress\"]},\"condition\":" + condition + "}";
    }
    private UnlockingRules condition(String condition) {
        return rules("{\"default\":\"lockedUntilVisited\",\"rules\":{\"test\":" + rule(condition) + "}}");
    }

    @Test void defaultsAndExplicitRules() {
        Progress p = new Progress();
        assertEquals(STRUCTURES, rules("{}").earnedOnOpen(STRUCTURES, p));
        assertTrue(rules("{\"default\":\"lockedUntilVisited\"}").earnedOnOpen(STRUCTURES, p).isEmpty());
        p.visits.add(A);
        assertEquals(Set.of(A), rules("{\"default\":\"lockedUntilVisited\"}").earnedOnOpen(STRUCTURES, p));
        UnlockingRules rules = rules("{\"default\":\"unlocked\",\"rules\":{\"test\":" + rule("{\"type\":\"advancement\",\"advancements\":[\"minecraft:story/mine_stone\"]}") + "}}");
        assertEquals(Set.of(A, C), rules.earnedOnOpen(STRUCTURES, p));
    }

    @Test void nestedAllAnyAndImmediateProgress() {
        UnlockingRules rules = condition("""
            {"all":[{"type":"visit","structure":"minecraft:ancient_city"},
                {"any":[{"type":"advancement","advancements":["minecraft:story/mine_stone"]},
                        {"type":"visit","structure":"minecraft:stronghold"}]}]}
            """);
        Progress p = new Progress();
        p.visits.add(A);
        assertFalse(rules.earnedOnProgress(p, A, null).contains(B));
        p.advancements.add(ADVANCEMENT);
        assertTrue(rules.earnedOnProgress(p, null, ADVANCEMENT).contains(B));
        assertTrue(rules.earnedOnProgress(p, null, new Identifier("test:unrelated")).isEmpty());
    }

    @Test void visitListsAndSetsRespectAnyAll() {
        Progress p = new Progress(); p.visits.add(A);
        assertTrue(condition("{\"type\":\"visit\",\"structures\":[\"minecraft:ancient_city\",\"minecraft:stronghold\"]}").earnedOnOpen(STRUCTURES, p).contains(B));
        assertFalse(condition("{\"type\":\"visit\",\"structures\":[\"minecraft:ancient_city\",\"minecraft:stronghold\"],\"match\":\"all\"}").earnedOnOpen(STRUCTURES, p).contains(B));
        assertTrue(condition("{\"type\":\"visit\",\"structureSet\":\"minecraft:test_set\",\"match\":\"any\"}").earnedOnOpen(STRUCTURES, p).contains(B));
        UnlockingRules all = condition("{\"type\":\"visit\",\"structureSet\":\"minecraft:test_set\",\"match\":\"all\"}");
        assertFalse(all.earnedOnOpen(STRUCTURES, p).contains(B));
        p.visits.add(B);
        assertTrue(all.earnedOnOpen(STRUCTURES, p).contains(B));
    }

    @Test void advancementListsRespectAnyAllAndKeepIndividualChecks() {
        String ids = "[\"minecraft:story/mine_stone\",\"minecraft:end/dragon_egg\"]";
        UnlockingRules any = condition("{\"type\":\"advancement\",\"advancements\":" + ids + ",\"match\":\"any\"}");
        UnlockingRules all = condition("{\"type\":\"advancement\",\"advancements\":" + ids + ",\"match\":\"all\"}");
        Progress progress = new Progress();
        assertFalse(any.earnedOnOpen(STRUCTURES, progress).contains(B));
        progress.advancements.add(ADVANCEMENT);
        assertTrue(any.earnedOnProgress(progress, null, ADVANCEMENT).contains(B));
        assertFalse(all.earnedOnProgress(progress, null, ADVANCEMENT).contains(B));
        RequirementNode tree = all.requirements(B, progress);
        assertEquals("Complete each of these advancements:", tree.label().getString());
        assertFalse(tree.checked());
        assertTrue(tree.children().get(0).checked());
        assertFalse(tree.children().get(1).checked());
        assertEquals(Formatting.DARK_PURPLE.getColorValue(), tree.children().get(1).label().getStyle().getColor().getRgb());
        assertEquals("Hold the Dragon Egg", tree.children().get(1).tooltipDescription().getString());
        progress.advancements.add(DRAGON_EGG);
        assertTrue(all.earnedOnProgress(progress, null, DRAGON_EGG).contains(B));
        assertTrue(all.requirements(B, progress).checked());
        assertEquals("Complete any of these advancements:", any.requirements(B, progress).label().getString());
        assertTrue(condition("{\"type\":\"advancement\",\"advancements\":[\"minecraft:story/mine_stone\"]}")
                .earnedOnOpen(STRUCTURES, progress).contains(B));
    }

    @Test void advancementConditionRequiresArrayAndValidMatch() {
        assertTrue(condition("{\"type\":\"advancement\",\"id\":\"minecraft:story/mine_stone\"}").ruleNames().isEmpty());
        assertTrue(condition("{\"type\":\"advancement\",\"advancements\":[]}").ruleNames().isEmpty());
        assertTrue(condition("{\"type\":\"advancement\",\"advancements\":[\"minecraft:story/mine_stone\"],\"match\":\"neither\"}").ruleNames().isEmpty());
    }

    @Test void targetsSupportStructureSets() {
        UnlockingRules rules = rules("""
            {"rules":{
              "set":{"unlocks":{"structureSet":"minecraft:test_set"},"condition":{"type":"visit","structure":"minecraft:stronghold"}}
            }}
            """);
        assertEquals(Set.of(A, B), rules.ruleTargets("set"));
    }

    @Test void overlappingRulesNeedOnlyOneAndInvalidRuleDoesNotOverrideDefault() {
        UnlockingRules rules = rules("{\"rules\":{\"a\":" + rule("{\"type\":\"visit\",\"structure\":\"minecraft:ancient_city\"}")
                + ",\"b\":" + rule("{\"type\":\"advancement\",\"advancements\":[\"minecraft:story/mine_stone\"]}")
                + ",\"invalid\":{\"unlocks\":{\"structures\":[\"minecraft:stronghold\"]},\"condition\":{\"all\":[]}}}}");
        Progress p = new Progress();
        assertFalse(rules.earnedOnOpen(STRUCTURES, p).contains(B));
        assertTrue(rules.earnedOnOpen(STRUCTURES, p).contains(C));
        p.visits.add(A);
        assertTrue(rules.earnedOnOpen(STRUCTURES, p).contains(B));
        assertEquals(Set.of("a", "b"), rules.ruleNames());
    }

    @Test void unavailableRequirementsNeverSatisfyAllVacuously() {
        Progress p = new Progress(); p.visits.add(A);
        assertFalse(condition("{\"type\":\"visit\",\"structureSet\":\"missing:set\",\"match\":\"all\"}").earnedOnOpen(STRUCTURES, p).contains(B));
        assertFalse(condition("{\"type\":\"visit\",\"structures\":[\"minecraft:ancient_city\",\"missing:type\"],\"match\":\"all\"}").earnedOnOpen(STRUCTURES, p).contains(B));
        p.advancements.add(new Identifier("missing:advancement"));
        assertFalse(condition("{\"type\":\"advancement\",\"advancements\":[\"missing:advancement\"]}").earnedOnOpen(STRUCTURES, p).contains(B));
    }

    @Test void requirementsPreserveDescriptionAndGenerateReadableFallback() {
        UnlockingRules rules = rules("""
            {"rules":{"hint":{"unlocks":{"structures":["minecraft:ancient_city"]},
              "description":"Follow the lanterns.","condition":{"type":"advancement","advancements":["minecraft:story/mine_stone"]}}}}
            """);
        RequirementNode description = rules.requirements(A, new Progress());
        assertEquals("Follow the lanterns.", description.label().getString());
        assertTrue(description.description());
        RequirementNode advancement = condition("{\"type\":\"advancement\",\"advancements\":[\"minecraft:story/mine_stone\"]}").requirements(B, new Progress());
        assertEquals("Complete Advancement:", advancement.label().getString());
        assertFalse(advancement.description());
        assertEquals("Stone Age", advancement.children().get(0).label().getString());
        assertEquals(Formatting.GREEN.getColorValue(), advancement.children().get(0).label().getStyle().getColor().getRgb());
        assertEquals("Mine stone", advancement.children().get(0).tooltipDescription().getString());
    }

    @Test void requirementTreeShowsEachSatisfiedLeafAndCompositeState() {
        UnlockingRules rules = condition("""
            {"any":[{"type":"visit","structures":["minecraft:ancient_city","minecraft:stronghold"],"match":"all"},
                    {"type":"advancement","advancements":["minecraft:story/mine_stone"]}]}
            """);
        Progress progress = new Progress();
        progress.visits.add(A);
        RequirementNode root = rules.requirements(B, progress);
        assertEquals("Do any of:", root.label().getString());
        assertTrue(root.logicalGroup());
        assertFalse(root.checked());
        RequirementNode visits = root.children().get(0);
        assertEquals("Visit each of:", visits.label().getString());
        assertFalse(visits.logicalGroup());
        assertFalse(visits.checked());
        assertTrue(visits.children().get(0).checked() || visits.children().get(1).checked());
        assertEquals(Formatting.AQUA.getColorValue(), visits.children().get(0).label().getStyle().getColor().getRgb());
        assertFalse(root.children().get(1).checked());
        progress.visits.add(C);
        root = rules.requirements(B, progress);
        assertTrue(root.children().get(0).checked());
        assertTrue(root.checked());
        assertFalse(root.children().get(1).checked());
    }

    @Test void requirementPacketPreservesChecksAndColors() {
        Progress progress = new Progress();
        progress.visits.add(A);
        RequirementNode original = condition("{\"type\":\"visit\",\"structures\":[\"minecraft:ancient_city\",\"minecraft:stronghold\"],\"match\":\"all\"}")
                .requirements(B, progress);
        PacketByteBuf packet = new PacketByteBuf(Unpooled.buffer());
        original.write(packet);
        RequirementNode copy = RequirementNode.read(packet);
        assertFalse(copy.checked());
        assertFalse(copy.logicalGroup());
        assertTrue(copy.children().get(0).checked());
        assertFalse(copy.children().get(1).checked());
        assertEquals(Formatting.AQUA.getColorValue(), copy.children().get(0).label().getStyle().getColor().getRgb());
        PacketByteBuf groupPacket = new PacketByteBuf(Unpooled.buffer());
        new RequirementNode(Text.literal("Do all of:"), true, true, java.util.List.of(original)).write(groupPacket);
        RequirementNode groupCopy = RequirementNode.read(groupPacket);
        assertTrue(groupCopy.logicalGroup());
        assertTrue(groupCopy.checked());
        PacketByteBuf descriptionPacket = new PacketByteBuf(Unpooled.buffer());
        new RequirementNode(Text.literal("Follow the lanterns"), false, false, true, java.util.List.of()).write(descriptionPacket);
        assertTrue(RequirementNode.read(descriptionPacket).description());
        RequirementNode advancement = condition("{\"type\":\"advancement\",\"advancements\":[\"minecraft:end/dragon_egg\"]}")
                .requirements(B, new Progress()).children().get(0);
        PacketByteBuf advancementPacket = new PacketByteBuf(Unpooled.buffer());
        advancement.write(advancementPacket);
        RequirementNode advancementCopy = RequirementNode.read(advancementPacket);
        assertEquals("The Next Generation", advancementCopy.label().getString());
        assertEquals("Hold the Dragon Egg", advancementCopy.tooltipDescription().getString());
        assertEquals(Formatting.DARK_PURPLE.getColorValue(), advancementCopy.label().getStyle().getColor().getRgb());
    }

    @Test void invalidMatchSkipsRule() {
        assertTrue(condition("{\"type\":\"visit\",\"structure\":\"minecraft:ancient_city\",\"match\":\"typo\"}").ruleNames().isEmpty());
    }

    @Test void latchingSurvivesRequirementLossRuleChangesAndNbtRoundTrip() {
        UUID player = UUID.randomUUID(), other = UUID.randomUUID();
        UnlockedStructures saved = new UnlockedStructures();
        Progress p = new Progress(); p.visits.add(A);
        UnlockingRules original = condition("{\"type\":\"visit\",\"structure\":\"minecraft:ancient_city\"}");
        saved.add(player, original.earnedOnOpen(STRUCTURES, p));
        p.visits.clear();
        saved.add(player, original.earnedOnOpen(STRUCTURES, p));
        saved.add(player, rules("{\"default\":\"lockedUntilVisited\"}").earnedOnOpen(STRUCTURES, p));
        saved = UnlockedStructures.fromNbt(saved.writeNbt(new NbtCompound()));
        assertTrue(saved.contains(player, B));
        assertFalse(saved.contains(other, B));
        saved.remove(player, Set.of(B));
        assertFalse(saved.contains(player, B));
        p.visits.add(A);
        saved.add(player, original.earnedOnOpen(STRUCTURES, p));
        assertTrue(saved.contains(player, B));
    }

    @Test void notificationStateSurvivesRestartAndPendingCountsExcludeRelockedStructures() {
        UUID player = UUID.randomUUID(), other = UUID.randomUUID();
        UnlockedStructures saved = new UnlockedStructures();
        assertFalse(saved.notificationsEnabled(player));
        saved.add(player, Set.of(A, B));
        saved.queueNotification(player, Set.of(A, B));
        saved.remove(player, Set.of(B));
        saved = UnlockedStructures.fromNbt(saved.writeNbt(new NbtCompound()));
        assertEquals(1, saved.takePendingNotificationCount(player));
        assertEquals(0, saved.takePendingNotificationCount(player));
        assertTrue(saved.enableNotifications(player));
        assertFalse(saved.enableNotifications(player));
        saved.add(other, Set.of(C));
        saved.queueNotification(other, Set.of(C));
        saved = UnlockedStructures.fromNbt(saved.writeNbt(new NbtCompound()));
        assertTrue(saved.notificationsEnabled(player));
        assertFalse(saved.notificationsEnabled(other));
        assertEquals(1, saved.takePendingNotificationCount(other));
        saved.queueNotification(player, Set.of(A));
        assertFalse(saved.enableNotifications(player));
        assertEquals(1, saved.takePendingNotificationCount(player));
    }

    @Test void firstCompassClearsPendingAndKeepsAllPreviouslyUnlockedStructures() {
        UUID player = UUID.randomUUID();
        UnlockedStructures saved = new UnlockedStructures();
        saved.add(player, Set.of(A));
        saved.queueNotification(player, Set.of(A));
        saved.add(player, Set.of(B));
        assertTrue(saved.enableNotifications(player));
        saved = UnlockedStructures.fromNbt(saved.writeNbt(new NbtCompound()));
        assertEquals(2, saved.getStructures(player).size());
        assertEquals(0, saved.takePendingNotificationCount(player));
        assertFalse(saved.enableNotifications(player));
    }

    @Test void reearnedCompassRestoresOnlyNewlyUnlockedStructureTypes() {
        UUID player = UUID.randomUUID();
        UnlockedStructures saved = new UnlockedStructures();
        UnlockingRules rules = rules("{}");
        Progress progress = new Progress();
        saved.add(player, STRUCTURES);
        saved.enableNotifications(player);
        saved.remove(player, Set.of(A, B));
        assertEquals(2, saved.add(player, rules.earnedOnOpen(STRUCTURES, progress)));
        assertEquals(0, saved.add(player, rules.earnedOnOpen(STRUCTURES, progress)));
    }

    @Test void loadingAndMalformedFileDoNotTouchPlayerData(@TempDir Path directory) throws Exception {
        UUID player = UUID.randomUUID();
        UnlockedStructures saved = new UnlockedStructures(); saved.add(player, Set.of(B));
        Path file = directory.resolve("unlocking_rules.json");
        UnlockingRules initial = UnlockingRules.loadFile(file, catalog);
        assertEquals(STRUCTURES, initial.earnedOnOpen(STRUCTURES, new Progress()));
        Files.writeString(file, "{ broken");
        UnlockingRules fallback = UnlockingRules.loadFile(file, catalog);
        assertEquals(STRUCTURES, fallback.earnedOnOpen(STRUCTURES, new Progress()));
        assertEquals(Set.of(B), saved.getStructures(player));
        assertEquals("{ broken", Files.readString(file));
    }

    @Test void clearVisitsRemovesAllInstancesOfOneTypeAndPreservesOtherPlayer() {
        UUID player = UUID.randomUUID(), other = UUID.randomUUID();
        NbtList players = new NbtList();
        for (UUID uuid : new UUID[]{player, other}) {
            NbtCompound p = new NbtCompound(); p.putUuid("UUID", uuid);
            NbtList visits = new NbtList();
            for (int i = 0; i < 3; i++) {
                NbtCompound v = new NbtCompound();
                v.putString("Dimension", i == 0 ? "minecraft:overworld" : "minecraft:the_nether");
                v.putString("Structure", (i < 2 ? A : B).toString()); v.putInt("ChunkX", i); v.putInt("ChunkZ", i);
                visits.add(v);
            }
            p.put("Visits", visits); players.add(p);
        }
        NbtCompound nbt = new NbtCompound(); nbt.put("Players", players);
        VisitedStructures history = VisitedStructures.fromNbt(nbt);
        assertEquals(2, history.clear(player, A));
        assertFalse(history.hasVisitedType(player, A));
        assertTrue(history.hasVisitedType(player, B));
        assertTrue(history.hasVisitedType(other, A));
        assertEquals(1, history.clear(player, null));
        assertFalse(history.hasVisitedType(player, B));
        assertEquals(3, history.clear(other, null));
    }
}
