package net.dashmc.plots.plot.data;

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

public class VirtualItemTest {
	private final VirtualItem<?> itemBlockHandler = VirtualItem.getHandler(ItemBlock.class);

	@Test
	public void itemBlockSubclassesUseTheItemBlockHandler() {
		assertSame(itemBlockHandler, VirtualItem.getHandler(ItemCloth.class));
		assertSame(itemBlockHandler, VirtualItem.getHandler(ItemLeaves.class));
		assertSame(itemBlockHandler, VirtualItem.getHandler(ItemMultiTexture.class));
		assertSame(itemBlockHandler, VirtualItem.getHandler(ItemWithAuxData.class));
		assertSame(itemBlockHandler, VirtualItem.getHandler(ItemAnvil.class));
	}

	@Test
	public void itemsWithAHandlerOfTheirOwnUseThatOne() {
		assertNotSame(itemBlockHandler, VirtualItem.getHandler(ItemStep.class));
	}

	// the ItemBlock handler would place these wrong
	@Test
	public void bannersDoublePlantsAndLilyPadsDontUseTheItemBlockHandler() {
		assertNotSame(itemBlockHandler, VirtualItem.getHandler(ItemBanner.class));
		assertNotSame(itemBlockHandler, VirtualItem.getHandler(ItemTallPlant.class));
		assertNotSame(itemBlockHandler, VirtualItem.getHandler(ItemWaterLily.class));
	}
}
