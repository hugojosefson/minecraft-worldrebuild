package com.hugojosefson.mc.worldrescue.commands;

import com.hugojosefson.mc.worldrescue.WorldRescue;
import com.hugojosefson.mc.worldrescue.io.Io;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import static java.util.Arrays.copyOfRange;
import static java.util.Arrays.stream;
import static org.apache.commons.lang.StringUtils.defaultString;

public class Commands implements CommandExecutor {
  public final WorldRescue plugin;

  private final SubCommandHandler[] subCommandHandlers = new SubCommandHandler[]{
    new SubCommandHandler("free", Commands::free),
    new SubCommandHandler("save", this::save),
    new SubCommandHandler("rebuild", this::rebuild),
    new SubCommandHandler("delete", Commands::delete),
    new SubCommandHandler("list", Commands::listBackups),
    new SubCommandHandler("duplicate", this::duplicate),
    new SubCommandHandler("tp", Commands::tp)
  };

  public Commands(final WorldRescue plugin) {
    this.plugin = plugin;
  }

  @Override
  public boolean onCommand(final @NotNull CommandSender sender, final @NotNull Command command, final @NotNull String commandLabel, String[] args) {
    final Player player = sender instanceof Player
      ? (Player) sender
      : null;

    if (args.length == 0) {
      return help(player);
    }

    final String subCommand = args[0];
    final String[] actualArgs = copyOfRange(args, 1, args.length);

    return stream(subCommandHandlers)
      .filter(handler -> handler.handles(subCommand))
      .findFirst()
      .map(handler -> handler.handle(player, subCommand, actualArgs))
      .orElseGet(() -> help(player));
  }

  public static boolean free(final Player player, final String[] args) {
    sendMessage(player, "There is " + Io.getFreeSpace() / 1024 / 1024 + " MB of free space left.");
    return true;
  }

  public static boolean delete(final Player player, final String[] args) {
    if (args.length == 3) {
      final String world = getWorldWithAnyMe(player, args[1]);
      final String backupIndex = args[2];

      if (Io.delete(Paths.get(world + "_" + backupIndex + "#backup"))) {
        Bukkit.getServer().broadcastMessage(displayName(player) + ChatColor.GREEN + " deleted the backup '" + world + "' (" + backupIndex + ").");
      } else {
        sendMessage(player, ChatColor.DARK_RED + "The backup '" + world + "' (" + backupIndex + ") does not exist.");
      }
    } else {
      help(player);
    }
    return true;
  }

  private static String displayName(final Player player) {
    return player == null ? "Admin on the server console" : player.getDisplayName();
  }

  public boolean duplicate(final Player player, final String[] args) {
    if (args.length <= 1) {
      help(player);
      return true;
    }

    final String world = getWorldWithAnyMe(player, args[1]);
    final String newWorld = world + "-new"; // TODO: Make sure it doesn't already exist. If so, append a number. Keep looking until an available name+number is found.

    Bukkit.getScheduler().runTask(plugin, () -> {
      sendMessage(player, ChatColor.GOLD + "Creating a copy of the world '" + world + "' with the name '" + newWorld + "'. This may take a while.");
      create(newWorld);
      unload(newWorld);
      if (!Io.copy(Paths.get(world), Paths.get(newWorld))) {
        sendMessage(player, ChatColor.DARK_RED + "Copying world '" + world + " failed. Does it even exist?");
        delete(newWorld);
        return;
      }

      Io.delete(Paths.get(newWorld, "uid.dat"));
      load(newWorld);
      final String tpCommand = hasMultiverse() ? "/mvtp" : "/wr tp";
      sendMessage(player, ChatColor.GOLD + "Done. Now just type '" + ChatColor.GREEN + tpCommand + " " + newWorld + ChatColor.GOLD + "' and use this world to continue building.");
    });
    return true;
  }

  private static String getWorldWithAnyMe(final Player player, final String world) {
    if ("me".equals(world) && player != null) {
      return player.getWorld().getName();
    }
    return world;
  }

  private static String getWorldFromListBackupsArgs(final Player player, final String[] args) {
    if (args.length == 1) {
      return player.getWorld().getName();
    }
    return getWorldWithAnyMe(player, args[1]);
  }

  public static boolean listBackups(final Player player, final String[] args) {
    if ((args.length <= 0 || player == null) && (player != null || args.length <= 1)) {
      help(player);
      return true;
    }

    final String world = getWorldFromListBackupsArgs(player, args);
    final Optional<String[]> maybeBackupIndices = Io.listBackups(Paths.get(world));

    if (!maybeBackupIndices.isPresent()) {
      sendMessage(player, ChatColor.DARK_RED + "The world '" + world + "' does not exist.");
      return true;
    }
    final String[] backupIndices = maybeBackupIndices.get();


    if (backupIndices.length == 0) {
      sendMessage(player, "There are no backups of the world '" + world + "'.");
      return true;
    }

    sendMessage(player, ChatColor.GOLD + "There are " + backupIndices.length + " backups:");
    for (String backupIndex : backupIndices) {
      sendMessage(player, ChatColor.GREEN + "   '" + world + "' (" + backupIndex + ")");
    }
    return true;
  }

