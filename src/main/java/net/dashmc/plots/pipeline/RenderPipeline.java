package net.dashmc.plots.pipeline;

import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import com.google.common.collect.Lists;

import lombok.RequiredArgsConstructor;
import net.dashmc.plots.plot.data.VirtualChunk;
import net.dashmc.plots.plot.data.VirtualChunk.Section;
import net.minecraft.server.v1_8_R3.ChunkSection;
import net.minecraft.server.v1_8_R3.PacketPlayOutMapChunk.ChunkMap;

@RequiredArgsConstructor
public class RenderPipeline {
	private static byte[][] lightArrays = new byte[16][];

	private final List<IRenderTransformer> transformers;

	public RenderPipeline() {
		this(new LinkedList<>());
	}

	public ChunkMap render(VirtualChunk chunk) {
		return render(chunk, true);
	}

	public ChunkMap render(VirtualChunk chunk, boolean includeBiome) {
		return render(chunk, 0xffff, includeBiome);
	}

	public ChunkMap render(VirtualChunk chunk, int mask, boolean includeBiome) {
		ChunkMap map = getChunkMap(chunk, mask, includeBiome);

		int nonEmptyChunkSections = Integer.bitCount(map.b);
		// block ids are 2 bytes each - light and biome data follow them
		int metaStartIdx = nonEmptyChunkSections * 2 * 16 * 16 * 16;

		transformers.forEach((consumer) -> consumer.accept(chunk, map, metaStartIdx));
		return map;
	}

	protected ChunkMap getChunkMap(VirtualChunk chunk, int mask, boolean includeBiome) {
		boolean isOverworld = !chunk.getEnvironment().getNmsWorld().worldProvider.o();

		ChunkMap chunkMap = new ChunkMap();
		List<Section> arraylist = Lists.newArrayList();
		Section[] sections = chunk.getSections();

		int j;
		for (j = 0; j < sections.length; j++) {
			if ((chunk.getSectionMask() & 1 << j) != 0) {
				Section section = sections[j];
				if (section != null && !section.isEmpty() && (mask & 1 << j) != 0) {
					chunkMap.b |= 1 << j;
					arraylist.add(section);
				}
			} else {
				ChunkSection chunkSection = chunk.getChunk().getSections()[j];
				if (chunkSection == null)
					continue;
				Section section = chunk.new Section(chunkSection);
				if (!section.isEmpty() && (mask & 1 << j) != 0) {
					chunkMap.b |= 1 << j;
					arraylist.add(section);
				}
			}
		}

		int nonEmptyChunkSections = Integer.bitCount(chunkMap.b);
		chunkMap.a = new byte[calculateNeededBytes(nonEmptyChunkSections, isOverworld, includeBiome)];

		j = 0;
		Section section;
		Iterator<Section> iterator = arraylist.iterator();
		while (iterator.hasNext()) {
			section = iterator.next();
			char[] idArray = section.getBlockIds();

			for (int l = 0; l < idArray.length; l++) {
				char c0 = idArray[l];
				chunkMap.a[j++] = (byte) (c0 & 255);
				chunkMap.a[j++] = (byte) (c0 >> 8 & 255);
			}
		}

		byte[] lightArray = lightArrays[nonEmptyChunkSections];
		System.arraycopy(lightArray, 0, chunkMap.a, j, lightArray.length);

		if (isOverworld) {
			j += lightArray.length;
			System.arraycopy(lightArray, 0, chunkMap.a, j, lightArray.length);
		}

		if (includeBiome) {
			j += lightArray.length;
			System.arraycopy(chunk.getChunk().getBiomeIndex(), 0, chunkMap.a, j, 256);
		}

		return chunkMap;
	}

	private static int calculateNeededBytes(int i, boolean isOverworld, boolean includeBiome) {
		int j = i * 2 * 16 * 16 * 16;
		int k = i * 16 * 16 * 8;
		int l = isOverworld ? i * 16 * 16 * 8 : 0;
		int biome = includeBiome ? 256 : 0;

		return j + k + l + biome;
	}

	static {
		for (int i = 0; i < lightArrays.length; i++) {
			int lightArrayLength = i * 16 * 16 * 8;
			lightArrays[i] = new byte[lightArrayLength];
			Arrays.fill(lightArrays[i], (byte) 255);
		}
	}

}
