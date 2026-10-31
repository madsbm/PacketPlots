package net.dashmc.plots.plot.data;

import net.dashmc.plots.utils.helpers.ClassRegistry;
import net.minecraft.server.v1_8_R3.Block;

/**
 * The block handlers, by the class of block each one handles. A block uses the
 * handler of its class, or else of its closest superclass with one, or else
 * the default, which does what vanilla does.
 */
public class VirtualBlockRegistry {
	/**
	 * The handler of blocks without one of their own.
	 */
	private static final VirtualBlock<Block> DEFAULT = new VirtualBlock<Block>() {
	};

	private final ClassRegistry<VirtualBlock<?>> handlers = new ClassRegistry<>();

	public <T extends Block> void register(Class<T> blockClass, VirtualBlock<? super T> handler) {
		handlers.register(blockClass, handler);
	}

	/**
	 * @return the handler of the block, which is given the block along with the
	 *         rest, e.g. handlerOf(block).onPlace(block, environment, pos, data)
	 */
	@SuppressWarnings("unchecked")
	public VirtualBlock<Block> handlerOf(Block block) {
		// the handler is for the block's class or a superclass of it, so it takes the
		// block
		VirtualBlock<Block> handler = (VirtualBlock<Block>) handlers.get(block.getClass());
		return handler != null ? handler : DEFAULT;
	}
}
