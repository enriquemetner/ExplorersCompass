package com.chaosthedude.explorerscompass.network;

import java.util.ArrayList;
import java.util.List;

import com.chaosthedude.explorerscompass.ExplorersCompass;
import com.chaosthedude.explorerscompass.items.ExplorersCompassItem;
import com.chaosthedude.explorerscompass.util.ItemUtils;

import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;

public class SearchListedPacket extends PacketByteBuf {

    public static final Identifier ID = new Identifier(ExplorersCompass.MODID, "search_listed");

	public SearchListedPacket(List<Identifier> ids, boolean excludeVisited, int requestId) {
        super(Unpooled.buffer());
		writeInt(requestId);
		writeBoolean(excludeVisited);
        writeVarInt(ids.size());
        for (Identifier id : ids) writeIdentifier(id);
    }

    public static void apply(MinecraftServer server, ServerPlayerEntity player, ServerPlayNetworkHandler handler, PacketByteBuf buf, PacketSender responseSender) {
		int requestId = buf.readInt();
		boolean excludeVisited = buf.readBoolean();
        int count = buf.readVarInt();
        if (count < 1 || count > 32767) {
            server.execute(() -> SearchResultPacket.send(player, requestId, SearchResultPacket.REJECTED, 0));
            return;
        }
        List<Identifier> ids = new ArrayList<>(count);
        for (int i = 0; i < count; i++) ids.add(buf.readIdentifier());
        server.execute(() -> {
            ItemStack stack = ItemUtils.getHeldItem(player, ExplorersCompass.EXPLORERS_COMPASS_ITEM);
            if (stack.isEmpty()) {
                SearchResultPacket.send(player, requestId, SearchResultPacket.REJECTED, 0);
                return;
            }
            ((ExplorersCompassItem) stack.getItem()).searchForListedStructures((ServerWorld) player.getEntityWorld(), player,
					player.getBlockPos(), ids, excludeVisited, stack, requestId);
        });
    }
}
