package net.dashmc.plots.pipeline.transformers;

import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import net.dashmc.plots.pipeline.IRenderTransformer;
import net.dashmc.plots.plot.data.VirtualChunk;
import net.dashmc.plots.utils.CuboidRegion;
import net.dashmc.plots.utils.misc.Utils;
import net.minecraft.server.v1_8_R3.Chunk;
import net.minecraft.server.v1_8_R3.ChunkCoordIntPair;
import net.minecraft.server.v1_8_R3.ChunkSection;
import net.minecraft.server.v1_8_R3.PacketPlayOutMapChunk.ChunkMap;

@RequiredArgsConstructor
@AllArgsConstructor
public class BackdropAdderTransformer implements IRenderTransformer {
	private final Chunk backdropChunk;
	private CuboidRegion toExclude = null;

	@Override
	public void accept(VirtualChunk virtualChunk, ChunkMap chunkMap, int metaStartIdx) {
		int mapPointer = 0;
		ChunkCoordIntPair backdropXZ = backdropChunk.j();

		for (int i = 0; i < 16; i++) {
			ChunkSection section = backdropChunk.getSections()[i];
			if ((chunkMap.b & (1 << i)) == 0)
				continue;
			else if ((section == null)) {
				mapPointer += 4096 * 2;
				continue;
			}

			char[] blockIds = section.getIdArray();
			for (int sectionPointer = 0; sectionPointer < blockIds.length; sectionPointer++, mapPointer += 2) {
				if (toExclude != null) {
					int shortIdx = mapPointer >> 1;

					// dont apply the offset here as the region isnt offset!
					int x = (shortIdx & 0xF) + (backdropXZ.x << 4);
					int y = ((shortIdx >> 8) & 0xF) + (i << 4);
					int z = ((shortIdx >> 4) & 0xF) + (backdropXZ.z << 4);

					boolean includes = toExclude.includes(x, y, z);
					if (includes)
						continue;
				}

				if (Utils.getMapBlockId(chunkMap.a, mapPointer) != (char) 0)
					continue;

				Utils.setMapBlockId(chunkMap.a, mapPointer, blockIds[sectionPointer]);
			}
		}
	}

}
