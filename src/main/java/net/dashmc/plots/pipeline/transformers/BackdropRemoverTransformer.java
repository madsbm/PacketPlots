package net.dashmc.plots.pipeline.transformers;

import net.dashmc.plots.nms.NmsChunks;
import net.dashmc.plots.pipeline.IRenderTransformer;
import net.dashmc.plots.plot.data.VirtualChunk;
import net.dashmc.plots.utils.misc.Utils;
import net.minecraft.server.v1_8_R3.ChunkSection;
import net.minecraft.server.v1_8_R3.PacketPlayOutMapChunk.ChunkMap;

public class BackdropRemoverTransformer implements IRenderTransformer {

	@Override
	public void accept(VirtualChunk virtualChunk, ChunkMap chunkMap, int metaStartIdx) {
		byte[] data = NmsChunks.getData(chunkMap);
		int sectionMask = NmsChunks.getSectionMask(chunkMap);
		int mapPointer = 0;
		for (int i = 0; i < 16; i++) {
			if ((sectionMask & (1 << i)) == 0)
				continue;
			if ((virtualChunk.getSectionMask() & (1 << i)) == 0) {
				final int newPointer = mapPointer + 4096 * 2;
				for (; mapPointer < newPointer; mapPointer++) {
					data[mapPointer] = 0;
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
				if (blockIds[sectionPointer] != Utils.getMapBlockId(data, mapPointer))
					continue;

				Utils.setMapBlockId(data, mapPointer, (char) 0);
			}
		}
	}

}
