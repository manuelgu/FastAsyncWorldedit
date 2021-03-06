package com.boydti.fawe.forge;


import com.boydti.fawe.Fawe;
import com.boydti.fawe.IFawe;
import com.boydti.fawe.forge.v0.ForgeQueue_All;
import com.boydti.fawe.object.FaweCommand;
import com.boydti.fawe.object.FawePlayer;
import com.boydti.fawe.object.FaweQueue;
import com.boydti.fawe.regions.FaweMaskManager;
import com.boydti.fawe.util.MainUtil;
import com.boydti.fawe.util.TaskManager;
import com.boydti.fawe.wrappers.WorldWrapper;
import com.sk89q.worldedit.forge.ForgeWorld;
import com.sk89q.worldedit.world.World;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.ModMetadata;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.management.InstanceAlreadyExistsException;
import net.minecraft.command.ServerCommandManager;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import org.apache.logging.log4j.Logger;

public class FaweForge implements IFawe {

    private final ForgeMain parent;
    private final File directory;
    private final Logger logger;
    private final ModMetadata mod;

    public FaweForge(ForgeMain plugin, Logger logger, ModMetadata mod, File directory) {
        this.parent = plugin;
        this.logger = logger;
        this.directory = directory;
        this.mod = mod;
        try {
            Fawe.set(this);
        } catch (InstanceAlreadyExistsException e) {
            MainUtil.handleError(e);
        }
    }

    @Override
    public void debug(String s) {
        logger.error(s);
    }

    @Override
    public File getDirectory() {
        return directory;
    }

    private HashMap<String, FaweCommand> commands = new HashMap<>();

    @Override
    public void setupCommand(String label, FaweCommand cmd) {
        this.commands.put(label, cmd);
    }

    public void insertCommands() {
        for (Map.Entry<String, FaweCommand> entry : commands.entrySet()) {
            ServerCommandManager scm = (ServerCommandManager) FMLCommonHandler.instance().getMinecraftServerInstance().getCommandManager();
            scm.registerCommand(new ForgeCommand(entry.getKey(), entry.getValue()));
        }
    }

    @Override
    public FawePlayer wrap(Object obj) {
        EntityPlayerMP player = null;
        if (obj instanceof String) {
            MinecraftServer server = MinecraftServer.getServer();
            player = server.getConfigurationManager().func_152612_a((String) obj);
        } else if (obj instanceof EntityPlayerMP) {
            player = (EntityPlayerMP) obj;
        }
        if (player == null) {
            return null;
        }
        FawePlayer existing = Fawe.get().getCachedPlayer(player.getCommandSenderName());
        return existing != null ? existing : new ForgePlayer(player);
    }

    @Override
    public void setupVault() {
        // Do nothing
    }

    @Override
    public TaskManager getTaskManager() {
        return new ForgeTaskMan(512);
    }

    @Override
    public String getWorldName(World world) {
        if (world instanceof WorldWrapper) {
            world = ((WorldWrapper) world).getParent();
        }
        return getWorldName(((ForgeWorld) world).getWorld());

    }

    public String getWorldName(net.minecraft.world.World w) {
        return w.getWorldInfo().getWorldName() + ";" + w.provider.dimensionId;
    }

    @Override
    public FaweQueue getNewQueue(String world, boolean dontCareIfFast) {
        return new ForgeQueue_All(world);
    }

    @Override
    public Collection<FaweMaskManager> getMaskManagers() {
        return new ArrayList<>();
    }

    @Override
    public void startMetrics() {
        try {
            ForgeMetrics metrics = new ForgeMetrics("FastAsyncWorldEdit", "3.5.1");
            metrics.start();
        } catch (Throwable e) {
            debug("[FAWE] &cFailed to load up metrics.");
        }
    }

    @Override
    public String getPlatform() {
        return "forge";
    }

    @Override
    public UUID getUUID(String name) {
        try {
            return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
        } catch (Throwable e) {
            return null;
        }
    }

    @Override
    public String getName(UUID uuid) {
        return uuid.toString();
    }

    @Override
    public Object getBlocksHubApi() {
        return null;
    }
}
