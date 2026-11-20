package com.deceasedcraft.doomedtraits.server;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Выбор наших черт, который клиент прислал до подтверждения. */
public final class Draft {
    private static final Map<UUID, Integer> DRAFTS = new ConcurrentHashMap<>();

    private Draft() {
    }

    public static void set(UUID id, int mask2) {
        DRAFTS.put(id, mask2);
    }

    public static int get(UUID id) {
        return DRAFTS.getOrDefault(id, 0);
    }

    public static void forget(UUID id) {
        DRAFTS.remove(id);
    }
}
