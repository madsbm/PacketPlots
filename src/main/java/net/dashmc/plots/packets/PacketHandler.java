package net.dashmc.plots.packets;

import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import lombok.RequiredArgsConstructor;
import net.dashmc.plots.nms.NmsPackets;
import net.dashmc.plots.plot.VirtualConnection;
import net.dashmc.plots.plot.interaction.ChestCloser;
import net.minecraft.server.v1_8_R3.EntityPlayer;
import net.minecraft.server.v1_8_R3.Packet;
import net.minecraft.server.v1_8_R3.PacketListenerPlayIn;
import net.minecraft.server.v1_8_R3.PacketListenerPlayOut;

/**
 * PacketHandler for a virtual connection. Sorts through the packets and
 * passes the important ones to the InterceptorRegistry. Put in the player's
 * pipeline by a PacketInjector.
 *
 * Threading: outgoing packets are intercepted on the Netty thread, as they're
 * written, so outgoing interceptors may only read from the environment.
 * Incoming packets are intercepted on the main thread, like vanilla handles
 * them.
 *
 * https://minecraft.wiki/w/Protocol?oldid=2772100
 */
@RequiredArgsConstructor
public class PacketHandler extends ChannelDuplexHandler {
	private final VirtualConnection connection;
	private final InterceptorRegistry interceptors;
	// runs tasks on the main thread in the order the server handles packets
	private final Executor packetQueue;
	// closes the player's chest when their connection fails
	private final ChestCloser chests;
	private final Logger logger;

	// Outgoing, on the Netty thread
	@SuppressWarnings("unchecked")
	@Override
	public void write(ChannelHandlerContext chx, Object obj, ChannelPromise promise) throws Exception {
		if (!(obj instanceof Packet)) {
			super.write(chx, obj, promise);
			return;
		}

		Packet<PacketListenerPlayOut> packet = (Packet<PacketListenerPlayOut>) obj;
		if (interceptors.intercept(packet, connection))
			return;

		super.write(chx, packet, promise);
	}

	// Incomming, handed over to the main thread
	@SuppressWarnings("unchecked")
	@Override
	public void channelRead(ChannelHandlerContext chx, Object obj) throws Exception {
		if (!(obj instanceof Packet) || !interceptors.isIntercepted(obj.getClass())) {
			super.channelRead(chx, obj);
			return;
		}

		Packet<PacketListenerPlayIn> packet = (Packet<PacketListenerPlayIn>) obj;
		EntityPlayer player = connection.getPlayer();
		packetQueue.execute(() -> {
			try {
				if (!connection.isInjected() || !interceptors.intercept(packet, connection))
					NmsPackets.handle(packet, player.playerConnection);
			} catch (Throwable t) {
				logger.log(Level.SEVERE,
						"Failed handling " + packet.getClass().getSimpleName() + " for " + player.getName(), t);
			}
		});
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
		logger.log(Level.SEVERE, "Netty pipeline exception for " + connection.getPlayer().getName()
				+ " (environment owner uuid: " + connection.getEnvironment().getOwnerUuid()
				+ "). Attempting to shut down player gracefully!", cause);

		// the chest is closed on the main thread, as it changes the player's inventory
		packetQueue.execute(this::shutdownGracefully);

		super.exceptionCaught(ctx, cause);
	}

	public void shutdownGracefully() {
		chests.closeCurrentChest(connection.getPlayer(), connection.getEnvironment(), true);
	}
}
