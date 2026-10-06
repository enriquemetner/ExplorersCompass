package com.chaosthedude.explorerscompass.items;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.UUID;
import java.util.Map;
import java.util.LinkedHashSet;

import com.chaosthedude.explorerscompass.ExplorersCompass;
import com.chaosthedude.explorerscompass.config.ExplorersCompassConfig;
import com.chaosthedude.explorerscompass.gui.GuiWrapper;
import com.chaosthedude.explorerscompass.network.SyncPacket;
import com.chaosthedude.explorerscompass.network.SearchResultPacket;
import com.chaosthedude.explorerscompass.util.CompassState;
import com.chaosthedude.explorerscompass.util.ItemUtils;
import com.chaosthedude.explorerscompass.util.PlayerUtils;
import com.chaosthedude.explorerscompass.util.StructureUtils;
import com.chaosthedude.explorerscompass.workers.SearchWorkerManager;
import com.google.common.collect.ListMultimap;

import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.Structure;

public class ExplorersCompassItem extends Item {

	public static final String NAME = "explorerscompass";

	private final Map<UUID, SearchSession> searches = new HashMap<>();

    private static class SearchSession {
        final SearchWorkerManager workers = new SearchWorkerManager();
        final ItemStack stack;
        final NbtCompound previousNbt;
        final int requestId;
        int maxRadius;
        SearchSession(ItemStack stack, int requestId) {
            this.stack = stack;
            this.previousNbt = stack.hasNbt() ? stack.getNbt().copy() : null;
            this.requestId = requestId;
        }
    }

    private SearchSession beginSearch(PlayerEntity player, ItemStack stack, int requestId) {
        cancelSearch(player, -1);
        SearchSession session = new SearchSession(stack, requestId);
        searches.put(player.getUuid(), session);
        return session;
    }

    public void cancelSearch(PlayerEntity player, int requestId) {
        SearchSession session = searches.get(player.getUuid());
        if (session != null && (requestId == -1 || session.requestId == requestId)) {
            session.workers.stop();
            session.workers.clear();
            searches.remove(player.getUuid());
            if (!session.stack.isEmpty() && session.stack.getItem() == this && getState(session.stack) == CompassState.SEARCHING) {
                session.stack.setNbt(session.previousNbt == null ? null : session.previousNbt.copy());
            }
        }
    }

	public void clearCompassTarget(ItemStack stack) {
		clearSearchData(stack);
	}

	private void rejectWrongDimension(PlayerEntity player, ItemStack stack, int requestId) {
		cancelSearch(player, -1);
		clearCompassTarget(stack);
		SearchResultPacket.send((ServerPlayerEntity) player, requestId, SearchResultPacket.WRONG_DIMENSION, 0);
	}

    private void finishSearch(PlayerEntity player, int result, int radius) {
        finishSearch(player, result, radius, null, 0, 0, false);
    }

    private void finishSearch(PlayerEntity player, int result, int radius, Identifier foundId, int foundX, int foundZ, boolean displayCoordinates) {
        SearchSession session = searches.remove(player.getUuid());
        if (session != null) {
            session.workers.stop();
            session.workers.clear();
            if (player instanceof ServerPlayerEntity serverPlayer) {
                SearchResultPacket.send(serverPlayer, session.requestId, result, radius, foundId, foundX, foundZ, displayCoordinates);
            }
        }
    }

    private List<Identifier> searchableInDimension(ServerWorld world, List<Identifier> ids) {
        List<Identifier> allowed = StructureUtils.getAllowedStructureIDs(world);
        List<Identifier> searchable = new ArrayList<>();
        for (Identifier id : ids) {
            Structure structure = StructureUtils.getStructureForID(world, id);
            if (allowed.contains(id) && structure != null && StructureUtils.getGeneratingDimensionIDs(world, structure).contains(world.getRegistryKey().getValue())) {
                searchable.add(id);
            }
        }
        return searchable;
    }

