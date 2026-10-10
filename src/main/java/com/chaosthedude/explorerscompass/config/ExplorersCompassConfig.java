package com.chaosthedude.explorerscompass.config;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import com.chaosthedude.explorerscompass.ExplorersCompass;
import com.chaosthedude.explorerscompass.util.OverlaySide;
import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.toml.TomlParser;
import com.electronwill.nightconfig.toml.TomlWriter;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.api.EnvType;

public class ExplorersCompassConfig {
	private static final String COMMON = "common.toml";
	private static final String CLIENT = "client.toml";
	private static final String COMMON_BLACKLIST = "common_blacklist.toml";
	private static final String HIDDEN_STRUCTURES = "hidden_structures.toml";

	public static boolean allowTeleport = true;
	public static boolean allowExcludeVisited = true;
	public static boolean displayCoordinates = true;
	public static boolean displayDistance = true;
	public static int maxSamples = 100000;
	public static int compassDurability = 0;
	public static int defaultXpCost = 0;
	public static boolean allowSearchWithoutEnoughXp = false;
	public static List<String> structureBlacklist = new ArrayList<>();
	public static List<String> hiddenStructures = new ArrayList<>();

	public static boolean displayWithChatOpen = true;
	public static boolean pauseGameWhenOpen = false;
	public static boolean clearFiltersOnClose = true;
	public static boolean clearSearchBarOnClose = true;
	public static boolean closeOnFound = true;
	public static boolean checkedFiltersFirst = true;
	public static boolean adjustRowsToFitView = true;
	public static boolean showTechnicalNames = false;
	public static int overlayLineOffset = 0;
	public static OverlaySide overlaySide = OverlaySide.LEFT;
	public static List<String> sortOrder = defaultSortOrder();

	private static List<String> defaultSortOrder() {
		return new ArrayList<>(Arrays.asList("NAME", "SOURCE", "DIMENSION", "GROUP"));
	}

	private static List<String> normalizeSortOrder(List<String> savedOrder) {
		if (savedOrder == null || savedOrder.size() != 4 || !new HashSet<>(savedOrder).equals(new HashSet<>(defaultSortOrder()))) {
			return defaultSortOrder();
		}
		return new ArrayList<>(savedOrder);
	}

	public static void load() {
		try {
			Files.createDirectories(directory());
			boolean client = FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT;
			loadCommon(readOrCreate(COMMON));
			structureBlacklist = strings(readOrCreate(COMMON_BLACKLIST), "structureBlacklist", structureBlacklist);
			if (client) {
				loadClient(readOrCreate(CLIENT));
				hiddenStructures = strings(readOrCreate(HIDDEN_STRUCTURES), "hiddenStructures", hiddenStructures);
			}
		} catch (IOException e) {
			throw new RuntimeException("Could not load Explorer's Compass configuration", e);
		}
	}

	private static Path directory() {
		return FabricLoader.getInstance().getConfigDir().resolve(ExplorersCompass.MODID);
	}

	private static CommentedConfig readOrCreate(String name) throws IOException {
		Path path = directory().resolve(name);
		if (Files.exists(path)) return parse(path);
		CommentedConfig config = CommentedConfig.inMemory();
		switch (name) {
			case COMMON -> writeCommon(config);
			case CLIENT -> writeClient(config);
			case COMMON_BLACKLIST -> writeCommonBlacklist(config);
			case HIDDEN_STRUCTURES -> writeHiddenStructures(config);
			default -> throw new IllegalArgumentException(name);
		}
		write(path, config);
		return config;
	}

