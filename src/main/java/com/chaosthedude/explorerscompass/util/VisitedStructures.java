package com.chaosthedude.explorerscompass.util;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructureStart;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.gen.structure.Structure;

/** Per-world visit history. A visit identifies a generated instance, not a structure type. */
public final class VisitedStructures extends PersistentState {
	private static final String SAVE_KEY = "explorerscompass_visited_structures";
	private final Map<UUID, Set<Visit>> visits = new HashMap<>();
	private final Map<UUID, CheckedPosition> lastChecked = new HashMap<>();

	private record Visit(Identifier dimension, Identifier structure, int chunkX, int chunkZ) {
	}

	private record CheckedPosition(Identifier dimension, BlockPos pos) {
	}

	public static VisitedStructures get(MinecraftServer server) {
		return server.getOverworld().getPersistentStateManager().getOrCreate(VisitedStructures::fromNbt, VisitedStructures::new, SAVE_KEY);
	}

	public static VisitedStructures fromNbt(NbtCompound nbt) {
		VisitedStructures state = new VisitedStructures();
		NbtList players = nbt.getList("Players", NbtElement.COMPOUND_TYPE);
		for (int i = 0; i < players.size(); i++) {
			NbtCompound player = players.getCompound(i);
			if (!player.containsUuid("UUID")) continue;
			Set<Visit> playerVisits = new HashSet<>();
			NbtList entries = player.getList("Visits", NbtElement.COMPOUND_TYPE);
			for (int j = 0; j < entries.size(); j++) {
				NbtCompound entry = entries.getCompound(j);
				Identifier dimension = Identifier.tryParse(entry.getString("Dimension"));
				Identifier structure = Identifier.tryParse(entry.getString("Structure"));
				if (dimension != null && structure != null) {
					playerVisits.add(new Visit(dimension, structure, entry.getInt("ChunkX"), entry.getInt("ChunkZ")));
				}
			}
			state.visits.put(player.getUuid("UUID"), playerVisits);
		}
		return state;
	}

	@Override
	public NbtCompound writeNbt(NbtCompound nbt) {
		NbtList players = new NbtList();
		for (Map.Entry<UUID, Set<Visit>> playerVisits : visits.entrySet()) {
			NbtCompound player = new NbtCompound();
			player.putUuid("UUID", playerVisits.getKey());
			NbtList entries = new NbtList();
			for (Visit visit : playerVisits.getValue()) {
				NbtCompound entry = new NbtCompound();
				entry.putString("Dimension", visit.dimension.toString());
				entry.putString("Structure", visit.structure.toString());
				entry.putInt("ChunkX", visit.chunkX);
				entry.putInt("ChunkZ", visit.chunkZ);
				entries.add(entry);
			}
			player.put("Visits", entries);
			players.add(player);
		}
		nbt.put("Players", players);
		return nbt;
	}

	public void checkPlayer(ServerPlayerEntity player) {
		ServerWorld world = player.getServerWorld();
		BlockPos pos = player.getBlockPos();
		Identifier dimension = world.getRegistryKey().getValue();
		CheckedPosition current = new CheckedPosition(dimension, pos);
		if (current.equals(lastChecked.put(player.getUuid(), current))) return;

		for (Structure structure : world.getStructureAccessor().getStructureReferences(pos).keySet()) {
			StructureStart start = world.getStructureAccessor().getStructureContaining(pos, structure);
			if (!start.hasChildren()) continue;
			Identifier structureId = world.getRegistryManager().get(RegistryKeys.STRUCTURE).getId(structure);
			if (structureId == null) continue;
			ChunkPos startChunk = start.getPos();
			Visit visit = new Visit(dimension, structureId, startChunk.x, startChunk.z);
			if (visits.computeIfAbsent(player.getUuid(), ignored -> new HashSet<>()).add(visit)) markDirty();
		}
	}

	public void forgetPosition(UUID playerId) {
		lastChecked.remove(playerId);
	}

	public boolean hasVisited(UUID playerId, Identifier dimension, Identifier structure, ChunkPos startChunk) {
		return visits.getOrDefault(playerId, Set.of()).contains(new Visit(dimension, structure, startChunk.x, startChunk.z));
	}
}