	private boolean rejectIfInsufficientXp(PlayerEntity player, int requestId) {
		int cost = ExplorersCompassConfig.defaultXpCost;
		if (!ExplorersCompassConfig.allowSearchWithoutEnoughXp && !player.getAbilities().creativeMode && cost > player.experienceLevel) {
			SearchResultPacket.send((ServerPlayerEntity) player, requestId, SearchResultPacket.INSUFFICIENT_XP, cost);
			return true;
		}
		return false;
	}

	public ExplorersCompassItem() {
		super(new FabricItemSettings().maxCount(1));
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
		if (!player.isSneaking()) {
			if (isBroken(player.getStackInHand(hand))) {
				return TypedActionResult.pass(player.getStackInHand(hand));
			}

			if (world.isClient) {
				final ItemStack stack = ItemUtils.getHeldItem(player, ExplorersCompass.EXPLORERS_COMPASS_ITEM);
				GuiWrapper.openGUI(world, player, stack);
			} else {
				final ServerWorld serverWorld = (ServerWorld) world;
				final ServerPlayerEntity serverPlayer = (ServerPlayerEntity) player;
				final boolean canTeleport = ExplorersCompassConfig.allowTeleport && PlayerUtils.canTeleport(player);
				final boolean hasInfiniteXp = player.getAbilities().creativeMode;
				final List<Identifier> allowedStructures = StructureUtils.getAllowedStructureIDs(serverWorld);
				final ListMultimap<Identifier, Identifier> dimensionsForAllowedStructures = StructureUtils.getGeneratingDimensionIDsForAllowedStructures(serverWorld, allowedStructures);
				final Map<Identifier, Identifier> structureIDsToGroupIDs = StructureUtils.getStructureIDsToGroupIDs(serverWorld);
				ServerPlayNetworking.send(serverPlayer, SyncPacket.ID, new SyncPacket(canTeleport, ExplorersCompassConfig.allowExcludeVisited, hasInfiniteXp, ExplorersCompassConfig.defaultXpCost, ExplorersCompassConfig.allowSearchWithoutEnoughXp, allowedStructures, dimensionsForAllowedStructures, structureIDsToGroupIDs));
			}
		} else {
			cancelSearch(player, -1);
			ItemStack stack = player.getStackInHand(hand);
			clearSearchData(stack);
		}

		return TypedActionResult.pass(player.getStackInHand(hand));
	}

	@Override
	public boolean isItemBarVisible(ItemStack stack) {
		int max = ExplorersCompassConfig.compassDurability;
		if (max > 0) {
			int damage = getDamage(stack);
			return damage > 0;
		}
		return false;
	}

	@Override
	public int getItemBarStep(ItemStack stack) {
		int max = ExplorersCompassConfig.compassDurability;
		if (max > 0) {
			int damage = getDamage(stack);
			return Math.round(13.0f * (1.0f - (float) damage / max));
		}
		return 13;
	}

	@Override
	public int getItemBarColor(ItemStack stack) {
		int max = ExplorersCompassConfig.compassDurability;
		int damage = getDamage(stack);
		float f = max > 0 ? (float) damage / max : 0.0f;
		return MathHelper.hsvToRgb(Math.max(0.0F, (1.0F - f) / 3.0F), 1.0F, 1.0F);
	}

