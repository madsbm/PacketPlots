package net.dashmc.plots.plot.data.items;

import lombok.RequiredArgsConstructor;
import net.dashmc.plots.nms.NmsBlocks;
import net.dashmc.plots.plot.IEnvironment;
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

	@Override
	public boolean interactWith(ItemStack item, EntityHuman player, IEnvironment environment, BlockPosition pos,
			EnumDirection direction, float cX, float cY, float cZ, boolean isBorderPlace) {
		if (item.count == 0)
			return false;
		else if (!PlacementRules.canPlace(environment, pos, direction, item, player))
			return false;

		BlockStepAbstract blockStep = stepField.get(item.getItem());
		IBlockData ibd = environment.getType(pos);
		if (ibd.getBlock() == blockStep) {
			IBlockState<?> ibs = NmsBlocks.getSlabVariantProperty(blockStep);
			Comparable<?> comparable = ibd.get(ibs);

			EnumSlabHalf halfSlab = ibd.get(BlockStepAbstract.HALF);

			boolean isBottomPlace = direction == EnumDirection.UP && halfSlab == EnumSlabHalf.BOTTOM;
			boolean isTopPlace = direction == EnumDirection.DOWN && halfSlab == EnumSlabHalf.TOP;
			if ((isBottomPlace || isTopPlace) && comparable == NmsBlocks.getSlabVariant(blockStep, item)) {
				BlockStepAbstract fullBlock = doubleField.get(item.getItem());

				@SuppressWarnings({ "rawtypes", "unchecked" })
				IBlockData updated = fullBlock.getBlockData().set((IBlockState) ibs, (Comparable) comparable);

				if (environment.setBlock(pos, updated, 3))
					item.count--;

				return true;
			}
		}

		ibd = environment.getType(pos.shift(direction));
		if (ibd.getBlock() == blockStep) {
			IBlockState<?> ibs = NmsBlocks.getSlabVariantProperty(blockStep);
			Comparable<?> comparable = ibd.get(ibs);
			if (comparable == NmsBlocks.getSlabVariant(blockStep, item)) {
				BlockStepAbstract fullBlock = doubleField.get(item.getItem());
				@SuppressWarnings({ "rawtypes", "unchecked" })
				IBlockData updated = fullBlock.getBlockData().set((IBlockState) ibs, (Comparable) comparable);

				if (environment.setBlock(pos.shift(direction), updated, 3))
					item.count--;

				return true;
			}
		}

		return itemBlock.interactWith(item, player, environment, pos, direction, cX, cY, cZ, isBorderPlace);
	}

}
