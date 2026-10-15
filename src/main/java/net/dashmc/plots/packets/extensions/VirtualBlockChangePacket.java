package net.dashmc.plots.packets.extensions;

import lombok.Getter;
import net.dashmc.plots.packets.IPacketExtension;
import net.dashmc.plots.plot.IEnvironment;
import net.dashmc.plots.utils.helpers.FieldAccess;
import net.minecraft.server.v1_8_R3.BlockPosition;
import net.minecraft.server.v1_8_R3.PacketPlayOutBlockChange;

public class VirtualBlockChangePacket implements IPacketExtension<PacketPlayOutBlockChange> {
	private static FieldAccess<BlockPosition> posField;

	@Getter
	private final PacketPlayOutBlockChange packet;

	public VirtualBlockChangePacket(IEnvironment env, BlockPosition pos) {
		packet = new PacketPlayOutBlockChange();
		packet.block = env.getType(pos);

		try {
			posField.set(packet, pos);
		} catch (IllegalArgumentException | IllegalAccessException e) {
			e.printStackTrace();
		}
	}

	static {
		try {
			posField = new FieldAccess<>(PacketPlayOutBlockChange.class, "a", BlockPosition.class);
		} catch (NoSuchFieldException | SecurityException e) {
			e.printStackTrace();
		}
	}
}