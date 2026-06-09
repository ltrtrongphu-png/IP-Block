package com.trongphu.iplock;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;

public final class IPLock extends JavaPlugin implements Listener {

    private File ipFile;
    private FileConfiguration ipConfig;

    @Override
    public void onEnable() {
        createIPFile();
        getServer().getPluginManager().registerEvents(this, this);

        getLogger().info("=====================================");
        getLogger().info("  IP-Lock Plugin - Activated!");
        getLogger().info("  Players can only join from 1 IP");
        getLogger().info("=====================================");
    }

    private void createIPFile() {
        ipFile = new File(getDataFolder(), "player-ips.yml");

        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }

        if (!ipFile.exists()) {
            try {
                ipFile.createNewFile();
                ipConfig = YamlConfiguration.loadConfiguration(ipFile);
                ipConfig.save(ipFile);
            } catch (IOException e) {
                getLogger().severe("Cannot create player-ips.yml!");
                e.printStackTrace();
            }
        } else {
            ipConfig = YamlConfiguration.loadConfiguration(ipFile);
        }
    }

    @EventHandler
    public void onPlayerLogin(PlayerLoginEvent event) {
        Player player = event.getPlayer();
        String playerName = player.getName();
        String playerIP = getPlayerIP(event);

        // Reload config mỗi lần join để check
        ipConfig = YamlConfiguration.loadConfiguration(ipFile);

        // Nếu player chưa từng vào
        if (!ipConfig.contains("players." + playerName)) {
            // Lưu IP lần đầu
            ipConfig.set("players." + playerName, playerIP);
            saveIPConfig();
            player.sendMessage(ChatColor.GREEN + "✓ IP của bạn đã được lưu! Chỉ vào server từ IP này.");
            return;
        }

        // Check IP
        String savedIP = ipConfig.getString("players." + playerName);

        if (!playerIP.equals(savedIP)) {
            // IP khác → Kick ngay
            event.setLoginResult(PlayerLoginEvent.Result.KICK_OTHER);
            event.setKickMessage(ChatColor.RED + "✗ Bạn không được phép vào từ IP khác!\n" +
                    ChatColor.GRAY + "IP được phép: " + savedIP + "\n" +
                    ChatColor.GRAY + "IP hiện tại: " + playerIP);
            getLogger().warning("[IP-Lock] " + playerName + " tried to join from different IP! " +
                    "Saved: " + savedIP + " | Actual: " + playerIP);
        }
    }

    private String getPlayerIP(PlayerLoginEvent event) {
        return event.getRealAddress().getHostAddress();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("iplock")) {
            return false;
        }

        if (!sender.hasPermission("iplock.admin")) {
            sender.sendMessage(ChatColor.RED + "Bạn không có quyền!");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(ChatColor.YELLOW + "========== IP-Lock Commands ==========");
            sender.sendMessage(ChatColor.GREEN + "/iplock check <player> - Kiểm tra IP đã lưu");
            sender.sendMessage(ChatColor.GREEN + "/iplock reset <player> - Reset IP của player");
            sender.sendMessage(ChatColor.GREEN + "/iplock list - Liệt kê tất cả player");
            sender.sendMessage(ChatColor.GREEN + "/iplock reload - Reload config");
            sender.sendMessage(ChatColor.YELLOW + "=====================================");
            return true;
        }

        if (args[0].equalsIgnoreCase("check")) {
            if (args.length < 2) {
                sender.sendMessage(ChatColor.RED + "Cú pháp: /iplock check <player>");
                return true;
            }

            String playerName = args[1];
            if (ipConfig.contains("players." + playerName)) {
                String ip = ipConfig.getString("players." + playerName);
                sender.sendMessage(ChatColor.GREEN + playerName + " → IP: " + ChatColor.YELLOW + ip);
            } else {
                sender.sendMessage(ChatColor.RED + "Player " + playerName + " chưa từng vào server!");
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("reset")) {
            if (args.length < 2) {
                sender.sendMessage(ChatColor.RED + "Cú pháp: /iplock reset <player>");
                return true;
            }

            String playerName = args[1];
            ipConfig.set("players." + playerName, null);
            saveIPConfig();
            sender.sendMessage(ChatColor.GREEN + "✓ Đã reset IP của " + playerName);
            return true;
        }

        if (args[0].equalsIgnoreCase("list")) {
            sender.sendMessage(ChatColor.YELLOW + "========== Danh sách Players ==========");
            if (ipConfig.contains("players")) {
                for (String playerName : ipConfig.getConfigurationSection("players").getKeys(false)) {
                    String ip = ipConfig.getString("players." + playerName);
                    sender.sendMessage(ChatColor.GREEN + playerName + ChatColor.GRAY + " → " + ip);
                }
            } else {
                sender.sendMessage(ChatColor.RED + "Chưa có player nào!");
            }
            sender.sendMessage(ChatColor.YELLOW + "=====================================");
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            ipConfig = YamlConfiguration.loadConfiguration(ipFile);
            sender.sendMessage(ChatColor.GREEN + "✓ Config đã reload!");
            return true;
        }

        return false;
    }

    private void saveIPConfig() {
        try {
            ipConfig.save(ipFile);
        } catch (IOException e) {
            getLogger().severe("Cannot save player-ips.yml!");
            e.printStackTrace();
        }
    }
}
