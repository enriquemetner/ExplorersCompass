package com.chaosthedude.explorerscompass.unlocking;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import net.minecraft.world.PersistentState;

/** Structure types permanently earned by each player in this world. */
public final class UnlockedStructures extends PersistentState {
    private final Map<UUID, Set<Identifier>> unlockedStructures = new HashMap<>();
    private final Set<UUID> notificationsEnabled = new HashSet<>();
    private final Map<UUID, Set<Identifier>> pendingNotifications = new HashMap<>();

    public static UnlockedStructures get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(
                UnlockedStructures::fromNbt, UnlockedStructures::new, "explorerscompass_unlocked_structures");
    }

    public boolean contains(UUID player, Identifier structure) {
        return unlockedStructures.getOrDefault(player, Set.of()).contains(structure);
    }

    public Set<Identifier> getStructures(UUID player) {
        return Set.copyOf(unlockedStructures.getOrDefault(player, Set.of()));
    }

    public boolean notificationsEnabled(UUID player) {
        return notificationsEnabled.contains(player);
    }

    /** Returns false after the first compass, including after advancement revocation. */
    public boolean enableNotifications(UUID player) {
        if (!notificationsEnabled.add(player)) return false;
        pendingNotifications.remove(player);
        markDirty();
        return true;
    }

    public void queueNotification(UUID player, Set<Identifier> added) {
        if (added.isEmpty()) return;
        if (pendingNotifications.computeIfAbsent(player, ignored -> new HashSet<>()).addAll(added)) markDirty();
    }

    /** Consume queued IDs, counting only structure types that remain unlocked. */
    public int takePendingNotificationCount(UUID player) {
        Set<Identifier> pending = pendingNotifications.remove(player);
        if (pending == null) return 0;
        markDirty();
        pending.retainAll(unlockedStructures.getOrDefault(player, Set.of()));
        return pending.size();
    }

    public int add(UUID player, Set<Identifier> structures) {
        if (structures.isEmpty()) return 0;
        Set<Identifier> saved = unlockedStructures.computeIfAbsent(player, ignored -> new HashSet<>());
        int before = saved.size();
        saved.addAll(structures);
        if (saved.size() != before) markDirty();
        return saved.size() - before;
    }

    public int remove(UUID player, Set<Identifier> structures) {
        Set<Identifier> pending = pendingNotifications.get(player);
        if (pending != null && pending.removeAll(structures)) {
            if (pending.isEmpty()) pendingNotifications.remove(player);
            markDirty();
        }
        Set<Identifier> saved = unlockedStructures.get(player);
        if (saved == null) return 0;
        int before = saved.size();
        saved.removeAll(structures);
        int removed = before - saved.size();
        if (saved.isEmpty()) unlockedStructures.remove(player);
        if (removed > 0) markDirty();
        return removed;
    }

    public static UnlockedStructures fromNbt(NbtCompound nbt) {
        UnlockedStructures state = new UnlockedStructures();
        NbtList players = nbt.getList("Players", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < players.size(); i++) {
            NbtCompound player = players.getCompound(i);
            if (!player.containsUuid("UUID")) continue;
            Set<Identifier> structures = new HashSet<>();
            NbtList ids = player.getList("UnlockedStructures", NbtElement.STRING_TYPE);
            for (int j = 0; j < ids.size(); j++) {
                Identifier id = Identifier.tryParse(ids.getString(j));
                if (id != null) structures.add(id);
            }
            state.unlockedStructures.put(player.getUuid("UUID"), structures);
            UUID uuid = player.getUuid("UUID");
            if (player.getBoolean("NotificationsEnabled")) state.notificationsEnabled.add(uuid);
            Set<Identifier> pending = new HashSet<>();
            NbtList queued = player.getList("PendingNotifications", NbtElement.STRING_TYPE);
            for (int j = 0; j < queued.size(); j++) {
                Identifier id = Identifier.tryParse(queued.getString(j));
                if (id != null) pending.add(id);
            }
            if (!pending.isEmpty()) state.pendingNotifications.put(uuid, pending);
        }
        return state;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        NbtList players = new NbtList();
        Set<UUID> uuids = new HashSet<>(unlockedStructures.keySet());
        uuids.addAll(notificationsEnabled);
        uuids.addAll(pendingNotifications.keySet());
        uuids.forEach(uuid -> {
            NbtCompound player = new NbtCompound();
            player.putUuid("UUID", uuid);
            NbtList ids = new NbtList();
            unlockedStructures.getOrDefault(uuid, Set.of()).stream().sorted().forEach(id -> ids.add(NbtString.of(id.toString())));
            player.put("UnlockedStructures", ids);
            player.putBoolean("NotificationsEnabled", notificationsEnabled.contains(uuid));
            NbtList queued = new NbtList();
            pendingNotifications.getOrDefault(uuid, Set.of()).stream().sorted().forEach(id -> queued.add(NbtString.of(id.toString())));
            player.put("PendingNotifications", queued);
            players.add(player);
        });
        nbt.put("Players", players);
        return nbt;
    }
}
