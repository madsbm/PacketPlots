package net.dashmc.plots.plot.data.blocks.wrappers;

import lombok.Getter;
import net.dashmc.plots.nms.NmsPlayers;
import net.dashmc.plots.nms.NmsTileEntities;
import net.dashmc.plots.plot.IVirtualWorld;
import net.dashmc.plots.utils.helpers.FieldAccess;
import net.minecraft.server.v1_8_R3.Blocks;
import net.minecraft.server.v1_8_R3.EntityHuman;
import net.minecraft.server.v1_8_R3.PacketPlayOutBlockAction;
import net.minecraft.server.v1_8_R3.TileEntityEnderChest;

@Getter
public class EnderChestTileWrapper extends TileEntityEnderChest {
	private final IVirtualWorld environment;
	private TileEntityEnderChest wrapped;

	public EnderChestTileWrapper(IVirtualWorld env, TileEntityEnderChest src) {
		this.environment = env;
		this.wrapped = src;

		FieldAccess.copyFields(TileEntityEnderChest.class, src, this);
	}

	// whether the player may use it
	@Override
	public boolean a(EntityHuman player) {
		if (!(environment.getTileEntity(this.position) instanceof TileEntityEnderChest))
			return false;

		return NmsPlayers.getDistanceSquared(player, position.getX() + 0.5, position.getY() + 0.5,
				position.getZ() + 0.5) <= 64.0;
	}

	// opened by a player
	@Override
	public void b() {
		NmsTileEntities.addPlayersUsing(wrapped, 1);

		PacketPlayOutBlockAction chestOpenPacket = new PacketPlayOutBlockAction(this.position, Blocks.ENDER_CHEST, 1,
				1);
		environment.broadcast(chestOpenPacket);
	}

	// closed by a player
	@Override
	public void d() {
		NmsTileEntities.addPlayersUsing(wrapped, -1);

		PacketPlayOutBlockAction chestClosePacket = new PacketPlayOutBlockAction(this.position, Blocks.ENDER_CHEST, 1,
				0);
		environment.broadcast(chestClosePacket);
	}
}