  private static boolean help(final Player player) {
    sendMessage(player, " ");
    sendMessage(player, ChatColor.GOLD + "<needed> [optional]");
    sendMessage(player, ChatColor.GOLD + "'" + ChatColor.GREEN + "/wr help" + ChatColor.GOLD + "' --> Lists all commands of WorldRescue.");
    sendMessage(player, ChatColor.GOLD + "'" + ChatColor.GREEN + "/wr list [world]" + ChatColor.GOLD + "' --> Lists all backups of a world.");
    sendMessage(player, ChatColor.GOLD + "'" + ChatColor.GREEN + "/wr save [world] [index]" + ChatColor.GOLD + "' --> Saves your chosen world.");
    sendMessage(player, ChatColor.GOLD + "'" + ChatColor.GREEN + "/wr rebuild [world] [index]" + ChatColor.GOLD + "' --> Rebuilds your chosen world.");
    sendMessage(player, ChatColor.GOLD + "'" + ChatColor.GREEN + "/wr delete <world> <index>" + ChatColor.GOLD + "' --> Deletes the chosen backup.");
    sendMessage(player, ChatColor.GOLD + "'" + ChatColor.GREEN + "/wr duplicate <world>" + ChatColor.GOLD + "' --> Duplicate the chosen world.");
    sendMessage(player, ChatColor.GOLD + "'" + ChatColor.GREEN + "/wr tp <world>" + ChatColor.GOLD + "' --> Teleport to a world.");
    sendMessage(player, ChatColor.GOLD + "'" + ChatColor.GREEN + "/wr free" + ChatColor.GOLD + "' --> Get free disk space.");
    sendMessage(player, ChatColor.GOLD + "If you don't know the name of a world, type 'me' instead.");
    return true;
  }

  public static boolean isDefaultWorld(final World world) {
    final World defaultWorld = getDefaultWorld();
    return world == defaultWorld;
  }

  public static class TeleportablePlayer {

    private final Player player;
    public final Location location;
    public final GameMode gameMode;
    public TeleportablePlayer(Player player, Location location, GameMode gameMode) {
      this.player = player;
      this.location = location;
      this.gameMode = gameMode;
    }

    public Player getPlayer() {
      return player;
    }

  }
  public static TeleportablePlayer teleportablePlayer(final Player player) {
    return new TeleportablePlayer(player, player.getLocation(), player.getGameMode());
  }

  public boolean save(Player player, String[] args) {
    return save(player, args[0], args[1]);
  }

  public boolean save(final Player player, final String worldToRebuild, final String index) {
    if (player == null && worldToRebuild == null) {
      help(null);
      return true;
    }

    Bukkit.getScheduler().runTask(plugin, () -> {
      final World world = resolveWorld(player, worldToRebuild);
      final String worldName = world.getName();
      final String backupIndex = defaultString(index, "default");

      Commands.sendMessage(player, ChatColor.GOLD + "Saving the world '" + worldName + "' (" + backupIndex + ")...");

      if (isDefaultWorld(world)) {
        Commands.sendMessage(player, ChatColor.RED + "The world '" + worldName + "' is your default world. Due to a restriction with Bukkit, WorldRescue can not create/restore a backup.");
        Commands.sendMessage(player, ChatColor.RED + "To solve this problem, type '" + ChatColor.GREEN + "/wr duplicate " + worldName + ChatColor.RED + "'.");
        Commands.sendMessage(player, ChatColor.RED + "This will create the new world '" + worldName + "-new' which will be the same as the world '" + worldName + "' and WorldRescue will be able to create/restore backups from '" + worldName + "-new'.");
        Commands.sendMessage(player, ChatColor.RED + "You can also open your server.config and change 'level-name' to point to another world.");
        return;
      }

      world.setAutoSave(false);
      world.save();

      final String backupName = worldName + "_" + backupIndex + "#backup";
      final boolean isSuccess = Io.copy(Paths.get(worldName), Paths.get(backupName));
      if (isSuccess) {
        Bukkit.getServer().broadcastMessage(displayName(player) + ChatColor.GREEN + " saved the world '" + worldName + "' (" + backupIndex + ").");
      } else {
        sendMessage(player, ChatColor.DARK_RED + "The world '" + worldName + "' with index '" + backupIndex + "' does not exist.");
      }
      world.setAutoSave(true);

    });
    return true;
  }

  public boolean rebuild(Player player, String[] args) {
    return rebuild(player, args[0], args[1]);
  }

