package com.swag.swagbounties.gui;

import com.swag.swagbounties.SwagBounties;
import com.swag.swagbounties.bounty.Bounty;
import com.swag.swagbounties.bounty.BountyManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Paginated 54-slot GUI displaying one skull per unique bounty target.
 *
 * <p>Pages are pre-built once in the constructor. The instance is replaced via
 * {@link SwagBounties#rebuildBountiesGUI()} whenever the bounty data changes,
 * so viewers always see a consistent snapshot.</p>
 *
 * <p>Slots 0–44 are content slots (up to 45 skulls per page).
 * Slots 45–53 are the navigation row.</p>
 */
public final class BountiesGUI {

    /** Simple carrier pairing a target UUID with its pre-built display stack. */
    public record InventoryItem(UUID targetUUID, ItemStack stack) {}

    private static final int CONTENT_SLOTS  = 45;
    private static final int INV_SIZE       = CONTENT_SLOTS + 9;
    private static final int SLOT_PREV      = CONTENT_SLOTS;
    private static final int SLOT_INFO      = CONTENT_SLOTS + 4;
    private static final int SLOT_NEXT      = CONTENT_SLOTS + 8;

    private final SwagBounties plugin;
    private final BountyManager bountyManager;

    /** Current page index per viewer UUID. Cleaned up on inventory close. */
    private final Map<UUID, Integer> playerPage = new HashMap<>();

    /** Pre-built pages; each page contains up to {@value #CONTENT_SLOTS} entries. */
    private final List<List<InventoryItem>> pages;

    // -------------------------------------------------------------------------
    // Construction
    // -------------------------------------------------------------------------

    public BountiesGUI(SwagBounties plugin) {
        this.plugin = plugin;
        this.bountyManager = plugin.getBountyManager();
        this.pages = buildPages();
    }

    /**
     * Builds the full page list from the current bounty data.
     * Called exactly once per instance during construction.
     */
    private List<List<InventoryItem>> buildPages() {
        // Collect unique target UUIDs in stable insertion order
        LinkedHashSet<UUID> targets = new LinkedHashSet<>();
        for (Bounty b : bountyManager.getAllBounties()) {
            targets.add(b.getTargetUUID());
        }

        // Build one InventoryItem per target
        List<InventoryItem> items = new ArrayList<>(targets.size());
        for (UUID targetUUID : targets) {
            items.add(buildSkullItem(targetUUID));
        }

        // Partition into pages of CONTENT_SLOTS
        List<List<InventoryItem>> result = new ArrayList<>();
        if (items.isEmpty()) {
            // Always have at least one (possibly empty) page so open() doesn't crash
            result.add(new ArrayList<>());
            return result;
        }

        List<InventoryItem> current = new ArrayList<>();
        for (InventoryItem item : items) {
            current.add(item);
            if (current.size() == CONTENT_SLOTS) {
                result.add(current);
                current = new ArrayList<>();
            }
        }
        if (!current.isEmpty()) {
            result.add(current);
        }

        return result;
    }

    /**
     * Builds a PLAYER_HEAD ItemStack for the given target UUID.
     * Uses {@link SkullMeta#setOwningPlayer(org.bukkit.OfflinePlayer)} for
     * Spigot/Paper compatibility without requiring Paper-specific API.
     */
    private InventoryItem buildSkullItem(UUID targetUUID) {
        org.bukkit.OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(targetUUID);
        String playerName = offlinePlayer.getName();
        if (playerName == null) {
            playerName = "Unknown";
        }

        double totalReward  = bountyManager.getTotalReward(targetUUID);
        int    bountyCount  = bountyManager.getBounties(targetUUID).size();

        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta  = (SkullMeta) skull.getItemMeta();

        if (meta != null) {
            meta.setOwningPlayer(offlinePlayer);
            meta.setDisplayName(ChatColor.YELLOW + playerName);

            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Total Reward: " + ChatColor.GREEN
                    + "$" + String.format("%.2f", totalReward));
            lore.add(ChatColor.GRAY + "Bounties: " + ChatColor.WHITE + bountyCount);
            lore.add(ChatColor.GRAY + "Click to view details");
            meta.setLore(lore);

            skull.setItemMeta(meta);
        }

        return new InventoryItem(targetUUID, skull);
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Opens the GUI at the given page for the player.
     * Page index is clamped to [0, pages.size()-1].
     */
    public void open(Player player, int page) {
        int maxPage = pages.size() - 1;
        if (page < 0) page = 0;
        if (page > maxPage) page = maxPage;

        List<InventoryItem> pageItems = pages.get(page);
        int totalPages = pages.size();

        String title = ChatColor.DARK_RED + "SwagBounties "
                + ChatColor.GRAY + "[" + (page + 1) + "/" + totalPages + "]";

        Inventory inv = Bukkit.createInventory(null, INV_SIZE, title);

        if (pageItems.isEmpty()) {
            ItemStack noBounties = new ItemStack(Material.BARRIER);
            ItemMeta nMeta = noBounties.getItemMeta();
            if (nMeta != null) {
                nMeta.setDisplayName(ChatColor.RED + "No active bounties");
                noBounties.setItemMeta(nMeta);
            }
            inv.setItem(0, noBounties);
        } else {
            for (int i = 0; i < pageItems.size(); i++) {
                inv.setItem(i, pageItems.get(i).stack());
            }
        }

        ItemStack filler = makeFiller();
        for (int s = CONTENT_SLOTS; s < INV_SIZE; s++) {
            inv.setItem(s, filler);
        }

        if (page > 0) {
            inv.setItem(SLOT_PREV, makeNavArrow(ChatColor.RED + "\u2190 Previous"));
        }

        ItemStack info = new ItemStack(Material.PAPER);
        ItemMeta infoMeta = info.getItemMeta();
        if (infoMeta != null) {
            infoMeta.setDisplayName(ChatColor.GOLD + "Page " + (page + 1) + "/" + totalPages);
            info.setItemMeta(infoMeta);
        }
        inv.setItem(SLOT_INFO, info);

        if (page < maxPage) {
            inv.setItem(SLOT_NEXT, makeNavArrow(ChatColor.GREEN + "Next \u2192"));
        }

        playerPage.put(player.getUniqueId(), page);
        player.openInventory(inv);
    }

    /** Returns the current page this player has open, or 0 if not tracked. */
    public int getPlayerPage(UUID uuid) {
        return playerPage.getOrDefault(uuid, 0);
    }

    /** Removes page tracking for this player. Call on inventory close. */
    public void removePlayerPage(UUID uuid) {
        playerPage.remove(uuid);
    }

    /** Returns the total number of pages (always at least 1). */
    public int getPageCount() {
        return pages.size();
    }

    /**
     * Returns the {@link InventoryItem} at the given slot on the given page,
     * or {@code null} if the slot is out of bounds or the page does not exist.
     */
    public InventoryItem getItem(int page, int slot) {
        if (page < 0 || page >= pages.size()) return null;
        List<InventoryItem> pageItems = pages.get(page);
        if (slot < 0 || slot >= pageItems.size()) return null;
        return pageItems.get(slot);
    }

    // -------------------------------------------------------------------------
    // Item factories
    // -------------------------------------------------------------------------

    private ItemStack makeFiller() {
        ItemStack glass = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = glass.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            glass.setItemMeta(meta);
        }
        return glass;
    }

    private ItemStack makeNavArrow(String displayName) {
        ItemStack arrow = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = arrow.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(displayName);
            arrow.setItemMeta(meta);
        }
        return arrow;
    }
}
