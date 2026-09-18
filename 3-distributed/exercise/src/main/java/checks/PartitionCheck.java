package checks;

import infrastructure.Corpus;
import infrastructure.Shuffle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Checks that every word belongs to exactly one reduce task.
 *
 * <pre>
 * ./gradlew partitionCheck
 * </pre>
 *
 * <h4>Why the job's own output cannot catch this</h4>
 *
 * <p>The Master merges every reduce reply with {@code merge(key, value,
 * Long::sum)}. If two machines disagreed about which reduce task {@code the}
 * belongs to, reduce task 2 would report part of it, reduce task 5 the rest, and
 * the Master would add them together. The row count would be right, the total
 * would be right, every row value would be right. Nothing downstream of the
 * Master can see it, so {@code ./gradlew run} printing 24 rows and 20 000
 * cannot fail on it.
 *
 * <p>The evidence lives in the individual groups, before anything is merged. So
 * this check reads them there, in one process, with no network and no Master.
 *
 * <h4>What it asserts</h4>
 *
 * <p>Every word is claimed by exactly one reduce task. Two claims is the split.
 * Zero claims is the other half of the same fault: {@code hashCode() % R} is
 * negative for about half of all strings, so those pairs get filed under a group
 * index no reduce task ever asks for and vanish without an error. The counts
 * adding up to the corpus is what catches the vanishing half even when it takes
 * a whole word with it.
 *
 * <p>It runs M map tasks across several machines, because one machine cannot
 * contradict itself and there would be no disagreement available to find.
 *
 * <p>This checks the supplied {@link Shuffle}, not your handlers. It is here so
 * that a wrong total from {@code ./gradlew run} tells you something: with this
 * passing, the fault is in what your messages carry or in how your reduce
 * handler collects, not in the partitioning underneath.
 */
public final class PartitionCheck {

    private static final long WORDS = 20_000;
    private static final long SEED = 5;
    private static final int MAP_TASKS = 21;
    private static final int REDUCE_TASKS = 7;
    private static final int MACHINES = 7;

    /** A bare machine: the supplied store and nothing on top of it. */
    private static final class Probe extends Shuffle { }

    public static void main(String[] args) {
        var machines = new ArrayList<Probe>();
        for (int i = 0; i < MACHINES; i++) machines.add(new Probe());

        // The Map phase, spread deterministically so that the holder of each map
        // task is obvious.
        for (int task = 0; task < MAP_TASKS; task++) {
            String piece = Corpus.piece(WORDS, SEED, MAP_TASKS, task);
            machines.get(task % MACHINES).store(task, piece, REDUCE_TASKS);
        }

        // The Reduce phase, one group at a time, kept apart. That is the point:
        // the Master would merge these and the evidence would be gone.
        var claimedBy = new LinkedHashMap<String, Set<Integer>>();
        var totals = new TreeMap<String, Long>();
        for (int partition = 0; partition < REDUCE_TASKS; partition++) {
            for (int task = 0; task < MAP_TASKS; task++) {
                Map<String, Long> group = machines.get(task % MACHINES).group(task, partition);
                if (group == null) {
                    System.out.println("FAIL: map task " + task + " is not where it was put.");
                    System.exit(1);
                }
                for (var e : group.entrySet()) {
                    claimedBy.computeIfAbsent(e.getKey(), w -> new LinkedHashSet<>()).add(partition);
                    totals.merge(e.getKey(), e.getValue(), Long::sum);
                }
            }
        }

        report(claimedBy, totals);
    }

    private static void report(Map<String, Set<Integer>> claimedBy, Map<String, Long> totals) {
        var split = new LinkedHashMap<String, Set<Integer>>();
        claimedBy.forEach((word, groups) -> {
            if (groups.size() > 1) split.put(word, groups);
        });

        long counted = totals.values().stream().mapToLong(Long::longValue).sum();
        List<String> vocabulary = Corpus.vocabulary(Corpus.DEFAULT_WORDS);

        System.out.println("partition check: " + MAP_TASKS + " map tasks on " + MACHINES
                + " machines, " + REDUCE_TASKS + " groups");
        System.out.println("  " + totals.size() + " distinct words, " + counted + " counted, "
                + WORDS + " expected");

        var problems = new ArrayList<String>();

        if (!split.isEmpty()) {
            problems.add(split.size() + " word(s) claimed by more than one reduce task");
            split.forEach((word, groups) ->
                    System.out.println("  SPLIT: '" + word + "' is in groups " + groups
                            + ". The Master would add these together and the answer would"
                            + " look correct"));
        }

        if (counted != WORDS) {
            problems.add("the counts add up to " + counted + ", not " + WORDS);
            System.out.println("  MISSING: " + (WORDS - counted) + " occurrences reached no"
                    + " reduce task. hashCode() % R is negative for about half of all words;"
                    + " Math.floorMod is the fix");
        }

        var absent = new ArrayList<String>();
        for (String word : vocabulary) if (!totals.containsKey(word)) absent.add(word);
        if (!absent.isEmpty()) {
            problems.add(absent.size() + " word(s) of the vocabulary belong to no reduce task");
            System.out.println("  ABSENT: " + absent);
        }

        System.out.println();
        if (problems.isEmpty()) {
            System.out.println("PASS: every word belongs to exactly one reduce task, and the"
                    + " counts add up to the corpus.");
        } else {
            System.out.println("FAIL: " + String.join("; ", problems));
            System.exit(1);
        }
    }
}
