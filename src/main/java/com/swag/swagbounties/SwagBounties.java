package com.swag.swagbounties;

import com.swag.swagbounties.bounty.BountyManager;
import com.swag.swagbounties.command.AdminCommand;
import com.swag.swagbounties.command.BountiesCommand;
import com.swag.swagbounties.command.BountyCommand;
import com.swag.swagbounties.gui.BountiesGUI;
import com.swag.swagbounties.listener.BountyListener;
import com.swag.swagbounties.listener.GUIListener;
import com.swag.swagbounties.placeholder.SwagBountiesExpansion;
import com.swag.swagbounties.task.ExpiryTask;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public final class SwagBounties extends JavaPlugin {

    private static SwagBounties instance;

    private BountyManager bountyManager;
    private Economy economy;
    private BountiesGUI bountiesGUI;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        if (!setupEconomy()) {
            getLogger().severe("Vault economy provider not found. Disabling SwagBounties.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        bountyManager = new BountyManager(new File(getDataFolder(), "bounties.yml"));
        bountyManager.loadFromDisk();

        BountyCommand bountyCommand = new BountyCommand(this);
        PluginCommand command = getCommand("bounty");
        if (command != null) {
            command.setExecutor(bountyCommand);
            command.setTabCompleter(bountyCommand);
        } else {
            getLogger().severe("'bounty' command missing from plugin.yml.");
        }

        getServer().getPluginManager().registerEvents(new BountyListener(this), this);

        bountiesGUI = new BountiesGUI(this);
        getServer().getPluginManager().registerEvents(new GUIListener(this), this);

        BountiesCommand bountiesCmd = new BountiesCommand(this);
        PluginCommand bountiesCommand = getCommand("bounties");
        if (bountiesCommand != null) {
            bountiesCommand.setExecutor(bountiesCmd);
        } else {
            getLogger().severe("'bounties' command missing from plugin.yml.");
        }

        AdminCommand adminCmd = new AdminCommand(this);
        PluginCommand adminCommand = getCommand("bountyadmin");
        if (adminCommand != null) {
            adminCommand.setExecutor(adminCmd);
            adminCommand.setTabCompleter(adminCmd);
        } else {
            getLogger().severe("'bountyadmin' command missing from plugin.yml.");
        }

        new ExpiryTask(this).runTaskTimer(this, 20L * 60 * 5, 20L * 60 * 5);

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new SwagBountiesExpansion(this).register();
            getLogger().info("PlaceholderAPI expansion registered.");
        }

        getLogger().info("SwagBounties enabled.");
    }

    @Override
    public void onDisable() {
        if (bountyManager != null) {
            try {
                bountyManager.saveToDisk();
            } catch (RuntimeException e) {
                getLogger().severe("Failed to save bounties.yml on shutdown: " + e.getMessage());
            }
        }
        getLogger().info("SwagBounties disabled.");
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp =
                getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        economy = rsp.getProvider();
        return economy != null;
    }

    public static SwagBounties getInstance() {
        return instance;
    }

    public BountyManager getBountyManager() {
        return bountyManager;
    }

    public Economy getEconomy() {
        return economy;
    }

    public BountiesGUI getBountiesGUI() {
        return bountiesGUI;
    }

    public void rebuildBountiesGUI() {
        bountiesGUI = new BountiesGUI(this);
    }
}
