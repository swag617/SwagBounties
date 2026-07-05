package com.swag.swagbounties.placeholder;

import com.swag.swagbounties.SwagBounties;
import com.swag.swagbounties.bounty.Bounty;
import com.swag.swagbounties.bounty.ClaimTracker;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * PlaceholderAPI expansion that exposes bounty data through the
 * {@code %swagbounties_*%} namespace.
 *
 * <p>Supported placeholders:</p>
 * <ul>
 *   <li>{@code %swagbounties_top_reward%} — highest total reward across all targets</li>
 *   <li>{@code %swagbounties_top_target%} — name of the most-wanted player</li>
 *   <li>{@code %swagbounties_top_hunter%} — most prolific bounty claimer's name</li>
 *   <li>{@code %swagbounties_top_hunter_claims%} — that hunter's total claim count</li>
 *   <li>{@code %swagbounties_top_hunter_earnings%} — that hunter's total earnings</li>
 *   <li>{@code %swagbounties_total_bounties%} — count of all active bounties</li>
 *   <li>{@code %swagbounties_bounty_<playerName>%} — total reward on the named player</li>
 *   <li>{@code %swagbounties_has_bounty_<playerName>%} — "yes" or "no"</li>
 *   <li>{@code %swagbounties_hunter_claims_<playerName>%} — that player's total bounty claims</li>
 *   <li>{@code %swagbounties_hunter_earnings_<playerName>%} — that player's total bounty earnings</li>
 * </ul>
 */
public final class SwagBountiesExpansion extends PlaceholderExpansion {

    private final SwagBounties plugin;

    public SwagBountiesExpansion(SwagBounties plugin) {
        this.plugin = plugin;
    }

    // -------------------------------------------------------------------------
    // PlaceholderExpansion contract
    // -------------------------------------------------------------------------

    @Override
    public @NotNull String getIdentifier() {
        return "swagbounties";
    }

