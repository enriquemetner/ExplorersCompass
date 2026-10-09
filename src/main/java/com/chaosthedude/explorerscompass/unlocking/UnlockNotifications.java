package com.chaosthedude.explorerscompass.unlocking;

import com.chaosthedude.explorerscompass.network.UnlockToastPacket;
import net.minecraft.server.network.ServerPlayerEntity;

/** Sends one notification per newly earned batch after the first compass. */
public final class UnlockNotifications {
    private UnlockNotifications() {}

    public static void newlyAdded(ServerPlayerEntity player, int count) {
        if (count > 0 && UnlockedStructures.get(player.getServer()).notificationsEnabled(player.getUuid())) {
            UnlockToastPacket.send(player, count);
        }
    }

    public static void compassAdvancementCompleted(ServerPlayerEntity player) {
        UnlockedStructures state = UnlockedStructures.get(player.getServer());
        if (state.notificationsEnabled(player.getUuid())) {
            // A revoked and re-earned compass is another opportunity to restore
            // currently satisfied defaults and rules, but only new IDs get a toast.
            UnlockingRules.get().evaluateOnOpen(player);
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
