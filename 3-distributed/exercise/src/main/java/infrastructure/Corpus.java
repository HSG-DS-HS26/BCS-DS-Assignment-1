package infrastructure;

// Keep this package name. `infrastructure` identifies supplied code.

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/** Generates the corpus. Supplied code.
 *
 * <h4>Inputs</h4>
 *
 * The plain gRPC client supplies record count and seed.
 *
 * <p>With dissaly, the scenario supplies the seed and size. `input:` gives the
 * full workload word count as `count:`, with `unit:` naming its item.
 * `scale:` is the ratio between full workload and measured run.
 *
 * <p>At `scale: 1`, the job receives the count from `input:` and the figures are
 * observed directly. At larger scales, the run measures
 * `units / (scale × 8000)` of the input and projects the rest. For example,
 * `scale: 3` with 24,000 words uses runs of 1,000 to 8,000 words.
 *
 * <pre>
 * seed: 5
 * scale: 3
 * input:
 *   unit:  word
 *   count: 24000
 * </pre>
 *
 * <pre>
 * // in Job.Load, which receives the Input the scenario declared
 * var corpus = Corpus.of(in.getCount(), Dissaly.current().seed(), pieces);
 * </pre>
 *
 * The same seed produces the same words. A different seed changes the corpus.
 *
 * <h4>Memory use</h4>
 *
 * A word from the default vocabulary uses about 5.7 bytes, so a million words
 * use about 5.4 MB. `records` is a `long`. A larger vocabulary changes the
 * estimate: measurements range from 4.47 bytes for 100 words to 6.53 bytes for
 * 20,000 words.
 *
 * Machines have a memory cap. It comes from the instance type (`c5.large` is
 * 4096 MB) and a scenario can set it per machine:
 *
 * <pre>
 * mappers:
 *   count: 4
 *   prefix: m
 *   instance: c5.large
 *   runs: { mapreduce.Worker: src/mapreduce/Worker.java }
 *   overrides:
 *     m2: { memoryMb: 8 }        # about 1.4 million words
 * </pre>
 *
 * The cap measures service fields at each sampler interval and at the end. A
 * machine above the cap receives an `oom` and is killed. Waiting calls time out.
 *
 * `piece` creates one corpus piece without retaining the others.
 *
 * The worked example uses a separate cluster. Its thumbnail service retains each
 * thumbnail, and the walkthrough measures forty million images.
 * [`tutorial/README.md`](../../../../../tutorial/README.md)
 *
 * <h4>Large workloads</h4>
 *
 * A literal corpus is bounded by the simulator machine. `of` retains the full
 * corpus. `piece` retains one piece but scans the corpus for each piece. A
 * gigabyte split into 256 pieces uses 3.8 MB and about ten minutes of real CPU.
 * Build large corpora in `Job.Load`, which runs off the clock.
 *
 * Gigabyte-scale workloads use `scale:` above 1, which measures smaller runs
 * and projects the target size. The worked example's
 * `4-scaled.yaml` is `scale: 5000`, a model of forty million images, and
 * reports about 874 MB held on every machine against the 4 GB a `c5.large`
 * has.
 *
 * A scaled run supplies a count from `Input` and `Dissaly.current().seed()`.
 * The corpus shrinks with the run. The design reports processed units with
 * `units(n)` and any measured memory value.
 *
 * <p>The engine fixes the ladder at 1,000, 2,000, 4,000, and 8,000 units on
 * clusters derived from the drawn design. If the design changes behavior in
 * that range, through spilling, an algorithm switch, or a limit, the engine
 * reports a wide error bar, refusal, or broken invariant for the machine and
 * resource. Fix the design rather than the scenario.
 *
 * <h4>Design decisions</h4>
 *
 * The job supplies `pieces` and determines work division and data transfer. This
 * class creates words.
 *
 * The class does not verify returned counts.
 *
 * <h4>Word parsing</h4>
 *
 * The corpus includes capitals and full stops. Task 2 defines a word as:
 *
 *   **fold to lower case with `Locale.ROOT`, then split on every run of
 *   characters that are neither letters nor digits**: {@code [^\p{L}\p{N}]+}
 *
 * The default vocabulary contains 24 words. A complete answer fits on one
 * screen regardless of the record count.
 *
 * <h4>Vocabulary size</h4>
 *
 * `words` is an argument on the four-argument forms, and {@link #vocabulary}
 * returns the list itself. The first 24 words are curated; later entries are
 * `w24`, `w25`, and so on, matching Task 2's input.
 *
 * `words` changes the quantity under study. With a fixed vocabulary, the
 * reducer's totals map has a fixed size. Tying `words` to corpus size changes
 * distinct-word growth. `Dissaly.current().reveal("distinctWords", n)` measures it.
 */
