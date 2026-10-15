package net.dashmc.plots.packets.interceptors;

import java.util.Collections;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;
import org.bukkit.util.NumberConversions;

import net.dashmc.plots.PacketPlots;
import net.dashmc.plots.events.VirtualPlayerMoveEvent;
import net.dashmc.plots.packets.PacketInterceptor;
import net.dashmc.plots.plot.VirtualConnection;
import net.dashmc.plots.utils.CuboidRegion;
import net.dashmc.plots.utils.helpers.FieldAccess;
import net.minecraft.server.v1_8_R3.EntityPlayer;
import net.minecraft.server.v1_8_R3.PacketPlayInFlying;
import net.minecraft.server.v1_8_R3.PacketPlayOutPosition;
import net.minecraft.server.v1_8_R3.PlayerConnection;
import net.minecraft.server.v1_8_R3.PacketPlayInFlying.PacketPlayInLook;
import net.minecraft.server.v1_8_R3.PacketPlayInFlying.PacketPlayInPosition;
import net.minecraft.server.v1_8_R3.PacketPlayInFlying.PacketPlayInPositionLook;

public class MovementPacketInterceptor extends PacketInterceptor<PacketPlayInFlying> {
	protected static MovementPacketInterceptor INSTANCE;
	private static CuboidRegion acDisabledRegion = PacketPlots.getPlotConfig().getDefaultedACRegion();

	@Override
	public boolean intercept(PacketPlayInFlying packet, VirtualConnection connection) {
		double x = packet.a();
		double y = packet.b();
		double z = packet.c();

		if (!NumberConversions.isFinite(x) || !NumberConversions.isFinite(y)
				|| !NumberConversions.isFinite(z) || !NumberConversions.isFinite(packet.d())
				|| !NumberConversions.isFinite(packet.e())) {

			connection.getPlayer().getBukkitEntity().kickPlayer("NaN in position");
			return true;
		}

		connection.setInsideACRegion(acDisabledRegion.includes((int) x, (int) y, (int) z));
		if (!connection.getEnvironment().isValidLocation((int) x, (int) y, (int) z)) {
			connection.setInsideEnvironment(false);
			return false;
		} else if (!connection.isMoved()) {
			connection.setMoved(true);
			return false;
		}

		connection.setInsideEnvironment(true);

		EntityPlayer player = connection.getPlayer();
		CraftPlayer bukkitPlayer = player.getBukkitEntity();

		Location from = getLocation(connection);
		Location to = bukkitPlayer.getLocation().clone();
		if (packet.g() && !(packet.g() && y == -999D)) {
			to.setX(x);
			to.setY(y);
			to.setZ(z);
		}

		if (packet.h()) {
			to.setYaw(packet.d());
			to.setPitch(packet.e());
		}

		double dx = to.getX() - from.getX();
		double dy = to.getY() - from.getY();
		double dz = to.getZ() - from.getZ();

		double delta = Math.pow(dx, 2) + Math.pow(dy, 2) + Math.pow(dz, 2);
		float deltaAngle = Math.abs(from.getYaw() - to.getYaw()) + Math.abs(from.getPitch() - to.getPitch());

		if ((delta > 1f / 256 || deltaAngle > 10f) && !player.dead) {
			// lastX = to.getX();
			// lastY = to.getY();
			// lastZ = to.getZ();

			Location oldTo = to.clone();
			VirtualPlayerMoveEvent event = new VirtualPlayerMoveEvent(bukkitPlayer, from, oldTo,
					connection.getEnvironment());
			Bukkit.getPluginManager().callEvent(event);

			if (event.isCancelled()) {
				player.playerConnection.sendPacket(new PacketPlayOutPosition(from.getX(), from.getY(), from.getZ(),
						from.getYaw(), from.getPitch(), Collections.emptySet()));
				return true;
			}
		}

		if (player.dead)
			return true;

		if (player.isSleeping()) {
			// player.l();
			player.setLocation(from.getX(), from.getY(), from.getZ(), from.getYaw(), from.getPitch());
			return true;
		}

		// set last loc
		setLocation(to, connection);

		if (packet.g() && packet.b() == -999D)
			packet.a(false);

		if (packet.g()) {
			if (Math.abs(packet.a()) > 3.0E7D || Math.abs(packet.c()) > 3.0E7D) {
				player.playerConnection.disconnect("Illegal position");
				return true;
			}
		}

		// player.l();
		player.setLocation(to.getX(), to.getY(), to.getZ(), to.getYaw(), to.getPitch());

		return true;
	}

