package com.cavetale.blocktrigger;

import com.cavetale.blocktrigger.util.Cuboid;
import com.cavetale.blocktrigger.util.WorldEdit;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.Value;
import org.bukkit.ChatColor;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.java.JavaPlugin;

public final class BlockTriggerPlugin extends JavaPlugin implements Listener {
    final List<Trigger> triggers = new ArrayList<>();
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
            Player player = (Player) sender;
            Cuboid cuboid = WorldEdit.getSelection(player);
            if (cuboid == null) {
                player.sendMessage(ChatColor.RED + "Make a WorldEdit selection first!");
                return true;
            }
            String name = args[1];
            reloadConfig();
            ConfigurationSection section = getConfig().getConfigurationSection(name);
            if (section == null) section = getConfig().createSection(name);
            section.set("from", cuboid.getMin().toArray());
            section.set("to", cuboid.getMax().toArray());
            section.set("type", "move");
            section.set("world", player.getWorld().getName());
            section.set("commands", new ArrayList<String>());
            section.set("console", new ArrayList<String>());
            section.set("permission", "");
            section.set("cooldown", 0);
            saveConfig();
            player.sendMessage("Trigger created: " + name + ". See config.yml");
            return true;
        }
        if (args.length == 2 && args[0].equals("update")) {
            if (!(sender instanceof Player)) return false;
            Player player = (Player) sender;
            Cuboid cuboid = WorldEdit.getSelection(player);
            if (cuboid == null) {
                player.sendMessage(ChatColor.RED + "Make a WorldEdit selection first!");
                return true;
            }
            String name = args[1];
            reloadConfig();
            ConfigurationSection section = getConfig().getConfigurationSection(name);
            if (section == null) {
                player.sendMessage(ChatColor.RED + "Not found: " + name);
                return true;
            }
            section.set("from", cuboid.getMin().toArray());
            section.set("to", cuboid.getMax().toArray());
            section.set("world", player.getWorld().getName());
            saveConfig();
            importConfig();
            player.sendMessage("Trigger upated: " + name);
            return true;
        }
        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, org.bukkit.command.Command command, String alias, String[] args) {
        if (args.length == 0) return null;
        if (args.length == 1) {
            return Stream.of("reload", "create", "update")
                .filter(it -> it.startsWith(args[0]))
                .collect(Collectors.toList());
        }
        if (args.length == 2) {
            return getConfig().getKeys(false).stream()
                .filter(it -> it.startsWith(args[1]))
                .collect(Collectors.toList());
        }
        return List.of();
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
                                      section.getString("permission", ""),
                                      section.getInt("cooldown", 0),
                                      section);
            } else {
                trigger = new Trigger(section.getString("world"),
                                      at.get(0), at.get(1), at.get(2),
                                      at.get(0), at.get(1), at.get(2),
                                      section.getString("type"),
                                      section.getString("permission", ""),
                                      section.getInt("cooldown", 0),
                                      section);
            }
            triggers.add(trigger);
        }
    }

    // --- Trigger utility

    @Value
    public static final class Trigger {
        private final String world;
        private final int ax;
        private final int ay;
        private final int az;
        private final int bx;
        private final int by;
        private final int bz;
        private final String type;
        private final String permission;
        private final int cooldown;
        private final ConfigurationSection section;
        private final Map<UUID, Long> cooldowns = new HashMap<>();
    }

    Trigger of(Block block) {
        int x = block.getX();
        int y = block.getY();
        int z = block.getZ();
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

    boolean runTrigger(Trigger trigger, Player player) {
        if (!trigger.permission.isEmpty() && !player.hasPermission(trigger.permission)) {
            return false;
        }
        if (trigger.cooldown > 0) {
            long now = System.currentTimeMillis();
            long playerCooldown = trigger.cooldowns.getOrDefault(player.getUniqueId(), 0L);
            if (playerCooldown > now) return false;
            trigger.cooldowns.put(player.getUniqueId(), now + (((long) trigger.cooldown) * 1000L));
        }
        for (String cmd: trigger.section.getStringList("commands")) {
            player.performCommand(cmd);
        }
        for (String cmd: trigger.section.getStringList("console")) {
            cmd = cmd
                .replace("{player}", player.getName())
                .replace("{uuid}", player.getUniqueId().toString());
            getServer().dispatchCommand(getServer().getConsoleSender(), cmd);
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
        return true;
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
        event.setUseInteractedBlock(Event.Result.DENY);
        if (event.getHand() != EquipmentSlot.HAND) return;
        runTrigger(trigger, event.getPlayer());
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

    @EventHandler
    protected void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        Trigger trigger = of(player.getLocation().getBlock());
        if (trigger == null) return;
        if (!trigger.type.equals("move")) return;
        player.teleport(player.getWorld().getSpawnLocation(), TeleportCause.PLUGIN);
    }
}
