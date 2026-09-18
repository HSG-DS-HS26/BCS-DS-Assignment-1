package engine;

import interfaces.MapperInterface;
import interfaces.Pair;
import interfaces.ReducerInterface;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A map task, then a reduce task, on one machine.
 *
 * <pre>
 *   map        : (K1, V1)            -> List&lt;Pair&lt;K2, V2&gt;&gt;
 *   reduce     : (K2, List&lt;V2&gt;)      -> V2
 *
 *   mapTask    : List&lt;Pair&lt;K1, V1&gt;&gt;  -> List&lt;Pair&lt;K2, V2&gt;&gt;
 *   reduceTask : List&lt;Pair&lt;K2, V2&gt;&gt;  -> Map&lt;K2, V2&gt;
 * </pre>
 */
public final class Pipeline {

    /** Result of one pipeline run. */
    public record Run<K2, V2>(Map<K2, V2> answer, int pairs) { }

    public static <K1, V1, K2, V2> Run<K2, V2> run(List<Pair<K1, V1>> inputs,
                                                   MapperInterface<K1, V1, K2, V2> mapper,
                                                   ReducerInterface<K2, V2> reducer) {
        List<Pair<K2, V2>> pairs = mapTask(inputs, mapper);
        return new Run<>(reduceTask(pairs, reducer), pairs.size());
    }

    /** Calls map once per input record. */
    public static <K1, V1, K2, V2> List<Pair<K2, V2>> mapTask(List<Pair<K1, V1>> inputs,
                                                              MapperInterface<K1, V1, K2, V2> mapper) {
        var pairs = new ArrayList<Pair<K2, V2>>();
        for (Pair<K1, V1> input : inputs) pairs.addAll(mapper.map(input.key(), input.value()));
        return pairs;
    }

    /** Groups the pairs by key, then calls reduce once per key. */
    public static <K2, V2> Map<K2, V2> reduceTask(List<Pair<K2, V2>> pairs,
                                                  ReducerInterface<K2, V2> reducer) {
        Map<K2, List<V2>> grouped = Group.by(pairs);

        var answer = new LinkedHashMap<K2, V2>();
        for (var e : grouped.entrySet()) {
            answer.put(e.getKey(), reducer.reduce(e.getKey(), e.getValue()));
        }
        return answer;
    }
}
