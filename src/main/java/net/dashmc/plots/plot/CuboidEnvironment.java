package net.dashmc.plots.plot;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.craftbukkit.v1_8_R3.CraftWorld;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_8_R3.util.CraftMagicNumbers;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import com.google.common.collect.Lists;

import lombok.Getter;
import lombok.Setter;
import net.dashmc.plots.PacketPlots;
import net.dashmc.plots.events.VirtualBlockBreakEvent;
import net.dashmc.plots.events.VirtualBlockCanBuildEvent;
import net.dashmc.plots.events.VirtualBlockDamageEvent;
import net.dashmc.plots.events.VirtualInteractEvent;
import net.dashmc.plots.packets.extensions.VirtualBlockChangePacket;
import net.dashmc.plots.pipeline.RenderPipeline;
import net.dashmc.plots.pipeline.RenderPipelineFactory;
import net.dashmc.plots.player.VirtualPlayerInteractManager;
import net.dashmc.plots.plot.data.BlockBag;
import net.dashmc.plots.plot.data.VirtualBlock;
import net.dashmc.plots.plot.data.VirtualChunk;
import net.dashmc.plots.plot.data.VirtualItem;
import net.dashmc.plots.utils.CuboidRegion;
import net.dashmc.plots.utils.misc.Debug;
import net.dashmc.plots.utils.misc.Utils;
import net.minecraft.server.v1_8_R3.AxisAlignedBB;
import net.minecraft.server.v1_8_R3.Block;
import net.minecraft.server.v1_8_R3.BlockCommand;
import net.minecraft.server.v1_8_R3.BlockDoor;
import net.minecraft.server.v1_8_R3.BlockPosition;
import net.minecraft.server.v1_8_R3.Blocks;
import net.minecraft.server.v1_8_R3.Chunk.EnumTileEntityState;
import net.minecraft.server.v1_8_R3.WorldSettings.EnumGamemode;
import net.minecraft.server.v1_8_R3.ChunkCoordIntPair;
import net.minecraft.server.v1_8_R3.Entity;
import net.minecraft.server.v1_8_R3.EntityHuman;
import net.minecraft.server.v1_8_R3.EntityPlayer;
import net.minecraft.server.v1_8_R3.EnumDirection;
import net.minecraft.server.v1_8_R3.IBlockData;
import net.minecraft.server.v1_8_R3.IInventory;
import net.minecraft.server.v1_8_R3.ITileInventory;
import net.minecraft.server.v1_8_R3.ItemStack;
import net.minecraft.server.v1_8_R3.ItemSword;
import net.minecraft.server.v1_8_R3.Material;
import net.minecraft.server.v1_8_R3.MinecraftServer;
import net.minecraft.server.v1_8_R3.Packet;
import net.minecraft.server.v1_8_R3.PacketPlayOutBlockBreakAnimation;
import net.minecraft.server.v1_8_R3.PacketPlayOutBlockChange;
import net.minecraft.server.v1_8_R3.PacketPlayOutMapChunk;
import net.minecraft.server.v1_8_R3.PacketPlayOutWorldEvent;
import net.minecraft.server.v1_8_R3.TileEntity;

/*
 * Data structure:
 * player uuid (16 bytes - 2x long)
 * world uid (16 bytes - 2x long)
 * 
 * region pos1 (x,y,z 12 bytes - 3x int)
 * region pos2 (x,y,z 12 bytes - 3x int)
 * combined blockdata in sequence z => y => x (so to unpack, loop x => y => z)
 */
public class CuboidEnvironment extends Environment {
	private @Getter final HashSet<VirtualConnection> connections = new HashSet<>();
	private @Getter HashMap<Integer, VirtualChunk> virtualChunks = new HashMap<>();
	private @Getter final List<TileEntity> tileEntities = Lists.newArrayList();
	private @Getter World world;
	private @Getter net.minecraft.server.v1_8_R3.World nmsWorld;
	private @Getter UUID ownerUuid;
	private @Getter InteractManager interactManager = new InteractManager();
	private @Getter CuboidRegion region;
	private @Getter @Setter RenderPipeline renderPipeline = RenderPipelineFactory.createEmptyPipeline();

