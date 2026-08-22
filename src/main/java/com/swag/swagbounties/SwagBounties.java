package com.swag.swagbounties;

import com.swag.swagbounties.bounty.BountyManager;
import com.swag.swagbounties.bounty.ClaimTracker;
import com.swag.swagbounties.command.AdminCommand;
import com.swag.swagbounties.command.BountiesCommand;
import com.swag.swagbounties.command.BountyCommand;
import com.swag.swagbounties.gui.BountiesGUI;
import com.swag.swagbounties.gui.TopBountiesGUI;
import com.swag.swagbounties.listener.BountyListener;
import com.swag.swagbounties.listener.GUIListener;
import com.swag.swagbounties.placeholder.SwagBountiesExpansion;
import com.swag.swagbounties.task.ExpiryTask;
// MIGRATED: Vault economy hook replaced by SwagAPI IEconomyService (see hookSwagAPI() / ecoService field below)
// import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public final class SwagBounties extends JavaPlugin {

    private static SwagBounties instance;

    private BountyManager bountyManager;
    private ClaimTracker claimTracker;
    // MIGRATED: replaced by SwagAPI IEconomyService — see ecoService field and hookSwagAPI()
    // private Economy economy;
    private BountiesGUI bountiesGUI;
    private TopBountiesGUI topBountiesGUI;

    // ── SwagAPI service references ──────────────────────────────────────────────
    private com.SwagDev.SwagAPI.api.IDatabaseService dbService;
    private com.SwagDev.SwagAPI.api.IEconomyService  ecoService;
    private com.SwagDev.SwagAPI.api.IEventBusService busService;
    private com.SwagDev.SwagAPI.api.IPrefixService   prefixService;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        // Step 1 — Hook SwagAPI FIRST, before any manager initialization
        if (!hookSwagAPI()) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        bountyManager = new BountyManager(new File(getDataFolder(), "bounties.yml"));
        bountyManager.loadFromDisk();

        claimTracker = new ClaimTracker(new File(getDataFolder(), "claims.yml"));
        claimTracker.loadFromDisk();

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
        topBountiesGUI = new TopBountiesGUI(this);
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
        if (claimTracker != null) {
            try {
                claimTracker.saveToDisk();
            } catch (RuntimeException e) {
                getLogger().severe("Failed to save claims.yml on shutdown: " + e.getMessage());
            }
        }
        getLogger().info("SwagBounties disabled.");
    }

    // MIGRATED: replaced by SwagAPI IEconomyService, hooked in hookSwagAPI() below
    // private boolean setupEconomy() {
    //     if (getServer().getPluginManager().getPlugin("Vault") == null) {
    //         return false;
    //     }
    //     RegisteredServiceProvider<Economy> rsp =
    //             getServer().getServicesManager().getRegistration(Economy.class);
    //     if (rsp == null) {
    //         return false;
    //     }
    //     economy = rsp.getProvider();
    //     return economy != null;
    // }

    /**
     * Hooks all SwagAPI services this plugin needs. IDatabaseService is a hard
     * requirement — if it's missing, SwagAPI isn't loaded and the plugin disables itself.
     * BountyManager continues to persist to bounties.yml directly; dbService is hooked
     * for suite-wide consistency and future use but is not yet used for storage.
     */
    private boolean hookSwagAPI() {
        org.bukkit.plugin.ServicesManager sm = getServer().getServicesManager();

        // ── Database — hard required by every plugin ─────────────────────────────
        RegisteredServiceProvider<com.SwagDev.SwagAPI.api.IDatabaseService> dbProv =
                sm.getRegistration(com.SwagDev.SwagAPI.api.IDatabaseService.class);
        if (dbProv == null) {
            getLogger().severe("SwagAPI IDatabaseService not found! Is SwagAPI loaded? Disabling.");
            return false;
        }
        dbService = dbProv.getProvider();
        getLogger().info("Hooked SwagAPI IDatabaseService.");

        // ── Economy ────────────────────────────────────────────────────────────
        RegisteredServiceProvider<com.SwagDev.SwagAPI.api.IEconomyService> ecoProv =
                sm.getRegistration(com.SwagDev.SwagAPI.api.IEconomyService.class);
        if (ecoProv != null) {
            ecoService = ecoProv.getProvider();
            if (ecoService.isEnabled()) {
                getLogger().info("Hooked SwagAPI IEconomyService (" + ecoService.getCurrencyName() + ").");
            } else {
                getLogger().warning("SwagAPI IEconomyService not available — economy features disabled.");
            }
        } else {
            getLogger().warning("SwagAPI IEconomyService not registered — economy features disabled.");
        }

        // ── Event bus — used to publish swagbounties:bounty_claimed ──────────────
        RegisteredServiceProvider<com.SwagDev.SwagAPI.api.IEventBusService> busProv =
                sm.getRegistration(com.SwagDev.SwagAPI.api.IEventBusService.class);
        if (busProv != null) {
            busService = busProv.getProvider();
            getLogger().info("Hooked SwagAPI IEventBusService.");
        }

        // ── Prefix override — lets an admin override our chat prefix from the panel ──
        RegisteredServiceProvider<com.SwagDev.SwagAPI.api.IPrefixService> prefixProv =
                sm.getRegistration(com.SwagDev.SwagAPI.api.IPrefixService.class);
        if (prefixProv != null) {
            prefixService = prefixProv.getProvider();
            getLogger().info("Hooked SwagAPI IPrefixService.");
        }

        getLogger().info("SwagAPI hook complete.");
        return true;
    }

    /**
     * Resolves this plugin's self-identifying chat prefix, honoring any admin
     * override configured via SwagAPI's {@code IPrefixService} (either a global
     * override for every plugin, or one scoped to just "SwagBounties"). Falls
     * back to the caller-supplied literal — this plugin's own historically
     * hardcoded prefix — when no service is hooked or nothing is configured.
     */
    private static final java.util.Map<String, String> MM_TAG_TO_LEGACY = java.util.Map.ofEntries(
            java.util.Map.entry("black", "§0"), java.util.Map.entry("dark_blue", "§1"), java.util.Map.entry("dark_green", "§2"),
            java.util.Map.entry("dark_aqua", "§3"), java.util.Map.entry("dark_red", "§4"), java.util.Map.entry("dark_purple", "§5"),
            java.util.Map.entry("gold", "§6"), java.util.Map.entry("gray", "§7"), java.util.Map.entry("grey", "§7"),
            java.util.Map.entry("dark_gray", "§8"), java.util.Map.entry("dark_grey", "§8"), java.util.Map.entry("blue", "§9"),
            java.util.Map.entry("green", "§a"), java.util.Map.entry("aqua", "§b"), java.util.Map.entry("red", "§c"),
            java.util.Map.entry("light_purple", "§d"), java.util.Map.entry("yellow", "§e"), java.util.Map.entry("white", "§f"),
            java.util.Map.entry("obfuscated", "§k"), java.util.Map.entry("bold", "§l"), java.util.Map.entry("strikethrough", "§m"),
            java.util.Map.entry("underlined", "§n"), java.util.Map.entry("italic", "§o"), java.util.Map.entry("reset", "§r"));

    /**
     * Renders a stored prefix value from SwagAPI's IPrefixService — which may be MiniMessage
     * tags (admin-typed via the web panel), legacy {@code &}/{@code §} codes (this plugin's own
     * fallback constants), or a mix — into a legacy §-coded string safe for this plugin's
     * ChatColor/sendMessage(String) pipeline. This plugin compiles against spigot-api (no
     * adventure-text-minimessage on the classpath, unlike the Paper-API-based Swag plugins), so
     * this is a lightweight regex-only converter rather than a real MiniMessage parse — no tag
     * nesting support, but adequate for a short prefix string that realistically has at most a
     * color tag and maybe one formatting tag. Named colors/formatting map to their legacy §
     * code; {@code <#rrggbb>} hex tags use BungeeChat's ChatColor.of (already on spigot-api's
     * classpath); any other/unrecognized tag is just stripped.
     */
    private static String toLegacyPrefix(String raw) {
        if (raw == null || raw.isEmpty()) return raw;
        String result = org.bukkit.ChatColor.translateAlternateColorCodes('&', raw);

        java.util.regex.Matcher hex = java.util.regex.Pattern.compile("(?i)<#([0-9a-f]{6})>").matcher(result);
        StringBuilder sb = new StringBuilder();
        while (hex.find()) {
            String legacyHex = net.md_5.bungee.api.ChatColor.of("#" + hex.group(1)).toString();
            hex.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(legacyHex));
        }
        hex.appendTail(sb);
        result = sb.toString();

        java.util.regex.Matcher tag = java.util.regex.Pattern.compile("</?([a-zA-Z_]+)>").matcher(result);
        StringBuilder sb2 = new StringBuilder();
        while (tag.find()) {
            String legacy = MM_TAG_TO_LEGACY.get(tag.group(1).toLowerCase());
            tag.appendReplacement(sb2, legacy != null ? java.util.regex.Matcher.quoteReplacement(legacy) : "");
        }
        tag.appendTail(sb2);
        return sb2.toString();
    }

    public String getPrefix(String fallback) {
        return toLegacyPrefix(prefixService != null ? prefixService.getPrefix("SwagBounties", fallback) : fallback);
    }

    public static SwagBounties getInstance() {
        return instance;
    }

    public BountyManager getBountyManager() {
        return bountyManager;
    }

    public ClaimTracker getClaimTracker() {
        return claimTracker;
    }

    public com.SwagDev.SwagAPI.api.IDatabaseService getDbService() {
        return dbService;
    }

    public com.SwagDev.SwagAPI.api.IEconomyService getEcoService() {
        return ecoService;
    }

    public com.SwagDev.SwagAPI.api.IEventBusService getBusService() {
        return busService;
    }

    public BountiesGUI getBountiesGUI() {
        return bountiesGUI;
    }

    public TopBountiesGUI getTopBountiesGUI() {
        return topBountiesGUI;
    }

    public void rebuildBountiesGUI() {
        java.util.Map<java.util.UUID, Integer> previousPages = bountiesGUI != null
                ? bountiesGUI.snapshotPlayerPages()
                : java.util.Map.of();
        bountiesGUI = new BountiesGUI(this, previousPages);
    }
}
