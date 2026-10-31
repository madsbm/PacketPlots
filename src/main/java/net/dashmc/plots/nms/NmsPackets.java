package net.dashmc.plots.nms;

import net.minecraft.server.v1_8_R3.BlockPosition;
import net.minecraft.server.v1_8_R3.EnumDirection;
import net.minecraft.server.v1_8_R3.Packet;
import net.minecraft.server.v1_8_R3.PacketListener;
import net.minecraft.server.v1_8_R3.PacketPlayInBlockDig;
import net.minecraft.server.v1_8_R3.PacketPlayInBlockDig.EnumPlayerDigType;
import net.minecraft.server.v1_8_R3.PacketPlayInBlockPlace;
import net.minecraft.server.v1_8_R3.PacketPlayInFlying;

/**
 * Packets.
 */
public class NmsPackets {

	/**
	 * Handles the packet like the server does when it receives it.
	 */
	public static <T extends PacketListener> void handle(Packet<T> packet, T listener) {
		packet.a(listener);
	}

	// digging

	public static BlockPosition getPosition(PacketPlayInBlockDig packet) {
		return packet.a();
	}

	public static EnumDirection getFace(PacketPlayInBlockDig packet) {
		return packet.b();
	}

	public static EnumPlayerDigType getDigType(PacketPlayInBlockDig packet) {
		return packet.c();
	}

	// placing

	/**
	 * @return the clicked block
	 */
	public static BlockPosition getPosition(PacketPlayInBlockPlace packet) {
		return packet.a();
	}

	/**
	 * @return where on the clicked block's face the crosshair was, from 0 to 1
	 */
	public static float getCursorX(PacketPlayInBlockPlace packet) {
		return packet.d();
	}

	public static float getCursorY(PacketPlayInBlockPlace packet) {
		return packet.e();
	}

	public static float getCursorZ(PacketPlayInBlockPlace packet) {
		return packet.f();
	}

	// moving, including looking

	public static double getX(PacketPlayInFlying packet) {
		return packet.a();
	}

	public static double getY(PacketPlayInFlying packet) {
		return packet.b();
	}

	public static double getZ(PacketPlayInFlying packet) {
		return packet.c();
	}

	public static float getYaw(PacketPlayInFlying packet) {
		return packet.d();
	}

	public static float getPitch(PacketPlayInFlying packet) {
		return packet.e();
	}

	/**
	 * @return whether the packet has a position, so the player moved
	 */
	public static boolean hasPosition(PacketPlayInFlying packet) {
		return packet.g();
	}

	/**
	 * @return whether the packet has a yaw and pitch, so the player looked around
	 */
	public static boolean hasLook(PacketPlayInFlying packet) {
		return packet.h();
	}

	/**
	 * @return whether the player's client says they stand on a block
	 */
	public static boolean isOnGround(PacketPlayInFlying packet) {
		return packet.f();
	}

	public static void setHasPosition(PacketPlayInFlying packet, boolean to) {
		packet.a(to);
	}
}
