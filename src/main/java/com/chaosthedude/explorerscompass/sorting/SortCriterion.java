package com.chaosthedude.explorerscompass.sorting;

import java.text.Collator;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

@Environment(EnvType.CLIENT)
public enum SortCriterion {
	NAME("Name", new NameSorting()),
	SOURCE("Source", new SourceSorting()),
	DIMENSION("Dimension", new DimensionSorting()),
	GROUP("Group", new GroupSorting());

	private final String label;
	private final ISorting comparator;

	SortCriterion(String label, ISorting comparator) {
		this.label = label;
		this.comparator = comparator;
	}

	public int compare(Identifier first, Identifier second, Collator collator) {
		return collator.compare(comparator.getValue(first), comparator.getValue(second));
	}

	public Text getLabel() {
		return Text.literal(label);
	}
}
