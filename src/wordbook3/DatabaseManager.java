package wordbook3;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

/** 创建数据库并提供 SQLite 连接。 */
public final class DatabaseManager {
    private static final String CREATE_WORDS_TABLE = """
            CREATE TABLE IF NOT EXISTS words (/* 建表 */
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                word TEXT NOT NULL COLLATE NOCASE UNIQUE,
                meaning TEXT NOT NULL,
                sentence TEXT NOT NULL DEFAULT '',
                voice TEXT NOT NULL DEFAULT '',
                created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
            )
            """;

    private static final String INSERT_STARTER_WORD = """
            INSERT OR IGNORE INTO words(word, meaning, sentence, voice)
            VALUES (?, ?, ?, ?)
            """;

    private final Path databasePath;

    public DatabaseManager(Path databasePath) {
        this.databasePath = databasePath.toAbsolutePath().normalize();
    }

    public void initialize() throws SQLException, IOException {
        try (Connection connection = openConnection();/* 打开 SQLite 连接 */
            Statement statement = connection.createStatement()) {/* 创建执行器 */
            statement.execute(CREATE_WORDS_TABLE);/* 执行建表语句 */
            insertStarterWords(connection);/* 写入默认数据 */
        }
    }

    public Connection openConnection() throws SQLException {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException exception) {
            throw new SQLException("缺少 SQLite JDBC 驱动", exception);
        }
        return DriverManager.getConnection("jdbc:sqlite:" + databasePath);
    }

    private void insertStarterWords(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT_STARTER_WORD)) {
            insertStarterWord(statement, "apple", "苹果", "An apple is a healthy fruit.", "apple.mp3");
            insertStarterWord(statement, "application", "申请；应用程序", "Please complete the application form.", "application.mp3");
            insertStarterWord(statement, "appreciate", "感激；欣赏", "I appreciate your help.", "appreciate.mp3");
            insertStarterWord(statement, "book", "书；预订", "This book is easy to read.", "book.mp3");
            insertStarterWord(statement, "language", "语言", "English is an international language.", "language.mp3");
            insertStarterWord(statement, "computer", "计算机", "The computer is on the desk.", "computer.mp3");
            insertStarterWord(statement, "practice", "练习", "Practice makes progress.", "practice.mp3");
            insertStarterWord(statement, "improve", "改善；提高", "Reading can improve your English.", "improve.mp3");
            insertStarterWord(statement, "journey", "旅行", "The journey takes two hours.", "journey.mp3");
            insertStarterWord(statement, "knowledge", "知识", "Knowledge opens new doors.", "knowledge.mp3");
            insertStarterWord(statement, "success", "成功", "Hard work leads to success.", "success.mp3");
            insertStarterWord(statement, "courage", "勇气", "Courage helps us face difficulty.", "courage.mp3");
            insertStarterWord(statement, "opportunity", "机会", "This course is a good opportunity.", "opportunity.mp3");
            insertStarterWord(statement, "comfortable", "舒适的", "This chair is comfortable.", "comfortable.mp3");
            insertStarterWord(statement, "summary", "总结", "Please write a short summary.", "summary.mp3");
        }
    }

    private void insertStarterWord(
            PreparedStatement statement,
            String word,
            String meaning,
            String sentence,
            String voice
    ) throws SQLException {
        statement.setString(1, word);
        statement.setString(2, meaning);
        statement.setString(3, sentence);
        statement.setString(4, voice);
        statement.executeUpdate();
    }
}
