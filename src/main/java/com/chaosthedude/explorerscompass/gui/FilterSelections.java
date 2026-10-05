package com.chaosthedude.explorerscompass.gui;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.chaosthedude.explorerscompass.ExplorersCompass;

import net.minecraft.util.Identifier;

/** Filters are client-session preferences. An empty set leaves that category unrestricted. */
public final class FilterSelections {
	public static final Set<String> sources = new HashSet<>();
	public static final Set<Identifier> dimensions = new HashSet<>();
	public static final Set<Identifier> groups = new HashSet<>();
	private static boolean currentSelected;
	private static Identifier currentDimension;

	private FilterSelections() {
	}

	public static void clear() {
		sources.clear();
		dimensions.clear();
		groups.clear();
		currentSelected = false;
	}

	public static boolean isEmpty() {
		return sources.isEmpty() && dimensions.isEmpty() && groups.isEmpty();
	}

	public static boolean isCurrentSelected() {
		return currentSelected;
	}

	public static void openedInDimension(Identifier dimension) {
		if (currentSelected && currentDimension != null && !currentDimension.equals(dimension)) {
			dimensions.remove(currentDimension);
			dimensions.add(dimension);
		}
		currentDimension = dimension;
	}

	public static void toggleCurrent() {
		currentSelected = !currentSelected;
		if (currentSelected) {
			dimensions.add(currentDimension);
		} else {
			dimensions.remove(currentDimension);
		}
	}

	public static void toggleDimension(Identifier dimension) {
		if (!dimensions.add(dimension)) {
			dimensions.remove(dimension);
			if (currentSelected && dimension.equals(currentDimension)) {
				currentSelected = false;
			}
		}
	}

	public static boolean matches(Identifier structureId) {
		if (!sources.isEmpty() && !sources.contains(structureId.getNamespace())) {
			return false;
		}
		Identifier group = ExplorersCompass.structureIDsToGroupIDs.get(structureId);
		if (!groups.isEmpty() && !groups.contains(group)) {
			return false;
		}
		if (dimensions.isEmpty()) {
			return true;
		}
		List<Identifier> structureDimensions = ExplorersCompass.allowedStructureIDsToDimensionIDs.get(structureId);
		for (Identifier dimension : structureDimensions) {
			if (dimensions.contains(dimension)) {
				return true;
			}
		}
		return false;
	}
}
