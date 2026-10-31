package net.dashmc.plots.packets;

import java.util.logging.Logger;

import io.netty.channel.Channel;
import lombok.RequiredArgsConstructor;
import net.dashmc.plots.plot.VirtualConnection;
import net.minecraft.server.v1_8_R3.EntityPlayer;

/**
 * Puts a PacketHandler in the Netty pipeline of a virtual connection's player,
 * and takes it out again.
 */
@RequiredArgsConstructor
public class PacketInjector {
	private static final String NETTY_PIPELINE_NAME = "Environment";

	private final InterceptorRegistry interceptors;
	private final Logger logger;

	public void inject(VirtualConnection connection) {
		Channel channel = connection.getPlayer().playerConnection.networkManager.channel;
		PacketHandler handler = new PacketHandler(connection, interceptors, logger);

		// a handler left behind, e.g. by a reload, is replaced rather than kept, as it
		// belongs to a connection that's no longer open
		if (channel.pipeline().get(NETTY_PIPELINE_NAME) != null) {
			channel.pipeline().replace(NETTY_PIPELINE_NAME, NETTY_PIPELINE_NAME, handler);
		} else {
			channel.pipeline().addBefore("packet_handler", NETTY_PIPELINE_NAME, handler);
		}
	}

	public void remove(EntityPlayer player) {
		Channel channel = player.playerConnection.networkManager.channel;
		if (channel.pipeline().get(NETTY_PIPELINE_NAME) != null) {
			channel.pipeline().remove(NETTY_PIPELINE_NAME);
		}
	}
}