public final class Corpus {

    /**
     * The initial words form the default vocabulary, which contains 24 words.
     */
    private static final String[] HEAD = {
        "the", "network", "machine", "answer", "clock", "message", "failure",
        "reduce", "map", "key", "value", "order", "partition", "retry",
        "deadline", "trace", "a", "of", "and", "in", "that", "it", "is", "not",
    };

    /** Number of vocabulary entries used by default. */
    public static final int DEFAULT_WORDS = HEAD.length;

    private Corpus() { }

    /**
     * Returns `words` distinct vocabulary entries, with `HEAD` first and then
     * `w24`, `w25`, and so on.
     *
     * The list contains every word this corpus can contain. It does not specify
     * frequencies.
     */
    public static List<String> vocabulary(int words) {
        if (words < 1) throw new IllegalArgumentException("words must be at least 1, not " + words);
        var all = new ArrayList<String>(words);
        for (int i = 0; i < words; i++) all.add(i < HEAD.length ? HEAD[i] : "w" + i);
        return all;
    }

    /**
     * Splits `records` words across `pieces` and returns every piece. Uses the
     * default vocabulary and round-robin assignment.
     *
     * <p>This method holds the whole corpus. Use {@link #piece} for larger corpora.
     *
     * @param records total word count from `Input`
     * @param seed    seed for the generated corpus
     * @param pieces  number of piles
     */
    public static List<String> of(long records, long seed, int pieces) {
        return of(records, seed, pieces, DEFAULT_WORDS);
    }

    /**
     * Deals words using a vocabulary of `words` distinct entries.
     *
     * @param words number of distinct vocabulary entries
     */
    public static List<String> of(long records, long seed, int pieces, int words) {
        check(records, pieces, words);
        var vocab = vocabulary(words);
        var text = new ArrayList<StringBuilder>(pieces);
        for (int i = 0; i < pieces; i++) text.add(new StringBuilder());
        deal(records, seed, pieces, -1, vocab, text);
        var piles = new ArrayList<String>(pieces);
        for (StringBuilder built : text) piles.add(built.toString());
        return piles;
    }

    /**
     * Builds one piece without retaining the others.
     *
     * <p>`piece(r, s, n, k)` equals `of(r, s, n).get(k)` while retaining only
     * pile `k`. Reaching that pile still requires processing every record.
     *
     * @param which index of the returned piece
     */
    public static String piece(long records, long seed, int pieces, int which) {
        return piece(records, seed, pieces, which, DEFAULT_WORDS);
    }

    /** Draws one piece from a vocabulary of `words` distinct entries. */
    public static String piece(long records, long seed, int pieces, int which, int words) {
        check(records, pieces, words);
        if (which < 0 || which >= pieces) {
            throw new IllegalArgumentException("which must be in 0.." + (pieces - 1) + ", not " + which);
        }
        var one = new StringBuilder();
        deal(records, seed, pieces, which, vocabulary(words), List.of(one));
        return one.toString();
    }

    /**
     * Selects words and assigns them to pieces.
     *
     * `only` selects the retained piece; -1 retains every piece. Each record
     * draws its word, case flag and punctuation flag before filtering, so the
     * result matches the corresponding piece from {@link #of}.
     */
    private static void deal(long records, long seed, int pieces, int only,
                             List<String> vocab, List<StringBuilder> into) {
        var random = new Random(seed);
        for (long i = 0; i < records; i++) {
            int pile = (int) (i % pieces);
            String word = vocab.get(random.nextInt(vocab.size()));
            boolean shout = random.nextInt(9) == 0;
            boolean stop = random.nextInt(12) == 0;
            if (only >= 0 && pile != only) continue;
            into.get(only >= 0 ? 0 : pile)
                .append(shout ? word.toUpperCase(Locale.ROOT) : word)
                .append(stop ? ".\n" : " ");
        }
    }

    private static void check(long records, int pieces, int words) {
        if (pieces < 1) throw new IllegalArgumentException("pieces must be at least 1, not " + pieces);
        if (records < 0) throw new IllegalArgumentException("records cannot be negative: " + records);
        if (words < 1) throw new IllegalArgumentException("words must be at least 1, not " + words);
    }
}
