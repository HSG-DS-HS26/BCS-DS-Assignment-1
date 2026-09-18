package interfaces;

/** One key/value pair: an input record or an intermediate result. */
public record Pair<K, V>(K key, V value) { }
