package net.dashmc.plots.compatibility.compatibilities;

import org.bukkit.Bukkit;
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

public class NCPCompatibility extends CompatibilityLoader {

	@Override
	public boolean shouldActivate() {
		return Bukkit.getPluginManager().getPlugin("NoCheatPlus") != null;
	}

	@Override
	public void activate(Plugin plugin, CuboidRegion acDisabledRegion, boolean forced) {
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
