package net.dashmc.plots.plot.data.items;

import lombok.RequiredArgsConstructor;
import net.dashmc.plots.nms.NmsBlocks;
import net.dashmc.plots.plot.IEnvironment;
import net.dashmc.plots.plot.IVirtualWorld;
import net.dashmc.plots.plot.data.VirtualItem;
import net.dashmc.plots.plot.interaction.PlacementRules;
import net.dashmc.plots.utils.helpers.FieldAccess;
import net.minecraft.server.v1_8_R3.BlockPosition;
import net.minecraft.server.v1_8_R3.BlockStepAbstract;
import net.minecraft.server.v1_8_R3.EntityHuman;
import net.minecraft.server.v1_8_R3.EnumDirection;
import net.minecraft.server.v1_8_R3.IBlockData;
import net.minecraft.server.v1_8_R3.IBlockState;
import net.minecraft.server.v1_8_R3.ItemBlock;
import net.minecraft.server.v1_8_R3.ItemStack;
import net.minecraft.server.v1_8_R3.ItemStep;
import net.minecraft.server.v1_8_R3.BlockStepAbstract.EnumSlabHalf;

@RequiredArgsConstructor
public class VirtualItemStep extends VirtualItem<ItemStep> {
	private static final FieldAccess<BlockStepAbstract> stepField = new FieldAccess<>(ItemStep.class, "b",
			BlockStepAbstract.class);
	private static final FieldAccess<BlockStepAbstract> doubleField = new FieldAccess<>(ItemStep.class, "c",
			BlockStepAbstract.class);

	// places the slab when it doesn't make a double slab
	private final VirtualItem<ItemBlock> itemBlock;
	private final PlacementRules placement;

	@Override
	public boolean interactWith(ItemStack item, EntityHuman player, IEnvironment environment, BlockPosition pos,
			EnumDirection direction, float cX, float cY, float cZ, boolean isBorderPlace) {
		if (item.count == 0)
			return false;
		else if (!placement.canPlace(environment, pos, direction, item, player))
			return false;

		BlockStepAbstract slab = stepField.get(item.getItem());

		IBlockData clicked = environment.getType(pos);
		if (isSameSlab(clicked, slab, item) && isOpenHalfClicked(clicked, direction)) {
			merge(environment, pos, clicked, item);
			return true;
		}

		BlockPosition inFront = pos.shift(direction);
		IBlockData front = environment.getType(inFront);
		if (isSameSlab(front, slab, item)) {
			merge(environment, inFront, front, item);
			return true;
		}

		return itemBlock.interactWith(item, player, environment, pos, direction, cX, cY, cZ, isBorderPlace);
	}

	/**
	 * @return whether the block is a slab of the kind and variant the item places
	 */
	private static boolean isSameSlab(IBlockData data, BlockStepAbstract slab, ItemStack item) {
		return data.getBlock() == slab
				&& data.get(NmsBlocks.getSlabVariantProperty(slab)) == NmsBlocks.getSlabVariant(slab, item);
	}

	/**
	 * @return whether the face clicked is the slab's empty half: the top of a
	 *         bottom slab, or the bottom of a top slab
	 */
	private static boolean isOpenHalfClicked(IBlockData slab, EnumDirection direction) {
		EnumSlabHalf half = slab.get(BlockStepAbstract.HALF);
		return direction == EnumDirection.UP && half == EnumSlabHalf.BOTTOM
				|| direction == EnumDirection.DOWN && half == EnumSlabHalf.TOP;
	}

	/**
	 * Turns the slab into the item's double slab of the same variant.
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	private static void merge(IVirtualWorld environment, BlockPosition pos, IBlockData slab, ItemStack item) {
		IBlockState variantProperty = NmsBlocks.getSlabVariantProperty((BlockStepAbstract) slab.getBlock());
		BlockStepAbstract doubleSlab = doubleField.get(item.getItem());
		IBlockData merged = doubleSlab.getBlockData().set(variantProperty, slab.get(variantProperty));

		if (environment.setBlock(pos, merged, IVirtualWorld.NOTIFY_AND_SEND))
			item.count--;
	}

}
