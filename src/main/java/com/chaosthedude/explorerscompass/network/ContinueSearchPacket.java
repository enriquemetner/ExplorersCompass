package com.chaosthedude.explorerscompass.network;

import com.chaosthedude.explorerscompass.ExplorersCompass;
import com.chaosthedude.explorerscompass.util.ItemUtils;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

public class ContinueSearchPacket extends PacketByteBuf {
	public static final Identifier ID = new Identifier(ExplorersCompass.MODID, "continue_search");

	public ContinueSearchPacket(int requestId) {
		super(Unpooled.buffer());
		writeInt(requestId);
	}

	public static void apply(MinecraftServer server, ServerPlayerEntity player, ServerPlayNetworkHandler handler, PacketByteBuf buf, PacketSender sender) {
		int requestId = buf.readInt();
		server.execute(() -> {
			ItemStack stack = ItemUtils.getHeldItem(player, ExplorersCompass.EXPLORERS_COMPASS_ITEM);
			ExplorersCompass.EXPLORERS_COMPASS_ITEM.continueSearch(player.getServerWorld(), player, stack, requestId);
		});
	}
}
