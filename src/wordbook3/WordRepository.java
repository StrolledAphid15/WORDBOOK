package wordbook3;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** 单词的新增、修改、删除和查询。 */
public final class WordRepository {
    private static final String SELECT_ALL = """
            SELECT id, word, meaning, sentence, voice, created_at
            FROM words
            ORDER BY word COLLATE NOCASE
            """;

    private static final String SEARCH_WORDS = """
            SELECT id, word, meaning, sentence, voice, created_at
            FROM words
            WHERE word LIKE ? 
            ORDER BY word COLLATE NOCASE
            """;

    private static final String INSERT_WORD = """
            INSERT INTO words(word, meaning, sentence, voice)
            VALUES (?, ?, ?, ?)
            """;

    private static final String UPDATE_WORD = """
            UPDATE words
            SET word = ?, meaning = ?, sentence = ?, voice = ?
            WHERE id = ?
            """;

    private static final String DELETE_WORD = "DELETE FROM words WHERE id = ?";

    private final DatabaseManager databaseManager;

    public WordRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public List<Word> findAll() throws SQLException {
        try (Connection connection = databaseManager.openConnection();
            PreparedStatement statement = connection.prepareStatement(SELECT_ALL);
            ResultSet resultSet = statement.executeQuery()) {
            return mapWords(resultSet);
        }
    }

    public void insert(String word, String meaning, String sentence, String voice) throws SQLException {
        try (Connection connection = databaseManager.openConnection();
            PreparedStatement statement = connection.prepareStatement(INSERT_WORD)) {
            bindWord(statement, word, meaning, sentence, voice);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw translateConstraintViolation(exception);
        }
    }

    public void update(long id, String word, String meaning, String sentence, String voice) throws SQLException {
        try (Connection connection = databaseManager.openConnection();
            PreparedStatement statement = connection.prepareStatement(UPDATE_WORD)) {
            bindWord(statement, word, meaning, sentence, voice);
            statement.setLong(5, id);
            if (statement.executeUpdate() == 0) {
                throw new IllegalArgumentException("要修改的单词不存在");
            }
        } catch (SQLException exception) {
            throw translateConstraintViolation(exception);
        }
    }

    public void delete(long id) throws SQLException {
        try (Connection connection = databaseManager.openConnection();
            PreparedStatement statement = connection.prepareStatement(DELETE_WORD)) {
            statement.setLong(1, id);
            if (statement.executeUpdate() == 0) {
                throw new IllegalArgumentException("要删除的单词不存在");
            }
        }
    }

    public List<Word> search(String keyword, SearchMode mode) throws SQLException {
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        if (normalizedKeyword.isEmpty()) {
            return findAll();
        }
        if (mode == null) {
            throw new IllegalArgumentException("请选择查询方式");
        }

        try (Connection connection = databaseManager.openConnection();
            PreparedStatement statement = connection.prepareStatement(SEARCH_WORDS)) {
            statement.setString(1, buildLikePattern(normalizedKeyword, mode));
            try (ResultSet resultSet = statement.executeQuery()) {
                return mapWords(resultSet);
            }
        }
    }

    private void bindWord(
            PreparedStatement statement,
            String word,
            String meaning,
            String sentence,
            String voice
    ) throws SQLException {
        statement.setString(1, required(word, "单词"));
        statement.setString(2, required(meaning, "中文释义"));
        statement.setString(3, optional(sentence));
        statement.setString(4, optional(voice));
    }

    private String required(String value, String label) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("请输入" + label);
        }
        return normalized;
    }

    private String optional(String value) {
        return value == null ? "" : value.trim();
    }

    private String buildLikePattern(String keyword, SearchMode mode) {
        String escapedKeyword = keyword
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return switch (mode) {
            case EXACT -> escapedKeyword;
            case PREFIX -> escapedKeyword + "%";
            case SUFFIX -> "%" + escapedKeyword;
            case CONTAINS -> "%" + escapedKeyword + "%";
        };
    }

    private List<Word> mapWords(ResultSet resultSet) throws SQLException {/* 把 ResultSet 转成 Java 对象 */
        List<Word> words = new ArrayList<>();
        while (resultSet.next()) {
            words.add(new Word(
                    resultSet.getLong("id"),
                    resultSet.getString("word"),
                    resultSet.getString("meaning"),
                    resultSet.getString("sentence"),
                    resultSet.getString("voice"),
                    resultSet.getString("created_at")
            ));
        }
        return words;
    }

    private SQLException translateConstraintViolation(SQLException exception) {/* 转换重复单词错误 */
        String message = exception.getMessage();
        if (message != null && message.contains("UNIQUE constraint failed: words.word")) {
            return new SQLException("单词已存在", exception);
        }
        return exception;
    }
}
