//
// Decompiled by Procyon v0.5.36
//

package de.tobynextdoor.worldrebuild;

import org.bukkit.Bukkit;
import org.bukkit.World;

import java.util.List;

public class AutosaveRunnable implements Runnable {
  WorldRebuildCommands cmd;

  public AutosaveRunnable() {
    this.cmd = new WorldRebuildCommands((WorldRebuild) Bukkit.getPluginManager().getPlugin("WorldRebuild"));
  }

  @Override
  public void run() {
    Bukkit.broadcastMessage("§f[§2WorldRebuild§f] §6Starting autosave...");
    final List<World> activeWorlds = Bukkit.getServer().getWorlds();
    for (final World w : activeWorlds) {
      final String[] args = {"save", w.getName(), "autosave"};
      this.cmd.saveRebuild(null, args);
    }
    Bukkit.broadcastMessage("§f[§2WorldRebuild§f] §6Finished autosave.");
  }
}