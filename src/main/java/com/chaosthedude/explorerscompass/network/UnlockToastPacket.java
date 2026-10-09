package com.chaosthedude.explorerscompass.network;

import com.chaosthedude.explorerscompass.ExplorersCompass;
import com.chaosthedude.explorerscompass.gui.UnlockToast;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

public final class UnlockToastPacket extends PacketByteBuf {
    public static final Identifier ID = new Identifier(ExplorersCompass.MODID, "unlock_toast");

    private UnlockToastPacket(int count) {
        super(Unpooled.buffer());
        writeVarInt(count);
    }

    public static void send(ServerPlayerEntity player, int count) {
        if (count > 0) ServerPlayNetworking.send(player, ID, new UnlockToastPacket(count));
    }

    public static void apply(MinecraftClient client, ClientPlayNetworkHandler handler, PacketByteBuf buf, PacketSender sender) {
        int count = buf.readVarInt();
        if (count > 0) client.execute(() -> client.getToastManager().add(new UnlockToast(count)));
    }
}
