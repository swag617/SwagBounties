package com.swag.swagbounties.command;

import com.swag.swagbounties.SwagBounties;
import com.swag.swagbounties.bounty.Bounty;
import com.swag.swagbounties.bounty.BountyManager;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public final class AdminCommand implements CommandExecutor, TabCompleter {

    private static final String PREFIX =
            ChatColor.DARK_RED + "[SwagBounties Admin] " + ChatColor.RESET;

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private static final String PERMISSION = "swagbounties.admin";

    /** UUID used for admin-placed bounties — refunding to this UUID is meaningless. */
    private static final UUID SERVER_UUID = new UUID(0, 0);

    // Keys whose type is double, with their valid ranges.
    // Range stored as double[]{min, max}; Double.MAX_VALUE means no upper bound.
    private static final List<String> DOUBLE_KEYS = List.of(
            "min-bounty",
            "max-bounty",
            "placement-tax",
            "expiry-refund-tax",
            "discord-notify-threshold"
    );

    // Keys whose type is int, with their valid ranges.
    private static final List<String> INT_KEYS = List.of(
            "bounty-expiry-days",
            "bounty-cooldown-seconds"
    );

    // Keys whose type is String (no special validation beyond the join).
    private static final List<String> STRING_KEYS = List.of(
            "set-message",
            "claim-message",
            "set-message-anon",
            "discord-webhook-url",
            "discord-set-message",
            "discord-claim-message",
            "discord-expire-message"
    );

    // All known editable keys in declaration order (used for /config list and tab completion).
    private static final List<String> ALL_KEYS;

    static {
        List<String> keys = new ArrayList<>();
        keys.addAll(DOUBLE_KEYS);
        keys.addAll(INT_KEYS);
        keys.addAll(STRING_KEYS);
        ALL_KEYS = Collections.unmodifiableList(keys);
    }

    private final SwagBounties plugin;

    public AdminCommand(SwagBounties plugin) {
        this.plugin = plugin;
    }

    // -------------------------------------------------------------------------
    // CommandExecutor
    // -------------------------------------------------------------------------

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    "&cYou do not have permission to use this command."));
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "config"   -> handleConfig(sender, args);
            case "remove"   -> handleRemove(sender, args);
            case "clear"    -> handleClear(sender, args);
            case "clearall" -> handleClearAll(sender);
            case "give"     -> handleGive(sender, args);
            case "inspect"  -> handleInspect(sender, args);
            case "reload"   -> handleReset(sender);
            default         -> sendHelp(sender);
        }

        return true;
    }

    // -------------------------------------------------------------------------
    // /bountyadmin config <sub> [args]
    // -------------------------------------------------------------------------

    private void handleConfig(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sendHelp(sender);
            return;
        }
        switch (args[1].toLowerCase()) {
            case "get"   -> handleGet(sender, args);
            case "set"   -> handleSet(sender, args);
            case "list"  -> handleList(sender);
            case "reset" -> handleReset(sender);
            default      -> sendHelp(sender);
        }
    }

    // -------------------------------------------------------------------------
    // /bountyadmin remove <target> [creator]
    // Force-removes a bounty. If creator is given, removes only that creator's
    // bounty and refunds them. If omitted, removes ALL bounties on target with refunds.
    // -------------------------------------------------------------------------

    private void handleRemove(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Usage: /bountyadmin remove <target> [creator]");
            return;
        }

        BountyManager bm = plugin.getBountyManager();
        Economy eco = plugin.getEconomy();

        OfflinePlayer target = resolveOfflinePlayer(args[1]);
        if (target == null) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Player " + ChatColor.YELLOW + args[1]
                    + ChatColor.RED + " has never joined this server.");
            return;
        }

        List<Bounty> bounties = new ArrayList<>(bm.getBounties(target.getUniqueId()));
        if (bounties.isEmpty()) {
            sender.sendMessage(PREFIX + ChatColor.YELLOW + args[1]
                    + ChatColor.RED + " has no active bounties.");
            return;
        }

        // Filter to a specific creator if provided
        if (args.length >= 3) {
            OfflinePlayer creator = resolveOfflinePlayer(args[2]);
            if (creator == null) {
                sender.sendMessage(PREFIX + ChatColor.RED + "Creator " + ChatColor.YELLOW + args[2]
                        + ChatColor.RED + " has never joined this server.");
                return;
            }
            final UUID creatorUUID = creator.getUniqueId();
            bounties.removeIf(b -> !b.getCreatorUUID().equals(creatorUUID));
            if (bounties.isEmpty()) {
                sender.sendMessage(PREFIX + ChatColor.YELLOW + args[2]
                        + ChatColor.RED + " has no bounty on " + ChatColor.YELLOW + args[1] + ChatColor.RED + ".");
                return;
            }
        }

        int removed = 0;
        for (Bounty b : bounties) {
            if (bm.removeBounty(target.getUniqueId(), b.getCreatorUUID())) {
                if (!b.getCreatorUUID().equals(SERVER_UUID)) {
                    eco.depositPlayer(Bukkit.getOfflinePlayer(b.getCreatorUUID()), b.getReward());
                }
                removed++;
            }
        }

        bm.saveToDisk();
        plugin.rebuildBountiesGUI();

        String targetName = target.getName() != null ? target.getName() : args[1];
        sender.sendMessage(PREFIX + ChatColor.GREEN + "Removed " + ChatColor.YELLOW + removed
                + ChatColor.GREEN + " bounty/bounties on " + ChatColor.YELLOW + targetName
                + ChatColor.GREEN + " and refunded creators.");
    }

    // -------------------------------------------------------------------------
    // /bountyadmin clear <player>
    // Removes all bounties on a player with full refunds to all creators.
    // -------------------------------------------------------------------------

    private void handleClear(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Usage: /bountyadmin clear <player>");
            return;
        }

        BountyManager bm = plugin.getBountyManager();
        Economy eco = plugin.getEconomy();

        OfflinePlayer target = resolveOfflinePlayer(args[1]);
        if (target == null) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Player " + ChatColor.YELLOW + args[1]
                    + ChatColor.RED + " has never joined this server.");
            return;
        }

        List<Bounty> bounties = new ArrayList<>(bm.getBounties(target.getUniqueId()));
        if (bounties.isEmpty()) {
            sender.sendMessage(PREFIX + ChatColor.YELLOW + (target.getName() != null ? target.getName() : args[1])
                    + ChatColor.RED + " has no active bounties.");
            return;
        }

        for (Bounty b : bounties) {
            if (bm.removeBounty(target.getUniqueId(), b.getCreatorUUID())) {
                if (!b.getCreatorUUID().equals(SERVER_UUID)) {
                    eco.depositPlayer(Bukkit.getOfflinePlayer(b.getCreatorUUID()), b.getReward());
                }
            }
        }

        bm.saveToDisk();
        plugin.rebuildBountiesGUI();

        String targetName = target.getName() != null ? target.getName() : args[1];
        sender.sendMessage(PREFIX + ChatColor.GREEN + "Cleared all " + ChatColor.YELLOW + bounties.size()
                + ChatColor.GREEN + " bounty/bounties on " + ChatColor.YELLOW + targetName
                + ChatColor.GREEN + " and refunded all creators.");
    }

    // -------------------------------------------------------------------------
    // /bountyadmin clearall
    // Wipes every active bounty on the server with full refunds.
    // -------------------------------------------------------------------------

    private void handleClearAll(CommandSender sender) {
        BountyManager bm = plugin.getBountyManager();
        Economy eco = plugin.getEconomy();

        List<Bounty> all = new ArrayList<>(bm.getAllBounties());
        if (all.isEmpty()) {
            sender.sendMessage(PREFIX + ChatColor.YELLOW + "There are no active bounties to clear.");
            return;
        }

        int count = 0;
        for (Bounty b : all) {
            if (bm.removeBounty(b.getTargetUUID(), b.getCreatorUUID())) {
                if (!b.getCreatorUUID().equals(SERVER_UUID)) {
                    eco.depositPlayer(Bukkit.getOfflinePlayer(b.getCreatorUUID()), b.getReward());
                }
                count++;
            }
        }

        bm.saveToDisk();
        plugin.rebuildBountiesGUI();

        sender.sendMessage(PREFIX + ChatColor.GREEN + "Cleared " + ChatColor.YELLOW + count
                + ChatColor.GREEN + " bounty/bounties server-wide and refunded all creators.");
    }

    // -------------------------------------------------------------------------
    // /bountyadmin give <target> <amount> [--anon]
    // Places a bounty without deducting money from anyone (admin grant).
    // -------------------------------------------------------------------------

    private void handleGive(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Usage: /bountyadmin give <target> <amount> [--anon]");
            return;
        }

        OfflinePlayer target = resolveOfflinePlayer(args[1]);
        if (target == null) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Player " + ChatColor.YELLOW + args[1]
                    + ChatColor.RED + " has never joined this server.");
            return;
        }

        double amount;
        try {
            amount = Double.parseDouble(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Invalid amount: " + ChatColor.YELLOW + args[2]);
            return;
        }

        if (!Double.isFinite(amount) || amount <= 0) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Amount must be a positive number.");
            return;
        }

        boolean anon = args.length >= 4 && args[3].equalsIgnoreCase("--anon");

        // Use a fixed "server" UUID as the creator so it's distinguishable in /bounty list
        UUID serverUUID = new UUID(0, 0);
        Bounty bounty = new Bounty(target.getUniqueId(), serverUUID, amount, anon);
        plugin.getBountyManager().addBounty(bounty);
        plugin.getBountyManager().saveToDisk();
        plugin.rebuildBountiesGUI();

        String targetName = target.getName() != null ? target.getName() : args[1];
        sender.sendMessage(PREFIX + ChatColor.GREEN + "Placed a " + ChatColor.YELLOW
                + String.format("$%.2f", amount) + ChatColor.GREEN + " admin bounty on "
                + ChatColor.YELLOW + targetName + ChatColor.GREEN + ".");

        // Notify target if online
        Player onlineTarget = Bukkit.getPlayer(target.getUniqueId());
        if (onlineTarget != null) {
            onlineTarget.sendMessage(ChatColor.DARK_RED + "[SwagBounties] "
                    + ChatColor.WHITE + "An admin has placed a "
                    + ChatColor.GREEN + String.format("$%.2f", amount)
                    + ChatColor.WHITE + " bounty on you!");
        }
    }

    // -------------------------------------------------------------------------
    // /bountyadmin inspect <player>
    // Shows all bounties on a player with full detail (no anonymity masking).
    // -------------------------------------------------------------------------

    private void handleInspect(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Usage: /bountyadmin inspect <player>");
            return;
        }

        OfflinePlayer target = resolveOfflinePlayer(args[1]);
        if (target == null) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Player " + ChatColor.YELLOW + args[1]
                    + ChatColor.RED + " has never joined this server.");
            return;
        }

        List<Bounty> bounties = plugin.getBountyManager().getBounties(target.getUniqueId());
        String targetName = target.getName() != null ? target.getName() : args[1];

        if (bounties.isEmpty()) {
            sender.sendMessage(PREFIX + ChatColor.YELLOW + targetName
                    + ChatColor.GRAY + " has no active bounties.");
            return;
        }

        double total = plugin.getBountyManager().getTotalReward(target.getUniqueId());
        sender.sendMessage(ChatColor.DARK_RED + "--- Inspect: " + ChatColor.YELLOW + targetName
                + ChatColor.DARK_RED + " (" + bounties.size() + " bounties, total: "
                + ChatColor.GREEN + String.format("$%.2f", total) + ChatColor.DARK_RED + ") ---");

        for (int i = 0; i < bounties.size(); i++) {
            Bounty b = bounties.get(i);
            String creatorName = resolvePlayerName(b.getCreatorUUID());
            String anonTag = b.isAnonymous() ? ChatColor.GRAY + " [anon]" : "";
            String date = DATE_FORMAT.format(Instant.ofEpochMilli(b.getPlacedAt()));

            sender.sendMessage(ChatColor.GRAY + "#" + (i + 1)
                    + ChatColor.WHITE + " Reward: " + ChatColor.GREEN + String.format("$%.2f", b.getReward())
                    + ChatColor.WHITE + " | Creator: " + ChatColor.YELLOW + creatorName + anonTag
                    + ChatColor.WHITE + " | Placed: " + ChatColor.AQUA + date);
        }
    }

    // -------------------------------------------------------------------------
    // /bountyadmin config get <key>
    // -------------------------------------------------------------------------

    private void handleGet(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Usage: /bountyadmin config get <key>");
            return;
        }

        String key = args[2];

        if (!ALL_KEYS.contains(key)) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Unknown config key: "
                    + ChatColor.YELLOW + key + ChatColor.RED + ".");
            return;
        }

        Object value = plugin.getConfig().get(key);
        sender.sendMessage(PREFIX + ChatColor.YELLOW + key
                + ChatColor.WHITE + " = "
                + ChatColor.GREEN + value);
    }

    // -------------------------------------------------------------------------
    // /bountyadmin config set <key> <value>
    // -------------------------------------------------------------------------

    private void handleSet(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(PREFIX + ChatColor.RED
                    + "Usage: /bountyadmin config set <key> <value>");
            return;
        }

        String key = args[2];

        if (!ALL_KEYS.contains(key)) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Unknown config key: "
                    + ChatColor.YELLOW + key + ChatColor.RED
                    + ". Use /bountyadmin config list to see valid keys.");
            return;
        }

        // String keys: join everything from args[3] onward to allow spaces in messages.
        if (STRING_KEYS.contains(key)) {
            StringBuilder sb = new StringBuilder(args[3]);
            for (int i = 4; i < args.length; i++) {
                sb.append(' ').append(args[i]);
            }
            String value = sb.toString();
            plugin.getConfig().set(key, value);
            plugin.saveConfig();
            sender.sendMessage(PREFIX + ChatColor.GREEN + "Set "
                    + ChatColor.YELLOW + key
                    + ChatColor.GREEN + " to "
                    + ChatColor.WHITE + value
                    + ChatColor.GREEN + ".");
            return;
        }

        // Double keys
        if (DOUBLE_KEYS.contains(key)) {
            double value;
            try {
                value = Double.parseDouble(args[3]);
            } catch (NumberFormatException e) {
                sender.sendMessage(PREFIX + ChatColor.RED + "Invalid number: "
                        + ChatColor.YELLOW + args[3]
                        + ChatColor.RED + ". Please enter a valid decimal number.");
                return;
            }

            String error = validateDouble(key, value);
            if (error != null) {
                sender.sendMessage(PREFIX + ChatColor.RED + error);
                return;
            }

            plugin.getConfig().set(key, value);
            plugin.saveConfig();
            sender.sendMessage(PREFIX + ChatColor.GREEN + "Set "
                    + ChatColor.YELLOW + key
                    + ChatColor.GREEN + " to "
                    + ChatColor.WHITE + value
                    + ChatColor.GREEN + ".");
            return;
        }

        // Int keys
        if (INT_KEYS.contains(key)) {
            int value;
            try {
                value = Integer.parseInt(args[3]);
            } catch (NumberFormatException e) {
                sender.sendMessage(PREFIX + ChatColor.RED + "Invalid integer: "
                        + ChatColor.YELLOW + args[3]
                        + ChatColor.RED + ". Please enter a whole number.");
                return;
            }

            String error = validateInt(key, value);
            if (error != null) {
                sender.sendMessage(PREFIX + ChatColor.RED + error);
                return;
            }

            plugin.getConfig().set(key, value);
            plugin.saveConfig();
            sender.sendMessage(PREFIX + ChatColor.GREEN + "Set "
                    + ChatColor.YELLOW + key
                    + ChatColor.GREEN + " to "
                    + ChatColor.WHITE + value
                    + ChatColor.GREEN + ".");
        }
    }

    // -------------------------------------------------------------------------
    // /bountyadmin config list
    // -------------------------------------------------------------------------

    private void handleList(CommandSender sender) {
        sender.sendMessage(ChatColor.DARK_RED + "=== SwagBounties Config ===");
        for (String key : ALL_KEYS) {
            Object value = plugin.getConfig().get(key);
            sender.sendMessage(ChatColor.YELLOW + key
                    + ChatColor.WHITE + " = "
                    + ChatColor.GREEN + value);
        }
    }

    // -------------------------------------------------------------------------
    // /bountyadmin config reset
    // -------------------------------------------------------------------------

    private void handleReset(CommandSender sender) {
        plugin.reloadConfig();
        sender.sendMessage(PREFIX + ChatColor.GREEN + "Config reloaded from disk.");
    }

    // -------------------------------------------------------------------------
    // Help
    // -------------------------------------------------------------------------

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.DARK_RED + "=== SwagBounties Admin ===");
        sender.sendMessage(ChatColor.YELLOW + "/bountyadmin config get <key>"
                + ChatColor.GRAY + " - View a config value");
        sender.sendMessage(ChatColor.YELLOW + "/bountyadmin config set <key> <value>"
                + ChatColor.GRAY + " - Change a config value");
        sender.sendMessage(ChatColor.YELLOW + "/bountyadmin config list"
                + ChatColor.GRAY + " - List all config values");
        sender.sendMessage(ChatColor.YELLOW + "/bountyadmin config reset"
                + ChatColor.GRAY + " - Reload config from disk");
        sender.sendMessage(ChatColor.YELLOW + "/bountyadmin reload"
                + ChatColor.GRAY + " - Shorthand for config reset");
        sender.sendMessage(ChatColor.YELLOW + "/bountyadmin remove <target> [creator]"
                + ChatColor.GRAY + " - Force-remove bounty/bounties with refund");
        sender.sendMessage(ChatColor.YELLOW + "/bountyadmin clear <player>"
                + ChatColor.GRAY + " - Clear all bounties on a player with refunds");
        sender.sendMessage(ChatColor.YELLOW + "/bountyadmin clearall"
                + ChatColor.GRAY + " - Wipe all bounties server-wide with refunds");
        sender.sendMessage(ChatColor.YELLOW + "/bountyadmin give <target> <amount> [--anon]"
                + ChatColor.GRAY + " - Place a free admin bounty");
        sender.sendMessage(ChatColor.YELLOW + "/bountyadmin inspect <player>"
                + ChatColor.GRAY + " - View all bounty details (anonymity unmasked)");
    }

    // -------------------------------------------------------------------------
    // Validation helpers
    // -------------------------------------------------------------------------

    /**
     * Returns an error message string if the value is out of range, or null if valid.
     */
    private String validateDouble(String key, double value) {
        return switch (key) {
            case "min-bounty" ->
                    value < 0 ? "min-bounty must be 0 or greater." : null;
            case "max-bounty" ->
                    value < 0 ? "max-bounty must be 0 or greater (0 = no limit)." : null;
            case "placement-tax" ->
                    (value < 0 || value > 100) ? "placement-tax must be between 0 and 100." : null;
            case "expiry-refund-tax" ->
                    (value < 0 || value > 100) ? "expiry-refund-tax must be between 0 and 100." : null;
            case "discord-notify-threshold" ->
                    value < 0 ? "discord-notify-threshold must be 0 or greater." : null;
            default -> null;
        };
    }

    /**
     * Returns an error message string if the value is out of range, or null if valid.
     */
    private String validateInt(String key, int value) {
        return switch (key) {
            case "bounty-expiry-days" ->
                    value < 0 ? "bounty-expiry-days must be 0 or greater (0 = never)." : null;
            case "bounty-cooldown-seconds" ->
                    value < 0 ? "bounty-cooldown-seconds must be 0 or greater (0 = disabled)." : null;
            default -> null;
        };
    }

    // -------------------------------------------------------------------------
    // TabCompleter
    // -------------------------------------------------------------------------

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            return Collections.emptyList();
        }

        List<String> topLevel = List.of("config", "remove", "clear", "clearall", "give", "inspect", "reload");

        if (args.length == 1) {
            return filterStartsWith(topLevel, args[0]);
        }

        String sub = args[0].toLowerCase();

        // Player-name completion for commands that take a target at args[1]
        if (args.length == 2 && (sub.equals("remove") || sub.equals("clear")
                || sub.equals("give") || sub.equals("inspect"))) {
            List<String> names = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                names.add(p.getName());
            }
            return filterStartsWith(names, args[1]);
        }

        // /bountyadmin remove <target> [creator] — complete creator at args[2]
        if (args.length == 3 && sub.equals("remove")) {
            List<String> names = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                names.add(p.getName());
            }
            return filterStartsWith(names, args[2]);
        }

        // /bountyadmin give <target> <amount> [--anon]
        if (args.length == 3 && sub.equals("give")) {
            return List.of("<amount>");
        }
        if (args.length == 4 && sub.equals("give")) {
            return filterStartsWith(List.of("--anon"), args[3]);
        }

        // /bountyadmin config sub-commands
        if (sub.equals("config")) {
            if (args.length == 2) {
                return filterStartsWith(List.of("get", "set", "reset", "list"), args[1]);
            }
            String sub2 = args[1].toLowerCase();
            if (args.length == 3 && (sub2.equals("get") || sub2.equals("set"))) {
                return filterStartsWith(ALL_KEYS, args[2]);
            }
            if (args.length == 4 && sub2.equals("set")) {
                String key = args[2];
                if (ALL_KEYS.contains(key)) {
                    Object current = plugin.getConfig().get(key);
                    if (current != null) {
                        return List.of(current.toString());
                    }
                }
            }
        }

        return Collections.emptyList();
    }

    // -------------------------------------------------------------------------
    // Utility
    // -------------------------------------------------------------------------

    private List<String> filterStartsWith(List<String> candidates, String input) {
        String lower = input.toLowerCase();
        List<String> result = new ArrayList<>();
        for (String s : candidates) {
            if (s.toLowerCase().startsWith(lower)) {
                result.add(s);
            }
        }
        return result;
    }

    /**
     * Resolves a player name to an OfflinePlayer. Returns null if the server has
     * no record of that player (i.e. hasPlayedBefore() is false and not online).
     */
    @SuppressWarnings("deprecation")
    private OfflinePlayer resolveOfflinePlayer(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online;
        OfflinePlayer op = Bukkit.getOfflinePlayer(name);
        return (op.hasPlayedBefore() || op.isOnline()) ? op : null;
    }

    /** Resolves a UUID to a display name, falling back to the UUID string. */
    private String resolvePlayerName(UUID uuid) {
        // Special case: UUID(0,0) is used for server-placed admin bounties
        if (uuid.equals(new UUID(0, 0))) return "Server";
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) return online.getName();
        OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
        return op.getName() != null ? op.getName() : uuid.toString();
    }
}
