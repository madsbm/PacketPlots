package net.dashmc.plots.nms;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;

import org.junit.Test;

import io.netty.buffer.Unpooled;
import net.minecraft.server.v1_8_R3.BlockPosition;
import net.minecraft.server.v1_8_R3.EnumDirection;
import net.minecraft.server.v1_8_R3.PacketDataSerializer;
import net.minecraft.server.v1_8_R3.PacketPlayInBlockDig;
import net.minecraft.server.v1_8_R3.PacketPlayInBlockDig.EnumPlayerDigType;
import net.minecraft.server.v1_8_R3.PacketPlayInBlockPlace;
import net.minecraft.server.v1_8_R3.PacketPlayInFlying;
import net.minecraft.server.v1_8_R3.PacketPlayInFlying.PacketPlayInPositionLook;

// the packets are read from bytes like the server reads them, with their a(PacketDataSerializer)
public class NmsPacketsTest {
	private static final BlockPosition POS = new BlockPosition(1, 64, -2);
	// EnumDirection#fromType1 of the byte a packet sends
	private static final int UP = 1;

	private final PacketDataSerializer data = new PacketDataSerializer(Unpooled.buffer());

	@Test
	public void readsTheDigPacket() throws IOException {
		data.writeByte(EnumPlayerDigType.STOP_DESTROY_BLOCK.ordinal());
		data.writeLong(POS.asLong());
		data.writeByte(UP);
		PacketPlayInBlockDig packet = new PacketPlayInBlockDig();
		packet.a(data);

		assertEquals(POS, NmsPackets.getPosition(packet));
		assertEquals(EnumDirection.UP, NmsPackets.getFace(packet));
		assertEquals(EnumPlayerDigType.STOP_DESTROY_BLOCK, NmsPackets.getDigType(packet));
	}

	@Test
	public void readsThePlacePacket() throws IOException {
		data.writeLong(POS.asLong());
		data.writeByte(UP);
		// no item
		data.writeShort(-1);
		// the cursor, in sixteenths
		data.writeByte(8);
		data.writeByte(4);
		data.writeByte(12);
		PacketPlayInBlockPlace packet = new PacketPlayInBlockPlace();
		packet.a(data);

		assertEquals(POS, NmsPackets.getPosition(packet));
		assertEquals(0.5f, NmsPackets.getCursorX(packet), 0);
		assertEquals(0.25f, NmsPackets.getCursorY(packet), 0);
		assertEquals(0.75f, NmsPackets.getCursorZ(packet), 0);
	}

	@Test
	public void readsTheMovementPacket() throws IOException {
		data.writeDouble(1.5);
		data.writeDouble(64);
		data.writeDouble(-2.5);
		data.writeFloat(90);
		data.writeFloat(-45);
		// on the ground
		data.writeByte(1);
		PacketPlayInFlying packet = new PacketPlayInPositionLook();
		packet.a(data);

		assertEquals(1.5, NmsPackets.getX(packet), 0);
		assertEquals(64, NmsPackets.getY(packet), 0);
		assertEquals(-2.5, NmsPackets.getZ(packet), 0);
		assertEquals(90, NmsPackets.getYaw(packet), 0);
		assertEquals(-45, NmsPackets.getPitch(packet), 0);
		assertTrue(NmsPackets.hasPosition(packet));
		assertTrue(NmsPackets.hasLook(packet));
	}

	@Test
	public void setHasPositionTakesThePositionAway() {
		PacketPlayInFlying packet = new PacketPlayInPositionLook();

		NmsPackets.setHasPosition(packet, false);

		assertEquals(false, NmsPackets.hasPosition(packet));
	}
}
