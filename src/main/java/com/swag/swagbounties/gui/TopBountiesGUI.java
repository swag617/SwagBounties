package com.swag.swagbounties.gui;

import com.swag.swagbounties.SwagBounties;
import com.swag.swagbounties.bounty.Bounty;
import com.swag.swagbounties.bounty.BountyManager;
import com.swag.swagbounties.bounty.ClaimTracker;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Single-page leaderboard GUI for {@code /bounty top}, with two tabs: most-wanted
 * targets by total active reward, and most-prolific bounty hunters by claim count.
 *
 * <p>Unlike {@link BountiesGUI}, this GUI carries no per-viewer state between opens —
 * it is rebuilt fresh from live data every time it is opened or a tab is switched,
 * so there is nothing to preserve across a rebuild.</p>
 */
public final class TopBountiesGUI {

    public enum Tab { WANTED, HUNTERS }

    /** Pairs a leaderboard entry's subject UUID (target or hunter) with its display stack. */
    public record RankEntry(UUID subjectUUID, ItemStack stack) {}

    public static final int CONTENT_SLOTS   = 45;
    private static final int INV_SIZE       = CONTENT_SLOTS + 9;
    public static final int SLOT_WANTED_TAB  = CONTENT_SLOTS;
    public static final int SLOT_HUNTERS_TAB = CONTENT_SLOTS + 8;
    private static final int SLOT_INFO      = CONTENT_SLOTS + 4;
    private static final int MAX_ENTRIES    = 10;

    private final SwagBounties plugin;

    public TopBountiesGUI(SwagBounties plugin) {
        this.plugin = plugin;
    }

