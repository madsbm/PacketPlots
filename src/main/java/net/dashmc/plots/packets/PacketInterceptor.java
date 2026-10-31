package net.dashmc.plots.packets;

import net.dashmc.plots.plot.VirtualConnection;
import net.minecraft.server.v1_8_R3.Packet;

/**
 * Handles a class of packet for virtual connections, in place of the server or
 * before it. Registered in an InterceptorRegistry.
 */
public abstract class PacketInterceptor<T extends Packet<?>> {
	public PacketInterceptor() {
	}

	public abstract boolean intercept(T packet, VirtualConnection connection);
}
