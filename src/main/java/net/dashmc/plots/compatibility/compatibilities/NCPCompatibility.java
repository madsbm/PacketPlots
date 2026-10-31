package net.dashmc.plots.compatibility.compatibilities;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;

import fr.neatmonster.nocheatplus.hooks.NCPExemptionManager;
import lombok.RequiredArgsConstructor;
import net.dashmc.plots.compatibility.CompatibilityLoader;
import net.dashmc.plots.events.ACRegionEnterExit;
import net.dashmc.plots.utils.CuboidRegion;
import net.minecraft.server.v1_8_R3.EntityPlayer;

@RequiredArgsConstructor
public class NCPCompatibility implements CompatibilityLoader {
	private final Plugin plugin;
	// where anticheats are disabled
	private final CuboidRegion acDisabledRegion;

	@Override
	public boolean shouldActivate() {
		return plugin.getServer().getPluginManager().getPlugin("NoCheatPlus") != null;
	}

	@Override
	public void activate() {
		plugin.getServer().getPluginManager().registerEvents(new EnvironmentListener(acDisabledRegion), plugin);
	}

	@RequiredArgsConstructor
	private static class EnvironmentListener implements Listener {
		private final CuboidRegion acDisabledRegion;

		@EventHandler
		public void onPlayerJoin(PlayerJoinEvent e) {
			boolean spawnedInsideRegion = acDisabledRegion.includes(e.getPlayer().getLocation());
			if (!spawnedInsideRegion)
				NCPExemptionManager.unexempt(e.getPlayer());
			else
				NCPExemptionManager.exemptPermanently(e.getPlayer());
		}

		@EventHandler
		public void onEnterExit(ACRegionEnterExit ev) {
			EntityPlayer player = ev.getConnection().getPlayer();
			if (ev.isEnter()) {
				NCPExemptionManager.exemptPermanently(player.getUniqueID());
			} else
				NCPExemptionManager.unexempt(player.getUniqueID());
		}

	}
}
