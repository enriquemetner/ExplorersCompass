package com.chaosthedude.explorerscompass.network;

import com.chaosthedude.explorerscompass.ExplorersCompass;
import com.chaosthedude.explorerscompass.gui.ExplorersCompassScreen;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

public class SearchResultPacket extends PacketByteBuf {
    public static final Identifier ID = new Identifier(ExplorersCompass.MODID, "search_result");
    public static final int FOUND = 0, NOT_FOUND = 1, REJECTED = 2, WRONG_DIMENSION = 3, INSUFFICIENT_XP = 4;

    public SearchResultPacket(int requestId, int result, int radius, Identifier foundId, int foundX, int foundZ, boolean displayCoordinates) {
        super(Unpooled.buffer());
        writeInt(requestId);
        writeInt(result);
        writeInt(radius);
        writeBoolean(foundId != null);
        if (foundId != null) {
            writeIdentifier(foundId);
            writeInt(foundX);
            writeInt(foundZ);
            writeBoolean(displayCoordinates);
        }
    }

    public static void send(ServerPlayerEntity player, int requestId, int result, int radius) {
        send(player, requestId, result, radius, null, 0, 0, false);
    }

    public static void send(ServerPlayerEntity player, int requestId, int result, int radius, Identifier foundId, int foundX, int foundZ, boolean displayCoordinates) {
        ServerPlayNetworking.send(player, ID, new SearchResultPacket(requestId, result, radius, foundId, foundX, foundZ, displayCoordinates));
    }

    public static void apply(MinecraftClient client, ClientPlayNetworkHandler handler, PacketByteBuf buf, PacketSender sender) {
        int requestId = buf.readInt(), result = buf.readInt(), radius = buf.readInt();
        Identifier foundId = null;
        int foundX = 0, foundZ = 0;
        boolean displayCoordinates = false;
        if (buf.readBoolean()) {
            foundId = buf.readIdentifier();
            foundX = buf.readInt();
            foundZ = buf.readInt();
            displayCoordinates = buf.readBoolean();
        }
        final Identifier resultId = foundId;
        final int resultX = foundX, resultZ = foundZ;
        final boolean showCoordinates = displayCoordinates;
        client.execute(() -> {
            if (client.currentScreen instanceof ExplorersCompassScreen screen) {
                screen.onSearchResult(requestId, result, radius, resultId, resultX, resultZ, showCoordinates);
            }
        });
    }
}
