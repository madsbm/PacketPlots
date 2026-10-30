package net.dashmc.plots.listeners;

import java.io.IOException;

import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import lombok.RequiredArgsConstructor;
import net.dashmc.plots.PacketPlots;
import net.dashmc.plots.player.VirtualPlayerInteractManager;
import net.dashmc.plots.plot.EnvironmentManager;
import net.dashmc.plots.utils.misc.Debug;

@RequiredArgsConstructor
public class ConnectionListener implements Listener {
	private final EnvironmentManager environments;

	@EventHandler
	public void onPlayerJoin(PlayerJoinEvent event) {
		Player player = event.getPlayer();
		try {
			Debug.log(((CraftPlayer) player).getHandle().playerInteractManager.getClass().getName());
			VirtualPlayerInteractManager.inject(player, environments);

			environments.load(player);
		} catch (IOException | IllegalArgumentException | IllegalAccessException e) {
			Bukkit.getLogger()
					.severe("A severe error occured whilst loading the PacketPlot of " + player.getUniqueId());
			e.printStackTrace();
		}

	}

	@EventHandler
	public void onPlayerQuit(PlayerQuitEvent event) {
		environments.unload(event.getPlayer());
	}

	public static void register(EnvironmentManager environments) {
		Bukkit.getPluginManager().registerEvents(new ConnectionListener(environments), PacketPlots.getInstance());
	}

}
