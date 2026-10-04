package com.chaosthedude.explorerscompass.sorting;

import com.chaosthedude.explorerscompass.util.StructureUtils;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.util.Identifier;

@Environment(EnvType.CLIENT)
public class NameSorting implements ISorting {
	
	@Override
	public int compare(Identifier id1, Identifier id2) {
		return StructureUtils.getStructureName(id1).compareTo(StructureUtils.getStructureName(id2));
	}

	@Override
	public String getValue(Identifier id) {
		return StructureUtils.getStructureName(id);
	}

	@Override
	public ISorting next() {
		return new SourceSorting();
	}

	@Override
	public String getLocalizedName() {
		return I18n.translate("string.explorerscompass.name");
	}

}