	private static FieldAccess<Double> xCoord;
	private static FieldAccess<Double> yCoord;
	private static FieldAccess<Double> zCoord;

	private static FieldAccess<Float> yaw;
	private static FieldAccess<Float> pitch;

	static {
		try {
			xCoord = new FieldAccess<>(PlayerConnection.class, "o", double.class);
			yCoord = new FieldAccess<>(PlayerConnection.class, "p", double.class);
			zCoord = new FieldAccess<>(PlayerConnection.class, "q", double.class);

			yaw = new FieldAccess<>(PlayerConnection.class, "lastYaw", float.class);
			pitch = new FieldAccess<>(PlayerConnection.class, "lastPitch", float.class);
		} catch (NoSuchFieldException | SecurityException e) {
			e.printStackTrace();
		}
	}

	private static void setLocation(Location loc, VirtualConnection vConn) {
		PlayerConnection conn = vConn.getPlayer().playerConnection;

		double x = loc.getX();
		double y = loc.getY();
		double z = loc.getZ();

		try {
			xCoord.set(conn, x);
			yCoord.set(conn, y);
			zCoord.set(conn, z);

			yaw.set(conn, loc.getYaw());
			pitch.set(conn, loc.getPitch());
		} catch (IllegalArgumentException | IllegalAccessException e) {
			e.printStackTrace();
		}
	}

	private static Location getLocation(VirtualConnection vConn) {
		PlayerConnection conn = vConn.getPlayer().playerConnection;

		try {
			double y = (double) yCoord.get(conn);
			double z = (double) zCoord.get(conn);
			double x = (double) xCoord.get(conn);

			float cYaw = (float) yaw.get(conn);
			float cPitch = (float) pitch.get(conn);

			return new Location(vConn.getEnvironment().getWorld(), x, y, z, cYaw, cPitch);
		} catch (IllegalArgumentException | IllegalAccessException e) {
			e.printStackTrace();
			return null;
		}
	}

	@Override
	public Class<PacketPlayInFlying> getClazz() {
		return PacketPlayInFlying.class;
	}

	public static void register() {
		INSTANCE = new MovementPacketInterceptor();
		VirtualConnection.registerInterceptor(INSTANCE);
		VirtualConnection.registerInterceptor(new LookPacketInterceptor());
		VirtualConnection.registerInterceptor(new PositionPacketInterceptor());
		VirtualConnection.registerInterceptor(new PositionLookPacketInterceptor());
	}

	public static class LookPacketInterceptor extends PacketInterceptor<PacketPlayInLook> {
		@Override
		public boolean intercept(PacketPlayInLook packet, VirtualConnection connection) {
			return INSTANCE.intercept(packet, connection);
		}

		@Override
		public Class<PacketPlayInLook> getClazz() {
			return PacketPlayInLook.class;
		}
	}

	public static class PositionPacketInterceptor extends PacketInterceptor<PacketPlayInPosition> {
		@Override
		public boolean intercept(PacketPlayInPosition packet, VirtualConnection connection) {
			return INSTANCE.intercept(packet, connection);
		}

		@Override
		public Class<PacketPlayInPosition> getClazz() {
			return PacketPlayInPosition.class;
		}
	}

	public static class PositionLookPacketInterceptor extends PacketInterceptor<PacketPlayInPositionLook> {
		@Override
		public boolean intercept(PacketPlayInPositionLook packet, VirtualConnection connection) {
			return INSTANCE.intercept(packet, connection);
		}

		@Override
		public Class<PacketPlayInPositionLook> getClazz() {
			return PacketPlayInPositionLook.class;
		}
	}

}