	public CuboidEnvironment(Player player) throws IOException {
		if (!player.isOnline())
			throw new IOException(
					"Tried initialization of CuboidEnvironment for offline player: " + player.getUniqueId());

		EntityPlayer nmsOwner = ((CraftPlayer) player).getHandle();
		File dataFile = new File(DATA_DIRECTORY, player.getUniqueId() + ".dat");

		if (dataFile.exists()) {
			FileInputStream fileInputStream = new FileInputStream(dataFile);
			DataInputStream dataInputStream = new DataInputStream(fileInputStream);
			readVersionCode(dataInputStream);

			UUID prevUuid = player.getUniqueId();
			deserialize(dataInputStream);

			if (!prevUuid.equals(ownerUuid))
				throw new IOException(
						"Mismatched UUIDs: (" + prevUuid + " => " + ownerUuid + "). File might be corrupt");

			createEnvironment(player);
			startVirtualization(nmsOwner);
			return;
		}

		Debug.log("No save file could be found for " + player.getName());

		createEnvironment(player);

		this.ownerUuid = player.getUniqueId();
		this.world = PacketPlots.getPlotConfig().getWorld();
		this.nmsWorld = ((CraftWorld) world).getHandle();

		this.region = PacketPlots.getPlotConfig().getRegion();

		Debug.log(Arrays.toString(region.getChunks()));

		for (ChunkCoordIntPair coord : region.getChunks()) {
			Debug.log("creating chunk @ " + coord.toString());
			VirtualChunk chunk = new VirtualChunk(this, coord);
			Debug.log("created chunk " + chunk);
			virtualChunks.put(coord.hashCode(), chunk);
		}

		// HashSet<ChunkConfig> chunks = PacketPlots.getPlotConfig().getVirtualChunks();
		// for (ChunkConfig chunk : chunks) {
		// virtualChunks.put(chunk.coords.hashCode(),
		// new VirtualChunk(this, chunk.coords, nmsWorld, chunk.getSectionsAsMask()));
		// }

		save();
		startVirtualization(nmsOwner);
	}

	/**
	 * Not yet implemented - entities arent supported
	 * 
	 * @param bb
	 * @param entity
	 * @return
	 */
	@Override
	public boolean isNoOtherEntitiesInside(AxisAlignedBB bb, Entity entity) {
		return true;
	}

	// https://github.com/Attano/Spigot-1.8/blob/9db48bc15e203179554b8d992ca6b0a528c8d300/net/minecraft/server/v1_8_R3/World.java#L2664
	@Override
	public boolean isBuildable(Block block, BlockPosition pos, boolean ignoreCollision, EnumDirection dir,
			Entity entity,
			ItemStack itemStack) {
		Block curr = getType(pos).getBlock();
		AxisAlignedBB bb = ignoreCollision ? null
				: VirtualBlock.getBoundingBox(block, this, pos, block.getBlockData());

		final boolean defaultReturn;
		if (bb != null && !isNoOtherEntitiesInside(bb, entity)) {
			defaultReturn = false;
		} else if (curr.getMaterial() == Material.ORIENTABLE && block == Blocks.ANVIL) {
			defaultReturn = true;
		} else {
			Debug.log("isReplaceable: " + curr.getMaterial().isReplaceable());
			Debug.log("mayPlace: " + VirtualBlock.mayPlace(block, this, pos, dir, itemStack) + " (pos: "
					+ pos.toString() + ")");

			defaultReturn = curr.getMaterial().isReplaceable()
					&& VirtualBlock.mayPlace(block, this, pos, dir, itemStack);
		}

		@SuppressWarnings("deprecation")
		VirtualBlockCanBuildEvent event = new VirtualBlockCanBuildEvent(defaultReturn,
				Utils.convertPosToLoc(world, pos), org.bukkit.Material.getMaterial(CraftMagicNumbers.getId(curr)),
				this);
		Bukkit.getPluginManager().callEvent(event);

		return event.isBuildable();
	}

	@Override
	public boolean canPlace(BlockPosition pos, EnumDirection dir, ItemStack itemStack, EntityHuman player) {
		if (player.abilities.mayBuild)
			return true;
		if (itemStack == null)
			return false;

		BlockPosition shifted = pos.shift(dir.opposite());
		Block block = getType(shifted).getBlock();
		return itemStack.d(block) || itemStack.x();
	}

	@Override
	public void broadcastPacket(Packet<?> packet) {
		if (packet == null)
			return;

		for (VirtualConnection conn : connections) {
			conn.getPlayer().playerConnection.sendPacket(packet);
		}
	}

