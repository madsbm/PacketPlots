package net.dashmc.plots.events;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.craftbukkit.v1_8_R3.block.CraftBlock;
import org.bukkit.craftbukkit.v1_8_R3.inventory.CraftItemStack;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.event.block.Action;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import net.dashmc.plots.plot.IEnvironment;
import net.dashmc.plots.utils.misc.Utils;
import net.minecraft.server.v1_8_R3.BlockPosition;
import net.minecraft.server.v1_8_R3.EntityHuman;
import net.minecraft.server.v1_8_R3.EnumDirection;
import net.minecraft.server.v1_8_R3.ItemStack;

@Getter
@RequiredArgsConstructor
public class VirtualInteractEvent extends Event implements Cancellable {
	private final Player player;
	private final CraftItemStack itemInHand;
	private final Location location;
	private final Action action;
	private final BlockFace face;
	private final IEnvironment environment;

	@Setter
	private Result useClickedBlock;
	@Setter
	private Result useItemInHand;

	public VirtualInteractEvent(EntityHuman who, Action action, BlockPosition pos, EnumDirection dir, ItemStack item,
			boolean cancelledBlock, IEnvironment env) {
		this.environment = env;
		this.player = (Player) who.getBukkitEntity();
		CraftItemStack itemInHand = CraftItemStack.asCraftMirror(item);
		this.face = CraftBlock.notchToBlockFace(dir);

		if (itemInHand.getType() == Material.AIR || itemInHand.getAmount() == 0)
			itemInHand = null;
		this.itemInHand = itemInHand;
		this.action = action;
		this.location = Utils.convertPosToLoc(env.getRealWorld(), pos);
	}

	public void setCancelled(boolean cancel) {
		setUseClickedBlock(cancel ? Result.DENY : useClickedBlock == Result.DENY ? Result.DEFAULT : useClickedBlock);
		setUseItemInHand(cancel ? Result.DENY : useItemInHand == Result.DENY ? Result.DEFAULT : useItemInHand);
	}

	public boolean isCancelled() {
		return useClickedBlock == Result.DENY;
	}

	private static final HandlerList handlers = new HandlerList();

	@Override
	public HandlerList getHandlers() {
		return handlers;
	}

	public static HandlerList getHandlerList() {
		return handlers;
	}

}
