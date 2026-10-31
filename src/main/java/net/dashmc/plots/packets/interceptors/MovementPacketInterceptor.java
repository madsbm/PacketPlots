package net.dashmc.plots.packets.interceptors;

import java.util.Collections;
import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;
import org.bukkit.util.NumberConversions;

import lombok.RequiredArgsConstructor;
import net.dashmc.plots.events.EventDispatcher;
import net.dashmc.plots.events.VirtualPlayerMoveEvent;
import net.dashmc.plots.nms.NmsPackets;
import net.dashmc.plots.nms.NmsPlayers;
import net.dashmc.plots.packets.PacketInterceptor;
import net.dashmc.plots.plot.VirtualConnection;
import net.dashmc.plots.utils.CuboidRegion;
import net.dashmc.plots.utils.helpers.FieldAccess;
import net.minecraft.server.v1_8_R3.EntityPlayer;
import net.minecraft.server.v1_8_R3.MathHelper;
import net.minecraft.server.v1_8_R3.PacketPlayInFlying;
import net.minecraft.server.v1_8_R3.PacketPlayOutPosition;
import net.minecraft.server.v1_8_R3.PlayerConnection;

/**
 * Handles the player moving inside the environment they're shown, in place of
 * the server, which would move them against the real world's blocks.
 */
@RequiredArgsConstructor
public class MovementPacketInterceptor extends PacketInterceptor<PacketPlayInFlying> {
	private final CuboidRegion acDisabledRegion;
	// calls the VirtualPlayerMoveEvent
	private final EventDispatcher events;

	@Override
	public boolean intercept(PacketPlayInFlying packet, VirtualConnection connection) {
		EntityPlayer player = connection.getPlayer();

		// a packet without a position, e.g. one of those sent each tick the player
		// stands still, is where they already are
		boolean hasPosition = NmsPackets.hasPosition(packet) && NmsPackets.getY(packet) != -999D;
		double x = hasPosition ? NmsPackets.getX(packet) : player.locX;
		double y = hasPosition ? NmsPackets.getY(packet) : player.locY;
		double z = hasPosition ? NmsPackets.getZ(packet) : player.locZ;

		if (!NumberConversions.isFinite(x) || !NumberConversions.isFinite(y)
				|| !NumberConversions.isFinite(z) || !NumberConversions.isFinite(NmsPackets.getYaw(packet))
				|| !NumberConversions.isFinite(NmsPackets.getPitch(packet))) {

			player.getBukkitEntity().kickPlayer("NaN in position");
			return true;
		}

		int blockX = MathHelper.floor(x);
		int blockY = MathHelper.floor(y);
		int blockZ = MathHelper.floor(z);

		connection.setInsideACRegion(acDisabledRegion.includes(blockX, blockY, blockZ));
		if (!connection.getEnvironment().contains(blockX, blockY, blockZ)) {
			connection.setInsideEnvironment(false);
			return false;
		} else if (!connection.isMoved()) {
			connection.setMoved(true);
			return false;
		}

		connection.setInsideEnvironment(true);

		CraftPlayer bukkitPlayer = player.getBukkitEntity();

		Location from = getLocation(connection);
		Location to = bukkitPlayer.getLocation().clone();
		if (hasPosition) {
			to.setX(x);
			to.setY(y);
			to.setZ(z);
		}

		if (NmsPackets.hasLook(packet)) {
			to.setYaw(NmsPackets.getYaw(packet));
			to.setPitch(NmsPackets.getPitch(packet));
		}

		double dx = to.getX() - from.getX();
		double dy = to.getY() - from.getY();
		double dz = to.getZ() - from.getZ();

		double delta = Math.pow(dx, 2) + Math.pow(dy, 2) + Math.pow(dz, 2);
		float deltaAngle = Math.abs(from.getYaw() - to.getYaw()) + Math.abs(from.getPitch() - to.getPitch());

		if ((delta > 1f / 256 || deltaAngle > 10f) && !player.dead) {
			Location oldTo = to.clone();
			VirtualPlayerMoveEvent event = new VirtualPlayerMoveEvent(bukkitPlayer, from, oldTo,
					connection.getEnvironment());
			events.call(event);

			if (event.isCancelled()) {
				player.playerConnection.sendPacket(new PacketPlayOutPosition(from.getX(), from.getY(), from.getZ(),
						from.getYaw(), from.getPitch(), Collections.emptySet()));
				return true;
			}
		}

		if (player.dead)
			return true;

		if (player.isSleeping()) {
			NmsPlayers.tickBody(player);
			player.setLocation(from.getX(), from.getY(), from.getZ(), from.getYaw(), from.getPitch());
			return true;
		}

		// set last loc
		setLocation(to, connection);

		if (hasPosition && (Math.abs(x) > 3.0E7D || Math.abs(z) > 3.0E7D)) {
			player.playerConnection.disconnect("Illegal position");
			return true;
		}

		// like the server does for each move packet, before it moves the player. The
		// real world's blocks the tick finds can't hurt them in the environment, see
		// EnvironmentHazardListener
		double prevX = player.locX, prevY = player.locY, prevZ = player.locZ;
		NmsPlayers.tickBody(player);

		// the tick moved the player against the real world, where they may fall
		// through what they stand on in the environment, so it mustn't add up
		player.onGround = NmsPackets.isOnGround(packet);
		if (player.onGround)
			player.motY = 0;

		player.setLocation(to.getX(), to.getY(), to.getZ(), to.getYaw(), to.getPitch());
		NmsPlayers.countMovement(player, to.getX() - prevX, to.getY() - prevY, to.getZ() - prevZ);

		return true;
	}

	private static final FieldAccess<Double> xCoord = new FieldAccess<>(PlayerConnection.class, "o", double.class);
	private static final FieldAccess<Double> yCoord = new FieldAccess<>(PlayerConnection.class, "p", double.class);
	private static final FieldAccess<Double> zCoord = new FieldAccess<>(PlayerConnection.class, "q", double.class);

	private static final FieldAccess<Float> yaw = new FieldAccess<>(PlayerConnection.class, "lastYaw", float.class);
	private static final FieldAccess<Float> pitch = new FieldAccess<>(PlayerConnection.class, "lastPitch",
			float.class);

	private static void setLocation(Location loc, VirtualConnection vConn) {
		PlayerConnection conn = vConn.getPlayer().playerConnection;

		xCoord.set(conn, loc.getX());
		yCoord.set(conn, loc.getY());
		zCoord.set(conn, loc.getZ());

		yaw.set(conn, loc.getYaw());
		pitch.set(conn, loc.getPitch());
	}

	private static Location getLocation(VirtualConnection vConn) {
		PlayerConnection conn = vConn.getPlayer().playerConnection;

		return new Location(vConn.getEnvironment().getRealWorld().getWorld(), xCoord.get(conn), yCoord.get(conn),
				zCoord.get(conn), yaw.get(conn), pitch.get(conn));
	}

}