	public void searchForStructure(ServerWorld world, PlayerEntity player, BlockPos pos, Identifier structureOrGroupId, boolean isGroup, boolean excludeVisited, ItemStack stack, int requestId) {
		if (!isBroken(stack)) {


			List<Identifier> structureIds;
			if (isGroup) {
				structureIds = StructureUtils.getStructuresForGroup(world, structureOrGroupId);
			} else {
				structureIds = List.of(structureOrGroupId);
			}

			List<Structure> structures = new ArrayList<Structure>();
            structureIds = searchableInDimension(world, structureIds);
            if (structureIds.isEmpty()) {
				rejectWrongDimension(player, stack, requestId);
                return;
            }
			if (rejectIfInsufficientXp(player, requestId)) return;
            SearchSession session = beginSearch(player, stack, requestId);
            setSearching(stack, structureOrGroupId);
            for (Identifier id : structureIds) {
				structures.add(StructureUtils.getStructureForID(world, id));
			}
			session.workers.createWorkers(world, player, stack, structures, structureOrGroupId, excludeVisited, pos);
			boolean started = session.workers.start();
			if (!started) {
				fail(player, stack, structureOrGroupId, 0, 0);
			}

		} else {
            SearchResultPacket.send((ServerPlayerEntity) player, requestId, SearchResultPacket.REJECTED, 0);
        }
	}

    public void searchForListedStructures(ServerWorld world, PlayerEntity player, BlockPos pos, List<Identifier> requestedIds, boolean excludeVisited, ItemStack stack, int requestId) {
        if (isBroken(stack) || requestedIds.isEmpty() || requestedIds.size() > 32767) {
            SearchResultPacket.send((ServerPlayerEntity) player, requestId, SearchResultPacket.REJECTED, 0);
            return;
        }
        List<Identifier> allowed = StructureUtils.getAllowedStructureIDs(world);
        if (!allowed.containsAll(requestedIds)) {
            SearchResultPacket.send((ServerPlayerEntity) player, requestId, SearchResultPacket.REJECTED, 0);
            return;
        }
        List<Identifier> uniqueIds = new ArrayList<>(new LinkedHashSet<>(requestedIds));
        List<Identifier> searchable = searchableInDimension(world, uniqueIds);
        if (searchable.size() != uniqueIds.size()) {
            rejectWrongDimension(player, stack, requestId);
            return;
        }
		if (rejectIfInsufficientXp(player, requestId)) return;
        List<Structure> structures = new ArrayList<>();
        for (Identifier id : searchable) structures.add(StructureUtils.getStructureForID(world, id));
        SearchSession session = beginSearch(player, stack, requestId);
        Identifier firstId = searchable.get(0);
        setSearching(stack, firstId);
		session.workers.createWorkers(world, player, stack, structures, firstId, excludeVisited, pos);
        if (!session.workers.start()) fail(player, stack, firstId, 0, 0);
    }

	public void succeed(PlayerEntity player, ItemStack stack, Identifier structureID, int x, int z, int samples, boolean displayCoordinates) {
		SearchSession session = searches.get(player.getUuid());
		if (session == null || session.stack != stack || structureID == null) return;
		int xpLevels = ExplorersCompassConfig.defaultXpCost;
		if (!ExplorersCompassConfig.allowSearchWithoutEnoughXp && !player.getAbilities().creativeMode && xpLevels > player.experienceLevel) {
			cancelSearch(player, session.requestId);
			SearchResultPacket.send((ServerPlayerEntity) player, session.requestId, SearchResultPacket.INSUFFICIENT_XP, xpLevels);
			return;
		}
		if (!player.getAbilities().creativeMode && xpLevels > 0) player.addExperienceLevels(-xpLevels);
		clearSearchData(stack);
		if (ItemUtils.verifyNBT(stack)) {
			stack.getNbt().putString("StructureID", structureID.toString());
			stack.getNbt().putInt("State", CompassState.FOUND.getID());
			stack.getNbt().putInt("FoundX", x);
			stack.getNbt().putInt("FoundZ", z);
			stack.getNbt().putString("FoundDimension", player.getWorld().getRegistryKey().getValue().toString());
			stack.getNbt().putInt("Samples", samples);
			stack.getNbt().putBoolean("DisplayCoordinates", displayCoordinates);
		}
		damageCompass(stack);
		finishSearch(player, SearchResultPacket.FOUND, 0, structureID, x, z, displayCoordinates);
	}

