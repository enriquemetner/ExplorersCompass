package com.chaosthedude.explorerscompass.sorting;

import com.chaosthedude.explorerscompass.ExplorersCompass;
import com.chaosthedude.explorerscompass.util.StructureUtils;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Identifier;

@Environment(EnvType.CLIENT)
public class GroupSorting implements ISorting {
	
	@Override
	public int compare(Identifier id1, Identifier id2) {
		return ExplorersCompass.structureIDsToGroupIDs.get(id1).compareTo(ExplorersCompass.structureIDsToGroupIDs.get(id2));
	}

	@Override
	public String getValue(Identifier id) {
		return StructureUtils.getStructureName(ExplorersCompass.structureIDsToGroupIDs.get(id));
	}

	@Override
	public ISorting next() {
		return new NameSorting();
	}

	@Override
	public String getLocalizedName() {
		return "Group";
	}

}
