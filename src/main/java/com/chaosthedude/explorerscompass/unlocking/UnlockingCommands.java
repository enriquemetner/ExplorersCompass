package com.chaosthedude.explorerscompass.unlocking;

import java.io.Reader;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import com.chaosthedude.explorerscompass.ExplorersCompass;
import com.chaosthedude.explorerscompass.util.VisitedStructures;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.IdentifierArgumentType;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public final class UnlockingCommands {
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("explorerscompass").requires(source -> source.hasPermissionLevel(2))
                .then(literal("reloadRules").executes(context -> {
                    int count = UnlockingRules.reload(context.getSource().getServer());
                    context.getSource().sendFeedback(() -> Text.literal("Loaded " + count + " unlocking rules. Player data was not changed. Check the server log for warnings."), true);
                    return count;
                }))
                .then(literal("lock").then(structureTargets(false)))
                .then(literal("unlock").then(structureTargets(true)))
                .then(literal("clearVisited").then(playerArgument()
                        .then(literal("all").executes(context -> clearVisited(context, null)))
                        .then(literal("structure").then(structureArgument().executes(context ->
                                clearVisited(context, IdentifierArgumentType.getIdentifier(context, "id"))))))));
    }

    private static RequiredArgumentBuilder<ServerCommandSource, String> playerArgument() {
        return argument("player", StringArgumentType.word()).suggests((context, builder) ->
                CommandSource.suggestMatching(Arrays.asList(context.getSource().getServer().getPlayerManager().getPlayerNames()), builder));
    }

    private static RequiredArgumentBuilder<ServerCommandSource, Identifier> structureArgument() {
        return argument("id", IdentifierArgumentType.identifier()).suggests((context, builder) ->
                CommandSource.suggestIdentifiers(context.getSource().getRegistryManager().get(RegistryKeys.STRUCTURE).getIds(), builder));
    }

    private static RequiredArgumentBuilder<ServerCommandSource, String> structureTargets(boolean unlock) {
        return playerArgument()
                .then(literal("all").executes(context -> change(context, unlock, "all")))
                .then(literal("structure").then(structureArgument().executes(context -> change(context, unlock, "structure"))))
                .then(literal("structureSet").then(argument("id", IdentifierArgumentType.identifier())
                        .suggests((context, builder) -> CommandSource.suggestIdentifiers(
                                context.getSource().getRegistryManager().get(RegistryKeys.STRUCTURE_SET).getIds(), builder))
                        .executes(context -> change(context, unlock, "structureSet"))))
                .then(literal("rule").then(argument("rule", StringArgumentType.string())
                        .suggests((context, builder) -> CommandSource.suggestMatching(UnlockingRules.get().ruleNames(), builder))
                        .executes(context -> change(context, unlock, "rule"))));
    }

    private static int change(CommandContext<ServerCommandSource> context, boolean unlock, String selector) throws CommandSyntaxException {
        var server = context.getSource().getServer();
        UUID player = player(context);
        UnlockedStructures state = UnlockedStructures.get(server);
        Set<Identifier> targets;
        switch (selector) {
            case "all" -> targets = unlock ? server.getRegistryManager().get(RegistryKeys.STRUCTURE).getIds() : state.getStructures(player);
            case "rule" -> {
                targets = UnlockingRules.get().ruleTargets(StringArgumentType.getString(context, "rule"));
                if (targets == null) throw error("Unknown rule");
            }
            case "structureSet" -> {
                Identifier id = IdentifierArgumentType.getIdentifier(context, "id");
                if (!server.getRegistryManager().get(RegistryKeys.STRUCTURE_SET).containsId(id)) throw error("Unknown structure set: " + id);
                targets = UnlockingRules.setMembers(server, id);
            }
            default -> {
                Identifier id = IdentifierArgumentType.getIdentifier(context, "id");
                if (!server.getRegistryManager().get(RegistryKeys.STRUCTURE).containsId(id)
                        && (unlock || !state.contains(player, id))) throw error("Unknown structure: " + id);
                targets = Set.of(id);
            }
        }
        Set<Identifier> added = new HashSet<>();
        if (unlock) {
            added.addAll(targets);
            added.removeAll(state.getStructures(player));
        }
        int changed = unlock ? state.add(player, targets) : state.remove(player, targets);
        ServerPlayerEntity online = server.getPlayerManager().getPlayer(player);
        if (unlock && changed > 0) {
            if (online != null) UnlockNotifications.newlyAdded(online, changed);
            else state.queueNotification(player, added);
        }
        if (!unlock && changed > 0) {
            if (online != null) ExplorersCompass.EXPLORERS_COMPASS_ITEM.cancelSearch(online, -1);
        }
        context.getSource().sendFeedback(() -> Text.literal((unlock ? "Unlocked " : "Locked ") + changed + " structure types for " + player + ". Reopen the compass to refresh."), true);
        return changed;
    }

    private static int clearVisited(CommandContext<ServerCommandSource> context, Identifier structure) throws CommandSyntaxException {
        UUID player = player(context);
        int removed = VisitedStructures.get(context.getSource().getServer()).clear(player, structure);
        context.getSource().sendFeedback(() -> Text.literal("Cleared " + removed + " visited structure instances for " + player + "."), true);
        return removed;
    }

    private static UUID player(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        String name = StringArgumentType.getString(context, "player");
        try { return UUID.fromString(name); } catch (IllegalArgumentException ignored) { }
        var server = context.getSource().getServer();
        ServerPlayerEntity online = server.getPlayerManager().getPlayer(name);
        if (online != null) return online.getUuid();
        // Read cached profiles only; do not perform a blocking account lookup or invent an offline UUID.
        var cache = server.getRunDirectory().toPath().resolve("usercache.json");
        if (Files.exists(cache)) {
            try (Reader reader = Files.newBufferedReader(cache)) {
                for (JsonElement entry : JsonParser.parseReader(reader).getAsJsonArray()) {
                    var profile = entry.getAsJsonObject();
                    if (name.equalsIgnoreCase(profile.get("name").getAsString())) return UUID.fromString(profile.get("uuid").getAsString());
                }
            } catch (Exception e) {
                ExplorersCompass.LOGGER.warn("Could not read cached player names", e);
            }
        }
        throw error("Unknown cached player: " + name + ". Use the player's UUID instead.");
    }

    private static CommandSyntaxException error(String message) {
        return new SimpleCommandExceptionType(Text.literal(message)).create();
    }
}
