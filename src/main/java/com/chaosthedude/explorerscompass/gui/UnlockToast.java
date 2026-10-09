package com.chaosthedude.explorerscompass.gui;

import com.chaosthedude.explorerscompass.ExplorersCompass;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.toast.Toast;
import net.minecraft.client.toast.ToastManager;
import net.minecraft.text.Text;

/** Advancement-style toast without an advancement or a sound. */
public final class UnlockToast implements Toast {
    private final Text title;
    private final Text subtitle = Text.translatable("toast.explorerscompass.check_compass");

    public UnlockToast(int count) {
        title = count == 1
                ? Text.translatable("toast.explorerscompass.structure_unlocked")
                : Text.translatable("toast.explorerscompass.structures_unlocked", count);
    }

    @Override
    public Visibility draw(DrawContext context, ToastManager manager, long startTime) {
        context.drawTexture(TEXTURE, 0, 0, 0, 0, getWidth(), getHeight());
        var renderer = manager.getClient().textRenderer;
        drawLine(context, renderer, title, 7, 0xFFFFFF00);
        drawLine(context, renderer, subtitle, 18, 0xFFFFFFFF);
        context.drawItemWithoutEntity(ExplorersCompass.EXPLORERS_COMPASS_ITEM.getDefaultStack(), 8, 8);
        return startTime >= 5000.0 * manager.getNotificationDisplayTimeMultiplier() ? Visibility.HIDE : Visibility.SHOW;
    }

    private static void drawLine(DrawContext context, TextRenderer renderer, Text line, int y, int color) {
        int width = renderer.getWidth(line);
        float scale = width > 125 ? 125.0f / width : 1.0f;
        context.getMatrices().push();
        context.getMatrices().translate(30, y, 0);
        context.getMatrices().scale(scale, scale, 1);
        context.drawText(renderer, line, 0, 0, color, false);
        context.getMatrices().pop();
    }
}
