package net.dashmc.plots.packets;

import java.util.logging.Level;

import org.bukkit.Bukkit;

import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import lombok.RequiredArgsConstructor;
import net.dashmc.plots.plot.VirtualConnection;
import net.dashmc.plots.utils.helpers.ChestHelper;
import net.minecraft.server.v1_8_R3.EntityPlayer;
import net.minecraft.server.v1_8_R3.MinecraftServer;
import net.minecraft.server.v1_8_R3.Packet;
import net.minecraft.server.v1_8_R3.PacketListenerPlayIn;
import net.minecraft.server.v1_8_R3.PacketListenerPlayOut;

/**
 * PacketHandler for a virtual connection. Sorts through the packets and
 * passes the important ones to the InterceptorRegistry.
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
	private static final String NETTY_PIPELINE_NAME = "Environment";

	private final VirtualConnection connection;

	public static void inject(VirtualConnection connection) {
		Channel channel = connection.getPlayer().playerConnection.networkManager.channel;
		PacketHandler handler = new PacketHandler(connection);

		// a handler left behind, e.g. by a reload, is replaced rather than kept, as it
		// belongs to a connection that's no longer open
		if (channel.pipeline().get(NETTY_PIPELINE_NAME) != null) {
			channel.pipeline().replace(NETTY_PIPELINE_NAME, NETTY_PIPELINE_NAME, handler);
		} else {
			channel.pipeline().addBefore("packet_handler", NETTY_PIPELINE_NAME, handler);
		}
	}

	public static void remove(EntityPlayer player) {
		Channel channel = player.playerConnection.networkManager.channel;
		if (channel.pipeline().get(NETTY_PIPELINE_NAME) != null) {
			channel.pipeline().remove(NETTY_PIPELINE_NAME);
		}
	}

	// Outgoing, on the Netty thread
	@SuppressWarnings("unchecked")
	@Override
	public void write(ChannelHandlerContext chx, Object obj, ChannelPromise promise) throws Exception {
		if (!(obj instanceof Packet)) {
			super.write(chx, obj, promise);
			return;
		}

		Packet<PacketListenerPlayOut> packet = (Packet<PacketListenerPlayOut>) obj;
		if (InterceptorRegistry.intercept(packet, connection))
			return;

		super.write(chx, packet, promise);
	}

	// Incomming, handed over to the main thread
	@SuppressWarnings("unchecked")
	@Override
	public void channelRead(ChannelHandlerContext chx, Object obj) throws Exception {
		if (!(obj instanceof Packet) || !InterceptorRegistry.isIntercepted(obj.getClass())) {
			super.channelRead(chx, obj);
			return;
		}

		Packet<PacketListenerPlayIn> packet = (Packet<PacketListenerPlayIn>) obj;
		EntityPlayer player = connection.getPlayer();
		MinecraftServer.getServer().postToMainThread(() -> {
			try {
				if (!connection.isInjected() || !InterceptorRegistry.intercept(packet, connection))
					packet.a(player.playerConnection);
			} catch (Throwable t) {
				Bukkit.getLogger().log(Level.SEVERE,
						"Failed handling " + packet.getClass().getSimpleName() + " for " + player.getName(), t);
			}
		});
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
		Bukkit.getLogger().log(Level.SEVERE, "Netty pipeline exception for " + connection.getPlayer().getName()
				+ " (environment owner uuid: " + connection.getEnvironment().getOwnerUuid()
				+ "). Attempting to shut down player gracefully!", cause);

		// the chest is closed on the main thread, as it changes the player's inventory
		MinecraftServer.getServer().postToMainThread(this::shutdownGracefully);

		super.exceptionCaught(ctx, cause);
	}

	public void shutdownGracefully() {
		ChestHelper.closeCurrentChest(connection.getPlayer(), connection.getEnvironment(), true);
	}
}
