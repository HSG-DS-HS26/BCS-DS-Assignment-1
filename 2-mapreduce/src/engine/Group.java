package engine;

import interfaces.Pair;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Groups intermediate pairs by key while preserving key insertion order. */
public final class Group {

    public static <K, V> Map<K, List<V>> by(List<Pair<K, V>> pairs) {
        var grouped = new LinkedHashMap<K, List<V>>();
        for (Pair<K, V> p : pairs) {
            grouped.computeIfAbsent(p.key(), k -> new ArrayList<>()).add(p.value());
        }
        return grouped;
    }
}
