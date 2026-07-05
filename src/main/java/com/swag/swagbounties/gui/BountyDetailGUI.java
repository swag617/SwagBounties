package com.swag.swagbounties.gui;

import com.swag.swagbounties.SwagBounties;
import com.swag.swagbounties.bounty.Bounty;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Single-page 54-slot GUI showing every individual bounty placed on one target player.
 *
 * <p>One {@link Material#BOOK} item is placed per bounty (up to 45).
 * Slot 49 is the back button; remaining nav slots are black glass fillers.</p>
 */
public final class BountyDetailGUI {

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());
    private static final int CONTENT_SLOTS = 45;
    private static final int INV_SIZE      = CONTENT_SLOTS + 9;
    private static final int SLOT_BACK     = CONTENT_SLOTS + 4;

    private final SwagBounties plugin;

    public BountyDetailGUI(SwagBounties plugin) {
        this.plugin = plugin;
    }

    public void open(Player viewer, UUID targetUUID) {
        org.bukkit.OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(targetUUID);
        String targetName = offlinePlayer.getName();
        if (targetName == null) {
            targetName = "Unknown";
        }

        String title = ChatColor.DARK_RED + "Bounties on " + ChatColor.YELLOW + targetName;
        Inventory inv = Bukkit.createInventory(null, INV_SIZE, title);

        List<Bounty> bounties = plugin.getBountyManager().getBounties(targetUUID);

        boolean truncated = bounties.size() > CONTENT_SLOTS;
        // Reserve the last content slot for a "+N more" indicator when truncated
        int limit = truncated ? CONTENT_SLOTS - 1 : bounties.size();
        for (int i = 0; i < limit; i++) {
            inv.setItem(i, buildBountyBook(bounties.get(i)));
        }
        if (truncated) {
            inv.setItem(limit, buildMoreIndicator(bounties.size() - limit));
        }

        ItemStack filler = makeFiller();
        for (int s = CONTENT_SLOTS; s < INV_SIZE; s++) {
            inv.setItem(s, filler);
        }
        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta backMeta = back.getItemMeta();
        if (backMeta != null) {
            backMeta.setDisplayName(ChatColor.RED + "\u2190 Back to List");
            back.setItemMeta(backMeta);
        }
        inv.setItem(SLOT_BACK, back);

        viewer.openInventory(inv);
    }

    private ItemStack buildBountyBook(Bounty bounty) {
        ItemStack book = new ItemStack(Material.BOOK);
        ItemMeta meta = book.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(ChatColor.GREEN + "$" + String.format("%.2f", bounty.getReward()));

            String creatorDisplay;
            if (bounty.isAnonymous()) {
                creatorDisplay = ChatColor.GRAY + "Anonymous";
            } else {
                org.bukkit.OfflinePlayer creator = Bukkit.getOfflinePlayer(bounty.getCreatorUUID());
                String creatorName = creator.getName();
                if (creatorName == null) {
                    creatorName = bounty.getCreatorUUID().toString();
                }
                creatorDisplay = ChatColor.YELLOW + creatorName;
            }

            String formattedDate = DATE_FORMAT.format(Instant.ofEpochMilli(bounty.getPlacedAt()));

            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "By: " + creatorDisplay);
            lore.add(ChatColor.GRAY + "Placed: " + ChatColor.AQUA + formattedDate);
            meta.setLore(lore);

            book.setItemMeta(meta);
        }

        return book;
    }

    /** Built when a target has more individual bounties than fit on one page. */
    private ItemStack buildMoreIndicator(int remaining) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + "+" + remaining + " more not shown");
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