	public void fail(PlayerEntity player, ItemStack stack, Identifier structureId, int radius, int samples) {
        SearchSession session = searches.get(player.getUuid());
        if (session == null) return;
        session.maxRadius = Math.max(session.maxRadius, radius);
        session.workers.pop();
        boolean started = session.workers.start();
		if (!started) {
			clearSearchData(stack);
			if (ItemUtils.verifyNBT(stack)) {
				stack.getNbt().putString("StructureID", structureId.toString());
				stack.getNbt().putInt("State", CompassState.NOT_FOUND.getID());
				stack.getNbt().putInt("SearchRadius", session.maxRadius);
				stack.getNbt().putInt("Samples", samples);
			}
            finishSearch(player, SearchResultPacket.NOT_FOUND, ExplorersCompassConfig.maxRadius);
		}
	}

	public boolean isActive(ItemStack stack) {
		if (ItemUtils.verifyNBT(stack)) {
			return getState(stack) != CompassState.INACTIVE;
		}

		return false;
	}

	public void setSearching(ItemStack stack, Identifier structureID) {
		if (ItemUtils.verifyNBT(stack)) {
			stack.getNbt().putString("StructureID", structureID.toString());
			stack.getNbt().putInt("State", CompassState.SEARCHING.getID());
			stack.getNbt().putInt("SearchRadius", 0);
			stack.getNbt().putInt("Samples", 0);
		}
	}

	public void setFound(ItemStack stack, Identifier structureID, int x, int z, int samples) {
		if (ItemUtils.verifyNBT(stack)) {
			stack.getNbt().putString("StructureID", structureID.toString());
			stack.getNbt().putInt("State", CompassState.FOUND.getID());
			stack.getNbt().putInt("FoundX", x);
			stack.getNbt().putInt("FoundZ", z);
			stack.getNbt().putInt("Samples", samples);
		}
	}

	public void setNotFound(ItemStack stack, int searchRadius, int samples) {
		if (ItemUtils.verifyNBT(stack)) {
			stack.getNbt().putInt("State", CompassState.NOT_FOUND.getID());
			stack.getNbt().putInt("SearchRadius", searchRadius);
			stack.getNbt().putInt("Samples", samples);
		}
	}

	public void setInactive(ItemStack stack) {
		if (ItemUtils.verifyNBT(stack)) {
			stack.getNbt().putInt("State", CompassState.INACTIVE.getID());
		}
	}

	public void setState(ItemStack stack, BlockPos pos, CompassState state) {
		if (ItemUtils.verifyNBT(stack)) {
			stack.getNbt().putInt("State", state.getID());
		}
	}

	public void setFoundStructureX(ItemStack stack, int x) {
		if (ItemUtils.verifyNBT(stack)) {
			stack.getNbt().putInt("FoundX", x);
		}
	}

	public void setFoundStructureZ(ItemStack stack, int z) {
		if (ItemUtils.verifyNBT(stack)) {
			stack.getNbt().putInt("FoundZ", z);
		}
	}

	public void setStructureKey(ItemStack stack, Identifier structureID) {
		if (ItemUtils.verifyNBT(stack)) {
			stack.getNbt().putString("StructureID", structureID.toString());
		}
	}

	public void setSearchRadius(ItemStack stack, int searchRadius) {
		if (ItemUtils.verifyNBT(stack)) {
			stack.getNbt().putInt("SearchRadius", searchRadius);
		}
	}

	public void setSamples(ItemStack stack, int samples) {
		if (ItemUtils.verifyNBT(stack)) {
			stack.getNbt().putInt("Samples", samples);
		}
	}

	public void setDisplayCoordinates(ItemStack stack, boolean displayPosition) {
		if (ItemUtils.verifyNBT(stack)) {
			stack.getNbt().putBoolean("DisplayCoordinates", displayPosition);
		}
	}

