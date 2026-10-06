package com.chaosthedude.explorerscompass.workers;

import java.util.List;

import com.chaosthedude.explorerscompass.ExplorersCompass;
import com.chaosthedude.explorerscompass.config.ExplorersCompassConfig;
import com.chaosthedude.explorerscompass.items.ExplorersCompassItem;
import com.chaosthedude.explorerscompass.util.StructureUtils;
import com.chaosthedude.explorerscompass.util.VisitedStructures;
import com.mojang.datafixers.util.Pair;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructureStart;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.StructurePresence;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.gen.chunk.placement.StructurePlacement;
import net.minecraft.world.gen.structure.Structure;

public abstract class StructureSearchWorker<T extends StructurePlacement> implements WorldWorkerManager.IWorker {

	protected String managerId;
	protected ServerWorld level;
	protected PlayerEntity player;
	protected ItemStack stack;
	protected BlockPos startPos;
	protected BlockPos currentPos;
	protected T placement;
	protected List<Structure> structureSet;
	protected Identifier structureOrGroupId;
	protected boolean excludeVisited;
	protected int samples;
	protected boolean finished;
	protected int lastRadiusThreshold;
	protected int radiusLimit = ExplorersCompassItem.INITIAL_SEARCH_RADIUS;

	public StructureSearchWorker(ServerWorld level, PlayerEntity player, ItemStack stack, BlockPos startPos, T placement, List<Structure> structureSet, Identifier structureOrGroupId, boolean excludeVisited, String managerId) {
		this.level = level;
		this.player = player;
		this.stack = stack;
		this.startPos = startPos;
		this.structureSet = structureSet;
		this.structureOrGroupId = structureOrGroupId;
		this.excludeVisited = excludeVisited && ExplorersCompassConfig.allowExcludeVisited;
		this.placement = placement;
		this.managerId = managerId;

		currentPos = startPos;
		samples = 0;

		finished = !level.getServer().getSaveProperties().getGeneratorOptions().shouldGenerateStructures();
	}

	public void start() {
		if (!stack.isEmpty() && stack.getItem() == ExplorersCompass.EXPLORERS_COMPASS_ITEM) {
			ExplorersCompass.LOGGER.info("SearchWorkerManager " + managerId + ": " + getName() + " starting with " + (shouldLogRadius() ? radiusLimit + " radius limit, " : "") + ExplorersCompassConfig.maxSamples + " max samples");
			WorldWorkerManager.addWorker(this);
		}
	}

	public void setRadiusLimit(int radiusLimit) {
		this.radiusLimit = radiusLimit;
	}

	public boolean canWorkAt(int radiusLimit) {
		return !finished && samples < ExplorersCompassConfig.maxSamples && getRadius() < radiusLimit;
	}

	@Override
	public boolean hasWork() {
		return canWorkAt(radiusLimit);
	}

	@Override
	public boolean doWork() {
		int radius = getRadius();
		if (radius > 250 && radius / 250 > lastRadiusThreshold) {
			if (!stack.isEmpty() && stack.getItem() == ExplorersCompass.EXPLORERS_COMPASS_ITEM) {
				((ExplorersCompassItem) stack.getItem()).setSearchRadius(stack, roundRadius(radius, 250));
			}
			lastRadiusThreshold = radius / 250;
		}
		return false;
	}

	protected Pair<BlockPos, Structure> getStructureGeneratingAt(ChunkPos chunkPos) {
		for (Structure structure : structureSet) {
			StructurePresence result = level.getStructureAccessor().getStructurePresence(chunkPos, structure, false);
			if (result != StructurePresence.START_NOT_PRESENT) {
				if (result == StructurePresence.START_PRESENT) {
					if (wasVisited(structure, chunkPos)) continue;
					return Pair.of(placement.getLocatePos(chunkPos), structure);
				}

				Chunk chunk = level.getChunk(chunkPos.x, chunkPos.z, ChunkStatus.STRUCTURE_STARTS);
				StructureStart structureStart = level.getStructureAccessor().getStructureStart(ChunkSectionPos.from(chunk), structure, chunk);
				if (structureStart != null && structureStart.hasChildren() && !wasVisited(structure, structureStart.getPos())) {
					return Pair.of(placement.getLocatePos(structureStart.getPos()), structure);
				}
			}
		}

		return null;
	}

	private boolean wasVisited(Structure structure, ChunkPos startChunk) {
		if (!excludeVisited) return false;
		Identifier structureId = StructureUtils.getIDForStructure(level, structure);
		return structureId != null && VisitedStructures.get(level.getServer()).hasVisited(player.getUuid(),
			level.getRegistryKey().getValue(), structureId, startChunk);
	}

	protected void succeed(BlockPos pos, Structure structure) {
		ExplorersCompass.LOGGER.info("SearchWorkerManager " + managerId + ": " + getName() + " succeeded with " + (shouldLogRadius() ? getRadius() + " radius, " : "") + samples + " samples");
		if (!stack.isEmpty() && stack.getItem() == ExplorersCompass.EXPLORERS_COMPASS_ITEM) {
			((ExplorersCompassItem) stack.getItem()).succeed(player, stack, StructureUtils.getIDForStructure(level, structure), pos.getX(), pos.getZ(), samples, ExplorersCompassConfig.displayCoordinates);
		} else {
			ExplorersCompass.LOGGER.error("SearchWorkerManager " + managerId + ": " + getName() + " found invalid compass after successful search");
		}
		finished = true;
	}

	protected void fail() {
		ExplorersCompass.LOGGER.info("SearchWorkerManager " + managerId + ": " + getName() + " failed with " + (shouldLogRadius() ? getRadius() + " radius, " : "") + samples + " samples");
		if (!stack.isEmpty() && stack.getItem() == ExplorersCompass.EXPLORERS_COMPASS_ITEM) {
			((ExplorersCompassItem) stack.getItem()).fail(player, stack, structureOrGroupId, getRadius(), samples);
		} else {
			ExplorersCompass.LOGGER.error("SearchWorkerManager " + managerId + ": " + getName() + " found invalid compass after failed search");
			finished = true;
		}
	}

	public void stop() {
		ExplorersCompass.LOGGER.info("SearchWorkerManager " + managerId + ": " + getName() + " stopped with " + (shouldLogRadius() ? getRadius() + " radius, " : "") + samples + " samples");
		finished = true;
	}

	protected int getRadius() {
		return StructureUtils.getHorizontalDistanceToLocation(startPos, currentPos.getX(), currentPos.getZ());
	}

	protected int roundRadius(int radius, int roundTo) {
		return ((int) radius / roundTo) * roundTo;
	}

	protected abstract String getName();

	protected abstract boolean shouldLogRadius();

}