	@Override
	public void broadcastTile(TileEntity tile) {
		broadcastPacket(tile.getUpdatePacket());
	}

	@Override
	public void sendTile(TileEntity tile, EntityPlayer to) {
		Packet<?> packet = tile.getUpdatePacket();
		if (packet == null)
			return;

		to.playerConnection.sendPacket(packet);
	}

	@Override
	public void sendTiles(EntityPlayer to) {
		Iterator<TileEntity> tileIterator = tileEntities.iterator();
		while (tileIterator.hasNext()) {
			TileEntity tile = tileIterator.next();
			sendTile(tile, to);
		}
	}

	@Override
	public void sendTiles(Player to) {
		sendTiles(((CraftPlayer) to).getHandle());
	}

	@Override
	public void close() {
		save();

		for (VirtualConnection conn : connections) {
			if (conn.getOriginal() == this)
				continue;

			// we visit locally here to prevent concurrent modification.
			// it doesnt matter anyway, as the connections are cleared after anyway!
			IEnvironment newEnv = conn.visitLocally(conn.getOriginal());
			newEnv.getConnections().add(conn);
		}

		connections.clear();

		removeEnvironment(getOwner());
		virtualChunks.clear();
	}

	@Override
	public void save() {
		try {
			File dataFile = new File(DATA_DIRECTORY, getOwnerUuid() + ".dat");
			FileOutputStream fileOutputStream = new FileOutputStream(dataFile);
			DataOutputStream dataOutputStream = new DataOutputStream(fileOutputStream);
			String version = PacketPlots.getInstance().getDescription().getVersion();

			dataOutputStream.writeInt(version.length());
			for (char versionCodeChar : version.toCharArray())
				dataOutputStream.writeChar(versionCodeChar);

			serialize(dataOutputStream);

			fileOutputStream.close();
			dataOutputStream.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public CuboidEnvironment(DataInputStream stream) throws IOException {
		readVersionCode(stream);
		deserialize(stream);

		Player owner = getOwner();
		if (owner == null || !owner.isOnline())
			throw new IOException("Tried initialization of CuboidEnvironment for offline player: " + ownerUuid);

		EntityPlayer nmsOwner = ((CraftPlayer) owner).getHandle();
		createEnvironment(owner);
		startVirtualization(nmsOwner);
	}

	private String readVersionCode(DataInputStream stream) throws IOException {
		StringBuilder versionCode = new StringBuilder();
		int versionLength = stream.readInt();
		for (int i = 0; i < versionLength; i++)
			versionCode.append(stream.readChar());

		String generatedWith = versionCode.toString();
		String currentVer = PacketPlots.getInstance().getDescription().getVersion();
		if (!generatedWith.equalsIgnoreCase(currentVer)) {
			// TODO: apply some sort of migration or different loading style

			Bukkit.getLogger().warning("[PacketPlots] Save file differs in versions! Trying to load anyway... ("
					+ generatedWith + " -> " + currentVer + ")");
		}

		return generatedWith;
	}

	@Override
	public void stopVirtualization(EntityPlayer player) {
		VirtualConnection conn = VirtualConnection.get(player);
		if (conn == null)
			return;

		conn.close();

		getVirtualChunks().values().forEach((val) -> {
			Packet<?> packet = new PacketPlayOutMapChunk(val.getChunk(), false, 65535);
			player.playerConnection.sendPacket(packet);
		});
	}

	@Override
	public void startVirtualization(Player player) {
		startVirtualization(((CraftPlayer) player).getHandle());
	}

	@Override
	public void stopVirtualization(Player player) {
		stopVirtualization(((CraftPlayer) player).getHandle());
	}

	@Override
	public void startVirtualization(EntityPlayer player) {
		if (VirtualConnection.get(player) != null)
			return;

		VirtualConnection conn = VirtualConnection.establish(player, this);
		connections.add(conn);

		conn.refreshVirtualizedChunks();
	}

	@Override
	public IBlockData getType(BlockPosition pos) {
		if (!isValidLocation(pos))
			return Blocks.AIR.getBlockData();

		VirtualChunk chunk = virtualChunks.get(Utils.getChunkCoordHash(pos));
		return chunk.getBlockData(pos);
	}

	@Override
	public TileEntity getTileEntity(BlockPosition pos) {
		if (!isValidLocation(pos))
			return null;

		TileEntity tileEntity = virtualChunks.get(Utils.getChunkCoordHash(pos)).getTileEntity(pos,
				EnumTileEntityState.IMMEDIATE);
		if (tileEntity == null) {
			for (int i = 0; i < tileEntities.size(); i++) {
				TileEntity tile = tileEntities.get(i);
				if (!tile.x() && tile.getPosition().equals(pos)) {
					tileEntity = tile;
					break;
				}
			}
		}

		return tileEntity;
	}

	@Override
	public boolean setBlock(BlockPosition pos, IBlockData blockData, int i) {
		if (!this.isValidLocation(pos))
			return false;

		VirtualChunk chunk = virtualChunks.get(Utils.getChunkCoordHash(pos));
		boolean succeeded = chunk.setBlock(pos, blockData);
		if (succeeded) {
			// any blocks removed due to physics will become property of environment owner
			notify(pos, BlockBag.getBlockBag(getOwner()));
		}

		return succeeded;
	}

	private void notify(BlockPosition pos, BlockBag bag) {
		notifyBlock(pos.west(), bag);
		notifyBlock(pos.east(), bag);
		notifyBlock(pos.down(), bag);
		notifyBlock(pos.up(), bag);
		notifyBlock(pos.north(), bag);
		notifyBlock(pos.south(), bag);

		broadcastPacket(new VirtualBlockChangePacket(this, pos).getPacket());
	}

	private void notifyBlock(BlockPosition pos, BlockBag bag) {
		IBlockData ibd = getType(pos);
		VirtualBlock.notify(ibd.getBlock(), this, pos, bag, ibd);
	}

	@Override
	public boolean isValidLocation(BlockPosition pos) {
		return isValidLocation(pos.getX(), pos.getY(), pos.getZ());
	}

	@Override
	public boolean isValidLocation(int x, int y, int z) {
		if (!(x >= -30000000 && z >= -30000000 && x < 30000000
				&& z < 30000000 && y >= 0 && y < 256))
			return false;

		return region.includes(x, y, z);
	}

	@Override
	public Player getOwner() {
		return Bukkit.getPlayer(ownerUuid);
	}

	@Override
	public EntityPlayer getNMSOwner() {
		return ((CraftPlayer) getOwner()).getHandle();
	}

	@Override
	public void deserialize(DataInputStream stream) throws IOException {
		this.ownerUuid = new UUID(stream.readLong(), stream.readLong());
		this.world = Bukkit.getWorld(new UUID(stream.readLong(), stream.readLong()));
		this.nmsWorld = ((CraftWorld) world).getHandle();

		BlockPosition pos1 = new BlockPosition(stream.readInt(), stream.readInt(), stream.readInt());
		BlockPosition pos2 = new BlockPosition(stream.readInt(), stream.readInt(), stream.readInt());
		this.region = new CuboidRegion(pos1, pos2);

		int chunks = stream.readInt();
		for (int i = 0; i < chunks; i++) {
			VirtualChunk chunk = new VirtualChunk(this, stream);
			virtualChunks.put(chunk.getCoordPair().hashCode(), chunk);
		}

		Debug.log("Loaded chunks from save file: " + virtualChunks.size());
		// TODO: if the loaded region != the saved one, delete blocks and add to block
		// bag.

		getBlockBag().deserialize(stream);
	}

	@Override
	public void setTileEntity(BlockPosition blockPosition, TileEntity tileEntity) {
		if (tileEntity != null && !tileEntity.x()) {
			tileEntities.add(tileEntity);
			this.getVirtualChunks().get(Utils.getChunkCoordHash(blockPosition)).setTileEntity(blockPosition,
					tileEntity);
		}
	}

	@Override
	public void serialize(DataOutputStream stream) throws IOException {
		stream.writeLong(ownerUuid.getMostSignificantBits());
		stream.writeLong(ownerUuid.getLeastSignificantBits());

		stream.writeLong(world.getUID().getMostSignificantBits());
		stream.writeLong(world.getUID().getLeastSignificantBits());

		BlockPosition pos1 = region.getPos1();
		stream.writeInt(pos1.getX());
		stream.writeInt(pos1.getY());
		stream.writeInt(pos1.getZ());

		BlockPosition pos2 = region.getPos2();
		stream.writeInt(pos2.getX());
		stream.writeInt(pos2.getY());
		stream.writeInt(pos2.getZ());

		stream.writeInt(virtualChunks.size());

		for (VirtualChunk virtualChunk : virtualChunks.values()) {
			virtualChunk.serialize(stream);
		}

		getBlockBag().serialize(stream);
	}

	@Override
	public BlockBag getBlockBag() {
		return BlockBag.getBlockBag(getOwner());
	}

	public class InteractManager {
		public boolean interact(EntityHuman human, IEnvironment env, ItemStack item, BlockPosition pos,
				EnumDirection dir, float cX, float cY, float cZ, boolean isBorderPlace) {
			IBlockData blockData = isBorderPlace ? nmsWorld.getType(pos) : env.getType(pos);

			if (!env.getOwnerUuid().equals(human.getUniqueID())) {
				handleGateMismatch(blockData, human, pos, env);
				return false;
			}

			boolean result = false;

			Debug.log("Interact IBD:" + blockData.getBlock().toString());
			Debug.log("Block is not air?: " + (blockData.getBlock() != Blocks.AIR));

			if (blockData.getBlock() != Blocks.AIR) {
				boolean cancelledBlock = false;
				GameMode gameMode = getOwner().getGameMode();

				if (gameMode == GameMode.SPECTATOR) {
					TileEntity tile = env.getTileEntity(pos);
					cancelledBlock = !(tile instanceof ITileInventory || tile instanceof IInventory);
				}

				if (!human.getBukkitEntity().isOp() && item != null
						&& Block.asBlock(item.getItem()) instanceof BlockCommand) {
					cancelledBlock = true;
				}

				VirtualInteractEvent ev = new VirtualInteractEvent(human, Action.RIGHT_CLICK_BLOCK, pos, dir, item,
						cancelledBlock, env);
				if (cancelledBlock)
					ev.setUseClickedBlock(Event.Result.DENY);
				Bukkit.getPluginManager().callEvent(ev);

				if (ev.getUseClickedBlock() == Event.Result.DENY) {
					handleGateMismatch(blockData, human, pos, env);
					result = ev.getUseItemInHand() != Event.Result.ALLOW;
				} else if (gameMode == GameMode.SPECTATOR) {
					// TODO: implement
				} else if (!human.isSneaking() || item == null) {
					result = VirtualBlock.interact(env, pos, blockData, human, dir, cX, cY, cZ);
				}

				Debug.log("Item is: " + item);
				Debug.log("Result is: " + result);

				if (item != null && !result) {
					int data = item.getData();
					int count = item.count;

					result = VirtualItem.placeItem(item, human, env, pos, dir, cX, cY, cZ, isBorderPlace);

					if (gameMode == GameMode.CREATIVE) {
						item.setData(data);
						item.count = count;
					}
				}
			}

			return result;
		}

		private void handleGateMismatch(IBlockData ibd, EntityHuman entity, BlockPosition pos, IEnvironment env) {
			EntityPlayer player = (EntityPlayer) entity;

			if (ibd.getBlock() instanceof BlockDoor) {
				boolean bottom = ibd.get(BlockDoor.HALF) == BlockDoor.EnumDoorHalf.LOWER;

				player.playerConnection.sendPacket(new VirtualBlockChangePacket(env, pos).getPacket());
				player.playerConnection
						.sendPacket(new VirtualBlockChangePacket(env, bottom ? pos.up() : pos.down()).getPacket());
			} else if (ibd.getBlock() == Blocks.TRAPDOOR) {
				player.playerConnection.sendPacket(new VirtualBlockChangePacket(env, pos).getPacket());
			}
		}

		// https://github.com/Attano/Spigot-1.8/blob/master/net/minecraft/server/v1_8_R3/PlayerInteractManager.java#L105
		public void startDestroy(EntityPlayer player, BlockPosition pos, EnumDirection dir) {
			VirtualInteractEvent event = new VirtualInteractEvent(player, Action.LEFT_CLICK_BLOCK, pos, dir,
					player.inventory.getItemInHand(), false,
					CuboidEnvironment.this);
			Bukkit.getPluginManager().callEvent(event);

			Debug.log("startDestroy called, is interact event cancelled? " + event.isCancelled());

			if (event.isCancelled()) {
				player.playerConnection
						.sendPacket(new VirtualBlockChangePacket(CuboidEnvironment.this, pos).getPacket());
				TileEntity tile = getTileEntity(pos);
				if (tile != null)
					player.playerConnection.sendPacket(tile.getUpdatePacket());
				return;
			}

			Debug.log("Is player creative?" + player.playerInteractManager.isCreative());

			if (player.playerInteractManager.isCreative()) {
				breakBlock(player, pos);
				return;
			}

			Block block = getType(pos).getBlock();

			Debug.log("Is survival or adventure mode?" + player.playerInteractManager.c());

			if (player.playerInteractManager.c()) {
				if (player.playerInteractManager.getGameMode() == EnumGamemode.SPECTATOR)
					return;

				if (!player.cn()) {
					ItemStack item = player.bZ();
					if (item == null)
						return;
					if (!item.c(block))
						return;
				}
			}

			VirtualPlayerInteractManager playerInteractManager = (VirtualPlayerInteractManager) player.playerInteractManager;
			playerInteractManager.setLastDigTick(playerInteractManager.getCurrentTick());

			float f = 1f;

			if (event.getUseClickedBlock() == Event.Result.DENY) {
				handleGateMismatch(getType(pos), player, pos, CuboidEnvironment.this);
			} else if (block.getMaterial() != Material.AIR) {
				// block.attack(nmsWorld, pos, player);
				f = block.getDamage(player, nmsWorld, pos);
			}

			if (event.getUseItemInHand() == Event.Result.DENY) {
				if (f > 1.0f)
					player.playerConnection
							.sendPacket(new VirtualBlockChangePacket(CuboidEnvironment.this, pos).getPacket());

				return;
			}

			VirtualBlockDamageEvent blockEvent = new VirtualBlockDamageEvent(Utils.convertPosToLoc(world, pos),
					player.getBukkitEntity(), player.getBukkitEntity().getItemInHand(), CuboidEnvironment.this);
			blockEvent.setInstaBreak(f >= 1.0f);
			Bukkit.getPluginManager().callEvent(blockEvent);

			if (blockEvent.isCancelled()) {
				player.playerConnection
						.sendPacket(new VirtualBlockChangePacket(CuboidEnvironment.this, pos).getPacket());
				return;
			}

			if (blockEvent.isInstaBreak())
				f = 2.0f;

			if (block.getMaterial() != Material.AIR && f >= 1.0f) {

				breakBlock(player, pos);
			} else { // https://github.com/Attano/Spigot-1.8/blob/master/net/minecraft/server/v1_8_R3/PlayerInteractManager.java#L190
				Debug.log("Setting destroy-fields, pos " + pos);
				VirtualPlayerInteractManager interactManager = (VirtualPlayerInteractManager) player.playerInteractManager;

				interactManager.setIsDestroying(true);
				interactManager.setDestroyPosition(pos);
				interactManager.setForce((int) (f * 10f));

				broadcastPacket(new PacketPlayOutBlockBreakAnimation(player.getId(), pos, (int) (f * 10f)));
			}

			return;
		}

		// https://github.com/Attano/Spigot-1.8/blob/master/net/minecraft/server/v1_8_R3/PlayerInteractManager.java#L203
		public void stopDestroy(EntityPlayer player, BlockPosition pos) {
			VirtualPlayerInteractManager interactManager = (VirtualPlayerInteractManager) player.playerInteractManager;

			Debug.log("stop destroy at pos: " + pos.toString() + ", destroy pos: "
					+ interactManager.getDestroyPosition());
			if (pos.equals(interactManager.getDestroyPosition())) {
				interactManager.setCurrentTick(MinecraftServer.currentTick);
				int diggingFor = interactManager.getCurrentTick() - interactManager.getLastDigTick();

				Block block = getType(pos).getBlock();
				if (block.getMaterial() != Material.AIR) {
					float f = block.getDamage(player, nmsWorld, pos) * (float) (diggingFor + 1);

					if (f >= 0.7F) {
						interactManager.setIsDestroying(false);
						broadcastPacket(new PacketPlayOutBlockBreakAnimation(player.getId(), pos, -1));
						breakBlock(player, pos);
					} else { // TODO:

					}
				}
			} else {
				player.playerConnection
						.sendPacket(new VirtualBlockChangePacket(CuboidEnvironment.this, pos).getPacket());
			}
		}

		// https://github.com/Attano/Spigot-1.8/blob/master/net/minecraft/server/v1_8_R3/PlayerInteractManager.java#L231
		public void abortDestroy(EntityPlayer player) {
			VirtualPlayerInteractManager interactManager = (VirtualPlayerInteractManager) player.playerInteractManager;
			interactManager.setIsDestroying(false);

			BlockPosition pos = interactManager.getDestroyPosition();
			broadcastPacket(new PacketPlayOutBlockBreakAnimation(player.getId(), pos, -1));
			return;
		}

		// https://github.com/Attano/Spigot-1.8/blob/master/net/minecraft/server/v1_8_R3/PlayerInteractManager.java#L249
		public boolean breakBlock(EntityPlayer player, BlockPosition pos) {
			VirtualBlockBreakEvent ev = null;

			boolean isSword = player.playerInteractManager.getGameMode().d() && player.bA() != null
					&& player.bA().getItem() instanceof ItemSword;
			if (getTileEntity(pos) == null && !isSword) {
				PacketPlayOutBlockChange packet = new VirtualBlockChangePacket(CuboidEnvironment.this, pos)
						.getPacket();
				packet.block = Blocks.AIR.getBlockData();
				player.playerConnection.sendPacket(packet);

			}

			ev = new VirtualBlockBreakEvent(Utils.convertPosToLoc(world, pos), CuboidEnvironment.this);
			ev.setCancelled(isSword);

			IBlockData nmsData = getType(pos);
			Block nmsBlock = nmsData.getBlock();

			// if(nmsBlock != null && !ev.isCancelled() &&
			// !player.playerInteractManager.isCreative() && player.b(nmsBlock)) {
			// if(!(nmsBlock.d() && !nmsBlock.isTileEntity() &&
			// EnchantmentManager.hasSilkTouchEnchantment(player))) {

			// }
			// }

			Bukkit.getPluginManager().callEvent(ev);
			TileEntity tile = getTileEntity(pos);

			Debug.log("Is block break event cancelled? " + ev.isCancelled());

			if (ev.isCancelled()) {
				if (isSword)
					return false;

				player.playerConnection
						.sendPacket(new VirtualBlockChangePacket(CuboidEnvironment.this, pos).getPacket());

				if (tile != null)
					player.playerConnection.sendPacket(tile.getUpdatePacket());

				return false;
			}

			if (nmsBlock == Blocks.AIR)
				return false;

			if (nmsBlock == Blocks.SKULL && !player.playerInteractManager.isCreative()) {
				VirtualBlock.harvestBlock(nmsBlock, nmsData, CuboidEnvironment.this, pos,
						BlockBag.getBlockBag(player), tile);

				return setBlock(pos, Blocks.AIR.getBlockData(), 3);
			}

			if (player.playerInteractManager.c()) {
				if (player.playerInteractManager.getGameMode() == EnumGamemode.SPECTATOR)
					return false;
				if (!player.cn()) {
					ItemStack item = player.bZ();
					if (item == null)
						return false;
					if (!item.c(nmsBlock))
						return false;
				}
			}

			broadcastPacket(new PacketPlayOutWorldEvent(2001, pos, Block.getCombinedId(nmsData), false));

			boolean couldSet = setBlock(pos, Blocks.AIR.getBlockData(), 3);

			if (player.playerInteractManager.isCreative())
				player.playerConnection
						.sendPacket(new VirtualBlockChangePacket(CuboidEnvironment.this, pos).getPacket());
			else {
				ItemStack held = player.bZ();
				boolean flag = player.b(nmsBlock);

				if (held != null) {
					held.a(null, nmsBlock, pos, player);
					if (held.count == 0)
						player.ca();
				}

				if (flag && couldSet) {
					// nmsBlock.a(nmsWorld, player, pos, nmsData, tile);
					VirtualBlock.harvestBlock(nmsBlock, nmsData, CuboidEnvironment.this, pos,
							BlockBag.getBlockBag(player), tile);
				}
			}

			return couldSet;
		}
	}

	static {
		DATA_DIRECTORY.mkdirs();
	}

	@Override
	public int getBlockPower(BlockPosition arg0, EnumDirection arg1) {
		throw new UnsupportedOperationException("Unimplemented method 'getBlockPower'");
	}

	@Override
	public boolean isEmpty(BlockPosition arg0) {
		throw new UnsupportedOperationException("Unimplemented method 'isEmpty'");
	}

}