	public CompassState getState(ItemStack stack) {
		if (ItemUtils.verifyNBT(stack)) {
			return CompassState.fromID(stack.getNbt().getInt("State"));
		}

		return null;
	}

	public int getFoundStructureX(ItemStack stack) {
		if (ItemUtils.verifyNBT(stack)) {
			return stack.getNbt().getInt("FoundX");
		}

		return 0;
	}

	public int getFoundStructureZ(ItemStack stack) {
		if (ItemUtils.verifyNBT(stack)) {
			return stack.getNbt().getInt("FoundZ");
		}

		return 0;
	}

	public Identifier getFoundDimension(ItemStack stack) {
		if (!ItemUtils.verifyNBT(stack)) return null;
		if (stack.getNbt().contains("FoundDimension")) return Identifier.tryParse(stack.getNbt().getString("FoundDimension"));
		Identifier structureId = Identifier.tryParse(stack.getNbt().getString("StructureID"));
		if (structureId == null || ExplorersCompass.allowedStructureIDsToDimensionIDs == null) return null;
		List<Identifier> dimensions = ExplorersCompass.allowedStructureIDsToDimensionIDs.get(structureId);
		return dimensions.stream().distinct().count() == 1 ? dimensions.get(0) : null;
	}

	public boolean isInFoundDimension(ItemStack stack, World world) {
		Identifier dimension = getFoundDimension(stack);
		return dimension == null || dimension.equals(world.getRegistryKey().getValue());
	}

	public Identifier getStructureID(ItemStack stack) {
		if (ItemUtils.verifyNBT(stack)) {
			return new Identifier(stack.getNbt().getString("StructureID"));
		}

		return new Identifier("");
	}

	public int getSearchRadius(ItemStack stack) {
		if (ItemUtils.verifyNBT(stack)) {
			return stack.getNbt().getInt("SearchRadius");
		}

		return -1;
	}

	public int getSamples(ItemStack stack) {
		if (ItemUtils.verifyNBT(stack)) {
			return stack.getNbt().getInt("Samples");
		}

		return -1;
	}

	public int getDistanceToBiome(PlayerEntity player, ItemStack stack) {
		return StructureUtils.getHorizontalDistanceToLocation(player, getFoundStructureX(stack), getFoundStructureZ(stack));
	}

	public boolean shouldDisplayCoordinates(ItemStack stack) {
		if (ItemUtils.verifyNBT(stack) && stack.getNbt().contains("DisplayCoordinates")) {
			return stack.getNbt().getBoolean("DisplayCoordinates");
		}

		return true;
	}

	public int getDamage(ItemStack stack) {
		if (ItemUtils.verifyNBT(stack) && stack.getNbt().contains("CompassDamage")) {
			return stack.getNbt().getInt("CompassDamage");
		}
		return 0;
	}

	public boolean isBroken(ItemStack stack) {
		int max = ExplorersCompassConfig.compassDurability;
		if (max > 0) {
			return getDamage(stack) >= max;
		}
		return false;
	}

	private void damageCompass(ItemStack stack) {
		int max = ExplorersCompassConfig.compassDurability;
		if (max > 0 && ItemUtils.verifyNBT(stack)) {
			int damage = getDamage(stack) + 1;
			stack.getNbt().putInt("CompassDamage", damage);
		}
	}

	private void clearSearchData(ItemStack stack) {
		if (ItemUtils.verifyNBT(stack)) {
			stack.getNbt().remove("State");
			stack.getNbt().remove("StructureID");
			stack.getNbt().remove("FoundX");
			stack.getNbt().remove("FoundZ");
			stack.getNbt().remove("FoundDimension");
			stack.getNbt().remove("PrevPos");
			stack.getNbt().remove("IsGroup");
			stack.getNbt().remove("IsListed");
			stack.getNbt().remove("DisplayCoordinates");
			stack.getNbt().remove("SearchRadius");
			stack.getNbt().remove("Samples");
		}
	}

}
