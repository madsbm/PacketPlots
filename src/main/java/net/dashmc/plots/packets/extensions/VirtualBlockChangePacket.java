package net.dashmc.plots.packets.extensions;

import lombok.Getter;
import net.dashmc.plots.packets.IPacketExtension;
import net.dashmc.plots.plot.IEnvironment;
import net.dashmc.plots.utils.helpers.FieldAccess;
import net.minecraft.server.v1_8_R3.BlockPosition;
import net.minecraft.server.v1_8_R3.EntityPlayer;
import net.minecraft.server.v1_8_R3.Packet;
import net.minecraft.server.v1_8_R3.PacketPlayOutBlockChange;
import net.minecraft.server.v1_8_R3.TileEntity;

public class VirtualBlockChangePacket implements IPacketExtension<PacketPlayOutBlockChange> {
	private static final FieldAccess<BlockPosition> posField = new FieldAccess<>(PacketPlayOutBlockChange.class,
			"a", BlockPosition.class);

	@Getter
	private final PacketPlayOutBlockChange packet;

	public VirtualBlockChangePacket(IEnvironment env, BlockPosition pos) {
		packet = new PacketPlayOutBlockChange();
		packet.block = env.getType(pos);
		posField.set(packet, pos);
	}

	/**
	 * Sends the player the block and its tile entity as they are in the
	 * environment, e.g. to undo what their client predicted when the server
	 * refused it.
	 */
	public static void resync(EntityPlayer player, IEnvironment env, BlockPosition pos) {
		player.playerConnection.sendPacket(new VirtualBlockChangePacket(env, pos).getPacket());

		TileEntity tile = env.getTileEntity(pos);
		if (tile == null)
			return;

		Packet<?> tilePacket = tile.getUpdatePacket();
		if (tilePacket != null)
			player.playerConnection.sendPacket(tilePacket);
	}
}
