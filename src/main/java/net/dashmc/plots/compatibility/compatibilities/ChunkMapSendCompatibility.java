package net.dashmc.plots.compatibility.compatibilities;

import org.bukkit.plugin.Plugin;

import lombok.RequiredArgsConstructor;
import net.dashmc.plots.compatibility.CompatibilityLoader;

@RequiredArgsConstructor
public class ChunkMapSendCompatibility implements CompatibilityLoader {
	private final Plugin plugin;

	@Override
	public boolean shouldActivate() {
		return plugin.getServer().getPluginManager().getPlugin("packetevents") != null;
	}

	/**
	 * doesnt actively change anything - is depended upon
	 * 
	 * @see net.dashmc.plots.plot.VirtualConnection#getRenderPacket
	 */
	@Override
	public void activate() {
	}

}
