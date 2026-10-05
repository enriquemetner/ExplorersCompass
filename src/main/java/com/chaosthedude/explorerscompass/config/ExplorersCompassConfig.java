package com.chaosthedude.explorerscompass.config;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import com.chaosthedude.explorerscompass.ExplorersCompass;
import com.chaosthedude.explorerscompass.util.OverlaySide;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.fabricmc.loader.api.FabricLoader;

public class ExplorersCompassConfig {

	private static Path configFilePath;
	private static Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	public static boolean allowTeleport = true;
	public static int maxNextSearches = 100;
	public static boolean displayCoordinates = true;
	public static int maxRadius = 10000;
	public static int maxSamples = 100000;
	public static int compassDurability = 0;
	public static int defaultXpLevel = 0;
	public static Map<String, Integer> perStructureXpLevels = new HashMap<String, Integer>();
	public static List<String> structureBlacklist = new ArrayList<String>();
	public static List<String> hiddenStructures = new ArrayList<String>();

	public static boolean displayWithChatOpen = true;
	public static boolean pauseGameWhenOpen = false;
	public static boolean clearFiltersOnClose = true;
	public static boolean clearSearchBarOnClose = true;
	public static boolean closeOnFound = true;
	public static boolean checkedFiltersFirst = false;
	public static boolean adjustRowsToFitView = true;
	public static boolean translateStructureNames = true;
	public static int overlayLineOffset = 1;
	public static OverlaySide overlaySide = OverlaySide.LEFT;
	public static List<String> sortOrder = defaultSortOrder();

	private static List<String> defaultSortOrder() {
		return new ArrayList<String>(Arrays.asList("NAME", "SOURCE", "DIMENSION", "GROUP"));
	}

	private static List<String> normalizeSortOrder(List<String> savedOrder) {
		if (savedOrder == null || savedOrder.size() != 4 || !new HashSet<String>(savedOrder).equals(new HashSet<String>(defaultSortOrder()))) {
			return defaultSortOrder();
		}
		return new ArrayList<String>(savedOrder);
	}

