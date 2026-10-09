package com.chaosthedude.explorerscompass;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.chaosthedude.explorerscompass.config.ExplorersCompassConfig;
import com.chaosthedude.explorerscompass.util.VisitedStructures;
import com.chaosthedude.explorerscompass.items.ExplorersCompassItem;
import com.chaosthedude.explorerscompass.network.SearchPacket;
import com.chaosthedude.explorerscompass.network.SearchListedPacket;
import com.chaosthedude.explorerscompass.network.CancelSearchPacket;
import com.chaosthedude.explorerscompass.network.ContinueSearchPacket;
import com.chaosthedude.explorerscompass.network.ResetCompassPacket;
import com.chaosthedude.explorerscompass.network.SyncPacket;
import com.chaosthedude.explorerscompass.network.TeleportPacket;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class ExplorersCompass implements ModInitializer {

	public static final String MODID = "explorerscompass";

	public static final Logger LOGGER = LogManager.getLogger(MODID);

	public static final ExplorersCompassItem EXPLORERS_COMPASS_ITEM = new ExplorersCompassItem();

	public static Map<Identifier, com.chaosthedude.explorerscompass.unlocking.RequirementNode> lockedRequirements = Map.of();
	public static boolean synced;
	public static boolean canTeleport;
	public static boolean canExcludeVisited;
	public static boolean infiniteXp;
	public static List<Identifier> allowedStructureIDs;
	public static int defaultXpCost;
	public static boolean allowSearchWithoutEnoughXp;
	public static ListMultimap<Identifier, Identifier> allowedStructureIDsToDimensionIDs;
	public static Map<Identifier, Identifier> structureIDsToGroupIDs;

	@Override
	public void onInitialize() {
		ExplorersCompassConfig.load();
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTED.register(
                com.chaosthedude.explorerscompass.unlocking.UnlockingRules::reload);
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(server ->
                com.chaosthedude.explorerscompass.unlocking.UnlockingRules.clear());
        net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                com.chaosthedude.explorerscompass.unlocking.UnlockingCommands.register(dispatcher));

		Registry.register(Registries.ITEM, new Identifier(MODID, "explorerscompass"), EXPLORERS_COMPASS_ITEM);

		ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries -> entries.add(EXPLORERS_COMPASS_ITEM));

		ServerPlayNetworking.registerGlobalReceiver(SearchPacket.ID, SearchPacket::apply);
		ServerPlayNetworking.registerGlobalReceiver(SearchListedPacket.ID, SearchListedPacket::apply);
		ServerPlayNetworking.registerGlobalReceiver(CancelSearchPacket.ID, CancelSearchPacket::apply);
		ServerPlayNetworking.registerGlobalReceiver(ContinueSearchPacket.ID, ContinueSearchPacket::apply);
		ServerPlayNetworking.registerGlobalReceiver(ResetCompassPacket.ID, ResetCompassPacket::apply);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> EXPLORERS_COMPASS_ITEM.cancelSearch(handler.player, -1));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> VisitedStructures.get(server).forgetPosition(handler.player.getUuid()));
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTicks() % 20 != 0 || server.getPlayerManager().getPlayerList().isEmpty()) return;
			VisitedStructures history = VisitedStructures.get(server);
			for (net.minecraft.server.network.ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
				com.chaosthedude.explorerscompass.unlocking.UnlockNotifications.deliverPending(player);
				history.checkPlayer(player);
			}
		});
		ServerPlayNetworking.registerGlobalReceiver(TeleportPacket.ID, TeleportPacket::apply);

		allowedStructureIDs = new ArrayList<Identifier>();
		allowedStructureIDsToDimensionIDs = ArrayListMultimap.create();
		structureIDsToGroupIDs = new HashMap<Identifier, Identifier>();
	}

}
