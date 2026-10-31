package net.dashmc.plots.plot.interaction;

import org.bukkit.craftbukkit.v1_8_R3.util.CraftMagicNumbers;

import net.dashmc.plots.events.VirtualBlockCanBuildEvent;
import net.dashmc.plots.plot.IEnvironment;
import net.dashmc.plots.plot.IVirtualWorld;
import net.dashmc.plots.plot.data.VirtualBlock;
import net.dashmc.plots.utils.misc.Debug;
import net.dashmc.plots.utils.misc.Utils;
import net.minecraft.server.v1_8_R3.AxisAlignedBB;
import net.minecraft.server.v1_8_R3.Block;
import net.minecraft.server.v1_8_R3.BlockPosition;
import net.minecraft.server.v1_8_R3.Blocks;
import net.minecraft.server.v1_8_R3.Entity;
import net.minecraft.server.v1_8_R3.EntityHuman;
import net.minecraft.server.v1_8_R3.EnumDirection;
import net.minecraft.server.v1_8_R3.ItemStack;
import net.minecraft.server.v1_8_R3.Material;

/**
 * Whether a block may be placed at a position in a virtual environment.
 */
public class PlacementRules {

	// https://github.com/Attano/Spigot-1.8/blob/9db48bc15e203179554b8d992ca6b0a528c8d300/net/minecraft/server/v1_8_R3/World.java#L2664
	public static boolean isBuildable(IEnvironment env, Block block, BlockPosition pos, boolean ignoreCollision,
			EnumDirection dir, Entity entity, ItemStack itemStack) {
		Block curr = env.getType(pos).getBlock();
		AxisAlignedBB bb = ignoreCollision ? null
				: VirtualBlock.getBoundingBox(block, env, pos, block.getBlockData());

		final boolean defaultReturn;
		if (bb != null && !isNoOtherEntitiesInside(bb, entity)) {
			defaultReturn = false;
		} else if (curr.getMaterial() == Material.ORIENTABLE && block == Blocks.ANVIL) {
			defaultReturn = true;
		} else {
			Debug.log(() -> "isReplaceable: " + curr.getMaterial().isReplaceable());
			Debug.log(() -> "mayPlace: " + VirtualBlock.mayPlace(block, env, pos, dir, itemStack) + " (pos: "
					+ pos.toString() + ")");

			defaultReturn = curr.getMaterial().isReplaceable()
					&& VirtualBlock.mayPlace(block, env, pos, dir, itemStack);
		}

		@SuppressWarnings("deprecation")
		VirtualBlockCanBuildEvent event = new VirtualBlockCanBuildEvent(defaultReturn,
				Utils.convertPosToLoc(env.getRealWorld(), pos),
				org.bukkit.Material.getMaterial(CraftMagicNumbers.getId(curr)), env);
		env.getEvents().call(event);

		return event.isBuildable();
	}

	public static boolean canPlace(IVirtualWorld world, BlockPosition pos, EnumDirection dir, ItemStack itemStack,
			EntityHuman player) {
		if (player.abilities.mayBuild)
			return true;
		if (itemStack == null)
			return false;

		BlockPosition shifted = pos.shift(dir.opposite());
		Block block = world.getType(shifted).getBlock();
		return itemStack.d(block) || itemStack.x();
	}

	/**
	 * Not yet implemented - entities arent supported
	 *
	 * @param bb
	 * @param entity
	 * @return
	 */
	private static boolean isNoOtherEntitiesInside(AxisAlignedBB bb, Entity entity) {
		return true;
	}
}
