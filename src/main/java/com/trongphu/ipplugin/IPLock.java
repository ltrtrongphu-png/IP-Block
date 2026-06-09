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
import java.util.ArrayList;
import java.util.List;

public final class IPLock extends JavaPlugin implements Listener {
    private File ipFile;
    private FileConfiguration ipConfig;
    private static final int DEFAULT_IP_LIMIT = 10;

    @Override
    public void onEnable() {
        createIPFile();
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("=====================================");
        getLogger().info("  IP-Lock Plugin - Activated!");
        getLogger().info("  IP Account Limiter: " + DEFAULT_IP_LIMIT + " accounts/IP");
        getLogger().info("=====================================");
    }

    @Override
    public void onDisable() {
        getLogger().info("IP-Lock Plugin disabled!");
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

        // Reload config
        ipConfig = YamlConfiguration.loadConfiguration(ipFile);

        // Nếu player chưa từng vào
        if (!ipConfig.contains("players." + playerName)) {
            // Check số lượng accounts cho IP này
            List<String> accountsOnIP = getAccountsOnIP(playerIP);
            int maxLimit = getIPLimit(playerIP);

            if (accountsOnIP.size() >= maxLimit) {
                // Vượt quá limit → Kick
                String kickMessage = ChatColor.RED + "Bạn đã vượt quá giới hạn accounts trên IP này!\n" +
                        ChatColor.YELLOW + "Giới hạn: " + maxLimit + " accounts/IP";
                event.disallow(PlayerLoginEvent.Result.KICK_OTHER, kickMessage);
                getLogger().warning(playerName + " kicked - IP limit exceeded!");
                return;
            }

            // Nếu chưa vượt, thêm vào danh sách
            addPlayerIP(playerName, playerIP);
        }
    }

    private String getPlayerIP(PlayerLoginEvent event) {
        return event.getAddress().getHostAddress();
    }

    private List<String> getAccountsOnIP(String ip) {
        List<String> accounts = new ArrayList<>();
        if (ipConfig.contains("ips." + ip)) {
            accounts = ipConfig.getStringList("ips." + ip);
        }
        return accounts;
    }

    private int getIPLimit(String ip) {
        if (ipConfig.contains("limits." + ip)) {
            return ipConfig.getInt("limits." + ip);
        }
        return DEFAULT_IP_LIMIT;
    }

    private void addPlayerIP(String playerName, String ip) {
        try {
            // Thêm vào danh sách player
            ipConfig.set("players." + playerName, ip);

            // Thêm vào danh sách IP
            List<String> accounts = getAccountsOnIP(ip);
            if (!accounts.contains(playerName)) {
                accounts.add(playerName);
                ipConfig.set("ips." + ip, accounts);
            }

            ipConfig.save(ipFile);
        } catch (IOException e) {
            getLogger().severe("Cannot save player IP!");
            e.printStackTrace();
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("iplock")) {
            if (args.length == 0) {
                sender.sendMessage(ChatColor.GOLD + "=== IP-Lock Commands ===");
                sender.sendMessage(ChatColor.YELLOW + "/iplock info <player> - Xem info IP của player");
                sender.sendMessage(ChatColor.YELLOW + "/iplock list <ip> - Liệt kê accounts trên IP");
                sender.sendMessage(ChatColor.YELLOW + "/iplock reload - Reload config");
                return true;
            }

            if (args[0].equalsIgnoreCase("info") && args.length > 1) {
                String playerName = args[1];
                if (ipConfig.contains("players." + playerName)) {
                    String ip = ipConfig.getString("players." + playerName);
                    sender.sendMessage(ChatColor.GREEN + "Player: " + playerName);
                    sender.sendMessage(ChatColor.GREEN + "IP: " + ip);
                } else {
                    sender.sendMessage(ChatColor.RED + "Player not found!");
                }
                return true;
            }

            if (args[0].equalsIgnoreCase("list") && args.length > 1) {
                String ip = args[1];
                List<String> accounts = getAccountsOnIP(ip);
                sender.sendMessage(ChatColor.GREEN + "Accounts on IP " + ip + ": " + accounts.size());
                for (String account : accounts) {
                    sender.sendMessage(ChatColor.YELLOW + "  - " + account);
                }
                return true;
            }

            if (args[0].equalsIgnoreCase("reload")) {
                createIPFile();
                sender.sendMessage(ChatColor.GREEN + "Config reloaded!");
                return true;
            }

            return false;
        }
        return false;
    }
}
