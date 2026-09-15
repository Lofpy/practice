package com.poppy.practice.kit;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public final class KitManager {
    private final Map<String, Kit> kits = new LinkedHashMap<String, Kit>();

    public KitManager() {
        register(new NoDebuffKit());
        register(new BoxingKit());
        register(new ComboKit());
    }

    public void register(Kit kit) {
        kits.put(kit.getId().toLowerCase(), kit);
    }

    public Kit get(String id) {
        return id == null ? null : kits.get(id.toLowerCase());
    }

    public Collection<Kit> all() {
        return new ArrayList<Kit>(kits.values());
    }
}
