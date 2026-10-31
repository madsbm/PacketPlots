package net.dashmc.plots.nms;

import net.minecraft.server.v1_8_R3.BlockPosition;
import net.minecraft.server.v1_8_R3.World;

/**
 * Worlds.
 */
public class NmsWorlds {

	/**
	 * @return whether the world has a sky, so its chunks have sky light, unlike the
	 *         nether's
	 */
	public static boolean hasSky(World world) {
		return !world.worldProvider.o();
	}

	public static void removeTileEntity(World world, BlockPosition pos) {
		world.t(pos);
	}
}
