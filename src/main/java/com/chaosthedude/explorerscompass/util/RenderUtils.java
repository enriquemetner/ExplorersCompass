package com.chaosthedude.explorerscompass.util;

import com.chaosthedude.explorerscompass.config.ExplorersCompassConfig;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

@Environment(EnvType.CLIENT)
public class RenderUtils {
	public static final int NORMAL_BACKGROUND_OPACITY_PERCENT = 50;
	public static final int HIGHLIGHTED_BACKGROUND_OPACITY_PERCENT = 75;
	public static final int LOCKED_ENTRY_BACKGROUND_OPACITY_PERCENT = 65;
	public static final int DISABLED_BUTTON_OPACITY_PERCENT = 20;
	public static final int SEARCH_FIELD_OPACITY_PERCENT = 25;
	public static final int SCROLLBAR_THUMB_OPACITY_PERCENT = 80;
	
	public static final MinecraftClient mc = MinecraftClient.getInstance();

	public static int blackWithOpacity(int percent) {
		if (percent < 0 || percent > 100) throw new IllegalArgumentException("Opacity must be between 0 and 100 percent");
		return Math.round(percent * 255.0f / 100.0f) << 24;
	}

	public static void drawStringLeft(DrawContext context, String string, TextRenderer textRenderer, int x, int y, int color) {
		context.drawTextWithShadow(textRenderer, string, x, y, color);
	}

	public static void drawStringRight(DrawContext context, String string, TextRenderer textRenderer, int x, int y, int color) {
		context.drawTextWithShadow(textRenderer, string, x - textRenderer.getWidth(string), y, color);
	}

	public static void drawConfiguredStringOnHUD(DrawContext context, String string, TextRenderer textRenderer, int xOffset, int yOffset, int color, int relLineOffset) {
		yOffset += (relLineOffset + ExplorersCompassConfig.overlayLineOffset) * 9;
		if (ExplorersCompassConfig.overlaySide == OverlaySide.LEFT) {
			drawStringLeft(context, string, textRenderer, xOffset + 2, yOffset + 2, color);
		} else {
			drawStringRight(context, string, textRenderer, mc.getWindow().getScaledWidth() - xOffset - 2, yOffset + 2, color);
		}
	}

	public static void drawScrollbarThumb(DrawContext context, int x, int y, int width, int height, int color) {
		if (width < 6 || height < 7) {
			context.fill(x, y, x + width, y + height, color);
			return;
		}
		int firstDotY = y + (height - 7) / 2;
		int fillFrom = y;
		for (int row = 0; row < 3; row++) {
			int dotY = firstDotY + row * 3;
			context.fill(x, fillFrom, x + width, dotY, color);
			drawDotRow(context, x, dotY, width, color);
			fillFrom = dotY + 1;
		}
		context.fill(x, fillFrom, x + width, y + height, color);
	}

	private static void drawDotRow(DrawContext context, int x, int y, int width, int color) {
		// Half-pixel coordinates keep the pair distinct and centered at any thumb width.
		int firstDot = width - 4;
		int secondDot = width + 2;
		context.getMatrices().push();
		context.getMatrices().translate(x, 0, 0);
		context.getMatrices().scale(0.5f, 1.0f, 1.0f);
		context.fill(0, y, firstDot, y + 1, color);
		context.fill(firstDot + 2, y, secondDot, y + 1, color);
		context.fill(secondDot + 2, y, width * 2, y + 1, color);
		context.getMatrices().pop();
	}

}
