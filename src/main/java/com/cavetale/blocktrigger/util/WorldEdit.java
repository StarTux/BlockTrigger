package com.cavetale.blocktrigger.util;

import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.bukkit.WorldEditPlugin;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.world.World;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * WorldEdit access. Based on WorldEdit 7.2.0.
 */
public final class WorldEdit {
    private WorldEdit() { }

    public static WorldEditPlugin getWorldEdit() {
        return (WorldEditPlugin) Bukkit.getServer().getPluginManager().getPlugin("WorldEdit");
    }

    public static Cuboid getSelection(Player player) {
        WorldEditPlugin we = getWorldEdit();
        LocalSession session = we.getSession(player);
        World world = session.getSelectionWorld();
        final Region region;
        try {
            region = session.getSelection(world);
        } catch (Exception e) {
            return null;
        }
        if (region == null) return null;
        BlockVector3 min = region.getMinimumPoint();
        BlockVector3 max = region.getMaximumPoint();
        return new Cuboid(min.getBlockX(), min.getBlockY(), min.getBlockZ(),
                          max.getBlockX(), max.getBlockY(), max.getBlockZ());
    }
}
