package com.swag.swagbounties.command;

import com.swag.swagbounties.SwagBounties;
import com.swag.swagbounties.bounty.Bounty;
import com.swag.swagbounties.bounty.BountyManager;
import com.swag.swagbounties.discord.DiscordWebhook;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class BountyCommand implements CommandExecutor, TabCompleter {

    private static final String PREFIX = ChatColor.RED + "[SwagBounties] " + ChatColor.RESET;
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private final SwagBounties plugin;
    private final BountyManager bountyManager;
    private final Economy economy;

    /**
     * Tracks the epoch-millisecond timestamp of the last bounty action (set/remove/add)
     * per player UUID. Checked against the configured cooldown on each mutating command.
     * Held in memory only — resets on server restart (intentional).
     */
    private final HashMap<UUID, Long> cooldowns = new HashMap<>();

    public BountyCommand(SwagBounties plugin) {
        this.plugin = plugin;
        this.bountyManager = plugin.getBountyManager();
        this.economy = plugin.getEconomy();
    }

    // -------------------------------------------------------------------------
    // CommandExecutor
    // -------------------------------------------------------------------------

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "set"    -> handleSet(sender, args);
            case "add"    -> handleAdd(sender, args);
            case "remove" -> handleRemove(sender, args);
            case "list"   -> handleList(sender, args);
            case "top"    -> handleTop(sender);
            case "help"   -> sendHelp(sender);
            default       -> {
                sender.sendMessage(PREFIX + ChatColor.RED + "Unknown sub-command. Use "
                        + ChatColor.YELLOW + "/" + label + " help" + ChatColor.RED + " for usage.");
            }
        }
        return true;
    }

    // -------------------------------------------------------------------------
    // /bounty set <player> <amount> [--anon]
    // -------------------------------------------------------------------------

    private void handleSet(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Only players can set bounties.");
            return;
        }

        if (args.length < 3) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Usage: /bounty set <player> <amount> [--anon]");
            return;
        }

        // Resolve target — prefer online player, fall back to offline player cache
        org.bukkit.OfflinePlayer target;
        Player onlineTarget = Bukkit.getPlayerExact(args[1]);
        if (onlineTarget != null) {
            target = onlineTarget;
        } else {
            @SuppressWarnings("deprecation")
            org.bukkit.OfflinePlayer op = Bukkit.getOfflinePlayer(args[1]);
            if (!op.hasPlayedBefore() && !op.isOnline()) {
                player.sendMessage(PREFIX + ChatColor.RED + "Player " + ChatColor.YELLOW + args[1]
                        + ChatColor.RED + " has never joined this server.");
                return;
            }
            target = op;
        }

        if (target.getUniqueId().equals(player.getUniqueId())) {
            player.sendMessage(PREFIX + ChatColor.RED + "You cannot place a bounty on yourself.");
            return;
        }

        // Cooldown check
        if (isOnCooldown(player)) return;

        // Parse amount
        double amount;
        try {
            amount = Double.parseDouble(args[2]);
        } catch (NumberFormatException e) {
            player.sendMessage(PREFIX + ChatColor.RED + "Invalid amount: " + ChatColor.YELLOW + args[2]
                    + ChatColor.RED + ". Please enter a positive number.");
            return;
        }

        if (!Double.isFinite(amount) || amount <= 0) {
            player.sendMessage(PREFIX + ChatColor.RED + "The bounty amount must be a positive number.");
            return;
        }

        // Config validation
        double minBounty = plugin.getConfig().getDouble("min-bounty", 100.0);
        double maxBounty = plugin.getConfig().getDouble("max-bounty", 0.0);

        if (amount < minBounty) {
            player.sendMessage(PREFIX + ChatColor.RED + "The minimum bounty amount is "
                    + ChatColor.GREEN + String.format("$%.2f", minBounty) + ChatColor.RED + ".");
            return;
        }

        if (maxBounty > 0 && amount > maxBounty) {
            player.sendMessage(PREFIX + ChatColor.RED + "The maximum bounty amount is "
                    + ChatColor.GREEN + String.format("$%.2f", maxBounty) + ChatColor.RED + ".");
            return;
        }

        // Anonymous flag
        boolean isAnon = args.length >= 4 && args[3].equalsIgnoreCase("--anon");

        // Tax calculation — full amount is withdrawn; the stored reward is post-tax
        double placementTax = plugin.getConfig().getDouble("placement-tax", 5.0);
        double taxedAmount = amount * (placementTax / 100.0);
        double actualReward = amount - taxedAmount;

        // Economy checks
        if (!economy.has(player, amount)) {
            player.sendMessage(PREFIX + ChatColor.RED + "You do not have enough money. You need "
                    + ChatColor.GREEN + String.format("$%.2f", amount) + ChatColor.RED + ".");
            return;
        }

        String targetName = target.getName() != null ? target.getName() : args[1];

        economy.withdrawPlayer(player, amount);
        bountyManager.addBounty(new Bounty(target.getUniqueId(), player.getUniqueId(), actualReward, isAnon));
        bountyManager.saveToDisk();
        SwagBounties.getInstance().rebuildBountiesGUI();
        stampCooldown(player);

        // Broadcast
        String broadcastKey = isAnon ? "set-message-anon" : "set-message";
        String rawBroadcast = plugin.getConfig().getString(broadcastKey, "");
        String broadcast = ChatColor.translateAlternateColorCodes('&', rawBroadcast
                .replace("%target%", targetName)
                .replace("%creator%", player.getName())
                .replace("%amount%", String.format("%.2f", actualReward)));
        if (!broadcast.isEmpty()) {
            Bukkit.broadcastMessage(broadcast);
        }

        // Discord notification for bounty set events — fires on a virtual thread so the
        // HTTP call never blocks the main thread.
        String webhookUrl = plugin.getConfig().getString("discord-webhook-url", "");
        double threshold = plugin.getConfig().getDouble("discord-notify-threshold", 500.0);
        if (!webhookUrl.isEmpty() && actualReward >= threshold) {
            String discordTemplate = plugin.getConfig().getString("discord-set-message",
                    "💰 **%creator%** placed a **$%amount%** bounty on **%target%**!");
            String discordMsg = discordTemplate
                    .replace("%creator%", isAnon ? "Anonymous" : player.getName())
                    .replace("%target%", targetName)
                    .replace("%amount%", String.format("%.2f", actualReward));
            DiscordWebhook.sendAsync(webhookUrl, discordMsg);
        }

        // Confirmation to the setter
        player.sendMessage(PREFIX + ChatColor.GREEN + "Bounty placed on "
                + ChatColor.YELLOW + targetName
                + ChatColor.GREEN + "! Net reward after tax: "
                + ChatColor.YELLOW + String.format("$%.2f", actualReward)
                + ChatColor.GREEN + " (tax deducted: "
                + ChatColor.RED + String.format("$%.2f", taxedAmount)
                + ChatColor.GREEN + ").");
    }

    // -------------------------------------------------------------------------
    // /bounty remove <player>
    // -------------------------------------------------------------------------

    private void handleRemove(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Only players can remove bounties.");
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Usage: /bounty remove <player>");
            return;
        }

        // Cooldown check — only blocks if the player is actually attempting a mutation
        if (isOnCooldown(player)) return;

        // Resolve target — allow offline targets (by name lookup through online list first)
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            // Target is offline; we still allow removal by searching stored bounties
            // for any target whose UUID maps to this name. Since BountyManager stores by
            // UUID we need to find by iterating all bounties for a matching creator UUID.
            handleRemoveByName(player, args[1]);
            return;
        }

        // Target is online — fast path
        removeBountyForTarget(player, target.getUniqueId(), target.getName());
    }

    /**
     * Searches all stored bounties for one placed by {@code player} on a target whose
     * last-known name matches {@code targetName} (case-insensitive).  This covers the
     * offline-target case where we cannot resolve a UUID from an online player object.
     */
    private void handleRemoveByName(Player player, String targetName) {
        for (Bounty b : bountyManager.getAllBounties()) {
            if (!b.getCreatorUUID().equals(player.getUniqueId())) continue;
            // Attempt to resolve the display name via the server's offline player cache
            org.bukkit.OfflinePlayer op = Bukkit.getOfflinePlayer(b.getTargetUUID());
            if (op.getName() != null && op.getName().equalsIgnoreCase(targetName)) {
                double refund = b.getReward();
                boolean removed = bountyManager.removeBounty(b.getTargetUUID(), player.getUniqueId());
                if (removed) {
                    economy.depositPlayer(player, refund);
                    bountyManager.saveToDisk();
                    SwagBounties.getInstance().rebuildBountiesGUI();
                    stampCooldown(player);
                    player.sendMessage(PREFIX + ChatColor.GREEN + "Your bounty on "
                            + ChatColor.YELLOW + targetName
                            + ChatColor.GREEN + " has been removed. "
                            + ChatColor.YELLOW + String.format("$%.2f", refund)
                            + ChatColor.GREEN + " refunded.");
                } else {
                    player.sendMessage(PREFIX + ChatColor.RED
                            + "Failed to remove the bounty (concurrent modification). Please try again.");
                }
                return;
            }
        }
        player.sendMessage(PREFIX + ChatColor.RED + "You have no bounty on "
                + ChatColor.YELLOW + targetName + ChatColor.RED + ".");
    }

    /**
     * Finds and removes the bounty placed by {@code player} on {@code targetUUID}, then
     * refunds the stored reward amount.
     */
    private void removeBountyForTarget(Player player, java.util.UUID targetUUID, String targetDisplayName) {
        // Find the matching bounty first to capture the reward before removal
        Bounty match = null;
        for (Bounty b : bountyManager.getBounties(targetUUID)) {
            if (b.getCreatorUUID().equals(player.getUniqueId())) {
                match = b;
                break;
            }
        }

        if (match == null) {
            player.sendMessage(PREFIX + ChatColor.RED + "You have no bounty on "
                    + ChatColor.YELLOW + targetDisplayName + ChatColor.RED + ".");
            return;
        }

        double refund = match.getReward();
        boolean removed = bountyManager.removeBounty(targetUUID, player.getUniqueId());

        if (removed) {
            economy.depositPlayer(player, refund);
            bountyManager.saveToDisk();
            SwagBounties.getInstance().rebuildBountiesGUI();
            stampCooldown(player);
            player.sendMessage(PREFIX + ChatColor.GREEN + "Your bounty on "
                    + ChatColor.YELLOW + targetDisplayName
                    + ChatColor.GREEN + " has been removed. "
                    + ChatColor.YELLOW + String.format("$%.2f", refund)
                    + ChatColor.GREEN + " refunded.");
        } else {
            // Race condition — the bounty was removed between our read and our write
            player.sendMessage(PREFIX + ChatColor.RED
                    + "Failed to remove the bounty (concurrent modification). Please try again.");
        }
    }

    // -------------------------------------------------------------------------
    // /bounty add <player> <amount> — stack onto an existing bounty
    // -------------------------------------------------------------------------

    private void handleAdd(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Only players can add to bounties.");
            return;
        }

        if (args.length < 3) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Usage: /bounty add <player> <amount>");
            return;
        }

        // Resolve target
        org.bukkit.OfflinePlayer target;
        Player onlineTarget = Bukkit.getPlayerExact(args[1]);
        if (onlineTarget != null) {
            target = onlineTarget;
        } else {
            @SuppressWarnings("deprecation")
            org.bukkit.OfflinePlayer op = Bukkit.getOfflinePlayer(args[1]);
            if (!op.hasPlayedBefore() && !op.isOnline()) {
                player.sendMessage(PREFIX + ChatColor.RED + "Player " + ChatColor.YELLOW + args[1]
                        + ChatColor.RED + " has never joined this server.");
                return;
            }
            target = op;
        }

        if (target.getUniqueId().equals(player.getUniqueId())) {
            player.sendMessage(PREFIX + ChatColor.RED + "You cannot place a bounty on yourself.");
            return;
        }

        // Cooldown check
        if (isOnCooldown(player)) return;

        // Find existing bounty by this creator on this target
        Bounty existing = null;
        for (Bounty b : bountyManager.getBounties(target.getUniqueId())) {
            if (b.getCreatorUUID().equals(player.getUniqueId())) {
                existing = b;
                break;
            }
        }

        if (existing == null) {
            player.sendMessage(PREFIX + ChatColor.RED + "You have no existing bounty on "
                    + ChatColor.YELLOW + (target.getName() != null ? target.getName() : args[1])
                    + ChatColor.RED + ". Use " + ChatColor.YELLOW + "/bounty set"
                    + ChatColor.RED + " to place a new bounty.");
            return;
        }

        // Parse additional amount
        double amount;
        try {
            amount = Double.parseDouble(args[2]);
        } catch (NumberFormatException e) {
            player.sendMessage(PREFIX + ChatColor.RED + "Invalid amount: " + ChatColor.YELLOW + args[2]
                    + ChatColor.RED + ". Please enter a positive number.");
            return;
        }

        if (!Double.isFinite(amount) || amount <= 0) {
            player.sendMessage(PREFIX + ChatColor.RED + "The amount to add must be a positive number.");
            return;
        }

        // Config validation — min applies to the additional amount; max applies to the resulting total
        double minBounty = plugin.getConfig().getDouble("min-bounty", 100.0);
        double maxBounty = plugin.getConfig().getDouble("max-bounty", 0.0);

        if (amount < minBounty) {
            player.sendMessage(PREFIX + ChatColor.RED + "The minimum amount to add is "
                    + ChatColor.GREEN + String.format("$%.2f", minBounty) + ChatColor.RED + ".");
            return;
        }

        // Apply placement tax to the additional amount only
        double placementTax = plugin.getConfig().getDouble("placement-tax", 5.0);
        double taxedAmount = amount * (placementTax / 100.0);
        double additionalReward = amount - taxedAmount;

        String targetName = target.getName() != null ? target.getName() : args[1];
        double newTotalReward = existing.getReward() + additionalReward;

        if (maxBounty > 0 && newTotalReward > maxBounty) {
            player.sendMessage(PREFIX + ChatColor.RED + "Adding this amount would exceed the maximum bounty of "
                    + ChatColor.GREEN + String.format("$%.2f", maxBounty) + ChatColor.RED + ".");
            return;
        }

        // Economy check
        if (!economy.has(player, amount)) {
            player.sendMessage(PREFIX + ChatColor.RED + "You do not have enough money. You need "
                    + ChatColor.GREEN + String.format("$%.2f", amount) + ChatColor.RED + ".");
            return;
        }

        // Replace: remove old bounty (no refund — player is keeping it active) then add
        // updated bounty preserving the original placedAt timestamp and anonymous flag.
        boolean removed = bountyManager.removeBounty(target.getUniqueId(), player.getUniqueId());
        if (!removed) {
            player.sendMessage(PREFIX + ChatColor.RED
                    + "Failed to update the bounty (concurrent modification). Please try again.");
            return;
        }

        economy.withdrawPlayer(player, amount);
        bountyManager.addBounty(new Bounty(
                target.getUniqueId(),
                player.getUniqueId(),
                newTotalReward,
                existing.isAnonymous(),
                existing.getPlacedAt()));
        bountyManager.saveToDisk();
        SwagBounties.getInstance().rebuildBountiesGUI();
        stampCooldown(player);

        player.sendMessage(PREFIX + ChatColor.GREEN + "Added " + ChatColor.YELLOW
                + String.format("$%.2f", additionalReward)
                + ChatColor.GREEN + " (after tax) to your bounty on "
                + ChatColor.YELLOW + targetName
                + ChatColor.GREEN + ". New total reward: "
                + ChatColor.YELLOW + String.format("$%.2f", newTotalReward)
                + ChatColor.GREEN + " (tax deducted: "
                + ChatColor.RED + String.format("$%.2f", taxedAmount)
                + ChatColor.GREEN + ").");
    }

    // -------------------------------------------------------------------------
    // /bounty top — show the top 5 most-wanted players
    // -------------------------------------------------------------------------

    private void handleTop(CommandSender sender) {
        List<Bounty> all = bountyManager.getAllBounties();

        if (all.isEmpty()) {
            sender.sendMessage(PREFIX + ChatColor.GRAY + "There are no active bounties.");
            return;
        }

        // Group by targetUUID and sum rewards
        Map<UUID, Double> totals = new LinkedHashMap<>();
        for (Bounty b : all) {
            totals.merge(b.getTargetUUID(), b.getReward(), Double::sum);
        }

        // Sort descending by total reward, take top 5
        List<Map.Entry<UUID, Double>> sorted = new ArrayList<>(totals.entrySet());
        sorted.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));

        int limit = Math.min(5, sorted.size());

        sender.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "--- Top Bounties ---");
        for (int i = 0; i < limit; i++) {
            Map.Entry<UUID, Double> entry = sorted.get(i);
            String name = resolvePlayerName(entry.getKey());
            sender.sendMessage(ChatColor.YELLOW + "#" + (i + 1)
                    + " " + ChatColor.WHITE + name
                    + ChatColor.GRAY + " - " + ChatColor.GREEN
                    + String.format("$%.2f", entry.getValue()));
        }
    }

    // -------------------------------------------------------------------------
    // /bounty list [player]
    // -------------------------------------------------------------------------

    private void handleList(CommandSender sender, String[] args) {
        if (args.length < 2) {
            // No argument — list bounties on the sender
            if (!(sender instanceof Player player)) {
                sender.sendMessage(PREFIX + ChatColor.RED
                        + "Console must specify a player: /bounty list <player>");
                return;
            }
            listBountiesFor(sender, player.getUniqueId(), player.getName());
        } else {
            // Argument provided — any sender can look up any player by name
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target != null) {
                listBountiesFor(sender, target.getUniqueId(), target.getName());
            } else {
                // Try offline lookup for the display name
                // We search all bounties for a matching target name via OfflinePlayer cache
                boolean found = false;
                for (java.util.UUID uuid : collectDistinctTargetUUIDs()) {
                    org.bukkit.OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
                    if (op.getName() != null && op.getName().equalsIgnoreCase(args[1])) {
                        listBountiesFor(sender, uuid, op.getName());
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    sender.sendMessage(PREFIX + ChatColor.RED + "No bounties found for "
                            + ChatColor.YELLOW + args[1] + ChatColor.RED + ".");
                }
            }
        }
    }

    private void listBountiesFor(CommandSender sender, java.util.UUID targetUUID, String targetName) {
        List<Bounty> bounties = bountyManager.getBounties(targetUUID);
        if (bounties.isEmpty()) {
            sender.sendMessage(PREFIX + ChatColor.YELLOW + targetName
                    + ChatColor.GRAY + " has no active bounties.");
            return;
        }

        double total = bountyManager.getTotalReward(targetUUID);
        sender.sendMessage(ChatColor.RED + "--- Bounties on " + ChatColor.YELLOW + targetName
                + ChatColor.RED + " ---");
        for (int i = 0; i < bounties.size(); i++) {
            Bounty b = bounties.get(i);
            String creatorDisplay = b.isAnonymous()
                    ? ChatColor.GRAY + "Anonymous"
                    : ChatColor.YELLOW + resolvePlayerName(b.getCreatorUUID());
            String dateStr = DATE_FORMAT.format(Instant.ofEpochMilli(b.getPlacedAt()));
            sender.sendMessage(ChatColor.GRAY + "#" + (i + 1)
                    + ChatColor.WHITE + " Reward: " + ChatColor.GREEN + String.format("$%.2f", b.getReward())
                    + ChatColor.WHITE + " | By: " + creatorDisplay
                    + ChatColor.WHITE + " | Placed: " + ChatColor.AQUA + dateStr);
        }
        sender.sendMessage(ChatColor.WHITE + "Total reward: " + ChatColor.GREEN
                + String.format("$%.2f", total));
    }

    // -------------------------------------------------------------------------
    // /bounty help
    // -------------------------------------------------------------------------

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.RED + "=== SwagBounties Help ===");
        sender.sendMessage(ChatColor.YELLOW + "/bounty set <player> <amount> [--anon]"
                + ChatColor.GRAY + " - Place a new bounty on a player.");
        sender.sendMessage(ChatColor.YELLOW + "/bounty add <player> <amount>"
                + ChatColor.GRAY + " - Add to an existing bounty you placed on a player.");
        sender.sendMessage(ChatColor.YELLOW + "/bounty remove <player>"
                + ChatColor.GRAY + " - Remove your bounty on a player and receive a refund.");
        sender.sendMessage(ChatColor.YELLOW + "/bounty list [player]"
                + ChatColor.GRAY + " - List bounties on yourself or another player.");
        sender.sendMessage(ChatColor.YELLOW + "/bounty top"
                + ChatColor.GRAY + " - Show the top 5 most-wanted players.");
        sender.sendMessage(ChatColor.YELLOW + "/bounty help"
                + ChatColor.GRAY + " - Show this help message.");
    }

    // -------------------------------------------------------------------------
    // TabCompleter
    // -------------------------------------------------------------------------

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filterStartsWith(List.of("set", "add", "remove", "list", "top", "help"), args[0]);
        }

        String sub = args[0].toLowerCase();

        if (args.length == 2) {
            if (sub.equals("set") || sub.equals("add") || sub.equals("remove") || sub.equals("list")) {
                List<String> names = new ArrayList<>();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    names.add(p.getName());
                }
                return filterStartsWith(names, args[1]);
            }
        }

        if (args.length == 3 && (sub.equals("set") || sub.equals("add"))) {
            return List.of("<amount>");
        }

        if (args.length == 4 && sub.equals("set")) {
            return filterStartsWith(List.of("--anon"), args[3]);
        }

        return Collections.emptyList();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Returns {@code true} and notifies the player if they are still within their
     * bounty-action cooldown window.  Returns {@code false} (and does nothing) if
     * the cooldown is disabled or has expired.
     */
    private boolean isOnCooldown(Player player) {
        int cooldownSeconds = plugin.getConfig().getInt("bounty-cooldown-seconds", 60);
        if (cooldownSeconds <= 0) {
            return false;
        }
        Long last = cooldowns.get(player.getUniqueId());
        if (last == null) {
            return false;
        }
        long elapsed = (System.currentTimeMillis() - last) / 1000L;
        if (elapsed < cooldownSeconds) {
            long remaining = cooldownSeconds - elapsed;
            player.sendMessage(PREFIX + ChatColor.RED + "You must wait "
                    + ChatColor.YELLOW + remaining + ChatColor.RED
                    + " more second" + (remaining == 1 ? "" : "s")
                    + " before placing or removing a bounty.");
            return true;
        }
        // Cooldown has expired — prune the entry to avoid unbounded map growth
        cooldowns.remove(player.getUniqueId());
        return false;
    }

    /**
     * Records the current time as the player's last bounty action timestamp.
     * Call this immediately after a successful mutating bounty operation.
     */
    private void stampCooldown(Player player) {
        int cooldownSeconds = plugin.getConfig().getInt("bounty-cooldown-seconds", 60);
        if (cooldownSeconds > 0) {
            cooldowns.put(player.getUniqueId(), System.currentTimeMillis());
        }
    }

    /**
     * Returns a filtered list of {@code candidates} whose entries start with {@code input}
     * (case-insensitive).
     */
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
     * Resolves a UUID to a player name, preferring online players then falling back to
     * the offline player cache. Returns the UUID string if no name is available.
     */
    private String resolvePlayerName(java.util.UUID uuid) {
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) return online.getName();
        org.bukkit.OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
        return op.getName() != null ? op.getName() : uuid.toString();
    }

    /**
     * Collects the set of distinct target UUIDs present in all stored bounties.
     * Used for name-based offline player lookups in /bounty list.
     */
    private java.util.Set<java.util.UUID> collectDistinctTargetUUIDs() {
        java.util.Set<java.util.UUID> uuids = new java.util.HashSet<>();
        for (Bounty b : bountyManager.getAllBounties()) {
            uuids.add(b.getTargetUUID());
        }
        return uuids;
    }
}
