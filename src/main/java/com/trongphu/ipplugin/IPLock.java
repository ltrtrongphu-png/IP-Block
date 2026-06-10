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
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public final class IPLock extends JavaPlugin implements Listener {
    private File ipFile;
    private File configFile;
    private FileConfiguration ipConfig;
    private FileConfiguration config;
    private Map<String, Long> joinTimestamps = new HashMap<>();

    private static final int DEFAULT_IP_LIMIT = 10;
    private static final String PREFIX = ChatColor.GOLD + "[IPLock] " + ChatColor.RESET;

    @Override
    public void onEnable() {
        createConfigFiles();
        getServer().getPluginManager().registerEvents(this, this);

        logInfo("=====================================");
        logInfo("  🔒 IPLock Plugin - v2.1 - Activated!");
        logInfo("  👥 IP Account Limiter: " + getIPLimit(null) + " accounts/IP");
        logInfo("  📊 Config loaded successfully");
        logInfo("=====================================");
    }

    @Override
    public void onDisable() {
        logInfo("IPLock Plugin disabled!");
    }

    private void createConfigFiles() {
        configFile = new File(getDataFolder(), "config.yml");
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }

        if (!configFile.exists()) {
            createDefaultConfig();
        }
        config = YamlConfiguration.loadConfiguration(configFile);

        ipFile = new File(getDataFolder(), "player-ips.yml");
        if (!ipFile.exists()) {
            try {
                ipFile.createNewFile();
                ipConfig = YamlConfiguration.loadConfiguration(ipFile);
                ipConfig.save(ipFile);
            } catch (IOException e) {
                logError("Cannot create player-ips.yml!");
                e.printStackTrace();
            }
        } else {
            ipConfig = YamlConfiguration.loadConfiguration(ipFile);
        }
    }

    private void createDefaultConfig() {
        try {
            FileConfiguration newConfig = new YamlConfiguration();
            newConfig.set("settings.max-accounts-per-ip", 10);
            newConfig.set("settings.enable-notifications", true);
            newConfig.set("settings.enable-logging", true);
            newConfig.set("settings.whitelist-enabled", false);
            newConfig.set("messages.kicked", "&cQuá số lượng accounts trên IP này!");
            newConfig.set("messages.joined", "&a%player% vào từ IP: %ip%");
            newConfig.set("messages.admin-alert", "&c⚠️ %player% từ IP &6%ip% &c(%count%/%max% accounts)");
            newConfig.save(configFile);
            logInfo("Default config created!");
        } catch (IOException e) {
            logError("Cannot create default config!");
        }
    }

    // ══════════════════════════════════════════════════
    //  LOGIN — kiểm tra giới hạn IP (player mới)
    // ══════════════════════════════════════════════════
    @EventHandler
    public void onPlayerLogin(PlayerLoginEvent event) {
        Player player = event.getPlayer();
        String playerName = player.getName();
        String playerIP = event.getAddress().getHostAddress();
        UUID uuid = player.getUniqueId();

        ipConfig = YamlConfiguration.loadConfiguration(ipFile);

        if (isWhitelisted(playerIP)) return;

        if (!ipConfig.contains("players." + playerName)) {
            List<String> accountsOnIP = getAccountsOnIP(playerIP);
            int maxLimit = getIPLimit(null);

            if (accountsOnIP.size() >= maxLimit) {
                String kickMessage = ChatColor.RED + "❌ Quá giới hạn accounts!\n" +
                        ChatColor.YELLOW + "Giới hạn: " + maxLimit + " accounts/IP\n" +
                        ChatColor.GRAY + "Liên hệ admin nếu cần hỗ trợ";
                event.disallow(PlayerLoginEvent.Result.KICK_OTHER, kickMessage);
                logWarning(playerName + " bị kick - Vượt quá IP limit! (IP: " + playerIP + ")");
                notifyAdmins(playerName, playerIP, accountsOnIP.size(), maxLimit);
                return;
            }

            addPlayerIP(playerName, uuid, playerIP);
            logInfo(playerName + " login từ IP: " + playerIP);
        }
    }

    // ══════════════════════════════════════════════════
    //  JOIN — phát hiện đổi IP, kick sau ~0.01s
    // ══════════════════════════════════════════════════
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String playerName = player.getName();
        String currentIP = player.getAddress().getAddress().getHostAddress();

        ipConfig = YamlConfiguration.loadConfiguration(ipFile);

        // Kiểm tra xem IP hiện tại có khác IP đã lưu không
        if (ipConfig.contains("players." + playerName)) {
            String savedIP = ipConfig.getString("players." + playerName + ".ip");

            if (savedIP != null && !savedIP.equals(currentIP) && !isWhitelisted(currentIP)) {
                // Kick sau 0 tick (thực thi ngay tick tiếp theo ≈ 0.05s, gần nhất có thể)
                Bukkit.getScheduler().scheduleSyncDelayedTask(this, () -> {
                    if (player.isOnline()) {
                        player.kickPlayer(
                                ChatColor.RED + "❌ IP của bạn đã thay đổi!\n" +
                                ChatColor.YELLOW + "IP cũ: " + savedIP + "\n" +
                                ChatColor.YELLOW + "IP mới: " + currentIP + "\n" +
                                ChatColor.GRAY + "Liên hệ admin nếu đây là nhầm lẫn."
                        );
                        logWarning(playerName + " bị kick vì đổi IP! Cũ: " + savedIP + " → Mới: " + currentIP);
                        notifyAdminsIPChange(playerName, savedIP, currentIP);
                    }
                }, 0L); // 0L = tick tiếp theo (~0.05s), sớm nhất Bukkit scheduler cho phép
                return;
            }
        }

        // Thông báo cho admin nếu có nhiều account cùng IP
        List<String> accounts = getAccountsOnIP(currentIP);
        if (isNotificationEnabled() && accounts.size() > 1) {
            String message = PREFIX + ChatColor.YELLOW + playerName + ChatColor.RESET +
                    " (" + accounts.size() + "/" + getIPLimit(null) + " accounts)";
            for (Player admin : Bukkit.getOnlinePlayers()) {
                if (admin.hasPermission("iplock.admin")) {
                    admin.sendMessage(message);
                }
            }
        }

        joinTimestamps.put(playerName, System.currentTimeMillis());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        joinTimestamps.remove(event.getPlayer().getName());
    }

    // ══════════════════════════════════════════════════
    //  COMMANDS
    // ══════════════════════════════════════════════════
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("iplock")) return false;

        if (args.length == 0) {
            showHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "info":
                if (args.length < 2) { sender.sendMessage(PREFIX + ChatColor.RED + "Cú pháp: /iplock info <player>"); return true; }
                showPlayerInfo(sender, args[1]);
                break;

            case "list":
                if (args.length < 2) { sender.sendMessage(PREFIX + ChatColor.RED + "Cú pháp: /iplock list <ip>"); return true; }
                listAccountsOnIP(sender, args[1]);
                break;

            case "kick":
                if (args.length < 2) { sender.sendMessage(PREFIX + ChatColor.RED + "Cú pháp: /iplock kick <ip>"); return true; }
                kickIP(sender, args[1]);
                break;

            case "whitelist":
                if (args.length < 3) { sender.sendMessage(PREFIX + ChatColor.RED + "Cú pháp: /iplock whitelist <add|remove> <ip>"); return true; }
                manageWhitelist(sender, args[1], args[2]);
                break;

            case "setlimit":
                if (args.length < 3) { sender.sendMessage(PREFIX + ChatColor.RED + "Cú pháp: /iplock setlimit <ip> <number>"); return true; }
                setIPLimit(sender, args[1], args[2]);
                break;

            case "stats":
                showStats(sender);
                break;

            case "reload":
                reloadConfigs(sender);
                break;

            case "clear":
                if (args.length < 2) { sender.sendMessage(PREFIX + ChatColor.RED + "Cú pháp: /iplock clear <ip|player>"); return true; }
                clearData(sender, args[1]);
                break;

            case "resetip":
                if (args.length < 2) { sender.sendMessage(PREFIX + ChatColor.RED + "Cú pháp: /iplock resetip <player>"); return true; }
                resetPlayerIP(sender, args[1]);
                break;

            case "help":
                showHelp(sender);
                break;

            default:
                sender.sendMessage(PREFIX + ChatColor.RED + "Lệnh không tồn tại! Gõ /iplock help");
                break;
        }
        return true;
    }

    // ══════════════════════════════════════════════════
    //  HELP — hiện toàn bộ lệnh
    // ══════════════════════════════════════════════════
    private void showHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "╔══════════════════════════════════╗");
        sender.sendMessage(ChatColor.GOLD + "║   " + ChatColor.AQUA + "🔒 IPLock - Danh Sách Lệnh" + ChatColor.GOLD + "      ║");
        sender.sendMessage(ChatColor.GOLD + "╠══════════════════════════════════╣");
        sender.sendMessage(ChatColor.YELLOW + "  /iplock info <player>"     + ChatColor.GRAY + "       - Xem IP, ngày vào, số accounts của player");
        sender.sendMessage(ChatColor.YELLOW + "  /iplock list <ip>"          + ChatColor.GRAY + "          - Liệt kê tất cả accounts trên 1 IP");
        sender.sendMessage(ChatColor.YELLOW + "  /iplock kick <ip>"          + ChatColor.GRAY + "          - Kick toàn bộ player đang dùng IP đó");
        sender.sendMessage(ChatColor.YELLOW + "  /iplock whitelist add <ip>" + ChatColor.GRAY + "  - Thêm IP vào whitelist (bỏ qua giới hạn)");
        sender.sendMessage(ChatColor.YELLOW + "  /iplock whitelist remove <ip>" + ChatColor.GRAY + " - Xóa IP khỏi whitelist");
        sender.sendMessage(ChatColor.YELLOW + "  /iplock setlimit <ip> <n>" + ChatColor.GRAY + "   - Đặt giới hạn riêng cho 1 IP cụ thể");
        sender.sendMessage(ChatColor.YELLOW + "  /iplock resetip <player>"  + ChatColor.GRAY + "    - Xóa IP đã lưu, cho phép đổi IP lần sau");
        sender.sendMessage(ChatColor.YELLOW + "  /iplock clear <ip|player>" + ChatColor.GRAY + "   - Xóa toàn bộ dữ liệu của IP hoặc player");
        sender.sendMessage(ChatColor.YELLOW + "  /iplock stats"             + ChatColor.GRAY + "              - Thống kê tổng players, IPs, online");
        sender.sendMessage(ChatColor.YELLOW + "  /iplock reload"            + ChatColor.GRAY + "             - Tải lại config và dữ liệu IP");
        sender.sendMessage(ChatColor.YELLOW + "  /iplock help"              + ChatColor.GRAY + "               - Hiển thị menu này");
        sender.sendMessage(ChatColor.GOLD + "╚══════════════════════════════════╝");
    }

    // ══════════════════════════════════════════════════
    //  RESET IP — cho phép admin xóa IP đã lưu của player
    //  dùng khi player đổi IP hợp lệ
    // ══════════════════════════════════════════════════
    private void resetPlayerIP(CommandSender sender, String playerName) {
        if (!ipConfig.contains("players." + playerName)) {
            sender.sendMessage(PREFIX + ChatColor.RED + "❌ Không tìm thấy player: " + playerName);
            return;
        }

        try {
            String oldIP = ipConfig.getString("players." + playerName + ".ip");

            // Xóa tên player khỏi danh sách IP cũ
            List<String> accounts = getAccountsOnIP(oldIP);
            accounts.remove(playerName);
            ipConfig.set("ips." + oldIP, accounts.isEmpty() ? null : accounts);

            // Xóa bản ghi player
            ipConfig.set("players." + playerName, null);
            ipConfig.save(ipFile);

            sender.sendMessage(PREFIX + ChatColor.GREEN + "✓ Đã reset IP của " + playerName
                    + ChatColor.GRAY + " (IP cũ: " + oldIP + ")");
            sender.sendMessage(PREFIX + ChatColor.YELLOW + "Lần đăng nhập tiếp theo IP mới sẽ được ghi lại.");
            logInfo(sender.getName() + " reset IP của " + playerName + " (IP cũ: " + oldIP + ")");
        } catch (IOException e) {
            logError("Cannot reset player IP: " + e.getMessage());
        }
    }

    // ══════════════════════════════════════════════════
    //  Các helper còn lại
    // ══════════════════════════════════════════════════
    private List<String> getAccountsOnIP(String ip) {
        if (ipConfig.contains("ips." + ip)) {
            return new ArrayList<>(ipConfig.getStringList("ips." + ip));
        }
        return new ArrayList<>();
    }

    private int getIPLimit(String ip) {
        if (ip != null && ipConfig.contains("limits." + ip)) {
            return ipConfig.getInt("limits." + ip);
        }
        return config.getInt("settings.max-accounts-per-ip", DEFAULT_IP_LIMIT);
    }

    private void addPlayerIP(String playerName, UUID uuid, String ip) {
        try {
            ipConfig.set("players." + playerName + ".uuid", uuid.toString());
            ipConfig.set("players." + playerName + ".ip", ip);
            ipConfig.set("players." + playerName + ".join-date", getCurrentDateTime());

            List<String> accounts = getAccountsOnIP(ip);
            if (!accounts.contains(playerName)) {
                accounts.add(playerName);
                ipConfig.set("ips." + ip, accounts);
            }
            ipConfig.save(ipFile);
        } catch (IOException e) {
            logError("Cannot save player IP!");
        }
    }

    private void showPlayerInfo(CommandSender sender, String playerName) {
        if (ipConfig.contains("players." + playerName)) {
            String ip = ipConfig.getString("players." + playerName + ".ip");
            String joinDate = ipConfig.getString("players." + playerName + ".join-date");
            List<String> accounts = getAccountsOnIP(ip);

            sender.sendMessage(PREFIX + ChatColor.AQUA + "═══════════════════════════");
            sender.sendMessage(ChatColor.GOLD + "  Thông tin: " + ChatColor.GREEN + playerName);
            sender.sendMessage(ChatColor.GOLD + "  IP: " + ChatColor.YELLOW + ip);
            sender.sendMessage(ChatColor.GOLD + "  Ngày vào: " + ChatColor.YELLOW + joinDate);
            sender.sendMessage(ChatColor.GOLD + "  Accounts trên IP: " + ChatColor.YELLOW + accounts.size() + "/" + getIPLimit(ip));
            sender.sendMessage(PREFIX + ChatColor.AQUA + "═══════════════════════════");
        } else {
            sender.sendMessage(PREFIX + ChatColor.RED + "❌ Player không tìm thấy!");
        }
    }

    private void listAccountsOnIP(CommandSender sender, String ip) {
        List<String> accounts = getAccountsOnIP(ip);
        sender.sendMessage(PREFIX + ChatColor.AQUA + "═══════════════════════════");
        sender.sendMessage(ChatColor.GOLD + "  Accounts trên IP: " + ChatColor.YELLOW + ip);
        sender.sendMessage(ChatColor.GOLD + "  Tổng số: " + ChatColor.GREEN + accounts.size() + "/" + getIPLimit(ip));
        sender.sendMessage(ChatColor.DARK_GRAY + "  ─────────────────────────");
        for (String account : accounts) {
            sender.sendMessage(ChatColor.YELLOW + "    • " + ChatColor.WHITE + account);
        }
        sender.sendMessage(PREFIX + ChatColor.AQUA + "═══════════════════════════");
    }

    private void kickIP(CommandSender sender, String ip) {
        List<String> accounts = getAccountsOnIP(ip);
        int kicked = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getAddress().getAddress().getHostAddress().equals(ip)) {
                player.kickPlayer(ChatColor.RED + "Bạn đã bị kick bởi Admin!");
                kicked++;
            }
        }
        sender.sendMessage(PREFIX + ChatColor.GREEN + "✓ Đã kick " + kicked + " player từ IP " + ip);
        logWarning(sender.getName() + " đã kick IP: " + ip + " (" + kicked + " players)");
    }

    private void manageWhitelist(CommandSender sender, String action, String ip) {
        try {
            List<String> whitelist = ipConfig.getStringList("whitelist");
            if (action.equalsIgnoreCase("add")) {
                if (!whitelist.contains(ip)) {
                    whitelist.add(ip);
                    ipConfig.set("whitelist", whitelist);
                    ipConfig.save(ipFile);
                    sender.sendMessage(PREFIX + ChatColor.GREEN + "✓ Thêm " + ip + " vào whitelist");
                } else {
                    sender.sendMessage(PREFIX + ChatColor.YELLOW + "IP đã có trong whitelist rồi.");
                }
            } else if (action.equalsIgnoreCase("remove")) {
                if (whitelist.remove(ip)) {
                    ipConfig.set("whitelist", whitelist);
                    ipConfig.save(ipFile);
                    sender.sendMessage(PREFIX + ChatColor.GREEN + "✓ Xóa " + ip + " khỏi whitelist");
                } else {
                    sender.sendMessage(PREFIX + ChatColor.RED + "❌ IP không trong whitelist");
                }
            }
        } catch (IOException e) {
            logError("Cannot update whitelist!");
        }
    }

    private void setIPLimit(CommandSender sender, String ip, String limitStr) {
        try {
            int limit = Integer.parseInt(limitStr);
            ipConfig.set("limits." + ip, limit);
            ipConfig.save(ipFile);
            sender.sendMessage(PREFIX + ChatColor.GREEN + "✓ Đặt giới hạn cho IP " + ip + ": " + limit);
            logInfo(sender.getName() + " đặt limit cho IP " + ip + ": " + limit);
        } catch (NumberFormatException e) {
            sender.sendMessage(PREFIX + ChatColor.RED + "❌ Số không hợp lệ!");
        } catch (IOException e) {
            logError("Cannot save limit!");
        }
    }

    private void showStats(CommandSender sender) {
        int totalPlayers = ipConfig.contains("players")
                ? ipConfig.getConfigurationSection("players").getKeys(false).size() : 0;
        int totalIPs = ipConfig.contains("ips")
                ? ipConfig.getConfigurationSection("ips").getKeys(false).size() : 0;

        sender.sendMessage(PREFIX + ChatColor.AQUA + "═══════════════════════════");
        sender.sendMessage(ChatColor.GOLD + "  📊 Thống kê Server:");
        sender.sendMessage(ChatColor.GOLD + "  Tổng players: " + ChatColor.GREEN + totalPlayers);
        sender.sendMessage(ChatColor.GOLD + "  Tổng IPs: " + ChatColor.GREEN + totalIPs);
        sender.sendMessage(ChatColor.GOLD + "  Online: " + ChatColor.YELLOW + Bukkit.getOnlinePlayers().size());
        sender.sendMessage(PREFIX + ChatColor.AQUA + "═══════════════════════════");
    }

    private void clearData(CommandSender sender, String target) {
        try {
            if (ipConfig.contains("players." + target)) {
                String ip = ipConfig.getString("players." + target + ".ip");
                ipConfig.set("players." + target, null);
                List<String> accounts = getAccountsOnIP(ip);
                accounts.remove(target);
                ipConfig.set("ips." + ip, accounts.isEmpty() ? null : accounts);
                ipConfig.save(ipFile);
                sender.sendMessage(PREFIX + ChatColor.GREEN + "✓ Xóa dữ liệu player: " + target);
            } else if (ipConfig.contains("ips." + target)) {
                ipConfig.set("ips." + target, null);
                ipConfig.save(ipFile);
                sender.sendMessage(PREFIX + ChatColor.GREEN + "✓ Xóa IP: " + target);
            } else {
                sender.sendMessage(PREFIX + ChatColor.RED + "❌ Không tìm thấy!");
            }
        } catch (IOException e) {
            logError("Cannot clear data!");
        }
    }

    private void reloadConfigs(CommandSender sender) {
        createConfigFiles();
        sender.sendMessage(PREFIX + ChatColor.GREEN + "✓ Đã reload config!");
        logInfo(sender.getName() + " reload config");
    }

    private void notifyAdmins(String playerName, String ip, int count, int max) {
        String message = PREFIX + ChatColor.RED + "⚠️ " + ChatColor.YELLOW + playerName +
                ChatColor.RED + " bị kick - IP limit vượt (" + count + "/" + max + ")";
        for (Player admin : Bukkit.getOnlinePlayers()) {
            if (admin.hasPermission("iplock.admin")) {
                admin.sendMessage(message);
            }
        }
    }

    private void notifyAdminsIPChange(String playerName, String oldIP, String newIP) {
        String message = PREFIX + ChatColor.RED + "⚠️ " + ChatColor.YELLOW + playerName +
                ChatColor.RED + " bị kick vì đổi IP!" +
                ChatColor.GRAY + " (" + oldIP + " → " + newIP + ")" +
                ChatColor.GRAY + " Dùng /iplock resetip " + playerName + " nếu hợp lệ.";
        for (Player admin : Bukkit.getOnlinePlayers()) {
            if (admin.hasPermission("iplock.admin")) {
                admin.sendMessage(message);
            }
        }
    }

    private boolean isWhitelisted(String ip) {
        return ipConfig.getStringList("whitelist").contains(ip);
    }

    private boolean isNotificationEnabled() {
        return config.getBoolean("settings.enable-notifications", true);
    }

    private String getCurrentDateTime() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"));
    }

    private void logInfo(String msg)    { getLogger().info(msg); }
    private void logWarning(String msg) { getLogger().warning(msg); }
    private void logError(String msg)   { getLogger().severe(msg); }
}
