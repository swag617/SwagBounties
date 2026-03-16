package com.swag.swagbounties.command;

import com.swag.swagbounties.SwagBounties;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Handles the /bounties command, which opens the paginated bounty list GUI.
 *
 * <p>A fresh {@link com.swag.swagbounties.gui.BountiesGUI} is built via
 * {@link SwagBounties#rebuildBountiesGUI()} each time the command is run so
 * that the viewer always sees up-to-date data. The listener holds a reference
 * to the plugin and reads the current GUI instance dynamically, so no
 * re-registration is needed after a rebuild.</p>
 */
public final class BountiesCommand implements CommandExecutor {

    private static final String PREFIX = ChatColor.RED + "[SwagBounties] " + ChatColor.RESET;

    private final SwagBounties plugin;

    public BountiesCommand(SwagBounties plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Only players can open the bounties GUI.");
            return true;
        }

        // Rebuild so the GUI always reflects the latest state
        plugin.rebuildBountiesGUI();
        plugin.getBountiesGUI().open(player, 0);
        return true;
    }
}
