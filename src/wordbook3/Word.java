package wordbook3;

/** 一条单词记录。 */
public record Word(
        long id,
        String word,
        String meaning,
        String sentence,
        String voice,
        String createdAt
) {
}
