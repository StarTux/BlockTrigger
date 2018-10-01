package com.cavetale.blocktrigger;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.Value;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.java.annotation.command.Command;
import org.bukkit.plugin.java.annotation.command.Commands;
import org.bukkit.plugin.java.annotation.permission.Permission;
import org.bukkit.plugin.java.annotation.permission.Permissions;
import org.bukkit.plugin.java.annotation.plugin.ApiVersion;
import org.bukkit.plugin.java.annotation.plugin.Description;
import org.bukkit.plugin.java.annotation.plugin.Plugin;
import org.bukkit.plugin.java.annotation.plugin.Website;
import org.bukkit.plugin.java.annotation.plugin.author.Author;

@Plugin(name = "BlockTrigger", version = "0.1")
@Description("Trigger actions based on blocks")
@ApiVersion(ApiVersion.Target.v1_13)
@Author("StarTux")
@Website("https://cavetale.com")
@Commands(@Command(name = "blocktrigger",
                   desc = "Admin interface",
                   aliases = {},
                   permission = "blocktrigger.blocktrigger",
                   usage = "/blocktrigger reload"
                   + "\n/blocktrigger create"))
@Permissions(@Permission(name = "blocktrigger.blocktrigger",
                         desc = "Use /blocktrigger",
                         defaultValue = PermissionDefault.OP))
public final class BlockTriggerPlugin extends JavaPlugin implements Listener {
    final List<Trigger> triggers = new ArrayList<>();;
    private static final String META_LAST = "blocktrigger.last";

    // --- Plugin overrides

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
        getServer().getPluginManager().registerEvents(this, this);
        importConfig();
    }

    @Override
    public void onDisable() {
    }

    @Override
    public boolean onCommand(CommandSender sender, org.bukkit.command.Command command, String alias, String[] args) {
        if (args.length == 1 && args[0].equals("reload")) {
            importConfig();
            sender.sendMessage("reloaded");
            return true;
        }
        // /create NAME
        if (args.length == 2 && args[0].equals("create")) {
            if (!(sender instanceof Player)) return false;
            Player player = (Player)sender;
            Integer ax = getMetaInt(player, "SelectionAX");
            Integer ay = getMetaInt(player, "SelectionAY");
            Integer az = getMetaInt(player, "SelectionAZ");
            Integer bx = getMetaInt(player, "SelectionBX");
            Integer by = getMetaInt(player, "SelectionBY");
            Integer bz = getMetaInt(player, "SelectionBZ");
            if (ax == null || ay == null || az == null
                || bx == null || by == null || bz == null) {
                player.sendMessage("No selection");
                return true;
            }
            String name = args[1];
            reloadConfig();
            ConfigurationSection section = getConfig().getConfigurationSection(name);
            if (section == null) section = getConfig().createSection(name);
            section.set("from", Arrays.asList(Math.min(ax, bx), Math.min(ay, by), Math.min(az, bz)));
            section.set("to", Arrays.asList(Math.max(ax, bx), Math.max(ay, by), Math.max(az, bz)));
            section.set("type", "move");
            section.set("world", player.getWorld().getName());
            section.set("commands", new ArrayList<String>());
            section.set("console", new ArrayList<String>());
            saveConfig();
            player.sendMessage("Trigger created: " + name + ". See config.yml");
            return true;
        }
        return false;
    }

    // --- Config

    void importConfig() {
        reloadConfig();
        triggers.clear();
        for (String key: getConfig().getKeys(false)) {
            ConfigurationSection section = getConfig().getConfigurationSection(key);
            List<Integer> at = section.getIntegerList("at");
            List<Integer> a = section.getIntegerList("from");
            List<Integer> b = section.getIntegerList("to");
            Trigger trigger;
            if (at.isEmpty()) {
                trigger = new Trigger(section.getString("world"),
                                      a.get(0), a.get(1), a.get(2),
                                      b.get(0), b.get(1), b.get(2),
                                      section.getString("type"),
                                      section);
            } else {
                trigger = new Trigger(section.getString("world"),
                                      at.get(0), at.get(1), at.get(2),
                                      at.get(0), at.get(1), at.get(2),
                                      section.getString("type"),
                                      section);
            }
            triggers.add(trigger);
        }
    }

    // --- Trigger utility

    @Value
    public static final class Trigger {
        String world;
        int ax, ay, az;
        int bx, by, bz;
        String type;
        ConfigurationSection section;
    }

    Trigger of(Block block) {
        int x = block.getX(), y = block.getY(), z = block.getZ();
        for (Trigger trigger: triggers) {
            if (!trigger.world.equals(block.getWorld().getName())) continue;
            if (x < trigger.ax) continue;
            if (y < trigger.ay) continue;
            if (z < trigger.az) continue;
            if (x > trigger.bx) continue;
            if (y > trigger.by) continue;
            if (z > trigger.bz) continue;
            return trigger;
        }
        return null;
    }

    void runTrigger(Trigger trigger, Player player) {
        for (String cmd: trigger.section.getStringList("commands")) {
            player.performCommand(cmd);
        }
        for (String cmd: trigger.section.getStringList("console")) {
            getServer().dispatchCommand(getServer().getConsoleSender(), cmd.replace("%player%", player.getName()).replace("%uuid%", player.getUniqueId().toString()));
        }
        String srv = trigger.section.getString("server");
        if (srv != null) {
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            DataOutputStream dataOutputStream = new DataOutputStream(byteArrayOutputStream);
            try {
                dataOutputStream.writeUTF("Connect");
                dataOutputStream.writeUTF(srv);
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }
            player.sendPluginMessage(this, "BungeeCord", byteArrayOutputStream.toByteArray());
        }
    }

    // --- Metadata utility

    private Object getMeta(Player player, String key) {
        for (MetadataValue val: player.getMetadata(key)) {
            if (val.getOwningPlugin() == this) return val.value();
        }
        return null;
    }

    private Integer getMetaInt(Player player, String key) {
        for (MetadataValue val: player.getMetadata(key)) {
            return val.asInt();
        }
        return null;
    }

    // --- Event Handlers

    @EventHandler(ignoreCancelled = false)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.LEFT_CLICK_BLOCK
            && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Trigger trigger = of(event.getClickedBlock());
        if (trigger == null) return;
        if (!trigger.type.equals("interact")) return;
        runTrigger(trigger, event.getPlayer());
        event.setCancelled(true);
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Trigger trigger = of(player.getLocation().getBlock());
        if (trigger == null) {
            player.removeMetadata(META_LAST, this);
            return;
        }
        if (!trigger.type.equals("move")) return;
        if (getMeta(player, META_LAST) == trigger) return;
        player.setMetadata(META_LAST, new FixedMetadataValue(this, trigger));
        runTrigger(trigger, player);
    }
}
