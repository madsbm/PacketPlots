package net.dashmc.plots.plot;

import org.bukkit.Location;

import lombok.RequiredArgsConstructor;
import net.dashmc.plots.events.EventDispatcher;
import net.dashmc.plots.packets.PacketInjector;
import net.dashmc.plots.utils.misc.Debug;
import net.minecraft.server.v1_8_R3.EntityPlayer;

/**
 * Opens players' virtual connections, giving each what they all share.
 */
@RequiredArgsConstructor
public class ConnectionFactory {
	// puts the connections' packet handlers in the players' pipelines
	private final PacketInjector packets;
	// where players are teleported to when they visit another environment
	private final Location safeLocation;
	// whether to render chunks before sending them, for plugins that read them on
	// the way, see VirtualConnection#getRenderPacket
	private final boolean forceChunkMapSend;
	// calls the events of players entering and leaving environments and the
	// anticheat region
	private final EventDispatcher events;
	private final Debug debug;

	public VirtualConnection establish(EntityPlayer player, IEnvironment environment) {
		VirtualConnection connection = new VirtualConnection(player, environment, safeLocation, packets,
				forceChunkMapSend, events, debug);
		connection.open();
		return connection;
	}
}
