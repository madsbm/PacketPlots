package net.dashmc.plots.packets.interceptors;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.util.NumberConversions;

import lombok.RequiredArgsConstructor;
import net.dashmc.plots.nms.NmsInventories;
import net.dashmc.plots.nms.NmsItems;
import net.dashmc.plots.nms.NmsPackets;
import net.dashmc.plots.nms.NmsPlayers;
import net.dashmc.plots.packets.PacketInterceptor;
import net.dashmc.plots.packets.extensions.VirtualBlockChangePacket;
import net.dashmc.plots.plot.IEnvironment;
import net.dashmc.plots.plot.VirtualConnection;
import net.dashmc.plots.plot.interaction.InteractionHandler;
import net.dashmc.plots.utils.misc.Debug;
import net.minecraft.server.v1_8_R3.BlockPosition;
import net.minecraft.server.v1_8_R3.EntityPlayer;
import net.minecraft.server.v1_8_R3.EnumDirection;
import net.minecraft.server.v1_8_R3.ItemStack;
import net.minecraft.server.v1_8_R3.PacketPlayInBlockPlace;
import net.minecraft.server.v1_8_R3.PacketPlayOutBlockChange;
import net.minecraft.server.v1_8_R3.PacketPlayOutSetSlot;
import net.minecraft.server.v1_8_R3.Slot;

// https://minecraft.wiki/w/Protocol?oldid=2772100#Player_Digging
// https://github.com/Attano/Spigot-1.8/blob/9db48bc15e203179554b8d992ca6b0a528c8d300/net/minecraft/server/v1_8_R3/PlayerConnection.java#L638
@RequiredArgsConstructor
public class BlockPlacementPacketInterceptor extends PacketInterceptor<PacketPlayInBlockPlace> {
	private final InteractionHandler interactions;
	private final Debug debug;

	@Override
	public boolean intercept(PacketPlayInBlockPlace packet, VirtualConnection conn) {
		EntityPlayer player = conn.getPlayer();
		IEnvironment env = conn.getEnvironment();

		BlockPosition pos = NmsPackets.getPosition(packet);

		EnumDirection dir = EnumDirection.fromType1(packet.getFace());
		boolean isBorderPlace = !env.contains(pos) && env.contains(pos.shift(dir));
		if (!env.contains(pos) && !isBorderPlace)
			return false;

		if (player.dead)
			return true;

		boolean always = false;
		ItemStack inHand = player.inventory.getItemInHand();
		boolean flag = false;

		player.resetIdleTimer();
		if (packet.getFace() == 255) {
			// handle bow stuff and such
		} else if (pos.getY() >= 255 && (dir == EnumDirection.UP || pos.getY() >= 256)) {
			// above build limit
			return true;
		} else {

			Location eyeLoc = player.getBukkitEntity().getEyeLocation();
			double reachDist = NumberConversions.square(eyeLoc.getX() - pos.getX())
					+ NumberConversions.square(eyeLoc.getY() - pos.getY())
					+ NumberConversions.square(eyeLoc.getZ() - pos.getZ());

			debug.log(() -> "Reach dist: " + reachDist);

			if (reachDist > (player.getBukkitEntity().getGameMode() == GameMode.CREATIVE ? 7 * 7 : 6 * 6))
				return true;

			double distanceSquared = NmsPlayers.getDistanceSquared(player, pos.getX() + 0.5D, pos.getY() + 0.5D,
					pos.getZ() + 0.5D);
			debug.log(() -> "Distance from block: " + distanceSquared);

			if (distanceSquared < 64D) {
				always = interactions.interact(env, player, inHand, pos, dir, NmsPackets.getCursorX(packet),
						NmsPackets.getCursorY(packet), NmsPackets.getCursorZ(packet),
						isBorderPlace);
			}

			flag = true;
		}

		if (flag) {
			if (isBorderPlace) {
				player.playerConnection.sendPacket(new PacketPlayOutBlockChange(env.getRealWorld(), pos));
			} else {
				VirtualBlockChangePacket.resync(player, env, pos);
			}
			VirtualBlockChangePacket.resync(player, env, pos.shift(dir));
		}

		inHand = player.inventory.getItemInHand();
		int inHandIndex = player.inventory.itemInHandIndex;
		if (inHand != null && inHand.count == 0) {
			player.inventory.items[inHandIndex] = null;
			inHand = null;
		}

		if (inHand == null || NmsItems.getUseDuration(inHand) == 0) {
			NmsPlayers.setChangingQuantityOnly(player, true);
			player.inventory.items[inHandIndex] = NmsItems.copy(player.inventory.items[inHandIndex]);
			Slot slot = player.activeContainer.getSlot(player.inventory, inHandIndex);

			NmsInventories.sendChanges(player.activeContainer);
			NmsPlayers.setChangingQuantityOnly(player, false);

			if (ItemStack.matches(player.inventory.getItemInHand(), packet.getItemStack()) || always) {
				player.playerConnection.sendPacket(new PacketPlayOutSetSlot(player.activeContainer.windowId,
						slot.rawSlotIndex, player.inventory.getItemInHand()));
			}
		}

		return true;
	}

}
