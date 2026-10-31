package net.dashmc.plots.nms;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import net.minecraft.server.v1_8_R3.EnumDirection;
import net.minecraft.server.v1_8_R3.EnumDirection.EnumAxis;

public class NmsDirectionsTest {

	@Test
	public void eastIsAQuarterTurnClockwiseFromNorthAndWestCounterclockwise() {
		assertEquals(EnumDirection.EAST, NmsDirections.rotateClockwise(EnumDirection.NORTH));
		assertEquals(EnumDirection.WEST, NmsDirections.rotateCounterClockwise(EnumDirection.NORTH));
	}

	@Test
	public void eastIsAlongTheXAxis() {
		assertEquals(EnumAxis.X, NmsDirections.getAxis(EnumDirection.EAST));
		assertEquals(EnumAxis.Z, NmsDirections.getAxis(EnumDirection.SOUTH));
	}
}
