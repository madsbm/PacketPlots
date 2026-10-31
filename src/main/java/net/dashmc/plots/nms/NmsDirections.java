package net.dashmc.plots.nms;

import net.minecraft.server.v1_8_R3.EnumDirection;
import net.minecraft.server.v1_8_R3.EnumDirection.EnumAxis;

/**
 * Directions.
 */
public class NmsDirections {

	public static EnumAxis getAxis(EnumDirection direction) {
		return direction.k();
	}

	/**
	 * @return the horizontal direction a quarter turn clockwise, seen from above
	 */
	public static EnumDirection rotateClockwise(EnumDirection direction) {
		return direction.e();
	}

	/**
	 * @return the horizontal direction a quarter turn counterclockwise, seen from
	 *         above
	 */
	public static EnumDirection rotateCounterClockwise(EnumDirection direction) {
		return direction.f();
	}
}
