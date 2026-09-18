package implementations.wordcount;

// The class name conflicts with java.util.Map, so no import is needed here.

/**
 * Implement the map stage for word counting.
 *
 * Implement {@code MapperInterface}. The input types are fixed by
 * {@code jobs.WordCount}: each call receives one document as
 * {@code (K1, V1) = (String, String)}, the file name and the file's text.
 * Choose the intermediate types K2 and V2; the Reduce class must use the same
 * ones. Emit one pair for each word occurrence; the mapper leaves aggregation
 * to the reducer.
 *
 * Word parsing is fixed: lower-case with {@code Locale.ROOT}, then split on
 * runs of non-letter and non-digit characters using {@code [^\\p{L}\\p{N}]+}.
 * Thus "Don't" and "three-way" each produce two words, while "HTTP" and
 * "http" produce the same word.
 */
public final class Map {

    // TODO: implement the mapping interface.
}
