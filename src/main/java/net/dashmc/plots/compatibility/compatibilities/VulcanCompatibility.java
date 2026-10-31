package net.dashmc.plots.compatibility.compatibilities;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.logging.Level;

import org.apache.commons.io.Charsets;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import lombok.RequiredArgsConstructor;
import me.frep.vulcan.api.event.VulcanFlagEvent;
import net.dashmc.plots.compatibility.CompatibilityLoader;
import net.dashmc.plots.utils.CuboidRegion;
import net.dashmc.plots.utils.misc.Debug;

public class VulcanCompatibility extends CompatibilityLoader {

	@Override
	public boolean shouldActivate() {
		return Bukkit.getPluginManager().getPlugin("Vulcan") != null;
	}

	@Override
	public void activate(Plugin plugin, CuboidRegion acDisabledRegion, boolean forced) {
		updateVulcanConfig();

		plugin.getServer().getPluginManager().registerEvents(new VulcanFlagListener(acDisabledRegion), plugin);
	}

	private void updateVulcanConfig() {
		Plugin vulcan = Bukkit.getPluginManager().getPlugin("Vulcan");

		File confFile = new File(vulcan.getDataFolder(), "config.yml");
		try (InputStreamReader reader = new InputStreamReader(new FileInputStream(confFile), Charsets.UTF_8)) {
			YamlConfiguration vulcanConfig = new YamlConfiguration();
			vulcanConfig.load(reader);

			Boolean isGhostBlocksEnabled = vulcanConfig.getBoolean("ghost-blocks-fix.enabled");
			Boolean isAPIEnabled = vulcanConfig.getBoolean("settings.enable-api");
			Debug.log(() -> "Is ghost blocks enabled? " + isGhostBlocksEnabled);
			Debug.log(() -> "Is api enabled? " + isAPIEnabled);

			boolean shouldReload = false;

			if (isGhostBlocksEnabled) {
				Bukkit.getLogger().warning("Ghost blocks fix was enabled... disabling for compatibility...");

				vulcanConfig.set("ghost-blocks-fix.enabled", false);
				vulcanConfig.save(confFile);

				shouldReload = true;
			}

			if (!isAPIEnabled) {
				Bukkit.getLogger().warning("The Vulcan API was disabled... enabling for compatibility...");

				vulcanConfig.set("settings.enable-api", true);
				vulcanConfig.save(confFile);

				shouldReload = true;
			}

			if (!shouldReload)
				return;

			Bukkit.getLogger().warning("Done! Reloading vulcan...");
			Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), "vulcan reload");
		} catch (IOException | InvalidConfigurationException e) {
			Bukkit.getLogger().log(Level.WARNING, "[PacketPlots] Couldn't update " + confFile
					+ ". Set ghost-blocks-fix.enabled to false and settings.enable-api to true by hand.", e);
		}
	}

	public enum VulcanViolationType {
		COMBAT,
		MOVEMENT,
		AUTOCLICKER,
		PLAYER,
		TIMER,
		SCAFFOLD;

		public static VulcanViolationType from(String category) {
			return valueOf(category.toUpperCase());
		}
	}

	@RequiredArgsConstructor
	private static class VulcanFlagListener implements Listener {
		private final CuboidRegion acDisabledRegion;

		@EventHandler
		public void onFlag(VulcanFlagEvent ev) {
			String category = ev.getCheck().getCategory();
			VulcanViolationType violationType = VulcanViolationType.from(category);
			if (violationType != VulcanViolationType.MOVEMENT)
				return;

			Location playerLocation = ev.getPlayer().getLocation();
			if (!acDisabledRegion.includesWithBuffer(playerLocation))
				return;

			ev.setCancelled(true);
		}

	}
}
