package net.dashmc.plots.compatibility.compatibilities;

import java.util.concurrent.atomic.AtomicReference;

import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.v1_8_R3.inventory.CraftItemStack;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import me.arcaniax.hdb.api.DatabaseLoadEvent;
import me.arcaniax.hdb.api.HeadDatabaseAPI;
import net.dashmc.plots.compatibility.CompatibilityLoader;
import net.dashmc.plots.events.PreBlockBagDepositEvent;
import net.dashmc.plots.utils.CuboidRegion;
import net.minecraft.server.v1_8_R3.ItemStack;
import net.minecraft.server.v1_8_R3.Items;

public class HDBCompatibility extends CompatibilityLoader implements Listener {
	@Getter
	private boolean activated = false;
	private AtomicReference<HeadDatabaseAPI> api = new AtomicReference<>();

	@Override
	public boolean shouldActivate() {
		return Bukkit.getPluginManager().getPlugin("HeadDatabase") != null;
	}

	@Override
	public void activate(Plugin plugin, CuboidRegion acDisabledRegion, boolean forced) {
		if (this.activated)
			return;
		this.activated = true;

		plugin.getServer().getPluginManager().registerEvents(this, plugin);
		plugin.getServer().getPluginManager().registerEvents(new BlockBagListener(api), plugin);
	}

	@EventHandler
	public void onDatabaseLoad(DatabaseLoadEvent event) {

		api.set(new HeadDatabaseAPI());
	}

	@RequiredArgsConstructor
	private static class BlockBagListener implements Listener {
		private final AtomicReference<HeadDatabaseAPI> api;

		@EventHandler
		public void onBagPreUpdate(PreBlockBagDepositEvent event) {
			HeadDatabaseAPI api = this.api.get();
			if (api == null) {
				Bukkit.getLogger().warning("PreBlockBagDepositEvent called before HDB was initialized!");
				return;
			}

			ItemStack[] items = event.getItems();
			for (int i = 0; i < items.length; i++) {
				ItemStack item = items[i];
				if (item == null)
					continue;
				if (item.getItem() != Items.SKULL)
					continue;
				if (item.getData() != 3)
					continue;

				org.bukkit.inventory.ItemStack bukkitItemStack = CraftItemStack.asBukkitCopy(item);
				String itemId = api.getItemID(bukkitItemStack);
				if (itemId == null)
					continue;

				org.bukkit.inventory.ItemStack result = api.getItemHead(itemId);
				if (result == null)
					continue;

				ItemStack nmsItemStack = CraftItemStack.asNMSCopy(result);
				items[i] = nmsItemStack;
			}
		}
	}

}
