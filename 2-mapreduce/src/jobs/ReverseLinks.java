package jobs;

import engine.Pipeline;
import engine.Solution;
import interfaces.Pair;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs the reverse-investment-links job.
 *
 * <p>One input record per line of {@code input/links.txt}:
 * {@code (K1, V1) = (String, List<String>)}, the investor and the companies it
 * invested in. The line is already split, so map does not parse text.
 */
public final class ReverseLinks {

    /** Project directory when launched from the repository root or project. */
    private static final Path HERE =
            Files.isDirectory(Path.of("2-mapreduce")) ? Path.of("2-mapreduce") : Path.of("");

    public static void main(String[] args) throws IOException {
        run();
    }

    public static void run() throws IOException {
        List<Pair<String, List<String>>> investors = records(HERE.resolve("input/links.txt"));

        var run = Pipeline.run(investors,
                new implementations.reverselinks.Map(),
                new implementations.reverselinks.Reduce());

        var lines = Solution.lines(run.answer());
        Path out = HERE.resolve("solution-reverselinks.txt");
        Solution.write(out, lines);
        System.out.printf("reverse links: %d investors read, %d pairs, %d companies invested in -> %s%n",
                investors.size(), run.pairs(), lines.size(), out);
    }

    /**
     * Splits each line {@code investor<TAB>company company ...} into
     * (investor, companies). Blank lines and lines without a tab are skipped.
     */
    public static List<Pair<String, List<String>>> records(Path file) throws IOException {
        var records = new ArrayList<Pair<String, List<String>>>();
        for (String line : Files.readAllLines(file)) {
            Pair<String, List<String>> record = record(line);
            if (record != null) records.add(record);
        }
        return records;
    }

    /** One line as (investor, companies), or {@code null} when it names no investor. */
    public static Pair<String, List<String>> record(String line) {
        int tab = line.indexOf('\t');
        if (tab < 0) return null;
        String investor = line.substring(0, tab).trim();
        if (investor.isEmpty()) return null;
        String rest = line.substring(tab + 1).trim();
        List<String> companies = rest.isEmpty() ? List.of() : List.of(rest.split("\\s+"));
        return new Pair<>(investor, companies);
    }
}
