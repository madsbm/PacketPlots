package net.dashmc.plots.events;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.dashmc.plots.plot.data.BlockBag;

@Getter
@RequiredArgsConstructor
public class BlockBagUpdatedEvent extends Event {
	private final boolean isDeposit;
	private final BlockBag bag;

	private static final HandlerList handlers = new HandlerList();

	@Override
	public HandlerList getHandlers() {
		return handlers;
	}

	public static HandlerList getHandlerList() {
		return handlers;
	}

}
