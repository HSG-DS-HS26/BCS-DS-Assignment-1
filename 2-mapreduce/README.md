# Task 2: MapReduce on one machine

## Goal 

Map processes input key/value pairs. Reduce receives the values grouped under
one key. The supplied engine handles grouping, input order, and output.

Implement both jobs under
[`src/implementations/`](src/implementations/):

- `wordcount/` counts words in [`input/words/`](input/words/). Key: the word;
  value: a count.
- `reverselinks/` reverses the investment links in
  [`input/links.txt`](input/links.txt). Each line names an investor, followed by
  the companies it invested in. Key: a company; value: the investors that
  invested in it.

Write the `Map.java` and `Reduce.java` for each job. Do not edit
[`src/interfaces/`](src/interfaces/) or [`src/engine/`](src/engine/). Keep job-specific code separate.

## Types

```
map        : (K1, V1)            -> List<Pair<K2, V2>>     yours
reduce     : (K2, List<V2>)      -> V2                     yours

mapTask    : List<Pair<K1, V1>>  -> List<Pair<K2, V2>>     engine: map on every input record
reduceTask : List<Pair<K2, V2>>  -> Map<K2, V2>            engine: group by K2, reduce once per key
```

Each job's runner in [`src/jobs/`](src/jobs/) reads the input and fixes K1 and V1:

| Job | K1 | V1 |
|---|---|---|
| `wordcount` | `String`, the file name | `String`, the file's text |
| `reverselinks` | `String`, the investor | `List<String>`, the companies it invested in |

The `reverselinks` runner has already split each line at the tab and at the
spaces, so V1 is a `List<String>` and your map does not parse text.

You choose K2 and V2. Before you decide, read the Java documentation for
[`List`](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/List.html)
and
[`Set`](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/Set.html),
and compare how each handles duplicates and order.

Reduce returns a V2, the same type as the values it
receives, so its result can be reduced again together with other values for
the same key. [`test/Invariants.java`](test/Invariants.java) checks this.

For `reverselinks`, that rule decides V2. The result for one company holds
several investors, so V2 has to be a collection, and map emits a collection
with one investor in it. Reduce merges the collections. Each investor should
appear once per company, whatever order the values arrive in, which makes
`Set<String>` a good fit. The output file prints any collection as its elements
in ascending order, separated by single spaces.

## Supporting information

Read the generic `MapperInterface`, `ReducerInterface`, and `Pair` in
[`src/interfaces/`](src/interfaces/). The engine in [`src/engine/`](src/engine/)
runs both tasks and writes the result. `Group.java` states the grouping
guarantees. [`test/Invariants.java`](test/Invariants.java) checks that reducers
handle partial results and values in any order. Comment out the check for an
unfinished job while working.

An interface defines the operations the pipeline expects without fixing the
implementation or the key/value types. That lets the same pipeline run both
jobs. If interfaces are new to you, read [Oracle's Java interface tutorial](https://docs.oracle.com/javase/tutorial/java/IandI/createinterface.html).

## Running the jobs

[`src/Main.java`](src/Main.java) starts with both jobs commented out. Uncomment a
job's import and call as you finish it. The output files are:

- `solution-wordcount.txt`
- `solution-reverselinks.txt`

Each line contains a tab-separated key/value pair. Lines are sorted by key.

The individual runners let you run one job before the other compiles.

## Place for Thoughts

Run `WordCount` with one input file as the program argument:

```
java jobs.WordCount THE_STORY_OF_CLAUDE.txt     -> solution-wordcount-THE_STORY_OF_CLAUDE.txt
java jobs.WordCount THE_STORY_OF_CHATGPT.txt    -> solution-wordcount-THE_STORY_OF_CHATGPT.txt
```

> **Windows (PowerShell).** We recommend a Codespace or the Dev Container,
> where the block above runs as printed. On a Windows host:
>
> ```powershell
> java jobs.WordCount THE_STORY_OF_CLAUDE.txt     -> solution-wordcount-THE_STORY_OF_CLAUDE.txt
> java jobs.WordCount THE_STORY_OF_CHATGPT.txt    -> solution-wordcount-THE_STORY_OF_CHATGPT.txt
> ```

Sort by count after the job runs:

```
sort -t$'\t' -k2 -rn solution-wordcount-THE_STORY_OF_CLAUDE.txt | head -30
```

> **Windows (PowerShell).** We recommend a Codespace or the Dev Container,
> where the block above runs as printed. On a Windows host:
>
> ```powershell
> Get-Content solution-wordcount-THE_STORY_OF_CLAUDE.txt |
>     Sort-Object { [int]($_ -split "`t")[1] } -Descending |
>     Select-Object -First 30
> ```

Compare rates rather than raw counts because the documents differ in length.
Look for words frequent in one document and absent from the other.

Anthropic describes a [text watermark for Claude](https://www.anthropic.com/news/claude-text-watermark) that is intended to identify likely Claude involvement without changing the
visible text. Could this word-count exercise recognise that watermark? 

The investment data raises a separate question: 
could reversing these links help us expose certain risks in our economy?

