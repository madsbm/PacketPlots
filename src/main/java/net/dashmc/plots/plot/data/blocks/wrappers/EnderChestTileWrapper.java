package net.dashmc.plots.plot.data.blocks.wrappers;

import lombok.Getter;
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

	@Override
	public boolean a(EntityHuman player) {
		if (!(environment.getTileEntity(this.position) instanceof TileEntityEnderChest))
			return false;

		return !(player.e((double) this.position.getX() + 0.5, (double) this.position.getY() + 0.5,
				(double) this.position.getZ() + 0.5) > 64.0);
	}

	@Override
	public void b() {
		++wrapped.g;

		PacketPlayOutBlockAction chestOpenPacket = new PacketPlayOutBlockAction(this.position, Blocks.ENDER_CHEST, 1,
				1);
		environment.broadcast(chestOpenPacket);
	}

	@Override
	public void d() {
		--wrapped.g;

		PacketPlayOutBlockAction chestClosePacket = new PacketPlayOutBlockAction(this.position, Blocks.ENDER_CHEST, 1,
				0);
		environment.broadcast(chestClosePacket);
	}
}
