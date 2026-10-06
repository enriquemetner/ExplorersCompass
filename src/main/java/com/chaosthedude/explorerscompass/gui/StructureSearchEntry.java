package com.chaosthedude.explorerscompass.gui;

import com.chaosthedude.explorerscompass.ExplorersCompass;
import com.chaosthedude.explorerscompass.config.ExplorersCompassConfig;
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
        int nameColor = isEnabled() ? 0xffffff : 0x808080;
        int infoColor = isEnabled() ? 0x808080 : 0x555555;
        int buttonWidth = parentScreen.rowActionButtonWidth();
        int buttonX = left + width - buttonWidth - 8;
        int buttonY = top + (height - 20) / 2;
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
        context.drawText(client.textRenderer, client.textRenderer.trimToWidth(Text.translatable("string.explorerscompass.source").getString() + ": " + StructureUtils.getStructureSource(structureID), textWidth), left + 5, top + (height / 2) - ((client.textRenderer.fontHeight + 2) * 1), infoColor, true);
        context.drawText(client.textRenderer, client.textRenderer.trimToWidth(Text.translatable("string.explorerscompass.group").getString() + ": " + StructureUtils.getStructureName(ExplorersCompass.structureIDsToGroupIDs.get(structureID)), textWidth), left + 5, top + (height / 2) - ((client.textRenderer.fontHeight + 2) * 0), infoColor, true);
        context.drawText(client.textRenderer, client.textRenderer.trimToWidth(Text.translatable("string.explorerscompass.dimension").getString() + ": " + StructureUtils.structureDimensionsToString(ExplorersCompass.allowedStructureIDsToDimensionIDs.get(structureID)), textWidth), left + 5, top + (height / 2) + ((client.textRenderer.fontHeight + 2) * 1), infoColor, true);
        boolean buttonHovered = structuresList.isActionButtonClick(this, mouseX, mouseY);
        context.fill(buttonX, buttonY, buttonX + buttonWidth, buttonY + 20, 0xff909090);
        context.fill(buttonX + 1, buttonY + 1, buttonX + buttonWidth - 1, buttonY + 19, buttonHovered ? 0xff424242 : 0xff242424);
        context.drawCenteredTextWithShadow(client.textRenderer, Text.translatable(parentScreen.isStructureHidden(structureID)
                ? "string.explorerscompass.undo" : "string.explorerscompass.hide"), buttonX + buttonWidth / 2, buttonY + 6, 0xffffff);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == 0 && isEnabled()) {
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
		return Text.literal(StructureUtils.getStructureName(structureID));
	}

}
