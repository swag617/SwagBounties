package com.swag.swagbounties.task;

import com.swag.swagbounties.SwagBounties;
import com.swag.swagbounties.bounty.Bounty;
import com.swag.swagbounties.discord.DiscordWebhook;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Scans active bounties every 5 minutes and expires any older than
 * {@code bounty-expiry-days}, issuing a tax-reduced refund to each creator.
 * Set {@code bounty-expiry-days: 0} to disable expiry entirely.
 */
public final class ExpiryTask extends BukkitRunnable {

    /** UUID used for admin-placed bounties — refunding to this UUID is meaningless. */
    private static final UUID SERVER_UUID = new UUID(0, 0);

    private final SwagBounties plugin;

    public ExpiryTask(SwagBounties plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        int expiryDays = plugin.getConfig().getInt("bounty-expiry-days", 7);
        if (expiryDays <= 0) {
            return; // expiry disabled
        }

        long expiryMillis = expiryDays * 24L * 60L * 60L * 1000L;
        long now = System.currentTimeMillis();

        double expiryRefundTax = plugin.getConfig().getDouble("expiry-refund-tax", 10.0);
        Economy economy = plugin.getEconomy();
        String webhookUrl = plugin.getConfig().getString("discord-webhook-url", "");
        double threshold = plugin.getConfig().getDouble("discord-notify-threshold", 500.0);

        List<Bounty> snapshot = new ArrayList<>(plugin.getBountyManager().getAllBounties());
        int count = 0;

        for (Bounty bounty : snapshot) {
            if (now - bounty.getPlacedAt() < expiryMillis) {
                continue;
            }

            boolean removed = plugin.getBountyManager()
                    .removeBounty(bounty.getTargetUUID(), bounty.getCreatorUUID());
            if (!removed) {
                continue;
            }

            double refund = bounty.getReward() * (1.0 - expiryRefundTax / 100.0);
            String targetName = resolveTargetName(bounty);

            // Admin-placed bounties use SERVER_UUID as creator; skip economy deposit for those.
            if (!bounty.getCreatorUUID().equals(SERVER_UUID)) {
                OfflinePlayer creator = Bukkit.getOfflinePlayer(bounty.getCreatorUUID());
                economy.depositPlayer(creator, refund);

                Player onlineCreator = Bukkit.getPlayer(bounty.getCreatorUUID());
                if (onlineCreator != null) {
                    onlineCreator.sendMessage(ChatColor.translateAlternateColorCodes('&',
                            "&c[SwagBounties] &fYour bounty on &e" + targetName
                            + " &fhas expired. &a$" + String.format("%.2f", refund) + " &frefunded."));
                }
            }

            if (!webhookUrl.isEmpty() && refund >= threshold) {
                String discordMsg = plugin.getConfig()
                        .getString("discord-expire-message",
                                "⏰ A **$%amount%** bounty on **%target%** has expired and was refunded.")
                        .replace("%amount%", String.format("%.2f", refund))
                        .replace("%target%", targetName);
                DiscordWebhook.sendAsync(webhookUrl, discordMsg);
            }

            count++;
        }

        if (count > 0) {
            plugin.getBountyManager().saveToDisk();
            SwagBounties.getInstance().rebuildBountiesGUI();
            plugin.getLogger().info("Expired " + count + " bounty/bounties and issued refunds.");
        }
    }

    private String resolveTargetName(Bounty bounty) {
        Player online = Bukkit.getPlayer(bounty.getTargetUUID());
        if (online != null) {
            return online.getName();
        }
        OfflinePlayer op = Bukkit.getOfflinePlayer(bounty.getTargetUUID());
        return op.getName() != null ? op.getName() : bounty.getTargetUUID().toString();
    }
}
