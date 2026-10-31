package net.dashmc.plots.plot.data.blocks;

import net.dashmc.plots.nms.NmsBlocks;
import net.dashmc.plots.plot.IVirtualWorld;
import net.dashmc.plots.plot.data.VirtualBlock;
import net.minecraft.server.v1_8_R3.Block;
import net.minecraft.server.v1_8_R3.BlockPosition;
import net.minecraft.server.v1_8_R3.BlockStateBoolean;
import net.minecraft.server.v1_8_R3.EntityHuman;
import net.minecraft.server.v1_8_R3.EnumDirection;
import net.minecraft.server.v1_8_R3.IBlockData;
import net.minecraft.server.v1_8_R3.Material;
import net.minecraft.server.v1_8_R3.PacketPlayOutWorldEvent;

/**
 * A door, trapdoor or fence gate, which a player opens and closes by using it,
 * like vanilla's interact of each.
 */
public abstract class VirtualOpenableBlock<T extends Block> extends VirtualBlock<T> {
	private static final int OPEN_SOUND = 1003;
	private static final int CLOSE_SOUND = 1006;

	// whether the block is open, e.g. BlockDoor.OPEN
	private final BlockStateBoolean openProperty;

	protected VirtualOpenableBlock(BlockStateBoolean openProperty) {
		this.openProperty = openProperty;
	}

	@Override
	public boolean interact(T block, IVirtualWorld environment, BlockPosition pos, IBlockData data,
			EntityHuman human, EnumDirection direction, float f, float f1, float f2) {
		// iron doors and trapdoors only open by redstone
		if (block.getMaterial() == Material.ORE)
			return true;

		BlockPosition statePos = getStatePosition(pos, data);
		IBlockData state = statePos.equals(pos) ? data : environment.getType(statePos);
		if (state.getBlock() != block)
			return false;

		IBlockData toggled = toggle(state, human);
		environment.setBlock(statePos, toggled, IVirtualWorld.SEND_TO_VIEWERS);

		int sound = toggled.get(openProperty) ? OPEN_SOUND : CLOSE_SOUND;
		environment.broadcast(new PacketPlayOutWorldEvent(sound, pos, 0, false));
		return true;
	}

	/**
	 * @return where the block keeps whether it's open, e.g. a door's lower half
	 */
	protected BlockPosition getStatePosition(BlockPosition pos, IBlockData data) {
		return pos;
	}

	/**
	 * @param human who opens or closes it
	 * @return the state opened if it's closed, or closed if it's open
	 */
	protected IBlockData toggle(IBlockData state, EntityHuman human) {
		return NmsBlocks.cycle(state, openProperty);
	}
}
