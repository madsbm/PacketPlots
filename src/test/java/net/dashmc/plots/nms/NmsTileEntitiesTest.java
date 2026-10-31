package net.dashmc.plots.nms;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.BeforeClass;
import org.junit.Test;

import net.minecraft.server.v1_8_R3.BlockPosition;
import net.minecraft.server.v1_8_R3.DispenserRegistry;
import net.minecraft.server.v1_8_R3.NBTTagCompound;
import net.minecraft.server.v1_8_R3.TileEntity;
import net.minecraft.server.v1_8_R3.TileEntityChest;

public class NmsTileEntitiesTest {

	@BeforeClass
	public static void bootstrap() {
		// registers the blocks and items, like the server does on startup
		DispenserRegistry.c();
	}

	@Test
	public void invalidateAndValidateMarkWhetherItsRemoved() {
		TileEntityChest chest = new TileEntityChest();
		assertFalse(NmsTileEntities.isInvalid(chest));

		NmsTileEntities.invalidate(chest);
		assertTrue(NmsTileEntities.isInvalid(chest));

		NmsTileEntities.validate(chest);
		assertFalse(NmsTileEntities.isInvalid(chest));
	}

	@Test
	public void aSavedChestLoadsWithItsPositionAndName() {
		TileEntityChest chest = new TileEntityChest();
		NmsTileEntities.setPosition(chest, new BlockPosition(1, 64, -2));
		NmsTileEntities.setCustomName(chest, "Loot");
		NBTTagCompound nbt = new NBTTagCompound();

		NmsTileEntities.save(chest, nbt);
		TileEntity loaded = NmsTileEntities.createFromNbt(nbt);

		assertTrue(loaded instanceof TileEntityChest);
		assertEquals(new BlockPosition(1, 64, -2), loaded.getPosition());
		assertEquals("Loot", ((TileEntityChest) loaded).getName());
	}

	@Test
	public void loadReadsWhatSaveWrote() {
		TileEntityChest chest = new TileEntityChest();
		NmsTileEntities.setCustomName(chest, "Loot");
		NBTTagCompound nbt = new NBTTagCompound();
		NmsTileEntities.save(chest, nbt);

		TileEntityChest loaded = new TileEntityChest();
		NmsTileEntities.load(loaded, nbt);

		assertEquals("Loot", loaded.getName());
	}
}
