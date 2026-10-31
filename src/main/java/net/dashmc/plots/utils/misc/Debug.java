package net.dashmc.plots.utils.misc;

import java.util.function.Supplier;
import java.util.logging.Logger;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Debug logging, turned on by the debug option in the config.
 */
@RequiredArgsConstructor
public class Debug {
	/**
	 * Logs nothing, e.g. in tests.
	 */
	public static final Debug OFF = new Debug(null, false);

	private final Logger logger;
	private final @Getter boolean enabled;

	public void log(String message) {
		if (!enabled)
			return;
		logger.info(message);
	}

	/**
	 * Logs the message, only building it when debugging is on.
	 */
	public void log(Supplier<String> message) {
		if (!enabled)
			return;
		logger.info(message.get());
	}

}
