package com.swag.swagbounties.bounty;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe in-memory store for all active bounties with YAML persistence.
 *
 * <p>The map key is the target's UUID. Each target can have multiple bounties
 * placed by different (or the same) creators. All public methods are safe to
 * call from any thread — writes synchronize on the per-target list, and reads
 * return unmodifiable snapshots so callers cannot mutate internal state.</p>
 *
 * <p>{@link #loadFromDisk()} and {@link #saveToDisk()} are both {@code synchronized}
 * on {@code this} to ensure a consistent view of the entire map during bulk I/O.</p>
 */
public class BountyManager {

    // Key: target UUID -> list of bounties placed on that target
    private final ConcurrentHashMap<UUID, List<Bounty>> bounties = new ConcurrentHashMap<>();

    private final File dataFile;

    public BountyManager(File dataFile) {
        this.dataFile = dataFile;
    }

    // -------------------------------------------------------------------------
    // In-memory operations
    // -------------------------------------------------------------------------

    /**
     * Adds a bounty. If the target has no existing bounties a new list is created atomically.
     */
    public void addBounty(Bounty bounty) {
        bounties.computeIfAbsent(bounty.getTargetUUID(), k -> new ArrayList<>())
                .add(bounty);
    }

    /**
     * Removes the first bounty found for {@code targetUUID} that was placed by
     * {@code creatorUUID}. Does nothing if no matching bounty exists.
     *
     * @return {@code true} if a bounty was removed, {@code false} otherwise
     */
    public boolean removeBounty(UUID targetUUID, UUID creatorUUID) {
        List<Bounty> list = bounties.get(targetUUID);
        if (list == null) {
            return false;
        }
        synchronized (list) {
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i).getCreatorUUID().equals(creatorUUID)) {
                    list.remove(i);
                    // Clean up the map entry when the list becomes empty
                    if (list.isEmpty()) {
                        bounties.remove(targetUUID, list);
                    }
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Returns an unmodifiable snapshot of all bounties on the given target,
     * or an empty list if none exist.
     */
    public List<Bounty> getBounties(UUID targetUUID) {
        List<Bounty> list = bounties.get(targetUUID);
        if (list == null) {
            return Collections.emptyList();
        }
        synchronized (list) {
            return Collections.unmodifiableList(new ArrayList<>(list));
        }
    }

    /**
     * Returns the sum of all rewards currently placed on the given target.
     * Returns {@code 0.0} if the target has no bounties.
     */
    public double getTotalReward(UUID targetUUID) {
        List<Bounty> list = bounties.get(targetUUID);
        if (list == null) {
            return 0.0;
        }
        synchronized (list) {
            return list.stream().mapToDouble(Bounty::getReward).sum();
        }
    }

    /**
     * Returns a flat, unmodifiable snapshot of every bounty across all targets.
     */
    public List<Bounty> getAllBounties() {
        List<Bounty> all = new ArrayList<>();
        for (List<Bounty> list : bounties.values()) {
            synchronized (list) {
                all.addAll(list);
            }
        }
        return Collections.unmodifiableList(all);
    }

    // -------------------------------------------------------------------------
    // Persistence
    // -------------------------------------------------------------------------

    /**
     * Loads all bounties from the YAML data file into the in-memory map.
     *
     * <p>Expected file structure:</p>
     * <pre>
     * bounties:
     *   &lt;targetUUID&gt;:
     *     - creator: "&lt;creatorUUID&gt;"
     *       reward: 100.0
     *       anonymous: false
     *       placedAt: 1700000000000
     * </pre>
     *
     * <p>If the file does not exist this method returns silently (first run).
     * Entries with an unparseable UUID are skipped with a warning to stderr.</p>
     */
    public synchronized void loadFromDisk() {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(dataFile);

        if (!config.isConfigurationSection("bounties")) {
            return;
        }

        for (String targetKey : config.getConfigurationSection("bounties").getKeys(false)) {
            UUID targetUUID;
            try {
                targetUUID = UUID.fromString(targetKey);
            } catch (IllegalArgumentException e) {
                System.err.println("[BountyManager] Skipping malformed target UUID in bounties.yml: " + targetKey);
                continue;
            }

            List<?> rawList = config.getList("bounties." + targetKey);
            if (rawList == null) {
                continue;
            }

            List<Bounty> loaded = new ArrayList<>();
            for (Object entry : rawList) {
                if (!(entry instanceof Map<?, ?> map)) {
                    continue;
                }

                String creatorStr = (String) map.get("creator");
                if (creatorStr == null) {
                    continue;
                }

                UUID creatorUUID;
                try {
                    creatorUUID = UUID.fromString(creatorStr);
                } catch (IllegalArgumentException e) {
                    System.err.println("[BountyManager] Skipping malformed creator UUID in bounties.yml: " + creatorStr);
                    continue;
                }

                double reward = map.get("reward") instanceof Number n ? n.doubleValue() : 0.0;
                boolean anonymous = map.get("anonymous") instanceof Boolean b && b;
                long placedAt = map.get("placedAt") instanceof Number n ? n.longValue() : System.currentTimeMillis();

                loaded.add(new Bounty(targetUUID, creatorUUID, reward, anonymous, placedAt));
            }

            if (!loaded.isEmpty()) {
                bounties.put(targetUUID, loaded);
            }
        }
    }

    /**
     * Saves the current in-memory bounty map to the YAML data file, replacing
     * any previous contents. The parent directory is created if it does not exist.
     *
     * @throws RuntimeException wrapping an {@link IOException} if the file cannot be written
     */
    public synchronized void saveToDisk() {
        YamlConfiguration config = new YamlConfiguration();

        for (Map.Entry<UUID, List<Bounty>> entry : bounties.entrySet()) {
            UUID targetUUID = entry.getKey();
            List<Bounty> list = entry.getValue();

            List<Map<String, Object>> serialized = new ArrayList<>();
            synchronized (list) {
                for (Bounty bounty : list) {
                    Map<String, Object> map = new java.util.LinkedHashMap<>();
                    map.put("creator", bounty.getCreatorUUID().toString());
                    map.put("reward", bounty.getReward());
                    map.put("anonymous", bounty.isAnonymous());
                    map.put("placedAt", bounty.getPlacedAt());
                    serialized.add(map);
                }
            }

            config.set("bounties." + targetUUID, serialized);
        }

        if (!dataFile.getParentFile().exists()) {
            dataFile.getParentFile().mkdirs();
        }

        try {
            config.save(dataFile);
        } catch (IOException e) {
            throw new RuntimeException("[BountyManager] Failed to save bounties.yml: " + e.getMessage(), e);
        }
    }
}