	private static CommentedConfig parse(Path path) throws IOException {
		try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			return new TomlParser().parse(reader);
		} catch (RuntimeException e) {
			throw new IllegalArgumentException("Invalid TOML in " + path, e);
		}
	}

	private static void write(Path path, CommentedConfig config) throws IOException {
		Files.createDirectories(path.getParent());
		try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
			new TomlWriter().write(config, writer);
		}
	}

	private static boolean bool(CommentedConfig config, String key, boolean fallback) {
		Object value = config.get(key);
		if (value == null) return fallback;
		if (value instanceof Boolean result) return result;
		throw bad(key, "boolean");
	}

	private static int number(CommentedConfig config, String key, int fallback, int minimum) {
		Object value = config.get(key);
		if (value == null) return fallback;
		if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue())
				|| number.doubleValue() != number.longValue()
				|| number.longValue() < minimum || number.longValue() > Integer.MAX_VALUE) throw bad(key, "integer >= " + minimum);
		return number.intValue();
	}

	private static List<String> strings(CommentedConfig config, String key, List<String> fallback) {
		Object value = config.get(key);
		if (value == null) return new ArrayList<>(fallback);
		if (!(value instanceof List<?> list)) throw bad(key, "string array");
		List<String> result = new ArrayList<>();
		for (Object item : list) {
			if (!(item instanceof String entry)) throw bad(key, "string array");
			result.add(entry);
		}
		return result;
	}

	private static IllegalArgumentException bad(String key, String expected) {
		return new IllegalArgumentException("Invalid Explorer's Compass setting '" + key + "'; expected " + expected);
	}

	private static void loadCommon(CommentedConfig config) {
		allowTeleport = bool(config, "permissions.allowTeleport", allowTeleport);
		allowExcludeVisited = bool(config, "search.allowExcludeVisited", allowExcludeVisited);
		displayCoordinates = bool(config, "compass.displayCoordinates", displayCoordinates);
		displayDistance = bool(config, "compass.displayDistance", displayDistance);
		maxSamples = number(config, "search.maxSamples", maxSamples, 1);
		compassDurability = number(config, "compass.compassDurability", compassDurability, 0);
		defaultXpCost = number(config, "search.defaultXpCost", defaultXpCost, 0);
		allowSearchWithoutEnoughXp = bool(config, "search.allowSearchWithoutEnoughXp", allowSearchWithoutEnoughXp);
	}

	private static void loadClient(CommentedConfig config) {
		displayWithChatOpen = bool(config, "overlay.displayWithChatOpen", displayWithChatOpen);
		pauseGameWhenOpen = bool(config, "behavior.pauseGameWhenOpen", pauseGameWhenOpen);
		clearFiltersOnClose = bool(config, "behavior.clearFiltersOnClose", clearFiltersOnClose);
		clearSearchBarOnClose = bool(config, "behavior.clearSearchBarOnClose", clearSearchBarOnClose);
		closeOnFound = bool(config, "behavior.closeOnFound", closeOnFound);
		checkedFiltersFirst = bool(config, "view.checkedFiltersFirst", checkedFiltersFirst);
		adjustRowsToFitView = bool(config, "view.adjustRowsToFitView", adjustRowsToFitView);
		showTechnicalNames = bool(config, "view.showTechnicalNames", showTechnicalNames);
		overlayLineOffset = number(config, "overlay.overlayLineOffset", overlayLineOffset, Integer.MIN_VALUE);
		Object side = config.get("overlay.overlaySide");
		if (side != null) {
			try {
				overlaySide = OverlaySide.valueOf((String) side);
			} catch (ClassCastException | IllegalArgumentException e) {
				throw bad("overlay.overlaySide", "LEFT or RIGHT");
			}
		}
		List<String> order = strings(config, "view.sortOrder", sortOrder);
		if (!normalizeSortOrder(order).equals(order)) throw bad("view.sortOrder", "permutation of NAME, SOURCE, DIMENSION, GROUP");
		sortOrder = order;
	}

	private static void put(CommentedConfig config, String key, Object value, String comment) {
		config.set(key, value);
		if (config.getComment(key) == null) config.setComment(key, comment);
	}

	private static void writeCommon(CommentedConfig config) {
		put(config, "permissions.allowTeleport", allowTeleport, "Allow teleporting to a located structure when permitted by player permissions.");
		put(config, "search.allowExcludeVisited", allowExcludeVisited, "Allow players to exclude visited structure instances from searches. Visits are always recorded.");
		put(config, "compass.displayCoordinates", displayCoordinates, "Show precise coordinates for located structures.");
		put(config, "compass.displayDistance", displayDistance, "Show distance to located structures.");
		put(config, "search.maxSamples", maxSamples, "Maximum search samples; must be at least 1.");
		put(config, "compass.compassDurability", compassDurability, "Successful searches before the compass breaks; 0 disables durability.");
		put(config, "search.defaultXpCost", defaultXpCost, "XP levels consumed after a successful search.");
		put(config, "search.allowSearchWithoutEnoughXp", allowSearchWithoutEnoughXp, "Permit searching with insufficient XP; a successful search drains remaining XP.");
	}

	private static void writeClient(CommentedConfig config) {
		put(config, "overlay.displayWithChatOpen", displayWithChatOpen, "Show compass information while chat is open.");
		put(config, "behavior.pauseGameWhenOpen", pauseGameWhenOpen, "Pause single-player while the compass screen is open.");
		put(config, "behavior.clearFiltersOnClose", clearFiltersOnClose, "Clear filters when the compass screen closes.");
		put(config, "behavior.clearSearchBarOnClose", clearSearchBarOnClose, "Clear the search bar when the compass screen closes.");
		put(config, "behavior.closeOnFound", closeOnFound, "Close the compass screen after finding a structure.");
		put(config, "view.checkedFiltersFirst", checkedFiltersFirst, "Show checked filters before unchecked filters.");
		put(config, "view.adjustRowsToFitView", adjustRowsToFitView, "Adjust row spacing so list entries fit the view.");
		put(config, "view.showTechnicalNames", showTechnicalNames, "Show and search technical structure IDs.");
		put(config, "overlay.overlayLineOffset", overlayLineOffset, "Vertical line offset of the HUD overlay.");
		put(config, "overlay.overlaySide", overlaySide.name(), "HUD overlay side: LEFT or RIGHT.");
		put(config, "view.sortOrder", sortOrder, "Order of the four structure sorting keys.");
	}

	private static void writeCommonBlacklist(CommentedConfig config) {
		put(config, "structureBlacklist", structureBlacklist, "Structures excluded from the compass. Supports * and ? wildcards.");
	}

	private static void writeHiddenStructures(CommentedConfig config) {
		put(config, "hiddenStructures", hiddenStructures, "Structures hidden from this client's compass list.");
	}

	private static void saveFile(String name, java.util.function.Consumer<CommentedConfig> update) {
		try {
			Path path = directory().resolve(name);
			CommentedConfig config = Files.exists(path) ? parse(path) : CommentedConfig.inMemory();
			update.accept(config);
			write(path, config);
		} catch (IOException e) {
			throw new RuntimeException("Could not save Explorer's Compass " + name, e);
		}
	}

	public static void saveClient() {
		saveFile(CLIENT, ExplorersCompassConfig::writeClient);
	}

	public static void saveCommon() {
		saveFile(COMMON, ExplorersCompassConfig::writeCommon);
	}

	public static void saveCommonBlacklist() {
		saveFile(COMMON_BLACKLIST, ExplorersCompassConfig::writeCommonBlacklist);
	}

	public static void saveHiddenStructures() {
		saveFile(HIDDEN_STRUCTURES, ExplorersCompassConfig::writeHiddenStructures);
	}
}
