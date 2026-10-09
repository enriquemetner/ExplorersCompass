package com.chaosthedude.explorerscompass.unlocking;

import com.chaosthedude.explorerscompass.network.UnlockToastPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

/** Sends one notification per newly earned batch after the first compass. */
public final class UnlockNotifications {
    private UnlockNotifications() {}

    public static void newlyAdded(ServerPlayerEntity player, int count) {
        if (count > 0 && UnlockedStructures.get(player.getServer()).notificationsEnabled(player.getUuid())) {
            UnlockToastPacket.send(player, count);
        }
    }

    public static void compassAdvancementCompleted(ServerPlayerEntity player, Identifier advancement) {
        UnlockedStructures state = UnlockedStructures.get(player.getServer());
        if (state.notificationsEnabled(player.getUuid())) {
            UnlockingRules.get().onProgress(player, null, advancement);
            return;
        }
        UnlockingRules.get().evaluateOnOpen(player);
        if (state.enableNotifications(player.getUuid())) {
            UnlockToastPacket.send(player, state.getStructures(player.getUuid()).size());
        }
    }

    public static void deliverPending(ServerPlayerEntity player) {
        UnlockedStructures state = UnlockedStructures.get(player.getServer());
        if (!state.notificationsEnabled(player.getUuid())) return;
        UnlockToastPacket.send(player, state.takePendingNotificationCount(player.getUuid()));
    }
}
