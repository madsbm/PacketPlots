package net.dashmc.plots.pipeline.transformers;

import net.dashmc.plots.pipeline.IRenderTransformer;
import net.dashmc.plots.plot.data.VirtualChunk;
import net.dashmc.plots.utils.misc.Utils;
import net.minecraft.server.v1_8_R3.ChunkSection;
import net.minecraft.server.v1_8_R3.PacketPlayOutMapChunk.ChunkMap;

public class BackdropRemoverTransformer implements IRenderTransformer {

	@Override
	public void accept(VirtualChunk virtualChunk, ChunkMap chunkMap, int metaStartIdx) {
		int mapPointer = 0;
		for (int i = 0; i < 16; i++) {
			if ((chunkMap.b & (1 << i)) == 0)
				continue;
			if ((virtualChunk.getSectionMask() & (1 << i)) == 0) {
				final int newPointer = mapPointer + 4096 * 2;
				for (; mapPointer < newPointer; mapPointer++) {
					chunkMap.a[mapPointer] = 0;
				}

				continue;
			}

			ChunkSection section = virtualChunk.getChunk().getSections()[i];
			if (section == null) {
				mapPointer += 4096 * 2;
				continue;
			}

			char[] blockIds = section.getIdArray();
			for (int sectionPointer = 0; sectionPointer < blockIds.length; sectionPointer++, mapPointer += 2) {
				if (blockIds[sectionPointer] != Utils.getMapBlockId(chunkMap.a, mapPointer))
					continue;

				Utils.setMapBlockId(chunkMap.a, mapPointer, (char) 0);
			}
		}
	}

}
