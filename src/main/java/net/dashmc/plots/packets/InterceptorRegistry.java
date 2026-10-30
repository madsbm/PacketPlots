package net.dashmc.plots.packets;

import net.dashmc.plots.plot.VirtualConnection;
import net.dashmc.plots.utils.helpers.ClassRegistry;
import net.minecraft.server.v1_8_R3.Packet;

/**
 * The packet interceptors, by the class of packet each one intercepts. An
 * interceptor also gets the subclasses of its packet, e.g. the one for
 * PacketPlayInFlying gets its look, position and position-look packets too.
 */
public class InterceptorRegistry {
	private static final ClassRegistry<PacketInterceptor<?>> interceptors = new ClassRegistry<>();

	public static <T extends Packet<?>> void register(PacketInterceptor<T> interceptor) {
		interceptors.register(interceptor.getClazz(), interceptor);
	}

	public static boolean isIntercepted(Class<?> packetClass) {
		return interceptors.get(packetClass) != null;
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
