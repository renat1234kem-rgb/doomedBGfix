package ru.renat.doomedbgfix.bandage;

import ru.renat.doomedbgfix.DoomedBGFix;
import ru.renat.doomedbgfix.config.BGConfig;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Quality ("strength") of a dressing, keyed by registry path of the item.
 * Multiplies both the bleed-stop speed and the total capacity of the dressing.
 * Unknown items fall back to bandageQualityDefault and are logged once,
 * so their id can be added to bandageProfiles in the config.
 */
public final class BandageProfiles {

    private static final Map<String, Float> CACHE = new HashMap<>();
    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

    private BandageProfiles() {}

    public static float quality(String path) {
        if (path == null || path.isEmpty()) {
            return BGConfig.bandageQualityDefault();
        }
        Float cached = CACHE.get(path);
        if (cached != null) return cached;

        float q = BGConfig.bandageQualityDefault();
        boolean known = false;
        for (String entry : BGConfig.bandageProfiles()) {
            int i = entry.indexOf('=');
            if (i <= 0) continue;
            if (!entry.substring(0, i).trim().equalsIgnoreCase(path)) continue;
            try {
                q = Float.parseFloat(entry.substring(i + 1).trim());
                known = true;
            } catch (NumberFormatException ignored) {
            }
            break;
        }

        CACHE.put(path, q);
        if (!known && WARNED.add(path)) {
            DoomedBGFix.LOGGER.info("[doomedbgfix] unprofiled bandage item 'doomedmatu:{}' -> quality {}. "
                    + "Add '{}=<value>' to bandageProfiles in doomedbgfix-common.toml to tune it.", path, q, path);
        }
        return q;
    }
}