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
 * Runs the word-count job.
 *
 * <p>One input record per document: {@code (K1, V1) = (String, String)}, the
 * file name and the file's text.
 */
public final class WordCount {

    /** Project directory when launched from the repository root or project. */
    private static final Path HERE =
            Files.isDirectory(Path.of("2-mapreduce")) ? Path.of("2-mapreduce") : Path.of("");

    /** Where the documents are. */
    private static final Path WORDS = HERE.resolve("input/words");

    public static void main(String[] args) throws IOException {
        if (args.length == 0) run(); else run(args[0]);
    }

    /** Counts every document in {@code input/words/} as one job. */
    public static void run() throws IOException {
        count(documents(WORDS), "solution-wordcount.txt");
    }

    /** Counts one named document in {@code input/words/} on its own. */
    public static void run(String name) throws IOException {
        Path file = WORDS.resolve(name);
        if (!Files.isRegularFile(file)) {
            file = Path.of(name);
            if (!Files.isRegularFile(file)) {
                System.out.println("there is no document called '" + name + "'. "
                        + "The ones in " + WORDS + " are:");
                for (Path p : listing(WORDS)) System.out.println("  " + p.getFileName());
                return;
            }
        }
        String fileName = file.getFileName().toString();
        String stem = fileName.replaceFirst("\\.txt$", "");
        count(List.of(new Pair<>(fileName, Files.readString(file))),
                "solution-wordcount-" + stem + ".txt");
    }

    /** Maps, groups, reduces and writes, whatever the documents were. */
    private static void count(List<Pair<String, String>> documents, String outName) throws IOException {
        var run = Pipeline.run(documents,
                new implementations.wordcount.Map(),
                new implementations.wordcount.Reduce());

        var lines = Solution.lines(run.answer());
        Path out = HERE.resolve(outName);
        Solution.write(out, lines);
        System.out.printf("word count: %d document%s, %d pairs, %d keys -> %s%n",
                documents.size(), documents.size() == 1 ? "" : "s",
                run.pairs(), lines.size(), out);
        if (lines.size() != run.answer().size()) {
            System.out.println("  (two of your keys print the same way, so one of them is gone)");
        }
    }

    /** Reads sorted {@code .txt} files as (file name, text) records. */
    private static List<Pair<String, String>> documents(Path folder) throws IOException {
        var documents = new ArrayList<Pair<String, String>>();
        for (Path file : listing(folder)) {
            documents.add(new Pair<>(file.getFileName().toString(), Files.readString(file)));
        }
        return documents;
    }

    /** The {@code .txt} files in a folder, in name order. */
    private static List<Path> listing(Path folder) throws IOException {
        try (var files = Files.list(folder)) {
            return files.filter(p -> p.toString().endsWith(".txt")).sorted().toList();
        }
    }
}
