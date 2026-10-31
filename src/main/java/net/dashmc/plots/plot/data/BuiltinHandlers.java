package net.dashmc.plots.plot.data;

import net.dashmc.plots.plot.data.blocks.VirtualBlockAir;
import net.dashmc.plots.plot.data.blocks.VirtualBlockDoor;
import net.dashmc.plots.plot.data.blocks.VirtualBlockLeaves2;
import net.dashmc.plots.plot.data.blocks.VirtualBlockTrapDoor;
import net.dashmc.plots.plot.data.blocks.VirtualChestBlock;
import net.dashmc.plots.plot.data.blocks.VirtualEnderChestBlock;
import net.dashmc.plots.plot.data.blocks.VirtualFenceGateBlock;
import net.dashmc.plots.plot.data.blocks.VirtualSkullBlock;
import net.dashmc.plots.plot.data.items.VirtualItemBlock;
import net.dashmc.plots.plot.data.items.VirtualItemDoor;
import net.dashmc.plots.plot.data.items.VirtualItemSkull;
import net.dashmc.plots.plot.data.items.VirtualItemStep;
import net.dashmc.plots.plot.interaction.PlacementRules;
import net.dashmc.plots.utils.misc.Debug;
import net.minecraft.server.v1_8_R3.BlockAir;
import net.minecraft.server.v1_8_R3.BlockChest;
import net.minecraft.server.v1_8_R3.BlockDoor;
import net.minecraft.server.v1_8_R3.BlockEnderChest;
import net.minecraft.server.v1_8_R3.BlockFenceGate;
import net.minecraft.server.v1_8_R3.BlockLeaves2;
import net.minecraft.server.v1_8_R3.BlockSkull;
import net.minecraft.server.v1_8_R3.BlockTrapdoor;
import net.minecraft.server.v1_8_R3.ItemBanner;
import net.minecraft.server.v1_8_R3.ItemBlock;
import net.minecraft.server.v1_8_R3.ItemDoor;
import net.minecraft.server.v1_8_R3.ItemSkull;
import net.minecraft.server.v1_8_R3.ItemStep;
import net.minecraft.server.v1_8_R3.ItemTallPlant;
import net.minecraft.server.v1_8_R3.ItemWaterLily;

/**
 * The block and item handlers the plugin comes with, which PacketPlots
 * registers when it's enabled. A new handler is added here.
 */
public class BuiltinHandlers {

	public static void registerBlocks(VirtualBlockRegistry blocks, Debug debug) {
		blocks.register(BlockAir.class, new VirtualBlockAir());
		blocks.register(BlockChest.class, new VirtualChestBlock());
		blocks.register(BlockDoor.class, new VirtualBlockDoor());
		blocks.register(BlockEnderChest.class, new VirtualEnderChestBlock());
		blocks.register(BlockFenceGate.class, new VirtualFenceGateBlock(debug));
		blocks.register(BlockSkull.class, new VirtualSkullBlock());
		blocks.register(BlockLeaves2.class, new VirtualBlockLeaves2());
		blocks.register(BlockTrapdoor.class, new VirtualBlockTrapDoor());
	}

	/**
	 * @param blocks    how the blocks the items place behave
	 * @param placement whether the items may place their blocks
	 */
	public static void registerItems(VirtualItemRegistry items, VirtualBlockRegistry blocks,
			PlacementRules placement, Debug debug) {
		// also handles ItemBlock's subclasses without a handler of their own, e.g. wool,
		// leaves, anvils and pistons
		VirtualItemBlock itemBlock = new VirtualItemBlock(blocks, placement, debug);
		items.register(ItemBlock.class, itemBlock);
		items.register(ItemDoor.class, new VirtualItemDoor(blocks, placement, debug));
		items.register(ItemSkull.class, new VirtualItemSkull(blocks));
		// places a slab like an ItemBlock, unless it makes a double slab
		items.register(ItemStep.class, new VirtualItemStep(itemBlock));

		// ItemBlocks the ItemBlock handler would place wrong, so they stay unplaceable:
		// a banner's color comes from the item, a double plant needs its upper half and
		// a lily pad goes on the water
		items.registerUnplaceable(ItemBanner.class);
		items.registerUnplaceable(ItemTallPlant.class);
		items.registerUnplaceable(ItemWaterLily.class);
	}
}
