package net.dashmc.plots.plot.data;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;

import org.junit.Test;

import net.minecraft.server.v1_8_R3.ItemAnvil;
import net.minecraft.server.v1_8_R3.ItemBanner;
import net.minecraft.server.v1_8_R3.ItemBlock;
import net.minecraft.server.v1_8_R3.ItemCloth;
import net.minecraft.server.v1_8_R3.ItemLeaves;
import net.minecraft.server.v1_8_R3.ItemMultiTexture;
import net.minecraft.server.v1_8_R3.ItemStep;
import net.minecraft.server.v1_8_R3.ItemTallPlant;
import net.minecraft.server.v1_8_R3.ItemWaterLily;
import net.minecraft.server.v1_8_R3.ItemWithAuxData;

// the handlers the plugin registers
public class VirtualItemTest {
	private final VirtualItemRegistry items = builtinItems();
	private final VirtualItem<?> itemBlockHandler = items.getHandler(ItemBlock.class);

	private static VirtualItemRegistry builtinItems() {
		VirtualItemRegistry items = new VirtualItemRegistry();
		BuiltinHandlers.registerItems(items);
		return items;
	}

	@Test
	public void itemBlockSubclassesUseTheItemBlockHandler() {
		assertSame(itemBlockHandler, items.getHandler(ItemCloth.class));
		assertSame(itemBlockHandler, items.getHandler(ItemLeaves.class));
		assertSame(itemBlockHandler, items.getHandler(ItemMultiTexture.class));
		assertSame(itemBlockHandler, items.getHandler(ItemWithAuxData.class));
		assertSame(itemBlockHandler, items.getHandler(ItemAnvil.class));
	}

	@Test
	public void itemsWithAHandlerOfTheirOwnUseThatOne() {
		assertNotSame(itemBlockHandler, items.getHandler(ItemStep.class));
	}

	// the ItemBlock handler would place these wrong
	@Test
	public void bannersDoublePlantsAndLilyPadsDontUseTheItemBlockHandler() {
		assertNotSame(itemBlockHandler, items.getHandler(ItemBanner.class));
		assertNotSame(itemBlockHandler, items.getHandler(ItemTallPlant.class));
		assertNotSame(itemBlockHandler, items.getHandler(ItemWaterLily.class));
	}

	@Test
	public void anEmptyRegistryPlacesNothing() {
		VirtualItemRegistry empty = new VirtualItemRegistry();

		assertSame(empty.getHandler(ItemBlock.class), empty.getHandler(ItemBanner.class));
		assertFalse(empty.getHandler(ItemBlock.class).interactWith(null, null, null, null, null, 0, 0, 0, false));
	}
}
