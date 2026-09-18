import engine.Group;
import engine.Pipeline;
import engine.Solution;
import interfaces.MapperInterface;
import interfaces.Pair;
import interfaces.ReducerInterface;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Checks order independence and partial reduction. */
public final class Invariants {

    public static void main(String[] args) {
        // Comment out checks for jobs you have not written yet.

        // Use short documents for the word-count check: (file name, text).
        check("word count",
                new implementations.wordcount.Map(),
                new implementations.wordcount.Reduce(),
                List.of(new Pair<>("one.txt", "The cat sat on the mat."),
                        new Pair<>("two.txt", "The cat sat.")));

        // Use a small graph for the reverse-links check: (investor, companies).
        check("reverse links",
                new implementations.reverselinks.Map(),
                new implementations.reverselinks.Reduce(),
                List.of(new Pair<>("A", List.of("B", "C")),
                        new Pair<>("B", List.of("C")),
                        new Pair<>("C", List.of("A", "G")),
                        new Pair<>("D", List.of("A", "C"))));
    }

    private static <K1, V1, K2, V2> void check(String job, MapperInterface<K1, V1, K2, V2> mapper,
                                               ReducerInterface<K2, V2> reducer,
                                               List<Pair<K1, V1>> inputs) {
        System.out.println("== " + job);

        List<Pair<K2, V2>> pairs = Pipeline.mapTask(inputs, mapper);
        var grouped = Group.by(pairs);
        System.out.printf("   %d input records, %d pairs, %d keys%n",
                inputs.size(), pairs.size(), grouped.size());

        var answer = new java.util.LinkedHashMap<K2, V2>();
        int shuffled = 0, combinable = 0, combinableTried = 0;
        var random = new Random(7);

        for (var e : grouped.entrySet()) {
            K2 key = e.getKey();
            List<V2> values = e.getValue();
            V2 said = reducer.reduce(key, values);
            answer.put(key, said);

            // Verify that value order does not change the result.
            var other = new ArrayList<>(values);
            java.util.Collections.shuffle(other, random);
            if (!Solution.text(reducer.reduce(key, other)).equals(Solution.text(said))) shuffled++;

            // Verify that partial reductions can be combined.
            if (values.size() >= 2) {
                combinableTried++;
                int half = values.size() / 2;
                try {
                    V2 a = reducer.reduce(key, values.subList(0, half));
                    V2 b = reducer.reduce(key, values.subList(half, values.size()));
                    if (Solution.text(reducer.reduce(key, List.of(a, b))).equals(Solution.text(said))) combinable++;
                } catch (RuntimeException ex) {
                    // The reducer rejects partial groups.
                }
            }
        }

        System.out.println(shuffled == 0
                ? "   order:      values can arrive in any order without changing the answer"
                : "   order:      " + shuffled + " keys changed after their values were shuffled");

        if (combinableTried == 0) {
            System.out.println("   combinable: no key had enough values for this check");
        } else if (combinable == combinableTried) {
            System.out.println("   combinable: yes, the reducer accepted partial values");
        } else {
            System.out.printf("   combinable: no (%d of %d keys): partial reduction changed the result%n",
                    combinable, combinableTried);
            System.out.println("               partial and final reductions produced different results");
        }

        System.out.println("   output:");
        for (String line : Solution.lines(answer)) System.out.println("     " + line);
        System.out.println();
    }
}
