package net.dashmc.plots.listeners;

import java.io.IOException;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import lombok.RequiredArgsConstructor;
import net.dashmc.plots.PacketPlots;
import net.dashmc.plots.plot.EnvironmentManager;

@RequiredArgsConstructor
public class ConnectionListener implements Listener {
	private final EnvironmentManager environments;

	@EventHandler
	public void onPlayerJoin(PlayerJoinEvent event) {
		join(event.getPlayer());
	}

	@EventHandler
	public void onPlayerQuit(PlayerQuitEvent event) {
		environments.unload(event.getPlayer());
	}

	/**
	 * Loads the player's environment. Also used for players who were already
	 * online when the plugin was enabled, e.g. by a reload.
	 */
	public void join(Player player) {
		try {
			environments.load(player);
		} catch (IOException e) {
			Bukkit.getLogger().log(Level.SEVERE,
					"A severe error occured whilst loading the PacketPlot of " + player.getUniqueId(), e);
		}
	}

	public static ConnectionListener register(EnvironmentManager environments) {
		ConnectionListener listener = new ConnectionListener(environments);
		Bukkit.getPluginManager().registerEvents(listener, PacketPlots.getInstance());
		return listener;
	}

}
