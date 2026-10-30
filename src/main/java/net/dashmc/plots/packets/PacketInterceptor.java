package net.dashmc.plots.packets;

import net.dashmc.plots.packets.interceptors.BlockDigPacketInterceptor;
import net.dashmc.plots.packets.interceptors.BlockPlacementPacketInterceptor;
import net.dashmc.plots.packets.interceptors.MapChunkBulkPacketInterceptor;
import net.dashmc.plots.packets.interceptors.MapChunkPacketInterceptor;
import net.dashmc.plots.packets.interceptors.MovementPacketInterceptor;
import net.dashmc.plots.packets.interceptors.WindowClosePacketInterceptor;
import net.dashmc.plots.plot.VirtualConnection;
import net.dashmc.plots.plot.interaction.InteractionHandler;
import net.minecraft.server.v1_8_R3.Packet;

public abstract class PacketInterceptor<T extends Packet<?>> {
	public PacketInterceptor() {
	}

	public abstract boolean intercept(T packet, VirtualConnection connection);

	public abstract Class<T> getClazz();

	public static void register() {
		InteractionHandler interactions = new InteractionHandler();

		MapChunkBulkPacketInterceptor.register();
		MapChunkPacketInterceptor.register();
		BlockDigPacketInterceptor.register(interactions);
		BlockPlacementPacketInterceptor.register(interactions);
		WindowClosePacketInterceptor.register();
		MovementPacketInterceptor.register();
	}
}