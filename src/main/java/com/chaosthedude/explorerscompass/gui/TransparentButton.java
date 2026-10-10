package com.chaosthedude.explorerscompass.gui;

import com.chaosthedude.explorerscompass.util.RenderUtils;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

@Environment(EnvType.CLIENT)
public class TransparentButton extends ButtonWidget {
	private int rightInset;

	public TransparentButton(int x, int y, int width, int height, Text label, PressAction onPress) {
		super(x, y, width, height, label, onPress, DEFAULT_NARRATION_SUPPLIER);
	}

	public void setRightInset(int rightInset) {
		this.rightInset = rightInset;
	}

	@Override
	public void renderButton(DrawContext context, int mouseX, int mouseY, float partialTicks) {
		if (visible) {
			MinecraftClient mc = MinecraftClient.getInstance();
			int opacity = !active ? RenderUtils.DISABLED_BUTTON_OPACITY_PERCENT
					: isHovered() ? RenderUtils.HIGHLIGHTED_BACKGROUND_OPACITY_PERCENT
					: RenderUtils.NORMAL_BACKGROUND_OPACITY_PERCENT;
			context.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), RenderUtils.blackWithOpacity(opacity));
			String label = getMessage().getString();
			int availableWidth = Math.max(1, getWidth() - rightInset - 8);
			if (mc.textRenderer.getWidth(label) > availableWidth) {
				label = mc.textRenderer.trimToWidth(label, Math.max(1, availableWidth - mc.textRenderer.getWidth("..."))) + "...";
			}
			context.drawCenteredTextWithShadow(mc.textRenderer, label, getX() + (getWidth() - rightInset) / 2, getY() + (getHeight() - 8) / 2, 0xffffff);
		}
	}

}
