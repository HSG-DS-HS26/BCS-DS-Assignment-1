package engine;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/** Writes map/reduce answers as sorted UTF-8 lines. */
public final class Solution {

    /** Converts answers to sorted tab-separated lines. */
    public static <K, V> List<String> lines(Map<K, V> answer) {
        var sorted = new TreeMap<String, String>();
        for (var e : answer.entrySet()) {
            sorted.put(text(e.getKey()), text(e.getValue()));
        }
        var lines = new ArrayList<String>();
        for (var e : sorted.entrySet()) lines.add(e.getKey() + "\t" + e.getValue());
        return lines;
    }

    /**
     * How a key or value prints. A collection prints as its elements in
     * ascending order, separated by single spaces, whatever collection it is.
     */
    public static String text(Object o) {
        if (o instanceof Collection<?> c) {
            return c.stream().map(String::valueOf).sorted().collect(Collectors.joining(" "));
        }
        return String.valueOf(o);
    }

    public static void write(Path out, List<String> lines) throws IOException {
        var text = new StringBuilder();
        for (String line : lines) text.append(line).append('\n');
        Path parent = out.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        Files.writeString(out, text, StandardCharsets.UTF_8);
    }
}
