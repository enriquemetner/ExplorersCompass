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

    public int add(UUID player, Set<Identifier> structures) {
        if (structures.isEmpty()) return 0;
        Set<Identifier> saved = unlockedStructures.computeIfAbsent(player, ignored -> new HashSet<>());
        int before = saved.size();
        saved.addAll(structures);
        if (saved.size() != before) markDirty();
        return saved.size() - before;
    }

    public int remove(UUID player, Set<Identifier> structures) {
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
        }
        return state;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        NbtList players = new NbtList();
        unlockedStructures.forEach((uuid, structures) -> {
            NbtCompound player = new NbtCompound();
            player.putUuid("UUID", uuid);
            NbtList ids = new NbtList();
            structures.stream().sorted().forEach(id -> ids.add(NbtString.of(id.toString())));
            player.put("UnlockedStructures", ids);
            players.add(player);
        });
        nbt.put("Players", players);
        return nbt;
    }
}