    public void open(Player viewer, Tab tab) {
        List<RankEntry> entries = tab == Tab.WANTED ? buildWantedEntries() : buildHunterEntries();

        String title = tab == Tab.WANTED
                ? ChatColor.DARK_RED + "Top Bounties" + ChatColor.GRAY + " - Most Wanted"
                : ChatColor.DARK_RED + "Top Bounties" + ChatColor.GRAY + " - Top Hunters";

        Inventory inv = Bukkit.createInventory(null, INV_SIZE, title);
        ItemStack filler = makeFiller();

        if (entries.isEmpty()) {
            ItemStack none = new ItemStack(Material.BARRIER);
            ItemMeta meta = none.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ChatColor.RED + (tab == Tab.WANTED
                        ? "No active bounties" : "No bounties have been claimed yet"));
                none.setItemMeta(meta);
            }
            inv.setItem(0, none);
            for (int s = 1; s < CONTENT_SLOTS; s++) {
                inv.setItem(s, filler);
            }
        } else {
            for (int i = 0; i < entries.size(); i++) {
                inv.setItem(i, entries.get(i).stack());
            }
            for (int s = entries.size(); s < CONTENT_SLOTS; s++) {
                inv.setItem(s, filler);
            }
        }

        for (int s = CONTENT_SLOTS; s < INV_SIZE; s++) {
            inv.setItem(s, filler);
        }

        inv.setItem(SLOT_WANTED_TAB, makeTabItem("Most Wanted", tab == Tab.WANTED));
        inv.setItem(SLOT_HUNTERS_TAB, makeTabItem("Top Hunters", tab == Tab.HUNTERS));

        ItemStack info = new ItemStack(Material.PAPER);
        ItemMeta infoMeta = info.getItemMeta();
        if (infoMeta != null) {
            infoMeta.setDisplayName(ChatColor.GOLD + (tab == Tab.WANTED ? "Most Wanted" : "Top Hunters"));
            info.setItemMeta(infoMeta);
        }
        inv.setItem(SLOT_INFO, info);

        viewer.openInventory(inv);
    }

    /**
     * Returns the {@link RankEntry} at the given content slot for the given tab, by
     * rebuilding the same live data used by {@link #open}. Used by the click handler
     * to resolve what was clicked — this GUI has no state to look up instead.
     */
    public RankEntry getEntry(Tab tab, int slot) {
        List<RankEntry> entries = tab == Tab.WANTED ? buildWantedEntries() : buildHunterEntries();
        if (slot < 0 || slot >= entries.size()) {
            return null;
        }
        return entries.get(slot);
    }

    // -------------------------------------------------------------------------
    // Data building
    // -------------------------------------------------------------------------

    private List<RankEntry> buildWantedEntries() {
        BountyManager bm = plugin.getBountyManager();
        List<Bounty> all = bm.getAllBounties();

        Map<UUID, Double> totals = new LinkedHashMap<>();
        for (Bounty b : all) {
            totals.merge(b.getTargetUUID(), b.getReward(), Double::sum);
        }

        List<Map.Entry<UUID, Double>> sorted = new ArrayList<>(totals.entrySet());
        sorted.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));

        int limit = Math.min(MAX_ENTRIES, sorted.size());
        List<RankEntry> entries = new ArrayList<>(limit);
        for (int i = 0; i < limit; i++) {
            UUID targetUUID = sorted.get(i).getKey();
            double total = sorted.get(i).getValue();
            int bountyCount = bm.getBounties(targetUUID).size();
            entries.add(new RankEntry(targetUUID, buildWantedItem(i + 1, targetUUID, total, bountyCount)));
        }
        return entries;
    }

    private List<RankEntry> buildHunterEntries() {
        List<ClaimTracker.HunterStat> top = plugin.getClaimTracker().getTopHunters(MAX_ENTRIES);
        List<RankEntry> entries = new ArrayList<>(top.size());
        for (int i = 0; i < top.size(); i++) {
            ClaimTracker.HunterStat stat = top.get(i);
            entries.add(new RankEntry(stat.hunterUUID(), buildHunterItem(i + 1, stat)));
        }
        return entries;
    }

    // -------------------------------------------------------------------------
    // Item factories
    // -------------------------------------------------------------------------

    private ItemStack buildWantedItem(int rank, UUID targetUUID, double totalReward, int bountyCount) {
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(targetUUID);
        String playerName = offlinePlayer.getName() != null ? offlinePlayer.getName() : "Unknown";

        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) skull.getItemMeta();
        if (meta != null) {
            meta.setOwningPlayer(offlinePlayer);
            meta.setDisplayName(rankColor(rank) + "#" + rank + " " + ChatColor.YELLOW + playerName);

            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Total Reward: " + ChatColor.GREEN + "$" + String.format("%.2f", totalReward));
            lore.add(ChatColor.GRAY + "Bounties: " + ChatColor.WHITE + bountyCount);
            lore.add(ChatColor.GRAY + "Click to view details");
            meta.setLore(lore);

            skull.setItemMeta(meta);
        }
        return skull;
    }

    private ItemStack buildHunterItem(int rank, ClaimTracker.HunterStat stat) {
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(stat.hunterUUID());
        String playerName = offlinePlayer.getName() != null ? offlinePlayer.getName() : "Unknown";

        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) skull.getItemMeta();
        if (meta != null) {
            meta.setOwningPlayer(offlinePlayer);
            meta.setDisplayName(rankColor(rank) + "#" + rank + " " + ChatColor.YELLOW + playerName);

            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Bounties Claimed: " + ChatColor.WHITE + stat.claimCount());
            lore.add(ChatColor.GRAY + "Total Earned: " + ChatColor.GREEN + "$" + String.format("%.2f", stat.totalEarned()));
            meta.setLore(lore);

            skull.setItemMeta(meta);
        }
        return skull;
    }

    private ChatColor rankColor(int rank) {
        return switch (rank) {
            case 1 -> ChatColor.GOLD;
            case 2 -> ChatColor.GRAY;
            case 3 -> ChatColor.RED;
            default -> ChatColor.WHITE;
        };
    }

    private ItemStack makeTabItem(String label, boolean active) {
        ItemStack item = new ItemStack(active ? Material.LIME_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName((active ? ChatColor.GREEN : ChatColor.GRAY) + label
                    + (active ? " (viewing)" : ""));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack makeFiller() {
        ItemStack glass = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = glass.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            glass.setItemMeta(meta);
        }
        return glass;
    }
}