	public static void load() {
		Reader reader;
		if(getFilePath().toFile().exists()) {
			try {
				reader = Files.newBufferedReader(getFilePath());

				Data data = gson.fromJson(reader, Data.class);

				allowTeleport = data.common.allowTeleport;
				maxNextSearches = data.common.maxNextSearches;
				displayCoordinates = data.common.displayCoordinates;
				maxRadius = data.common.maxRadius;
				maxSamples = data.common.maxSamples;
				compassDurability = data.common.compassDurability;
				defaultXpLevel = data.common.defaultXpLevel;
				perStructureXpLevels = data.common.perStructureXpLevels;
				structureBlacklist = data.common.structureBlacklist;

				displayWithChatOpen = data.client.displayWithChatOpen;
				pauseGameWhenOpen = data.client.pauseGameWhenOpen;
				clearFiltersOnClose = data.client.clearFiltersOnClose == null || data.client.clearFiltersOnClose;
				clearSearchBarOnClose = data.client.clearSearchBarOnClose == null || data.client.clearSearchBarOnClose;
				closeOnFound = data.client.closeOnFound == null || data.client.closeOnFound;
				checkedFiltersFirst = data.client.checkedFiltersFirst;
				adjustRowsToFitView = data.client.adjustRowsToFitView == null || data.client.adjustRowsToFitView;
				translateStructureNames = data.client.translateStructureNames;
				overlayLineOffset = data.client.overlayLineOffset;
				overlaySide = data.client.overlaySide;
				sortOrder = normalizeSortOrder(data.client.sortOrder);
			hiddenStructures = data.client.hiddenStructures == null ? new ArrayList<String>() : new ArrayList<String>(data.client.hiddenStructures);

				reader.close();
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
		save();
	}

	public static void save() {
		try {
			Writer writer = Files.newBufferedWriter(getFilePath());
			Data data = new Data(new Data.Common(allowTeleport, maxNextSearches, displayCoordinates, maxRadius, maxSamples, compassDurability, defaultXpLevel, perStructureXpLevels, structureBlacklist), new Data.Client(displayWithChatOpen, pauseGameWhenOpen, clearFiltersOnClose, clearSearchBarOnClose, closeOnFound, checkedFiltersFirst, adjustRowsToFitView, translateStructureNames, overlayLineOffset, overlaySide, sortOrder, hiddenStructures));
			gson.toJson(data, writer);
			writer.close();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private static Path getFilePath() {
		if(configFilePath == null) {
			configFilePath = FabricLoader.getInstance().getConfigDir().resolve(ExplorersCompass.MODID + ".json");
		}
		return configFilePath;
	}

	private static class Data {

		private Common common;
		private Client client;

		public Data(Common common, Client client) {
			this.common = common;
			this.client = client;
		}

		private static class Common {
			private final String allowTeleportComment = "Allows a player to teleport to a located structure when in creative mode, opped, or in cheat mode.";
			private final boolean allowTeleport;

			private final String maxNextSearchesComment = "The maximum number of times a player can search for the next instance of a located structure, excluding already-found locations. Set to 0 to disable searching for additional structure instances and make the compass always locate the nearest instance.";
			private final int maxNextSearches;

			private final String displayCoordinatesComment = "Allows players to view the precise coordinates and distance of a located structure on the HUD, rather than relying on the direction the compass is pointing.";
			private final boolean displayCoordinates;

			private final String maxRadiusComment = "The maximum radius that will be searched for a structure. Raising this value will increase search accuracy but will potentially make the process more resource intensive.";
			private final int maxRadius;

			private final String maxSamplesComment = "The maximum number of samples to be taken when searching for a structure.";
			private final int maxSamples;

			private final String compassDurabilityComment = "The number of successful structure searches before the compass breaks and must be repaired. Set to 0 to disable durability.";
			private final int compassDurability;

			private final String defaultXpLevelComment = "The default number of XP levels consumed when searching for a structure. Individual structures can be configured via perStructureXpLevels. Max of 3 levels.";
			private final int defaultXpLevel;

			private final String perStructureXpLevelsComment = "A map of per-structure XP level costs that override defaultXpLevel. Structures not listed here use defaultXpLevel. Max of 3 levels. The wildcard character * can be used to match any number of characters, and ? can be used to match one character. Ex: {\"minecraft:buried_treasure\":3, \"minecraft:end*\":2, \"minecraft:*village*\":1}";
			private final Map<String, Integer> perStructureXpLevels;

			private final String structureBlacklistComment = "A list of structures that the compass will not display in the GUI and will not be able to search for. The wildcard character * can be used to match any number of characters, and ? can be used to match one character. Ex (ignore backslashes): [\"minecraft:stronghold\", \"minecraft:endcity\", \"minecraft:*village*\"]";
			private final List<String> structureBlacklist;

			private Common() {
				allowTeleport = true;
				maxNextSearches = 100;
				displayCoordinates = true;
				maxRadius = 10000;
				maxSamples = 100000;
				compassDurability = 0;
				defaultXpLevel = 0;
				perStructureXpLevels = new HashMap<String, Integer>();
				structureBlacklist = new ArrayList<String>();
			}

			private Common(boolean allowTeleport, int maxNextSearches, boolean displayCoordinates, int maxRadius, int maxSamples, int compassDurability, int defaultXpLevel, Map<String, Integer> perStructureXpLevels, List<String> structureBlacklist) {
				this.allowTeleport = allowTeleport;
				this.maxNextSearches = maxNextSearches;
				this.displayCoordinates = displayCoordinates;
				this.maxRadius = maxRadius;
				this.maxSamples = maxSamples;
				this.compassDurability = compassDurability;
				this.defaultXpLevel = defaultXpLevel;
				this.perStructureXpLevels = perStructureXpLevels;
				this.structureBlacklist = structureBlacklist;
			}
		}

		private static class Client {
			private final String displayWithChatOpenComment = "Displays compass information even while chat is open.";
			private final boolean displayWithChatOpen;
			private final String pauseGameWhenOpenComment = "Pauses the game in single-player while the compass screen is open.";
			private final boolean pauseGameWhenOpen;
			private final String clearFiltersOnCloseComment = "Clears selected structure filters when the compass screen closes.";
			private final Boolean clearFiltersOnClose;
			private final String clearSearchBarOnCloseComment = "Clears the structure search bar when the compass screen closes.";
			private final Boolean clearSearchBarOnClose;
			private final Boolean closeOnFound;
			private final String checkedFiltersFirstComment = "Displays checked filter options above unchecked options in each filter column.";
			private final boolean checkedFiltersFirst;
			private final String adjustRowsToFitViewComment = "Adjusts row spacing, showing less content but only fully visible list entries. Smooth scrolling may still show partial entries.";
			private final Boolean adjustRowsToFitView;

			private final String translateStructureNamesComment = "Attempts to translate structure names before fixing the unlocalized names. Translations may not be available for all structures.";
			private final boolean translateStructureNames;

			private final String overlayLineOffsetComment = "The line offset for information rendered on the HUD.";
			private final int overlayLineOffset;

			private final String overlaySideComment = "The side for information rendered on the HUD. Ex: LEFT, RIGHT";
			private final OverlaySide overlaySide;
			private final List<String> sortOrder;
			private final List<String> hiddenStructures;

			private Client() {
				displayWithChatOpen = true;
				pauseGameWhenOpen = false;
				clearFiltersOnClose = true;
				clearSearchBarOnClose = true;
				closeOnFound = true;
				checkedFiltersFirst = false;
				adjustRowsToFitView = true;
				translateStructureNames = true;
				overlayLineOffset = 1;
				overlaySide = OverlaySide.LEFT;
				sortOrder = defaultSortOrder();
			hiddenStructures = new ArrayList<String>();
			}

			private Client(boolean displayWithChatOpen, boolean pauseGameWhenOpen, boolean clearFiltersOnClose, boolean clearSearchBarOnClose, boolean closeOnFound, boolean checkedFiltersFirst, boolean adjustRowsToFitView, boolean translateStructureNames, int overlayLineOffset, OverlaySide overlaySide, List<String> sortOrder, List<String> hiddenStructures) {
				this.displayWithChatOpen = displayWithChatOpen;
				this.pauseGameWhenOpen = pauseGameWhenOpen;
				this.clearFiltersOnClose = clearFiltersOnClose;
				this.clearSearchBarOnClose = clearSearchBarOnClose;
				this.closeOnFound = closeOnFound;
				this.checkedFiltersFirst = checkedFiltersFirst;
				this.adjustRowsToFitView = adjustRowsToFitView;
				this.translateStructureNames = translateStructureNames;
				this.overlayLineOffset = overlayLineOffset;
				this.overlaySide = overlaySide;
				this.sortOrder = normalizeSortOrder(sortOrder);
				this.hiddenStructures = new ArrayList<String>(hiddenStructures);
			}
		}
	}

}
