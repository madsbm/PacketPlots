package net.dashmc.plots.packets;

import java.util.HashMap;

import net.dashmc.plots.plot.VirtualConnection;
import net.minecraft.server.v1_8_R3.Packet;

/**
 * The packet interceptors, by the class of packet each one intercepts.
 */
public class InterceptorRegistry {
	private static final HashMap<Class<?>, PacketInterceptor<?>> interceptors = new HashMap<>();

	public static <T extends Packet<?>> void register(PacketInterceptor<T> interceptor) {
		interceptors.put(interceptor.getClazz(), interceptor);
	}

	public static boolean isIntercepted(Class<?> packetClass) {
		return interceptors.containsKey(packetClass);
	}

	/**
	 * @return true if the packet was intercepted, so it shouldn't be handled any
	 *         further
	 */
	@SuppressWarnings("unchecked")
	public static <T extends Packet<?>> boolean intercept(T packet, VirtualConnection connection) {
		PacketInterceptor<T> interceptor = (PacketInterceptor<T>) interceptors.get(packet.getClass());
		if (interceptor != null) {
			return interceptor.intercept(packet, connection);
		}

		return false;
	}
}
