package com.swag.swagbounties.listener;

import com.swag.swagbounties.SwagBounties;
import com.swag.swagbounties.bounty.Bounty;
import com.swag.swagbounties.bounty.BountyManager;
import com.swag.swagbounties.discord.DiscordWebhook;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;

import java.net.InetAddress;
import java.util.List;

/**
 * Handles bounty-related game events: death payouts and login notifications.
 */
public class BountyListener implements Listener {

    private final SwagBounties plugin;

    public BountyListener(SwagBounties plugin) {
        this.plugin = plugin;
    }

    // -------------------------------------------------------------------------
    // Login notification
    // -------------------------------------------------------------------------

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        BountyManager bountyManager = plugin.getBountyManager();
        double total = bountyManager.getTotalReward(player.getUniqueId());
        if (total <= 0) {
            return;
        }
        int count = bountyManager.getBounties(player.getUniqueId()).size();
        player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                "&c[SwagBounties] &eWarning: &cYou have &e" + count
                        + "&c active " + (count == 1 ? "bounty" : "bounties")
                        + " totalling &e$" + String.format("%.2f", total)
                        + "&c on your head!"));
    }

    // -------------------------------------------------------------------------
    // Death payout
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();

        if (killer == null || killer.getUniqueId().equals(victim.getUniqueId())) {
            return;
        }

        BountyManager bountyManager = plugin.getBountyManager();
        List<Bounty> snapshot = bountyManager.getBounties(victim.getUniqueId());

        if (snapshot.isEmpty()) {
            return;
        }

        // Same-IP exploit prevention. getAddress() on InetSocketAddress can itself
        // return null for unresolved hosts, so we guard both levels of unwrapping.
        java.net.InetSocketAddress killerSock = killer.getAddress();
        java.net.InetSocketAddress victimSock = victim.getAddress();
        InetAddress killerAddr = killerSock != null ? killerSock.getAddress() : null;
        InetAddress victimAddr = victimSock != null ? victimSock.getAddress() : null;

        if (killerAddr != null && victimAddr != null
                && killerAddr.getHostAddress().equals(victimAddr.getHostAddress())) {
            killer.sendMessage(ChatColor.RED
                    + "[SwagBounties] You cannot claim a bounty on a player from the same IP.");
            return;
        }

        double totalReward = bountyManager.getTotalReward(victim.getUniqueId());

        for (Bounty bounty : snapshot) {
            bountyManager.removeBounty(victim.getUniqueId(), bounty.getCreatorUUID());
        }

        Economy economy = plugin.getEconomy();
        economy.depositPlayer(killer, totalReward);

        bountyManager.saveToDisk();
        SwagBounties.getInstance().rebuildBountiesGUI();

        String template = plugin.getConfig().getString(
                "claim-message",
                "&c[SwagBounties] &e%killer% &fhas claimed a &a$%amount% &fbounty on &e%target%&f!");
        Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', template
                .replace("%killer%", killer.getName())
                .replace("%target%", victim.getName())
                .replace("%amount%", String.format("%.2f", totalReward))));

        String webhookUrl = plugin.getConfig().getString("discord-webhook-url", "");
        double threshold = plugin.getConfig().getDouble("discord-notify-threshold", 500.0);
        if (!webhookUrl.isEmpty() && totalReward >= threshold) {
            String discordMsg = plugin.getConfig()
                    .getString("discord-claim-message",
                            "⚔️ **%killer%** claimed a **$%amount%** bounty on **%target%**!")
                    .replace("%killer%", killer.getName())
                    .replace("%target%", victim.getName())
                    .replace("%amount%", String.format("%.2f", totalReward));
            DiscordWebhook.sendAsync(webhookUrl, discordMsg);
        }
    }
}
