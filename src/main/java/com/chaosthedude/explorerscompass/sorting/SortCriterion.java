package com.chaosthedude.explorerscompass.sorting;

import java.text.Collator;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

@Environment(EnvType.CLIENT)
public enum SortCriterion {
	NAME("name", new NameSorting()),
	SOURCE("source", new SourceSorting()),
	DIMENSION("dimension", new DimensionSorting()),
	GROUP("group", new GroupSorting());

	private final String translationSuffix;
	private final ISorting comparator;

	SortCriterion(String translationSuffix, ISorting comparator) {
		this.translationSuffix = translationSuffix;
		this.comparator = comparator;
	}

	public int compare(Identifier first, Identifier second, Collator collator) {
		return collator.compare(comparator.getValue(first), comparator.getValue(second));
	}

	public Text getLabel() {
		return Text.translatable("string.explorerscompass." + translationSuffix);
	}
}
