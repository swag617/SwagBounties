package com.swag.swagbounties.listener;

import com.swag.swagbounties.SwagBounties;
import com.swag.swagbounties.gui.BountiesGUI;
import com.swag.swagbounties.gui.BountyDetailGUI;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;

/**
 * Routes click and close events for the BountiesGUI and BountyDetailGUI.
 *
 * <p>Title-based detection is used because Bukkit does not provide a typed
 * InventoryHolder for inventories created via {@code Bukkit.createInventory(null, ...)}.
 * The listener holds a reference to the plugin (not directly to a {@link BountiesGUI}
 * instance) so that a rebuilt GUI is always picked up without re-registering the listener.</p>
 */
public final class GUIListener implements Listener {

    private static final String TITLE_MAIN   = ChatColor.DARK_RED + "SwagBounties";
    private static final String TITLE_DETAIL = ChatColor.DARK_RED + "Bounties on";

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

        if (!isMain && !isDetail) {
            return;
        }

        // Always cancel to prevent item theft from our custom inventory
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
        } else {
            handleDetailClick(viewer, slot);
        }
    }

    // -------------------------------------------------------------------------
    // Close handler
    // -------------------------------------------------------------------------

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        String title = event.getView().getTitle();
        if (!title.startsWith(TITLE_MAIN)) {
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
}
