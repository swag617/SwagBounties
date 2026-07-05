package com.swag.swagbounties.listener;

import com.swag.swagbounties.SwagBounties;
import com.swag.swagbounties.bounty.ClaimTracker;
import com.swag.swagbounties.gui.BountiesGUI;
import com.swag.swagbounties.gui.BountyDetailGUI;
import com.swag.swagbounties.gui.TopBountiesGUI;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;

import java.util.UUID;

/**
 * Routes click and close events for the BountiesGUI, BountyDetailGUI, and TopBountiesGUI.
 *
 * <p>Title-based detection is used because Bukkit does not provide a typed
 * InventoryHolder for inventories created via {@code Bukkit.createInventory(null, ...)}.
 * The listener holds a reference to the plugin (not directly to a {@link BountiesGUI}
 * instance) so that a rebuilt GUI is always picked up without re-registering the listener.</p>
 */
public final class GUIListener implements Listener {

    private static final String TITLE_MAIN   = ChatColor.DARK_RED + "SwagBounties";
    private static final String TITLE_DETAIL = ChatColor.DARK_RED + "Bounties on";
    private static final String TITLE_TOP    = ChatColor.DARK_RED + "Top Bounties";

    private static final int CONTENT_SLOTS = 45;
    private static final int SLOT_PREV     = CONTENT_SLOTS;
    private static final int SLOT_BACK     = CONTENT_SLOTS + 4;
    private static final int SLOT_NEXT     = CONTENT_SLOTS + 8;

    private final SwagBounties plugin;

    public GUIListener(SwagBounties plugin) {
        this.plugin = plugin;
    }

    // -------------------------------------------------------------------------
    // Click handler
    // -------------------------------------------------------------------------

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();

        boolean isMain   = title.startsWith(TITLE_MAIN);
        boolean isDetail = title.startsWith(TITLE_DETAIL);
        boolean isTop    = title.startsWith(TITLE_TOP);

        if (!isMain && !isDetail && !isTop) {
            return;
        }

        // Only cancel clicks in our own (top) inventory — clicks in the viewer's own
        // inventory (bottom half of the view) must be left alone so players can still
        // manage their inventory while the GUI is open.
        if (event.getClickedInventory() == null
                || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }

        // Cancel to prevent item theft from our custom inventory
        event.setCancelled(true);

        // Ignore empty slots
        if (event.getCurrentItem() == null
                || event.getCurrentItem().getType() == Material.AIR) {
            return;
        }

        HumanEntity human = event.getWhoClicked();
        if (!(human instanceof Player viewer)) {
            return;
        }

        int slot = event.getRawSlot();

        if (isMain) {
            handleMainClick(viewer, slot);
        } else if (isDetail) {
            handleDetailClick(viewer, slot);
        } else {
            handleTopClick(viewer, slot, title.contains("Top Hunters"));
        }
    }

    // -------------------------------------------------------------------------
    // Close handler
    // -------------------------------------------------------------------------

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        String title = event.getView().getTitle();

        // Clean up playerPage tracking when either the main list or the detail view is closed.
        // This covers the case where a player closes the detail GUI directly instead of using
        // the back button, preventing an unbounded map entry leak in BountiesGUI.
        if (!title.startsWith(TITLE_MAIN) && !title.startsWith(TITLE_DETAIL)) {
            return;
        }

        HumanEntity human = event.getPlayer();
        if (!(human instanceof Player player)) {
            return;
        }

        plugin.getBountiesGUI().removePlayerPage(player.getUniqueId());
    }

    // -------------------------------------------------------------------------
    // Internal routing
    // -------------------------------------------------------------------------

    private void handleMainClick(Player viewer, int slot) {
        BountiesGUI gui  = plugin.getBountiesGUI();
        int page         = gui.getPlayerPage(viewer.getUniqueId());
        int maxPage      = gui.getPageCount() - 1;

        if (slot == SLOT_PREV) {
            if (page > 0) {
                gui.open(viewer, page - 1);
            }
            return;
        }

        if (slot == SLOT_NEXT) {
            if (page < maxPage) {
                gui.open(viewer, page + 1);
            }
            return;
        }

        if (slot >= 0 && slot < CONTENT_SLOTS) {
            BountiesGUI.InventoryItem item = gui.getItem(page, slot);
            if (item != null) {
                new BountyDetailGUI(plugin).open(viewer, item.targetUUID());
            }
        }
    }

    private void handleDetailClick(Player viewer, int slot) {
        if (slot == SLOT_BACK) {
            BountiesGUI gui = plugin.getBountiesGUI();
            gui.open(viewer, gui.getPlayerPage(viewer.getUniqueId()));
        }
    }

    private void handleTopClick(Player viewer, int slot, boolean isHuntersTab) {
        TopBountiesGUI gui = plugin.getTopBountiesGUI();

        if (slot == TopBountiesGUI.SLOT_WANTED_TAB) {
            gui.open(viewer, TopBountiesGUI.Tab.WANTED);
            return;
        }
        if (slot == TopBountiesGUI.SLOT_HUNTERS_TAB) {
            gui.open(viewer, TopBountiesGUI.Tab.HUNTERS);
            return;
        }
        if (slot < 0 || slot >= TopBountiesGUI.CONTENT_SLOTS) {
            return;
        }

        TopBountiesGUI.Tab tab = isHuntersTab ? TopBountiesGUI.Tab.HUNTERS : TopBountiesGUI.Tab.WANTED;
        TopBountiesGUI.RankEntry entry = gui.getEntry(tab, slot);
        if (entry == null) {
            return;
        }

        if (tab == TopBountiesGUI.Tab.WANTED) {
            new BountyDetailGUI(plugin).open(viewer, entry.subjectUUID());
        } else {
            ClaimTracker.HunterStat stat = plugin.getClaimTracker().getHunterStat(entry.subjectUUID());
            if (stat != null) {
                String name = resolvePlayerName(entry.subjectUUID());
                viewer.sendMessage(ChatColor.GOLD + name + ChatColor.GRAY + ": " + ChatColor.WHITE + stat.claimCount()
                        + ChatColor.GRAY + " bounties claimed, " + ChatColor.GREEN + "$"
                        + String.format("%.2f", stat.totalEarned()) + ChatColor.GRAY + " earned.");
            }
        }
    }

    /** Resolves a UUID to a player name, preferring an online player then the offline cache. */
    private String resolvePlayerName(UUID uuid) {
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            return online.getName();
        }
        org.bukkit.OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
        return op.getName() != null ? op.getName() : uuid.toString();
    }
}
