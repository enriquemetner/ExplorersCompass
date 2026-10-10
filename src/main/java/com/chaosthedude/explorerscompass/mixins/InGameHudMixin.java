package com.chaosthedude.explorerscompass.mixins;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.chaosthedude.explorerscompass.ExplorersCompass;
import com.chaosthedude.explorerscompass.config.ExplorersCompassConfig;
import com.chaosthedude.explorerscompass.items.ExplorersCompassItem;
import com.chaosthedude.explorerscompass.util.CompassState;
import com.chaosthedude.explorerscompass.util.ItemUtils;
import com.chaosthedude.explorerscompass.util.RenderUtils;
import com.chaosthedude.explorerscompass.util.StructureUtils;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;

@Environment(EnvType.CLIENT)
@Mixin(InGameHud.class)
public class InGameHudMixin {

    @Shadow @Final private MinecraftClient client;

    @Inject(method = "render(Lnet/minecraft/client/gui/DrawContext;F)V", at = @At(value = "TAIL"))
    private void renderCompassInfo(DrawContext context, float tickDelta, CallbackInfo info) {
        if (client.player == null || client.options.hudHidden || client.options.debugEnabled
                || (client.currentScreen != null && !(ExplorersCompassConfig.displayWithChatOpen && client.currentScreen instanceof ChatScreen))) return;
        PlayerEntity player = client.player;
        ItemStack stack = ItemUtils.getHeldItem(player, ExplorersCompass.EXPLORERS_COMPASS_ITEM);
        if (!(stack.getItem() instanceof ExplorersCompassItem compass) || compass.getState(stack) != CompassState.FOUND) return;

        RenderUtils.drawConfiguredStringOnHUD(context, "Structure", client.textRenderer, 5, 5, 0xFFFFFF, 0);
        RenderUtils.drawConfiguredStringOnHUD(context, StructureUtils.getStructureName(compass.getStructureID(stack)), client.textRenderer, 5, 5, 0xAAAAAA, 1);
        if (!compass.isInFoundDimension(stack, player.getWorld())) {
            RenderUtils.drawConfiguredStringOnHUD(context, "Wrong Dimension", client.textRenderer, 5, 5, 0xFF4444, 3);
        } else {
            int nextLine = 3;
            if (compass.shouldDisplayDistance(stack)) {
                RenderUtils.drawConfiguredStringOnHUD(context, "Distance", client.textRenderer, 5, 5, 0xFFFFFF, nextLine++);
                RenderUtils.drawConfiguredStringOnHUD(context, String.valueOf(StructureUtils.getHorizontalDistanceToLocation(player, compass.getFoundStructureX(stack), compass.getFoundStructureZ(stack))), client.textRenderer, 5, 5, 0xAAAAAA, nextLine++);
                nextLine++;
            }
            if (compass.shouldDisplayCoordinates(stack)) {
                RenderUtils.drawConfiguredStringOnHUD(context, "Coordinates", client.textRenderer, 5, 5, 0xFFFFFF, nextLine++);
                RenderUtils.drawConfiguredStringOnHUD(context, compass.getFoundStructureX(stack) + ", " + compass.getFoundStructureZ(stack), client.textRenderer, 5, 5, 0xAAAAAA, nextLine);
            }
        }
    }
}
