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

    @Override
    public void onEnable() {
        createIPFile();
        getServer().getPluginManager().registerEvents(this, this);

        getLogger().info("=====================================");
        getLogger().info("  IP-Lock Plugin - Activated!");
        getLogger().info("  IP Account Limiter: 10 accounts/IP");
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

        // Reload config
        ipConfig = YamlConfiguration.loadConfiguration(ipFile);

        // Nếu player chưa từng vào
        if (!ipConfig.contains("players." + playerName)) {
            // Check số lượng accounts cho IP này
            List<String> accountsOnIP = getAccountsOnIP(playerIP);
            int maxLimit = getIPLimit(playerIP);

            if (accountsOnIP.size() >= maxLimit) {
                // Vượt quá limit → Kick
                event.setLoginResult(PlayerLoginEvent.Result.KICK_OTHER);
                event.setKickMe
