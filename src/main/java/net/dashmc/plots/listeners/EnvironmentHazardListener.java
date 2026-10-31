package net.dashmc.plots.listeners;

import java.util.EnumSet;
import java.util.Set;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustByEntityEvent;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;

import lombok.RequiredArgsConstructor;
import net.dashmc.plots.plot.EnvironmentManager;
import net.dashmc.plots.plot.VirtualConnection;

/**
 * Keeps the real world's blocks from hurting players inside the environment
 * they're shown, where they can't see them: suffocating in a real block where
 * the environment has air, drowning or burning in real water, lava or fire, or
 * touching a real cactus. The tick MovementPacketInterceptor gives players
 * checks the real world for these.
 */
@RequiredArgsConstructor
public class EnvironmentHazardListener implements Listener {
	private static final Set<DamageCause> REAL_BLOCK_CAUSES = EnumSet.of(DamageCause.SUFFOCATION,
			DamageCause.DROWNING, DamageCause.LAVA, DamageCause.FIRE, DamageCause.CONTACT);

	private final EnvironmentManager environments;

	@EventHandler(ignoreCancelled = true)
	public void onDamage(EntityDamageEvent event) {
		if (REAL_BLOCK_CAUSES.contains(event.getCause()) && isInsideEnvironment(event.getEntity()))
			event.setCancelled(true);
	}

	// set on fire by a block, e.g. real lava, rather than by e.g. a burning arrow
	@EventHandler(ignoreCancelled = true)
	public void onCombust(EntityCombustEvent event) {
		if (!(event instanceof EntityCombustByEntityEvent) && isInsideEnvironment(event.getEntity()))
			event.setCancelled(true);
	}

	private boolean isInsideEnvironment(Entity entity) {
		if (!(entity instanceof Player))
			return false;

		VirtualConnection connection = environments.getConnection((Player) entity);
		return connection != null && connection.isInsideEnvironment();
	}
}
