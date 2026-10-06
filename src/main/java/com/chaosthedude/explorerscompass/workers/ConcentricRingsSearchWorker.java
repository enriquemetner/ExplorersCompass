package com.chaosthedude.explorerscompass.workers;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.chaosthedude.explorerscompass.config.ExplorersCompassConfig;
import com.chaosthedude.explorerscompass.util.StructureUtils;
import com.mojang.datafixers.util.Pair;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.gen.chunk.placement.ConcentricRingsStructurePlacement;
import net.minecraft.world.gen.structure.Structure;

public class ConcentricRingsSearchWorker extends StructureSearchWorker<ConcentricRingsStructurePlacement> {

	private List<ChunkPos> potentialChunks;
	private int chunkIndex;
	private double minDistance;
	private Pair<BlockPos, Structure> closest;

	public ConcentricRingsSearchWorker(ServerWorld level, PlayerEntity player, ItemStack stack, BlockPos startPos, ConcentricRingsStructurePlacement placement, List<Structure> structureSet, Identifier structureOrGroupId, boolean excludeVisited, String managerId) {
		super(level, player, stack, startPos, placement, structureSet, structureOrGroupId, excludeVisited, managerId);

		minDistance = Double.MAX_VALUE;
		chunkIndex = 0;
		List<ChunkPos> positions = level.getChunkManager().getStructurePlacementCalculator().getPlacementPositions(placement);
		potentialChunks = positions == null ? List.of() : new ArrayList<>(positions);
		potentialChunks.sort(Comparator.comparingInt(this::distanceToChunk));

		finished = !level.getServer().getSaveProperties().getGeneratorOptions().shouldGenerateStructures() || potentialChunks.isEmpty();
	}

	private int distanceToChunk(ChunkPos chunkPos) {
		return StructureUtils.getHorizontalDistanceToLocation(startPos,
			ChunkSectionPos.getOffsetPos(chunkPos.x, 8), ChunkSectionPos.getOffsetPos(chunkPos.z, 8));
	}

	@Override
	public boolean canWorkAt(int radiusLimit) {
		return !finished && samples < ExplorersCompassConfig.maxSamples && chunkIndex < potentialChunks.size()
			&& distanceToChunk(potentialChunks.get(chunkIndex)) < radiusLimit;
	}

	@Override
	public boolean hasWork() {
		return canWorkAt(radiusLimit);
	}

	@Override
	public boolean doWork() {
		super.doWork();
		if (hasWork()) {
			ChunkPos chunkPos = potentialChunks.get(chunkIndex);
			currentPos = new BlockPos(ChunkSectionPos.getOffsetPos(chunkPos.x, 8), 0, ChunkSectionPos.getOffsetPos(chunkPos.z, 8));
			double distance = startPos.getSquaredDistance(currentPos);

			if (closest == null || distance < minDistance) {
				Pair<BlockPos, Structure> pair = getStructureGeneratingAt(chunkPos);
				if (pair != null) {
					minDistance = distance;
					closest = pair;
				}
			}

			samples++;
			chunkIndex++;
		}

		if (hasWork()) {
			return true;
		}

		if (closest != null) {
			succeed(closest.getFirst(), closest.getSecond());
		} else if (!finished) {
			fail();
		}

		return false;
	}

	@Override
	protected String getName() {
		return "ConcentricRingsSearchWorker";
	}

	@Override
	public boolean shouldLogRadius() {
		return false;
	}

	// Non-optimized method to get the closest structure, for testing purposes
	private Pair<BlockPos, Structure> getClosest() {
		List<ChunkPos> list = level.getChunkManager().getStructurePlacementCalculator().getPlacementPositions(placement);
		if (list == null) {
			return null;
		} else {
			Pair<BlockPos, Structure> closestPair = null;
			double minDistance = Double.MAX_VALUE;
			BlockPos.Mutable sampleBlockPos = new BlockPos.Mutable();
			for (ChunkPos chunkPos : list) {
				sampleBlockPos.set(ChunkSectionPos.getOffsetPos(chunkPos.x, 8), 32, ChunkSectionPos.getOffsetPos(chunkPos.z, 8));
				double distance = sampleBlockPos.getSquaredDistance(startPos);
				if (closestPair == null || distance < minDistance) {
					Pair<BlockPos, Structure> pair = getStructureGeneratingAt(chunkPos);
					if (pair != null) {
						closestPair = pair;
						minDistance = distance;
					}
				}
			}

			return closestPair;
		}
	}

}