  public boolean rebuild(final Player player, final String worldToRebuild, final String index) {
    if (player == null && worldToRebuild == null) {
      help(null);
      return true;
    }

    Bukkit.getScheduler().runTask(plugin, () -> {
      final World world = resolveWorld(player, worldToRebuild);
      final String worldName = world.getName();
      final String backupIndex = defaultString(index, "default");

      Commands.sendMessage(player, ChatColor.GOLD + "Rebuilding the world '" + worldName + "' (" + backupIndex + ")...");

      if (isDefaultWorld(world)) {
        Commands.sendMessage(player, ChatColor.RED + "The world '" + worldName + "' is your default world. Due to a restriction with Bukkit, WorldRescue can not create/restore a backup.");
        Commands.sendMessage(player, ChatColor.RED + "To solve this problem, type '" + ChatColor.GREEN + "/wr duplicate " + worldName + ChatColor.RED + "'.");
        Commands.sendMessage(player, ChatColor.RED + "This will create the new world '" + worldName + "-new' which will be the same as the world '" + worldName + "' and WorldRescue will be able to create/restore backups from '" + worldName + "-new'.");
        Commands.sendMessage(player, ChatColor.RED + "You can also open your server.config and change 'level-name' to point to another world.");
        return;
      }

      load(getDefaultWorld().getName());
      final List<TeleportablePlayer> playersInWorld = Bukkit.getServer().getOnlinePlayers().stream()
        .filter(onlinePlayer -> worldName.equalsIgnoreCase(onlinePlayer.getWorld().getName()))
        .map(Commands::teleportablePlayer)
        .collect(Collectors.toCollection(Arrays::asList));

      if (!hasMultiverse()) {
        final Location spawnLocation = getDefaultWorld().getSpawnLocation();
        playersInWorld.stream()
          .map(TeleportablePlayer::getPlayer)
          .forEach(playerInWorld -> teleport(playerInWorld, spawnLocation));
      }

      unload(worldName);
      final String backupName = worldName + "_" + backupIndex + "#backup";
      final boolean isSuccess = Io.copy(Paths.get(backupName), Paths.get(worldName));
      if (isSuccess) {
        Bukkit.getServer().broadcastMessage(displayName(player) + ChatColor.GREEN + " rebuilt the world '" + worldName + "' (" + backupIndex + ").");
      } else {
        sendMessage(player, ChatColor.DARK_RED + "A backup of the world '" + worldName + "' with index '" + backupIndex + "' does not exist.");
      }

      load(worldName);
      while (!Bukkit.getServer().getWorlds().contains(Bukkit.getWorld(worldName))) {
        try {
          Thread.sleep(50);
        } catch (InterruptedException ignored) {
        }
      }

      Bukkit.getScheduler().runTaskLater(
        plugin,
        () -> playersInWorld.forEach(p -> teleport(p.player, p.location)),
        10L
      );
      Bukkit.getScheduler().runTaskLater(
        plugin,
        () -> playersInWorld.forEach(p -> p.player.setGameMode(p.gameMode)),
        20L
      );


    });
    return true;
  }

  private static World getDefaultWorld() {
    return Bukkit.getServer().getWorlds().get(0);
  }

  @NotNull
  private static World resolveWorld(final Player player, final String worldName) {
    if (worldName != null && !"me".equals(worldName)) {
      return Objects.requireNonNull(Bukkit.getServer().getWorld(worldName));
    }

    if (player == null) {
      throw new IllegalArgumentException("Either player or worldName must be non-null to resolve a valid worldName.");
    }

    return player.getWorld();
  }

  public static boolean tp(final Player player, final String[] args) {
    final String worldName = args[1];
    final Server server = Bukkit.getServer();
    final World world = server.createWorld(new WorldCreator(worldName));
    assert world != null;
    teleport(player, world.getSpawnLocation());
    return true;
  }

  static void create(final String world) {
    if (hasMultiverse()) {
      Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), "mv create " + world + " normal");
    } else {
      Bukkit.getServer().createWorld(new WorldCreator(world));
    }
  }

  static void load(final String worldName) {
    if (hasMultiverse()) {
      Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), "mv load " + worldName);
    } else {
      Bukkit.getServer().createWorld(new WorldCreator(worldName));
    }
  }

  static void unload(final String world) {
    if (hasMultiverse()) {
      Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), "mv unload " + world);
    } else {
      Bukkit.getServer().unloadWorld(world, true);
    }
  }

  static void teleport(final Player player, final Location location) {
    if (hasMultiverse() && location.getWorld() != null) {
      Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), "mvtp " + player.getName() + " e:" + location.getWorld().getName() + ":" + location.getX() + "," + location.getY() + "," + location.getZ());
    } else {
      player.teleport(location);
    }
  }

  static void delete(final String world) {
    if (hasMultiverse()) {
      Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), "mv load " + world);
      Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), "mv delete " + world);
      Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), "mv confirm");
    } else {
      Io.delete(Paths.get(world));
    }
  }

  private static boolean hasMultiverse() {
    return Bukkit.getServer().getPluginManager().isPluginEnabled("Multiverse-Core");
  }

  public static void sendMessage(final Player receiver, final String message) {
    if (receiver == null) {
      System.out.println("[WorldRescue] " + message);
      return;
    }
    receiver.sendMessage(message);
  }

}
