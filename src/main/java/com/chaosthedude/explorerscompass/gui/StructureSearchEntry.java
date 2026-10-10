package com.chaosthedude.explorerscompass.gui;

import com.chaosthedude.explorerscompass.ExplorersCompass;
import com.chaosthedude.explorerscompass.config.ExplorersCompassConfig;
import com.chaosthedude.explorerscompass.util.RenderUtils;
import com.chaosthedude.explorerscompass.util.StructureUtils;
import com.mojang.blaze3d.systems.RenderSystem;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.AlwaysSelectedEntryListWidget;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;

@Environment(EnvType.CLIENT)
public class StructureSearchEntry extends AlwaysSelectedEntryListWidget.Entry<StructureSearchEntry> {

	private final MinecraftClient client;
	private final ExplorersCompassScreen parentScreen;
	private final Identifier structureID;
	private final StructureSearchList structuresList;
	private long lastClickTime;

	public StructureSearchEntry(StructureSearchList structuresList, Identifier structureID) {
		this.structuresList = structuresList;
		this.structureID = structureID;
		parentScreen = structuresList.getParentScreen();
		client = MinecraftClient.getInstance();
	}

	@Override
	public void render(DrawContext context, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovered, float tickDelta) {
        boolean locked = parentScreen.isStructureLocked(structureID);
        int nameColor = !locked ? 0xffffff : 0xc0c0c0;
        int infoColor = !locked ? 0x808080 : 0x707070;
        int buttonWidth = parentScreen.rowActionButtonWidth();
        int buttonX = left + width - buttonWidth - 7;
        int buttonHeight = StructureSearchList.ACTION_BUTTON_HEIGHT;
        int buttonY = top + (height - (buttonHeight * 2 + StructureSearchList.ACTION_BUTTON_GAP)) / 2;
        int textWidth = Math.max(1, buttonX - left - 10);
        String readableName = StructureUtils.getStructureName(structureID);
        int nameY = top + (height / 2) - ((client.textRenderer.fontHeight + 2) * 2);
        if (ExplorersCompassConfig.showTechnicalNames) {
            String technicalName = " (" + structureID + ")";
            int nameWidth = Math.max(textWidth / 2, textWidth - client.textRenderer.getWidth(technicalName));
            String visibleName = client.textRenderer.trimToWidth(readableName, nameWidth);
            int remainingWidth = Math.max(0, textWidth - client.textRenderer.getWidth(visibleName));
            context.drawText(client.textRenderer, visibleName, left + 5, nameY, nameColor, true);
            int bracketWidth = client.textRenderer.getWidth(" ()");
            if (remainingWidth >= bracketWidth) {
                String visibleId = client.textRenderer.trimToWidth(structureID.toString(), remainingWidth - bracketWidth);
                int technicalX = left + 5 + client.textRenderer.getWidth(visibleName);
                context.drawText(client.textRenderer, " (", technicalX, nameY, nameColor, true);
                technicalX += client.textRenderer.getWidth(" (");
                context.drawText(client.textRenderer, visibleId, technicalX, nameY, infoColor, true);
                context.drawText(client.textRenderer, ")", technicalX + client.textRenderer.getWidth(visibleId), nameY, nameColor, true);
            }
        } else {
            context.drawText(client.textRenderer, client.textRenderer.trimToWidth(readableName, textWidth), left + 5, nameY, nameColor, true);
        }
        context.drawText(client.textRenderer, client.textRenderer.trimToWidth(Text.literal("Source").getString() + ": " + StructureUtils.getStructureSource(structureID), textWidth), left + 5, top + (height / 2) - ((client.textRenderer.fontHeight + 2) * 1), infoColor, true);
        context.drawText(client.textRenderer, client.textRenderer.trimToWidth(Text.literal("Group").getString() + ": " + StructureUtils.getStructureName(ExplorersCompass.structureIDsToGroupIDs.get(structureID)), textWidth), left + 5, top + (height / 2) - ((client.textRenderer.fontHeight + 2) * 0), infoColor, true);
        context.drawText(client.textRenderer, client.textRenderer.trimToWidth(Text.literal("Dimension").getString() + ": " + StructureUtils.structureDimensionsToString(ExplorersCompass.allowedStructureIDsToDimensionIDs.get(structureID)), textWidth), left + 5, top + (height / 2) + ((client.textRenderer.fontHeight + 2) * 1), infoColor, true);
        boolean filterHovered = structuresList.isFilterButtonClick(this, mouseX, mouseY);
        context.fill(buttonX, buttonY, buttonX + buttonWidth, buttonY + buttonHeight, 0xff909090);
        context.fill(buttonX + 1, buttonY + 1, buttonX + buttonWidth - 1, buttonY + buttonHeight - 1, filterHovered ? 0xff424242 : 0xff242424);
        context.drawCenteredTextWithShadow(client.textRenderer, Text.literal("filters"), buttonX + buttonWidth / 2, buttonY + 4, 0xffffff);
        buttonY += buttonHeight + StructureSearchList.ACTION_BUTTON_GAP;
        boolean buttonHovered = structuresList.isActionButtonClick(this, mouseX, mouseY);
        context.fill(buttonX, buttonY, buttonX + buttonWidth, buttonY + buttonHeight, 0xff909090);
        context.fill(buttonX + 1, buttonY + 1, buttonX + buttonWidth - 1, buttonY + buttonHeight - 1, buttonHovered ? 0xff424242 : 0xff242424);
        context.drawCenteredTextWithShadow(client.textRenderer, Text.literal(parentScreen.isStructureHidden(structureID)
                ? "Undo" : "hide"), buttonX + buttonWidth / 2, buttonY + 4, 0xffffff);
		if (locked) {
			context.drawCenteredTextWithShadow(client.textRenderer, Text.literal("Locked"),
                    left + width / 2, top + (height - client.textRenderer.fontHeight) / 2, 0xffffff);
        }
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == 0 && isEnabled()) {
			if (structuresList.isFilterButtonClick(this, mouseX, mouseY)) {
				client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0F));
				parentScreen.openEntryFilters(structureID);
				lastClickTime = 0;
				return true;
			}
			if (structuresList.isActionButtonClick(this, mouseX, mouseY)) {
				client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0F));
				parentScreen.toggleStructureHidden(structureID);
				lastClickTime = 0;
				return true;
			}
			structuresList.setSelected(this);
			if (!parentScreen.isStructureHidden(structureID) && Util.getMeasuringTimeMs() - lastClickTime < 250L) {
				searchForStructure();
				return true;
			} else {
				lastClickTime = Util.getMeasuringTimeMs();
				return false;
			}
		}
		return false;
	}

	public void searchForStructure() {
		client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0F));
		parentScreen.searchForStructure(structureID);
	}

	public boolean isEnabled() {
		return true;
	}

	public Identifier getStructureID() {
		return structureID;
	}

	public Identifier getGroupID() {
		return ExplorersCompass.structureIDsToGroupIDs.get(structureID);
	}

	@Override
	public Text getNarration() {
		return Text.literal(StructureUtils.getStructureName(structureID)).append(parentScreen.isStructureLocked(structureID)
                ? Text.literal(". ").append(Text.literal("Locked")) : Text.empty());
	}

}
