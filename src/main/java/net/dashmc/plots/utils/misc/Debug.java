package net.dashmc.plots.utils.misc;

import java.util.function.Supplier;

import org.bukkit.Bukkit;

import lombok.Getter;
import lombok.Setter;

/**
 * Debug logging, turned on by the debug option in the config.
 */
public class Debug {
	private static @Getter @Setter boolean enabled = false;

	public static void log(String message) {
		if (!enabled)
			return;
		Bukkit.getLogger().info(message);
	}

	/**
	 * Logs the message, only building it when debugging is on.
	 */
	public static void log(Supplier<String> message) {
		if (!enabled)
			return;
		Bukkit.getLogger().info(message.get());
	}

}
