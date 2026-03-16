package com.swag.swagbounties.placeholder;

import com.swag.swagbounties.SwagBounties;
import com.swag.swagbounties.bounty.Bounty;
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
 *   <li>{@code %swagbounties_top_hunter%} — most prolific bounty claimer (stub)</li>
 *   <li>{@code %swagbounties_total_bounties%} — count of all active bounties</li>
 *   <li>{@code %swagbounties_bounty_<playerName>%} — total reward on the named player</li>
 *   <li>{@code %swagbounties_has_bounty_<playerName>%} — "yes" or "no"</li>
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
                // ClaimTracker not yet implemented — placeholder stub.
                return "Coming soon";
            }
            case "total_bounties" -> {
                return String.valueOf(all.size());
            }
            default -> {
                // Dynamic: bounty_<playerName> or has_bounty_<playerName>
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
