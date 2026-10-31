package net.dashmc.plots.nms;

import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;
import org.bukkit.entity.Player;

import net.minecraft.server.v1_8_R3.Block;
import net.minecraft.server.v1_8_R3.Entity;
import net.minecraft.server.v1_8_R3.EntityHuman;
import net.minecraft.server.v1_8_R3.EntityPlayer;
import net.minecraft.server.v1_8_R3.PlayerInteractManager;
import net.minecraft.server.v1_8_R3.Statistic;
import net.minecraft.server.v1_8_R3.StatisticList;

/**
 * Players, and the entities they are.
 */
public class NmsPlayers {
	public static final Statistic CHEST_OPENED = StatisticList.aa;
	public static final Statistic TRAPPED_CHEST_TRIGGERED = StatisticList.U;
	public static final Statistic ENDER_CHEST_OPENED = StatisticList.V;

	/**
	 * @return the server's player behind the Bukkit one
	 */
	public static EntityPlayer getHandle(Player player) {
		return ((CraftPlayer) player).getHandle();
	}

	/**
	 * Stops the player using their held item, e.g. drawing a bow.
	 */
	public static void stopUsingItem(EntityHuman player) {
		player.bU();
	}

	public static double getDistanceSquared(Entity entity, double x, double y, double z) {
		return entity.e(x, y, z);
	}

	/**
	 * @return whether the player's held item lets them get the block's drop
	 */
	public static boolean canHarvest(EntityHuman player, Block block) {
		return player.b(block);
	}

	/**
	 * Removes the player's held item, e.g. when it breaks.
	 */
	public static void destroyItemInHand(EntityHuman player) {
		player.ca();
	}

	public static void awardStatistic(EntityHuman player, Statistic statistic) {
		player.b(statistic);
	}

	/**
	 * While this is on, the player's container doesn't send them the slot of a
	 * stack whose amount changed.
	 */
	public static void setChangingQuantityOnly(EntityPlayer player, boolean to) {
		player.g = to;
	}

	/**
	 * @return whether the game mode limits what the player may break
	 */
	public static boolean isAdventureOrSpectator(PlayerInteractManager interactManager) {
		return interactManager.c();
	}
}
