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
 * https://minecraft.wiki/w/Protocol?oldid=2772100
 */
@RequiredArgsConstructor
public class PacketHandler extends ChannelDuplexHandler {
	private static final String NETTY_PIPELINE_NAME = "Environment";

	private final VirtualConnection connection;

	public static void inject(VirtualConnection connection) {
		Channel channel = connection.getPlayer().playerConnection.networkManager.channel;
		if (channel.pipeline().get(NETTY_PIPELINE_NAME) == null) {
			channel.pipeline().addBefore("packet_handler", NETTY_PIPELINE_NAME, new PacketHandler(connection));
		}
	}

	public static void remove(EntityPlayer player) {
		Channel channel = player.playerConnection.networkManager.channel;
		if (channel.pipeline().get(NETTY_PIPELINE_NAME) != null) {
			channel.pipeline().remove(NETTY_PIPELINE_NAME);
		}
	}

	// Outgoing
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

	// Incomming
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
		System.err.println("Netty pipeline exception: " + cause + " (environment owneruuid: "
				+ connection.getEnvironment().getOwnerUuid() + ")");
		cause.printStackTrace();

		System.err.println("Attempting to shut down player gracefully!");
		shutdownGracefully();

		super.exceptionCaught(ctx, cause);
	}

	public void shutdownGracefully() {
		ChestHelper.closeCurrentChest(connection.getPlayer(), connection.getEnvironment(), true);
	}
}