    @Override
    public @NotNull String getAuthor() {
        return "SwagTeam";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    /**
     * Returning {@code true} allows the expansion to remain registered even
     * after the parent plugin is reloaded, preventing duplicate registrations.
     */
    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    // -------------------------------------------------------------------------
    // Placeholder resolution
    // -------------------------------------------------------------------------

    /**
     * Handles all {@code %swagbounties_*%} placeholder requests.
     *
     * <p>The {@code player} parameter may be null (PAPI allows offline-safe
     * expansions). All data here comes purely from {@link com.swag.swagbounties.bounty.BountyManager}
     * so no player context is required.</p>
     */
    @Override
    public @Nullable String onPlaceholderRequest(@Nullable Player player, @NotNull String params) {
        List<Bounty> all = plugin.getBountyManager().getAllBounties();

        switch (params.toLowerCase()) {
            case "top_reward" -> {
                TopTarget top = findTopTarget(all);
                return top == null ? "None" : String.format("$%.2f", top.totalReward);
            }
            case "top_target" -> {
                TopTarget top = findTopTarget(all);
                return top == null ? "None" : top.name;
            }
            case "top_hunter" -> {
                ClaimTracker.HunterStat top = plugin.getClaimTracker().getTopHunter();
                return top == null ? "None" : resolveNameByUUID(top.hunterUUID());
            }
            case "top_hunter_claims" -> {
                ClaimTracker.HunterStat top = plugin.getClaimTracker().getTopHunter();
                return top == null ? "0" : String.valueOf(top.claimCount());
            }
            case "top_hunter_earnings" -> {
                ClaimTracker.HunterStat top = plugin.getClaimTracker().getTopHunter();
                return top == null ? "$0.00" : String.format("$%.2f", top.totalEarned());
            }
            case "total_bounties" -> {
                return String.valueOf(all.size());
            }
            default -> {
                // Dynamic: bounty_<playerName>, has_bounty_<playerName>,
                // hunter_claims_<playerName>, or hunter_earnings_<playerName>
                String lower = params.toLowerCase();
                if (lower.startsWith("bounty_")) {
                    String targetName = params.substring("bounty_".length());
                    double total = resolveRewardByName(all, targetName);
                    return String.format("$%.2f", total);
                }
                if (lower.startsWith("has_bounty_")) {
                    String targetName = params.substring("has_bounty_".length());
                    double total = resolveRewardByName(all, targetName);
                    return total > 0.0 ? "yes" : "no";
                }
                if (lower.startsWith("hunter_claims_")) {
                    String hunterName = params.substring("hunter_claims_".length());
                    ClaimTracker.HunterStat stat = resolveHunterStatByName(hunterName);
                    return String.valueOf(stat == null ? 0 : stat.claimCount());
                }
                if (lower.startsWith("hunter_earnings_")) {
                    String hunterName = params.substring("hunter_earnings_".length());
                    ClaimTracker.HunterStat stat = resolveHunterStatByName(hunterName);
                    return String.format("$%.2f", stat == null ? 0.0 : stat.totalEarned());
                }
                return null; // unknown placeholder — let PAPI handle gracefully
            }
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Aggregates total rewards per target UUID and returns the highest-valued entry. */
    private @Nullable TopTarget findTopTarget(List<Bounty> all) {
        if (all.isEmpty()) {
            return null;
        }

        // Sum rewards per target UUID.
        Map<UUID, Double> totals = new HashMap<>();
        for (Bounty b : all) {
            totals.merge(b.getTargetUUID(), b.getReward(), Double::sum);
        }

        UUID topUUID = null;
        double topReward = -1.0;
        for (Map.Entry<UUID, Double> entry : totals.entrySet()) {
            if (entry.getValue() > topReward) {
                topReward = entry.getValue();
                topUUID = entry.getKey();
            }
        }

        if (topUUID == null) {
            return null;
        }

        String name = resolveNameByUUID(topUUID);
        return new TopTarget(name, topReward);
    }

    /**
     * Sums all bounty rewards on targets whose resolved name matches
     * {@code targetName} (case-insensitive).
     */
    private double resolveRewardByName(List<Bounty> all, String targetName) {
        double total = 0.0;
        // Collect distinct target UUIDs first to avoid redundant name resolutions.
        Map<UUID, String> nameCache = new HashMap<>();
        for (Bounty b : all) {
            UUID uuid = b.getTargetUUID();
            if (!nameCache.containsKey(uuid)) {
                nameCache.put(uuid, resolveNameByUUID(uuid));
            }
            String resolved = nameCache.get(uuid);
            if (resolved != null && resolved.equalsIgnoreCase(targetName)) {
                total += b.getReward();
            }
        }
        return total;
    }

    /**
     * Resolves a hunter's aggregate claim stats by their last-known player name
     * (case-insensitive). Returns null if the name doesn't resolve to any known
     * player, or if that player has never claimed a bounty.
     */
    private @Nullable ClaimTracker.HunterStat resolveHunterStatByName(String hunterName) {
        Player online = plugin.getServer().getPlayerExact(hunterName);
        UUID uuid;
        if (online != null) {
            uuid = online.getUniqueId();
        } else {
            @SuppressWarnings("deprecation")
            org.bukkit.OfflinePlayer op = plugin.getServer().getOfflinePlayer(hunterName);
            if (!op.hasPlayedBefore() && !op.isOnline()) {
                return null;
            }
            uuid = op.getUniqueId();
        }
        return plugin.getClaimTracker().getHunterStat(uuid);
    }

    /**
     * Resolves a UUID to a player name, preferring an online player then the
     * offline cache. Returns null only if the server has no record of this UUID.
     */
    private @Nullable String resolveNameByUUID(UUID uuid) {
        Player online = plugin.getServer().getPlayer(uuid);
        if (online != null) {
            return online.getName();
        }
        org.bukkit.OfflinePlayer op = plugin.getServer().getOfflinePlayer(uuid);
        return op.getName(); // may be null for completely unknown UUIDs
    }

    // -------------------------------------------------------------------------
    // Value object
    // -------------------------------------------------------------------------

    private record TopTarget(String name, double totalReward) {}
}
