package net.dashmc.plots.packets;

import java.util.concurrent.Executor;

import net.dashmc.plots.packets.interceptors.BlockDigPacketInterceptor;
import net.dashmc.plots.packets.interceptors.BlockPlacementPacketInterceptor;
import net.dashmc.plots.packets.interceptors.MapChunkBulkPacketInterceptor;
import net.dashmc.plots.packets.interceptors.MapChunkPacketInterceptor;
import net.dashmc.plots.packets.interceptors.MovementPacketInterceptor;
import net.dashmc.plots.packets.interceptors.WindowClosePacketInterceptor;
import net.dashmc.plots.plot.VirtualConnection;
import net.dashmc.plots.plot.interaction.InteractionHandler;
import net.dashmc.plots.utils.CuboidRegion;
import net.minecraft.server.v1_8_R3.Packet;

public abstract class PacketInterceptor<T extends Packet<?>> {
	public PacketInterceptor() {
	}

	public abstract boolean intercept(T packet, VirtualConnection connection);

	public abstract Class<T> getClazz();

	/**
	 * @param acDisabledRegion where anticheats are disabled
	 * @param mainThread       runs tasks on the server's main thread
	 */
	public static void register(CuboidRegion acDisabledRegion, Executor mainThread) {
		InteractionHandler interactions = new InteractionHandler();

		MapChunkBulkPacketInterceptor.register(mainThread);
		MapChunkPacketInterceptor.register(mainThread);
		BlockDigPacketInterceptor.register(interactions);
		BlockPlacementPacketInterceptor.register(interactions);
		WindowClosePacketInterceptor.register();
		MovementPacketInterceptor.register(acDisabledRegion);
	}
}