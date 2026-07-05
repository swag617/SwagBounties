package com.swag.swagbounties.bounty;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Thread-safe in-memory store of bounty-claim history and per-hunter aggregate
 * stats, with YAML persistence to {@code claims.yml}.
 *
 * <p>Backs the {@code %swagbounties_top_hunter*%} placeholders, the "Top Hunters"
 * tab of {@link com.swag.swagbounties.gui.TopBountiesGUI}, and {@code /bountyadmin
 * history}. A claim is recorded exactly once, in {@link
 * com.swag.swagbounties.listener.BountyListener#onPlayerDeath}, after a bounty
 * is removed for payout.</p>
 */
public class ClaimTracker {

    /** Maximum number of recent claims retained server-wide (oldest trimmed first). */
    private static final int MAX_HISTORY = 100;

    private static final Logger LOGGER = Logger.getLogger("SwagBounties");

    /** One immutable record per claim. */
    public record Claim(UUID hunterUUID, UUID targetUUID, double reward, long claimedAt) {}

    /** Aggregate per-hunter totals. */
    public record HunterStat(UUID hunterUUID, int claimCount, double totalEarned) {}

    /** Mutable accumulator backing a single hunter's entry; synchronized per-instance. */
    private static final class MutableStat {
        int claimCount;
        double totalEarned;
    }

    private final File dataFile;
    private final ConcurrentHashMap<UUID, MutableStat> hunterStats = new ConcurrentHashMap<>();

    /** Newest claim last. Guarded by its own monitor since it's a plain LinkedList. */
    private final LinkedList<Claim> history = new LinkedList<>();

    public ClaimTracker(File dataFile) {
        this.dataFile = dataFile;
    }

    // -------------------------------------------------------------------------
    // Recording
    // -------------------------------------------------------------------------

    /** Records a claim, updating the hunter's aggregate stats and the recent history log. */
    public void recordClaim(UUID hunterUUID, UUID targetUUID, double reward) {
        MutableStat stat = hunterStats.computeIfAbsent(hunterUUID, k -> new MutableStat());
        synchronized (stat) {
            stat.claimCount++;
            stat.totalEarned += reward;
        }
        synchronized (history) {
            history.addLast(new Claim(hunterUUID, targetUUID, reward, System.currentTimeMillis()));
            while (history.size() > MAX_HISTORY) {
                history.removeFirst();
            }
        }
    }

    // -------------------------------------------------------------------------
    // Queries
    // -------------------------------------------------------------------------

    /** Returns the top hunters by claim count (most prolific), descending, capped at {@code limit}. */
    public List<HunterStat> getTopHunters(int limit) {
        List<HunterStat> all = new ArrayList<>();
        for (Map.Entry<UUID, MutableStat> entry : hunterStats.entrySet()) {
            MutableStat s = entry.getValue();
            synchronized (s) {
                all.add(new HunterStat(entry.getKey(), s.claimCount, s.totalEarned));
            }
        }
        all.sort((a, b) -> Integer.compare(b.claimCount(), a.claimCount()));
        return all.subList(0, Math.min(limit, all.size()));
    }

    /** Returns the single most prolific hunter, or {@code null} if no claims have been recorded. */
    public HunterStat getTopHunter() {
        List<HunterStat> top = getTopHunters(1);
        return top.isEmpty() ? null : top.get(0);
    }

    /** Returns this hunter's aggregate stats, or {@code null} if they have never claimed a bounty. */
    public HunterStat getHunterStat(UUID hunterUUID) {
        MutableStat s = hunterStats.get(hunterUUID);
        if (s == null) {
            return null;
        }
        synchronized (s) {
            return new HunterStat(hunterUUID, s.claimCount, s.totalEarned);
        }
    }

    /** Returns the most recent claims, newest first, capped at {@code limit}. */
    public List<Claim> getRecentHistory(int limit) {
        synchronized (history) {
            List<Claim> copy = new ArrayList<>(history);
            Collections.reverse(copy);
            return copy.subList(0, Math.min(limit, copy.size()));
        }
    }

    // -------------------------------------------------------------------------
    // Persistence
    // -------------------------------------------------------------------------

    public synchronized void loadFromDisk() {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(dataFile);

        if (config.isConfigurationSection("hunters")) {
            for (String key : config.getConfigurationSection("hunters").getKeys(false)) {
                UUID uuid;
                try {
                    uuid = UUID.fromString(key);
                } catch (IllegalArgumentException e) {
                    LOGGER.warning("Skipping malformed hunter UUID in claims.yml: " + key);
                    continue;
                }
                MutableStat stat = new MutableStat();
                stat.claimCount = config.getInt("hunters." + key + ".claimCount", 0);
                stat.totalEarned = config.getDouble("hunters." + key + ".totalEarned", 0.0);
                hunterStats.put(uuid, stat);
            }
        }

        List<?> rawHistory = config.getList("history");
        if (rawHistory != null) {
            synchronized (history) {
                for (Object entry : rawHistory) {
                    if (!(entry instanceof Map<?, ?> map)) {
                        continue;
                    }
                    Object hunterObj = map.get("hunter");
                    Object targetObj = map.get("target");
                    if (!(hunterObj instanceof String) || !(targetObj instanceof String)) {
                        continue;
                    }
                    UUID hunter;
                    UUID target;
                    try {
                        hunter = UUID.fromString((String) hunterObj);
                        target = UUID.fromString((String) targetObj);
                    } catch (IllegalArgumentException e) {
                        LOGGER.warning("Skipping malformed claim history entry in claims.yml.");
                        continue;
                    }
                    double reward = map.get("reward") instanceof Number n ? n.doubleValue() : 0.0;
                    long claimedAt = map.get("claimedAt") instanceof Number n ? n.longValue() : System.currentTimeMillis();
                    history.add(new Claim(hunter, target, reward, claimedAt));
                }
                while (history.size() > MAX_HISTORY) {
                    history.removeFirst();
                }
            }
        }
    }

    public synchronized void saveToDisk() {
        YamlConfiguration config = new YamlConfiguration();

        for (Map.Entry<UUID, MutableStat> entry : hunterStats.entrySet()) {
            MutableStat s = entry.getValue();
            synchronized (s) {
                config.set("hunters." + entry.getKey() + ".claimCount", s.claimCount);
                config.set("hunters." + entry.getKey() + ".totalEarned", s.totalEarned);
            }
        }

        List<Map<String, Object>> serializedHistory = new ArrayList<>();
        synchronized (history) {
            for (Claim claim : history) {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("hunter", claim.hunterUUID().toString());
                map.put("target", claim.targetUUID().toString());
                map.put("reward", claim.reward());
                map.put("claimedAt", claim.claimedAt());
                serializedHistory.add(map);
            }
        }
        config.set("history", serializedHistory);

        if (!dataFile.getParentFile().exists()) {
            dataFile.getParentFile().mkdirs();
        }

        try {
            config.save(dataFile);
        } catch (IOException e) {
            throw new RuntimeException("[ClaimTracker] Failed to save claims.yml: " + e.getMessage(), e);
        }
    }
}
