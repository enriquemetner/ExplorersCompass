package com.chaosthedude.explorerscompass.util;

import com.chaosthedude.explorerscompass.config.ExplorersCompassConfig;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

@Environment(EnvType.CLIENT)
public class RenderUtils {
	
	public static final MinecraftClient mc = MinecraftClient.getInstance();

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
		if (width < 6 || height < 6) {
			context.fill(x, y, x + width, y + height, color);
			return;
		}
		int firstLineY = y + (height - 4) / 2;
		int secondLineY = firstLineY + 3;
		context.fill(x, y, x + width, firstLineY, color);
		context.fill(x, firstLineY + 1, x + width, secondLineY, color);
		context.fill(x, secondLineY + 1, x + width, y + height, color);
		drawDotRow(context, x, firstLineY, width, color);
		drawDotRow(context, x, secondLineY, width, color);
	}

	private static void drawDotRow(DrawContext context, int x, int y, int width, int color) {
		// Half-pixel horizontal coordinates keep the closer pair distinct and centered on a 6px thumb.
		int firstDot = 3;
		int secondDot = width * 2 - firstDot - 2;
		context.getMatrices().push();
		context.getMatrices().translate(x, 0, 0);
		context.getMatrices().scale(0.5f, 1.0f, 1.0f);
		context.fill(0, y, firstDot, y + 1, color);
		context.fill(firstDot + 2, y, secondDot, y + 1, color);
		context.fill(secondDot + 2, y, width * 2, y + 1, color);
		context.getMatrices().pop();
	}

    public static int getBackgroundColor(boolean active, boolean hovered) {
        float state = 2;
        if (!active) {
            state = 5;
        } else if (hovered) {
            state = 3;
        }
        final float f = state / 2 * 0.9F + 0.1F;
        final int color = (int) (255.0F * f);
        return color / 2 << 24;
    }

}
