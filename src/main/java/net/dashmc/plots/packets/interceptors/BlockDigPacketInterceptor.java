package net.dashmc.plots.packets.interceptors;

import lombok.RequiredArgsConstructor;
import net.dashmc.plots.packets.PacketInterceptor;
import net.dashmc.plots.packets.extensions.VirtualBlockChangePacket;
import net.dashmc.plots.plot.IEnvironment;
import net.dashmc.plots.plot.VirtualConnection;
import net.dashmc.plots.plot.interaction.InteractionHandler;
import net.minecraft.server.v1_8_R3.BlockPosition;
import net.minecraft.server.v1_8_R3.EntityPlayer;
import net.minecraft.server.v1_8_R3.Material;
import net.minecraft.server.v1_8_R3.PacketPlayInBlockDig;
import net.minecraft.server.v1_8_R3.PacketPlayInBlockDig.EnumPlayerDigType;
import net.minecraft.server.v1_8_R3.TileEntity;

@RequiredArgsConstructor
public class BlockDigPacketInterceptor extends PacketInterceptor<PacketPlayInBlockDig> {
	private final InteractionHandler interactions;

	@Override
	// https://github.com/Attano/Spigot-1.8/blob/9db48bc15e203179554b8d992ca6b0a528c8d300/net/minecraft/server/v1_8_R3/PlayerConnection.java#L544
	// https://github.com/Attano/Spigot-1.8/blob/master/net/minecraft/server/v1_8_R3/PlayerInteractManager.java#L203
	public boolean intercept(PacketPlayInBlockDig packet, VirtualConnection conn) {
		IEnvironment env = conn.getEnvironment();
		EntityPlayer player = conn.getPlayer();

		BlockPosition pos = packet.a();
		if (!env.contains(pos))
			return false;

		if (!env.getOwnerUuid().equals(player.getUniqueID())) {
			player.playerConnection
					.sendPacket(new VirtualBlockChangePacket(env, pos).getPacket());
			TileEntity tile = env.getTileEntity(pos);
			if (tile != null)
				player.playerConnection.sendPacket(tile.getUpdatePacket());
			return true;
		}

		switch (packet.c()) {
			case DROP_ITEM:
				if (player.isSpectator())
					return true;

				// player.a(false);
				break;
			case DROP_ALL_ITEMS:
				if (player.isSpectator())
					return true;

				// player.a(true);
				break;
			case RELEASE_USE_ITEM:
				player.bU();
				break;
			case ABORT_DESTROY_BLOCK:
			case START_DESTROY_BLOCK:
			case STOP_DESTROY_BLOCK:
				double d0 = player.locX - ((double) pos.getX() + 0.5D);
				double d1 = player.locY - ((double) pos.getY() + 0.5D) + 1.5D;
				double d2 = player.locZ - ((double) pos.getZ() + 0.5D);
				double d3 = d0 * d0 + d1 * d1 + d2 * d2;

				if (d3 > 36D)
					return true;
				else if (pos.getY() >= 256)
					return true;
				else if (packet.c() == EnumPlayerDigType.START_DESTROY_BLOCK) {
					interactions.startDestroy(env, player, pos, packet.b());
					return true;
				} else if (packet.c() == EnumPlayerDigType.STOP_DESTROY_BLOCK) {
					interactions.stopDestroy(env, player, pos);
				} else if (packet.c() == EnumPlayerDigType.ABORT_DESTROY_BLOCK) {
					interactions.abortDestroy(env, player);
				}

				if (env.getType(pos).getBlock().getMaterial() != Material.AIR) {
					player.playerConnection.sendPacket(new VirtualBlockChangePacket(env, pos).getPacket());
				}

				break;
			default:
				break;
		}

		// if (player. == GameMode.CREATIVE
		// && packet.c() == EnumPlayerDigType.START_DESTROY_BLOCK) {
		// virtualChunk.setBlock(pos, Blocks.AIR.getBlockData());
		// } else if (packet.c() == EnumPlayerDigType.STOP_DESTROY_BLOCK) {
		// virtualChunk.setBlock(pos, Blocks.AIR.getBlockData());
		// }

		return true;
	}

	@Override
	public Class<PacketPlayInBlockDig> getClazz() {
		return PacketPlayInBlockDig.class;
	}

	public static void register(InteractionHandler interactions) {
		VirtualConnection.registerInterceptor(new BlockDigPacketInterceptor(interactions));
	}
}
