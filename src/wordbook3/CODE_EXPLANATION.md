# wordbook3 简化版代码详细说明

这份文档对应 `src/wordbook3` 中的实际源码。阅读时可以一边看 Java 文件，一边按下面的顺序对照。

## 1. 程序结构和运行目录

### 1.1 软件包位置

```text
src/wordbook3/
├─ Wordbook3Application.java  程序入口
├─ Wordbook3Frame.java        Swing 窗口和按钮事件
├─ DatabaseManager.java       SQLite 数据库初始化和连接
├─ WordRepository.java        单词增删改查
├─ Word.java                  单词数据对象
├─ SearchMode.java            查询方式枚举
├─ AudioPlayer.java           MP3 播放
├─ data/                      数据库文件
├─ audio/                     MP3 文件
└─ CODE_EXPLANATION.md        本说明文档
```

每个 Java 文件开头的：

```java
package wordbook3;
```

表示这些类都属于 `wordbook3` 软件包。文件夹名称和包名保持一致，Java 编译器才能正确找到类，也方便在 Eclipse 中按包管理代码。

程序编译后，脚本会把资源复制成：

```text
bin/wordbook3/
├─ *.class
├─ data/wordbook-simple.db
├─ audio/*.mp3
└─ CODE_EXPLANATION.md
```

运行时只使用 `bin/wordbook3` 下面的 `data` 和 `audio`。程序不会再向项目根目录寻找这两个文件夹。

### 1.2 启动后的调用关系

```text
Wordbook3Application.main
        │
        ├─ resolvePackageHome()
        ├─ DatabaseManager.initialize()
        │       └─ 创建表、写入演示单词
        ├─ WordRepository
        ├─ AudioPlayer
        └─ Wordbook3Frame
                ├─ WordRepository：新增、修改、删除、查询
                └─ AudioPlayer：播放 MP3
```

这样的分工是为了让窗口类只处理界面。数据库细节放进 `DatabaseManager` 和 `WordRepository`，音频细节放进 `AudioPlayer`，以后修改其中一部分时不会把所有代码混在一起。

## 2. `Wordbook3Application.java`：程序入口

### 2.1 导入的类

```java
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
```

这里使用的主要类有以下作用：

| 类 | 用途 | 为什么需要 |
| --- | --- | --- |
| `JOptionPane` | 弹出错误提示框 | 启动失败时让使用者看到原因 |
| `SwingUtilities` | 把界面任务放入 Swing 事件线程 | Swing 组件应在指定线程中创建和更新 |
| `UIManager` | 设置系统外观 | 让窗口按钮尽量使用 Windows 的外观 |
| `Path` | 表示文件或文件夹路径 | 比手写字符串拼接路径更安全、跨平台 |
| `Files` | 检查文件夹和文件 | 启动前确认包内资源真的存在 |
| `URISyntaxException` | 处理类路径转换错误 | `getLocation()` 转换为 `Path` 时可能失败 |

### 2.2 类声明和私有构造方法

```java
public final class Wordbook3Application {
    private Wordbook3Application() {
    }
```

- `public`：允许 Java 启动器访问这个类。
- `final`：入口类不需要被继承，禁止继承可以表达这个意图。
- 私有构造方法：这个类只提供静态的 `main` 方法，不需要创建对象。写成 `private` 可以防止别人误写 `new Wordbook3Application()`。

### 2.3 `main` 方法：按照顺序组装程序

```java
public static void main(String[] args) {
    try {
        Path packageHome = resolvePackageHome();
        Path dataDirectory = requireDirectory(packageHome.resolve("data"), "data");
        Path audioDirectory = requireDirectory(packageHome.resolve("audio"), "audio");
```

#### `public static void main(String[] args)`

- `public`：Java 虚拟机需要从外部调用它。
- `static`：启动时还没有任何对象，所以入口必须能直接通过类调用。
- `void`：程序入口不需要返回值。
- `String[] args`：接收命令行参数。本程序目前没有使用参数，但 Java 入口方法必须保留这个形式。

#### `resolvePackageHome()`

这个方法返回 `wordbook3` 包的运行目录。这里没有使用当前工作目录，也没有向上级目录搜索，因为那样会受到 Eclipse、PowerShell 当前目录变化的影响，可能误用项目根目录的资源。

#### `packageHome.resolve("data")`

`resolve` 是 `Path` 的方法，用来在当前路径后面追加一个子路径。它比：

```java
packageHome.toString() + "\\data"
```

更适合处理路径，因为 `Path` 会负责路径分隔符和规范化。

#### `requireDirectory(...)`

这个方法使用 `Files.isDirectory` 检查文件夹。如果包内资源缺失，程序会尽早报出明确错误，而不是等到查询或播放时才出现难以判断的问题。

```java
        DatabaseManager databaseManager = new DatabaseManager(
                dataDirectory.resolve("wordbook-simple.db")
        );
        databaseManager.initialize();

        WordRepository repository = new WordRepository(databaseManager);
        AudioPlayer audioPlayer = new AudioPlayer(audioDirectory);
        configureLookAndFeel();
        SwingUtilities.invokeLater(() -> new Wordbook3Frame(repository, audioPlayer).setVisible(true));
```

这段代码是依赖对象的组装过程：

1. 创建 `DatabaseManager`，告诉它数据库文件在哪里。
2. 调用 `initialize()` 建表并写入演示数据。
3. 创建 `WordRepository`，让它通过数据库管理器执行 SQL。
4. 创建 `AudioPlayer`，让它只使用包内的 `audio` 文件夹。
5. 设置窗口外观。
6. 创建并显示 Swing 窗口。

数据库初始化必须在窗口显示前完成。如果数据库连接失败，程序就不应该打开一个不能正常工作的空窗口。

#### `SwingUtilities.invokeLater(...)`

`invokeLater` 会把一段任务放到 Swing 事件分发线程中执行。括号中的 Lambda：

```java
() -> new Wordbook3Frame(repository, audioPlayer).setVisible(true)
```

意思是：稍后创建窗口，并把窗口设为可见。Swing 的按钮、表格、文本框等组件都由这个线程管理，这样可以减少界面线程之间的冲突。

#### `try/catch`

启动阶段可能出现数据库驱动缺失、数据库文件无法打开、资源文件夹不存在等异常，因此统一放在 `try` 中。发生异常时：

```java
        } catch (Exception exception) {
            exception.printStackTrace();
            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
                    null,
                    "启动失败：" + exception.getMessage(),
                    "启动失败",
                    JOptionPane.ERROR_MESSAGE
            ));
        }
```

- `printStackTrace()` 把完整错误栈打印到控制台，便于排查。
- `exception.getMessage()` 提取简短原因，显示给使用者。
- `JOptionPane.showMessageDialog` 弹出错误框。
- 这个错误框也通过 `invokeLater` 显示，因为它同样是 Swing 界面操作。

### 2.4 `resolvePackageHome()`：固定找到 wordbook3 目录

```java
static Path resolvePackageHome() {
    try {
        Path codeLocation = Path.of(Wordbook3Application.class
                .getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .toURI()).toAbsolutePath().normalize();
```

这几次方法调用是从“当前类被加载的位置”得到路径：

1. `Wordbook3Application.class`：取得入口类对应的 `Class` 对象。
2. `getProtectionDomain()`：取得类的运行保护域。
3. `getCodeSource()`：取得类代码来源。
4. `getLocation()`：取得编译输出目录或 JAR 文件的位置。
5. `toURI()`：把 URL 位置转换为 URI，减少 Windows 路径中空格等字符带来的问题。
6. `Path.of(...)`：把 URI 转成 Java 路径对象。
7. `toAbsolutePath()`：确保后面的路径是绝对路径。
8. `normalize()`：整理路径中的 `.` 和 `..`。

如果通过 Eclipse 或脚本运行，类通常位于 `bin/wordbook3`，所以可以从这里直接定位资源，而不必猜测当前工作目录。

```java
        if (Files.isRegularFile(codeLocation)) {
            codeLocation = codeLocation.getParent();
        }
        if (codeLocation.getFileName() != null
                && codeLocation.getFileName().toString().equals("wordbook3")) {
            return codeLocation;
        }
        return codeLocation.resolve("wordbook3");
```

- `Files.isRegularFile`：如果程序将来打成 JAR，代码位置可能是一个文件，此时先取它的父目录。
- `getFileName()`：取得当前路径最后一段。
- `equals("wordbook3")`：如果最后一段本身已经是包目录，就直接返回。
- `resolve("wordbook3")`：否则在代码位置下面追加 `wordbook3`。

方法最后固定返回一个名为 `wordbook3` 的目录，因此后续只会使用这个目录下的 `data` 和 `audio`。

```java
    } catch (URISyntaxException exception) {
        throw new IllegalStateException("无法确定 wordbook3 包目录", exception);
    }
}
```

`URISyntaxException` 属于检查型异常，必须捕获或声明。这里将它包装成 `IllegalStateException`，因为路径无法确定代表程序运行环境出了问题，不适合继续运行。

### 2.5 `requireDirectory()` 和 `configureLookAndFeel()`

```java
private static Path requireDirectory(Path directory, String name) throws IOException {
    if (!Files.isDirectory(directory)) {
        throw new IOException("wordbook3 包内缺少 " + name + " 文件夹：" + directory);
    }
    return directory;
}
```

- `private`：只在入口类内部使用。
- `static`：不依赖入口类对象。
- `Files.isDirectory`：确认路径是文件夹，而不只是路径存在。
- `throw new IOException(...)`：用带有具体路径的错误信息中断启动。

```java
private static void configureLookAndFeel() {
    try {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
    } catch (Exception ignored) {
        // 使用 Swing 默认外观。
    }
}
```

`UIManager.getSystemLookAndFeelClassName()` 取得操作系统推荐的 Swing 外观，`setLookAndFeel` 应用它。外观设置失败不影响单词簿的主要功能，所以这里只忽略异常，让程序继续使用 Swing 默认外观。

## 3. `DatabaseManager.java`：数据库初始化和连接

### 3.1 导入类

```java
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
```

- `Files`、`Path`：创建数据库所在的文件夹。
- `Connection`：表示一次数据库连接。
- `DriverManager`：根据 JDBC 地址创建连接。
- `Statement`：执行不带参数的建表语句。
- `PreparedStatement`：执行带参数的插入语句。
- `SQLException`：报告数据库操作失败。

### 3.2 建表 SQL

```java
private static final String CREATE_WORDS_TABLE = """
        CREATE TABLE IF NOT EXISTS words (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            word TEXT NOT NULL COLLATE NOCASE UNIQUE,
            meaning TEXT NOT NULL,
            sentence TEXT NOT NULL DEFAULT '',
            voice TEXT NOT NULL DEFAULT '',
            created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
        )
        """;
```

这里使用 Java 的文本块 `"""` 保存多行 SQL。这样比把整条 SQL 写成一行字符串更容易阅读，也不需要大量的 `+` 拼接。

字段解释如下：

| 字段 | 作用 | 这样设计的原因 |
| --- | --- | --- |
| `id` | 记录编号 | `PRIMARY KEY` 保证每条记录有唯一 ID，`AUTOINCREMENT` 自动编号 |
| `word` | 英文单词 | `NOT NULL` 禁止数据库空值，`UNIQUE` 禁止重复 |
| `COLLATE NOCASE` | 比较单词时忽略大小写 | `Apple` 和 `apple` 不会被当成两条不同单词 |
| `meaning` | 中文释义 | 单词和释义是必填内容 |
| `sentence` | 英文例句 | `DEFAULT ''` 允许例句暂时为空 |
| `voice` | 音频文件名 | 允许暂时没有发音文件 |
| `created_at` | 创建时间 | 数据库插入时自动记录时间 |

`IF NOT EXISTS` 很重要：程序每次启动都可以执行建表语句，已有表不会被删除或重复创建。

```java
private static final String INSERT_STARTER_WORD = """
        INSERT OR IGNORE INTO words(word, meaning, sentence, voice)
        VALUES (?, ?, ?, ?)
        """;
```

问号是参数占位符。默认单词也使用预编译 SQL，这样插入文本时不需要手动处理引号，并且和普通用户新增使用同样安全的方式。

### 3.3 字段和构造方法

```java
private final Path databasePath;

public DatabaseManager(Path databasePath) {
    this.databasePath = databasePath.toAbsolutePath().normalize();
}
```

- `private`：数据库路径不应被外部随意替换。
- `final`：对象创建后路径不再改变。
- 构造方法中立即调用 `toAbsolutePath().normalize()`，使后续连接数据库时不受当前工作目录变化影响。

### 3.4 `initialize()`：创建文件夹、建表、插入演示数据

```java
public void initialize() throws SQLException, IOException {
    Path parent = databasePath.getParent();
    if (parent != null) {
        Files.createDirectories(parent);
    }
```

- `getParent()` 取得数据库文件的上级目录。
- `Files.createDirectories` 可以一次创建不存在的多级目录；如果目录已经存在，也不会报错。
- `parent != null` 是对路径没有父目录的情况做保护。

```java
    try (Connection connection = openConnection();
         Statement statement = connection.createStatement()) {
        statement.execute(CREATE_WORDS_TABLE);
        insertStarterWords(connection);
    }
}
```

这里使用 `try-with-resources`。`Connection` 和 `Statement` 都实现了 `AutoCloseable`，代码离开 `try` 后会自动关闭，即使中途抛出异常也会关闭。数据库连接如果不关闭，反复启动或操作可能造成文件锁定和资源泄漏。

- `openConnection()` 打开 SQLite 连接。
- `createStatement()` 创建执行器。
- `execute(...)` 执行建表语句。
- `insertStarterWords(...)` 写入默认数据。

### 3.5 `openConnection()`：加载驱动并连接 SQLite

```java
public Connection openConnection() throws SQLException {
    try {
        Class.forName("org.sqlite.JDBC");
    } catch (ClassNotFoundException exception) {
        throw new SQLException("缺少 SQLite JDBC 驱动", exception);
    }
    return DriverManager.getConnection("jdbc:sqlite:" + databasePath);
}
```

- `Class.forName("org.sqlite.JDBC")` 主动加载 SQLite JDBC 驱动类。没有 `sqlite-jdbc` JAR 时会抛出 `ClassNotFoundException`。
- 捕获后改成中文 `SQLException`，让界面提示比原始类名更容易理解。
- `DriverManager.getConnection(...)` 根据 `jdbc:sqlite:` 地址打开本地数据库文件。

每次仓库操作都会申请一个短连接，操作结束后由 `try-with-resources` 关闭。这个程序规模较小，不需要额外维护连接池。

### 3.6 插入演示数据

```java
private void insertStarterWords(Connection connection) throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement(INSERT_STARTER_WORD)) {
        insertStarterWord(statement, "apple", "苹果", "An apple is a healthy fruit.", "apple.mp3");
        // 其余默认单词使用相同方式插入
    }
}
```

这里复用同一个 `PreparedStatement`，只是不断更换参数。这样比每个单词重新创建一个 SQL 执行器更简洁。

`INSERT OR IGNORE` 配合 `UNIQUE` 使用：首次启动写入演示数据，之后再次启动遇到已有单词时忽略插入，不会重复添加。

```java
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
```

- `setString(1, word)` 把第一个问号绑定为单词。
- 参数编号从 1 开始，不是从 0 开始。
- `executeUpdate()` 执行插入，并返回受影响的行数；这里不需要使用返回值。
- 参数绑定会自动处理单引号等特殊字符，因此比字符串拼接 SQL 更可靠。

## 4. `Word.java`：表示一条单词记录

```java
public record Word(
        long id,
        String word,
        String meaning,
        String sentence,
        String voice,
        String createdAt
) {
}
```

这里使用 Java 的 `record`，适合表示“只保存数据”的对象。编译器会自动生成：

- 与字段顺序对应的构造方法；
- `id()`、`word()`、`meaning()` 等读取方法；
- `equals`、`hashCode` 和 `toString`。

例如：

```java
Word word = new Word(1, "apple", "苹果", "An apple...", "apple.mp3", "...");
String text = word.word();
```

使用 `record` 可以省略大量 getter、setter 和构造方法代码。字段没有 setter，查询结果创建后不会被界面随意修改，适合在数据库层和界面层之间传递。

## 5. `SearchMode.java`：表示查询方式

```java
public enum SearchMode {
    EXACT("精确查询"),
    PREFIX("前缀查询"),
    SUFFIX("后缀查询"),
    CONTAINS("包含查询");
```

这里使用 `enum`，因为查询方式只有四种固定值。与直接使用字符串相比，枚举可以避免写出不存在的模式，例如 `"前缀查詢"` 或 `"prefixx"`。

```java
private final String label;

SearchMode(String label) {
    this.label = label;
}

@Override
public String toString() {
    return label;
}
```

- 每个枚举值保存一个中文显示名称。
- `toString()` 被重写后，`JComboBox` 直接显示“精确查询”等中文，而程序内部仍使用 `SearchMode.EXACT` 这样的清晰常量。
- `final` 表示显示名称创建后不变。

## 6. `WordRepository.java`：单词数据库操作

### 6.1 SQL 常量

```java
private static final String SELECT_ALL = """
        SELECT id, word, meaning, sentence, voice, created_at
        FROM words
        ORDER BY word COLLATE NOCASE
        """;
```

查询只选择界面需要的列，并按单词排序。`private static final` 表示这条固定 SQL 属于类本身，不需要每次创建对象时重新生成。

```java
private static final String SEARCH_WORDS = """
        SELECT id, word, meaning, sentence, voice, created_at
        FROM words
        WHERE word LIKE ? ESCAPE '\\'
        ORDER BY word COLLATE NOCASE
        """;
```

- `LIKE ?` 让查询词通过参数传入。
- `ESCAPE '\\'` 指定反斜杠是转义字符，这样代码可以把用户输入中的 `%` 和 `_` 当作普通字符。
- 如果没有转义，用户输入 `%` 会代表任意长度文本，查询范围会被意外扩大。

新增、修改、删除也使用带问号的预编译 SQL：

```java
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
```

这样写有两个原因：

1. SQL 结构和用户输入分开，单引号等字符不会破坏 SQL。
2. 同一条 SQL 可以反复绑定不同数据，代码更容易维护。

### 6.2 构造方法和 `findAll()`

```java
private final DatabaseManager databaseManager;

public WordRepository(DatabaseManager databaseManager) {
    this.databaseManager = databaseManager;
}
```

仓库不自己决定数据库文件位置，而是接收一个 `DatabaseManager`。这种写法叫依赖注入：入口类把依赖传进来，仓库只负责数据库操作，职责更清楚，也方便测试时换成临时数据库。

```java
public List<Word> findAll() throws SQLException {
    try (Connection connection = databaseManager.openConnection();
         PreparedStatement statement = connection.prepareStatement(SELECT_ALL);
         ResultSet resultSet = statement.executeQuery()) {
        return mapWords(resultSet);
    }
}
```

- `List<Word>`：返回多条单词记录。
- `prepareStatement(SELECT_ALL)`：准备查询语句。
- `executeQuery()`：执行查询并返回 `ResultSet`。
- `ResultSet` 可以理解为数据库结果的游标。
- `mapWords` 把每一行转换成一个 `Word`。
- 三个资源都放进 `try-with-resources`，结束后自动关闭。

### 6.3 `insert()`：新增单词

```java
public void insert(String word, String meaning, String sentence, String voice) throws SQLException {
    try (Connection connection = databaseManager.openConnection();
         PreparedStatement statement = connection.prepareStatement(INSERT_WORD)) {
        bindWord(statement, word, meaning, sentence, voice);
        statement.executeUpdate();
    } catch (SQLException exception) {
        throw translateConstraintViolation(exception);
    }
}
```

`bindWord` 统一绑定和检查四个字段，避免 `insert` 和 `update` 各写一遍相同代码。

`executeUpdate()` 用于会改变数据库的 SQL，例如 `INSERT`、`UPDATE` 和 `DELETE`。

如果数据库报告单词重复，`translateConstraintViolation` 会把 SQLite 的英文约束错误改成“单词已存在”，界面更容易理解。

### 6.4 `update()`：修改单词

```java
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
```

前四个问号由 `bindWord` 填入，第五个问号由 `setLong(5, id)` 填入。`WHERE id = ?` 保证只修改选中的记录。

`executeUpdate()` 返回受影响的行数。如果返回 0，说明这个 ID 没有对应记录，所以抛出明确提示，而不是悄悄当作修改成功。

### 6.5 `delete()`：删除单词

```java
public void delete(long id) throws SQLException {
    try (Connection connection = databaseManager.openConnection();
         PreparedStatement statement = connection.prepareStatement(DELETE_WORD)) {
        statement.setLong(1, id);
        if (statement.executeUpdate() == 0) {
            throw new IllegalArgumentException("要删除的单词不存在");
        }
    }
}
```

删除只需要一个 ID 参数。检查返回行数可以防止记录已经被删除时仍显示“删除成功”。窗口层在调用这个方法之前还会弹出二次确认框。

### 6.6 `search()`：四种查询

```java
public List<Word> search(String keyword, SearchMode mode) throws SQLException {
    String normalizedKeyword = keyword == null ? "" : keyword.trim();
    if (normalizedKeyword.isEmpty()) {
        return findAll();
    }
    if (mode == null) {
        throw new IllegalArgumentException("请选择查询方式");
    }
```

- `keyword == null ? "" : ...`：防止调用者传入 `null` 后发生空指针异常。
- `trim()`：去掉关键字前后的空格，让用户输入 ` app ` 时仍按 `app` 查询。
- 关键字为空时显示全部，使用者不需要额外选择特殊的“全部”模式。
- `mode == null`：防止下拉框没有选中项时执行不完整查询。

```java
    try (Connection connection = databaseManager.openConnection();
         PreparedStatement statement = connection.prepareStatement(SEARCH_WORDS)) {
        statement.setString(1, buildLikePattern(normalizedKeyword, mode));
        try (ResultSet resultSet = statement.executeQuery()) {
            return mapWords(resultSet);
        }
    }
}
```

`buildLikePattern` 只负责生成查询模式，数据库执行仍由本方法负责。分开两个职责后，查询流程和模式规则都更容易阅读。

### 6.7 `buildLikePattern()`：构造 LIKE 模式

```java
private String buildLikePattern(String keyword, SearchMode mode) {
    String escapedKeyword = keyword
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_");
```

`String.replace` 依次处理三个特殊字符：

- 反斜杠先转义，避免后面的转义符产生歧义；
- `%` 转为 `\%`，表示普通百分号；
- `_` 转为 `\_`，表示普通下划线。

处理顺序很重要：反斜杠必须先处理。

```java
    return switch (mode) {
        case EXACT -> escapedKeyword;
        case PREFIX -> escapedKeyword + "%";
        case SUFFIX -> "%" + escapedKeyword;
        case CONTAINS -> "%" + escapedKeyword + "%";
    };
}
```

这是 Java 的表达式 `switch`，可以直接返回结果：

| 模式 | 生成的模式 | 含义 |
| --- | --- | --- |
| `EXACT` | `app` | 只匹配 `app` |
| `PREFIX` | `app%` | 以 `app` 开头 |
| `SUFFIX` | `%app` | 以 `app` 结尾 |
| `CONTAINS` | `%app%` | 任意位置包含 `app` |

`%` 是 SQL `LIKE` 中表示任意长度文本的通配符。

### 6.8 `bindWord()` 和输入处理

```java
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
```

这里把“字段校验”和“参数绑定”集中在一个方法中：

- 单词和中文释义使用 `required`，必须有内容；
- 例句和发音文件名使用 `optional`，允许为空；
- `setString` 将 Java 字符串安全地绑定到 SQL 参数。

```java
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
```

`required` 将 `null` 和只包含空格的输入都当作空值，避免数据库中出现没有意义的单词。`optional` 将可选字段的 `null` 统一成空字符串，和数据库表的 `DEFAULT ''` 保持一致。

### 6.9 `mapWords()`：把 ResultSet 转成 Java 对象

```java
private List<Word> mapWords(ResultSet resultSet) throws SQLException {
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
```

- `new ArrayList<>()` 创建可变结果列表。
- `resultSet.next()` 把游标移动到下一行；没有下一行时返回 `false`。
- `getLong` 读取数字 ID。
- `getString` 读取文本列。
- 每一行创建一个 `Word`，再放进列表。

把 JDBC 的 `ResultSet` 在仓库内部转换掉，窗口类就不需要知道 SQL 和游标的细节。

### 6.10 `translateConstraintViolation()`：转换重复单词错误

```java
private SQLException translateConstraintViolation(SQLException exception) {
    String message = exception.getMessage();
    if (message != null && message.contains("UNIQUE constraint failed: words.word")) {
        return new SQLException("单词已存在", exception);
    }
    return exception;
}
```

SQLite 的原始错误信息通常是英文。方法检查错误是否来自 `words.word` 的唯一约束：

- 如果是，返回带中文信息的新 `SQLException`；
- 如果不是，原样返回，避免掩盖真正的数据库问题。

## 7. `AudioPlayer.java`：播放包内 MP3

### 7.1 导入类

```java
import javazoom.jl.decoder.JavaLayerException;
import javazoom.jl.player.Player;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;
```

- `Player` 和 `JavaLayerException` 来自项目中的 JLayer JAR，负责解码 MP3。
- `BufferedInputStream` 为文件输入增加缓冲。
- `InvalidPathException` 处理非法文件名。
- `Consumer<String>` 表示一个接收错误信息的回调函数。

### 7.2 构造方法

```java
private final Path audioDirectory;

public AudioPlayer(Path audioDirectory) {
    this.audioDirectory = audioDirectory.toAbsolutePath().normalize();
}
```

播放器保存规范化后的 `audio` 目录。使用 `final` 后，播放器不会在运行过程中突然换到另一个目录。

### 7.3 `playAsync()`：先检查，再异步播放

```java
public boolean playAsync(String voiceFileName, Consumer<String> onFailure) {
    Objects.requireNonNull(onFailure, "onFailure");
    Path audioFile;
    try {
        audioFile = resolveAudioFile(voiceFileName);
    } catch (IllegalArgumentException exception) {
        onFailure.accept(exception.getMessage());
        return false;
    }
```

- `Objects.requireNonNull`：调用者必须提供错误回调，否则播放失败时没有地方显示错误。
- `resolveAudioFile`：统一检查文件名和文件路径。
- 检查失败时调用 `onFailure.accept(...)`，然后返回 `false`。
- 把错误在当前按钮事件中快速处理，不启动无效的播放线程。

```java
    Thread playerThread = new Thread(
            () -> playSafely(audioFile, onFailure),
            "wordbook3-audio"
    );
    playerThread.setDaemon(true);
    playerThread.start();
    return true;
}
```

- `new Thread(Lambda, name)` 创建后台播放线程，并给线程取一个便于调试的名字。
- `setDaemon(true)`：主窗口关闭后，未结束的音频播放不会阻止 Java 程序退出。
- `start()` 真正启动线程；不能调用 `run()` 代替，否则音频会在 Swing 事件线程中同步播放，窗口可能卡住。
- 返回 `true` 表示已经启动播放任务，窗口可以显示“正在播放”。

### 7.4 `resolveAudioFile()`：限制只能访问包内音频

```java
Path resolveAudioFile(String voiceFileName) {
    String normalizedName = voiceFileName == null ? "" : voiceFileName.trim();
    if (normalizedName.isEmpty()) {
        throw new IllegalArgumentException("请先填写发音文件名");
    }
```

把 `null` 和空白文件名统一处理，避免 `Path.of(null)` 直接报空指针错误，并向使用者给出明确提示。

```java
try {
    Path namePath = Path.of(normalizedName);
    if (namePath.isAbsolute()
            || namePath.getNameCount() != 1
            || !namePath.getFileName().toString().equals(normalizedName)) {
        throw new IllegalArgumentException("发音文件名不能包含路径");
    }
} catch (InvalidPathException exception) {
    throw new IllegalArgumentException("发音文件名无效", exception);
}
```

这里要求输入的只是文件名，例如 `apple.mp3`，不允许：

- 绝对路径；
- `audio/apple.mp3` 这样的目录路径；
- `..\other\secret.mp3` 这样的上级目录路径。

`getNameCount() != 1` 检查路径是否含有多段，`getFileName()` 检查最后一段是否和原输入完全一致。这样做是为了防止音频按钮被用来读取 `audio` 目录以外的文件。

```java
if (!normalizedName.toLowerCase(Locale.ROOT).endsWith(".mp3")) {
    throw new IllegalArgumentException("仅支持 MP3 发音文件");
}

Path audioFile = audioDirectory.resolve(normalizedName).normalize();
if (!audioFile.startsWith(audioDirectory)) {
    throw new IllegalArgumentException("发音文件名不能包含路径");
}
if (!Files.isRegularFile(audioFile)) {
    throw new IllegalArgumentException("未找到发音文件：" + normalizedName);
}
return audioFile;
```

- `toLowerCase(Locale.ROOT)`：不受系统语言影响地检查扩展名，允许 `APPLE.MP3`。
- `resolve`：把文件名追加到固定的 `audioDirectory`。
- `normalize`：整理 `.` 和 `..`。
- `startsWith(audioDirectory)`：再次确认规范化后的路径仍在包内音频目录中。
- `Files.isRegularFile`：确认它是普通文件，而不是文件夹。

虽然前面已经禁止目录输入，`startsWith` 仍然保留作为最后一道路径边界检查。安全检查分两层写，后续有人改动前面的判断时，也不容易直接绕过目录限制。

### 7.5 `playSafely()`：捕获播放错误

```java
private void playSafely(Path audioFile, Consumer<String> onFailure) {
    try (BufferedInputStream audioInput = new BufferedInputStream(Files.newInputStream(audioFile))) {
        new Player(audioInput).play();
    } catch (IOException | JavaLayerException exception) {
        String detail = exception.getMessage();
        onFailure.accept("无法播放发音文件：" + (detail == null ? "MP3 文件或音频设备不可用" : detail));
    }
}
```

- `Files.newInputStream` 打开 MP3 文件。
- `BufferedInputStream` 提供缓冲读取。
- `new Player(audioInput).play()` 由 JLayer 解码并播放，直到播放结束或失败。
- `try-with-resources` 播放结束后自动关闭文件流。
- `catch (IOException | JavaLayerException)` 是多异常捕获，同时处理读文件错误和 MP3 解码错误。
- `Consumer.accept` 将错误交给窗口层；播放线程不直接操作 Swing 控件，窗口层再用 `SwingUtilities.invokeLater` 显示提示。

## 8. `Wordbook3Frame.java`：Swing 界面

这是代码量最大的文件，但职责很集中：创建窗口控件、绑定按钮事件、把仓库结果显示在表格里。

### 8.1 类声明和字段

```java
public final class Wordbook3Frame extends JFrame {
    private static final String[] TABLE_COLUMNS = {
            "ID", "单词", "释义", "例句", "发音文件", "创建时间"
    };
```

- `extends JFrame`：让当前类成为一个 Swing 窗口。
- `final`：主窗口不需要被继承。
- `static final`：表头是固定常量，所有窗口实例共用一份。

```java
private final WordRepository repository;
private final AudioPlayer audioPlayer;
private final JTextField searchField = new JTextField(18);
private final JComboBox<SearchMode> searchModeBox = new JComboBox<>(SearchMode.values());
private final JTextField wordField = new JTextField(18);
private final JTextField meaningField = new JTextField(18);
private final JTextArea sentenceArea = new JTextArea(3, 18);
private final JTextField voiceField = new JTextField(18);
```

这些字段是窗口上的控件或服务对象：

- `repository`：访问数据库；
- `audioPlayer`：播放 MP3；
- `JTextField`：单行输入框；
- `JTextArea`：多行例句输入框；
- `JComboBox<SearchMode>`：查询方式下拉框；
- `SearchMode.values()`：取得全部四个枚举值，直接填入下拉框。

```java
private final DefaultTableModel tableModel = new DefaultTableModel(TABLE_COLUMNS, 0) {
    @Override
    public boolean isCellEditable(int row, int column) {
        return false;
    }
};
private final JTable wordTable = new JTable(tableModel);
private final JLabel statusLabel = new JLabel("准备就绪");
private Long selectedId;
```

- `DefaultTableModel` 保存表格中的行和列数据。
- `new DefaultTableModel(...) { ... }` 是匿名子类，用来重写 `isCellEditable`。
- 返回 `false` 表示表格只用于查看，修改必须通过下方编辑框和“修改”按钮完成，避免表格内容和数据库内容不一致。
- `JTable` 显示表格模型。
- `JLabel` 显示当前状态。
- `Long selectedId` 用包装类而不是 `long`，因为 `null` 可以表示“当前没有选中记录”。

### 8.2 构造方法

```java
public Wordbook3Frame(WordRepository repository, AudioPlayer audioPlayer) {
    super("英语单词簿 - wordbook3");
    this.repository = repository;
    this.audioPlayer = audioPlayer;
    initializeWindow();
    installEventHandlers();
    refreshAll();
}
```

- `super(...)` 设置窗口标题。
- 保存传入的仓库和播放器，这就是依赖注入。
- `initializeWindow()` 创建布局和控件。
- `installEventHandlers()` 绑定表格选择事件。
- `refreshAll()` 在窗口第一次显示前加载数据库记录。

构造方法只负责按顺序组装窗口，不把所有布局和数据库代码都塞在一起，因此更容易定位问题。

### 8.3 `initializeWindow()`：设置窗口基本属性

```java
setDefaultCloseOperation(EXIT_ON_CLOSE);
setMinimumSize(new Dimension(920, 620));
```

- 关闭窗口时退出程序。
- `Dimension` 设置窗口最小宽高，防止窗口缩得太小后控件重叠。

```java
JPanel root = new JPanel(new BorderLayout(8, 8));
root.setBorder(new EmptyBorder(10, 10, 10, 10));
root.add(createSearchPanel(), BorderLayout.NORTH);
root.add(createContentPanel(), BorderLayout.CENTER);
root.add(createBottomPanel(), BorderLayout.SOUTH);
setContentPane(root);
```

- `JPanel` 是放置其他控件的面板。
- `BorderLayout` 把窗口分成上、中央、下三个区域，并设置区域间距 8 像素。
- `EmptyBorder` 提供窗口内边距。
- 查询区放 `NORTH`，表格和编辑区放 `CENTER`，按钮放 `SOUTH`。
- `setContentPane` 把这个根面板设为窗口内容。

```java
wordTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
wordTable.setAutoCreateRowSorter(true);
wordTable.getColumnModel().getColumn(0).setMinWidth(0);
wordTable.getColumnModel().getColumn(0).setMaxWidth(0);
wordTable.getColumnModel().getColumn(0).setPreferredWidth(0);
sentenceArea.setLineWrap(true);
sentenceArea.setWrapStyleWord(true);
setSize(1060, 680);
setLocationRelativeTo(null);
```

- `SINGLE_SELECTION`：一次只允许选择一条记录，和修改、删除一条记录的操作相符。
- `setAutoCreateRowSorter(true)`：允许点击表头排序。
- 第 0 列 ID 只供程序识别，不需要显示给用户，因此把宽度设为 0，但仍保留在模型里。
- `setLineWrap(true)`：例句太长时自动换行。
- `setWrapStyleWord(true)`：尽量按单词边界换行，提高英文例句可读性。
- `setSize` 设置初始尺寸。
- `setLocationRelativeTo(null)` 让窗口显示在屏幕中央。

### 8.4 `createSearchPanel()`：查询区

```java
private JPanel createSearchPanel() {
    JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
    panel.setBorder(BorderFactory.createTitledBorder("单词查询"));
    panel.add(new JLabel("关键字"));
    panel.add(searchField);
    panel.add(searchModeBox);
    panel.add(button("查询", this::refreshSearch));
    panel.add(button("显示全部", this::refreshAll));
    return panel;
}
```

- `FlowLayout` 让控件从左到右排列。
- `createTitledBorder` 给查询区加标题。
- `this::refreshSearch` 是方法引用，表示点击“查询”时调用当前窗口的 `refreshSearch()`。
- “显示全部”直接调用 `refreshAll()`，不需要重复写一个新的事件处理方法。

### 8.5 `createContentPanel()` 和 `createEditorPanel()`

```java
private JPanel createContentPanel() {
    JPanel panel = new JPanel(new BorderLayout(8, 8));
    JScrollPane tableScrollPane = new JScrollPane(wordTable);
    tableScrollPane.setBorder(BorderFactory.createTitledBorder("查询结果"));
    panel.add(tableScrollPane, BorderLayout.CENTER);
    panel.add(createEditorPanel(), BorderLayout.SOUTH);
    return panel;
}
```

`JScrollPane` 给表格提供滚动条。单词数量变多时，表格仍然可以查看所有行。中央区域的下方放编辑面板，所以表格和编辑框同时可见。

```java
private JPanel createEditorPanel() {
    JPanel panel = new JPanel(new GridBagLayout());
    panel.setBorder(BorderFactory.createTitledBorder("单词详情"));
    GridBagConstraints constraints = new GridBagConstraints();
    constraints.insets = new Insets(4, 4, 4, 4);
    constraints.anchor = GridBagConstraints.WEST;
```

`GridBagLayout` 适合“标签 + 输入框”这种不规则表单。`GridBagConstraints` 描述每个控件的位置、间距、填充方式和如何分配多余空间。

```java
addField(panel, constraints, 0, 0, "单词", wordField);
addField(panel, constraints, 2, 0, "中文释义", meaningField);
addField(panel, constraints, 0, 1, "发音文件名", voiceField);
```

三个输入框都通过 `addField` 添加。把重复的“添加标签、再添加输入框”提取成方法，可以减少重复布局代码。

```java
constraints.gridx = 0;
constraints.gridy = 2;
constraints.anchor = GridBagConstraints.NORTHWEST;
panel.add(new JLabel("例句"), constraints);
constraints.gridx = 1;
constraints.gridwidth = 3;
constraints.weightx = 1.0;
constraints.weighty = 1.0;
constraints.fill = GridBagConstraints.BOTH;
panel.add(new JScrollPane(sentenceArea), constraints);
return panel;
```

例句使用多行文本区，并包在 `JScrollPane` 中。`gridwidth = 3` 让它横跨多个网格，`weightx` 和 `weighty` 让它在窗口变大时分到多余空间，`BOTH` 允许横向和纵向填充。

### 8.6 `addField()`：复用表单布局

```java
private void addField(
        JPanel panel,
        GridBagConstraints constraints,
        int x,
        int y,
        String label,
        JTextField field
) {
    constraints.gridx = x;
    constraints.gridy = y;
    constraints.weightx = 0;
    constraints.fill = GridBagConstraints.NONE;
    panel.add(new JLabel(label), constraints);

    constraints.gridx = x + 1;
    constraints.weightx = 1.0;
    constraints.fill = GridBagConstraints.HORIZONTAL;
    panel.add(field, constraints);
}
```

先放标签，再把 `x` 加 1 放输入框：

- 标签不需要拉伸，所以 `weightx = 0`；
- 输入框可以横向扩大，所以 `weightx = 1.0`；
- `HORIZONTAL` 让输入框只在水平方向填充。

### 8.7 `createBottomPanel()` 和 `button()`

```java
private JPanel createBottomPanel() {
    JPanel panel = new JPanel(new BorderLayout(8, 0));
    JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
    buttons.add(button("新增", this::runCreate));
    buttons.add(button("修改", this::runUpdate));
    buttons.add(button("删除", this::runDelete));
    buttons.add(button("清空", this::clearEditor));
    buttons.add(button("播放发音", this::playVoice));
    panel.add(buttons, BorderLayout.WEST);
    panel.add(statusLabel, BorderLayout.CENTER);
    return panel;
}
```

按钮集中放在左侧，状态文本放在中央。每个按钮通过方法引用连接到一个操作方法。

```java
private JButton button(String text, Runnable action) {
    JButton button = new JButton(text);
    button.addActionListener(event -> action.run());
    return button;
}
```

- `Runnable` 表示无参数、无返回值的动作。
- `addActionListener` 注册点击监听器。
- `event -> action.run()`：按钮被点击时执行传进来的动作。

这样写后，创建按钮只需要写文字和要执行的方法，不需要为每个按钮重复创建监听器模板。

### 8.8 表格选择：`installEventHandlers()` 和 `fillEditorFromSelection()`

```java
private void installEventHandlers() {
    wordTable.getSelectionModel().addListSelectionListener(this::fillEditorFromSelection);
}
```

`getSelectionModel()` 取得表格的选择模型，`addListSelectionListener` 注册行选择监听器。用户选中一行时就会调用 `fillEditorFromSelection`。

```java
private void fillEditorFromSelection(ListSelectionEvent event) {
    if (event.getValueIsAdjusting() || wordTable.getSelectedRow() < 0) {
        return;
    }
    int row = wordTable.convertRowIndexToModel(wordTable.getSelectedRow());
```

- `getValueIsAdjusting()` 为 `true` 时，说明选择还在变化，暂时不处理，避免重复刷新。
- `getSelectedRow() < 0` 表示没有选中行。
- 表格启用了排序，所以屏幕上的行号可能和模型中的行号不同。`convertRowIndexToModel` 把显示行号转换成模型行号，确保读取的是正确记录。

```java
    selectedId = ((Number) tableModel.getValueAt(row, 0)).longValue();
    wordField.setText(String.valueOf(tableModel.getValueAt(row, 1)));
    meaningField.setText(String.valueOf(tableModel.getValueAt(row, 2)));
    sentenceArea.setText(String.valueOf(tableModel.getValueAt(row, 3)));
    voiceField.setText(String.valueOf(tableModel.getValueAt(row, 4)));
    showStatus("已选择：" + wordField.getText());
}
```

- 第 0 列保存隐藏 ID，用来执行修改和删除。
- 其余列写回编辑区，方便用户查看和修改。
- `String.valueOf` 即使数据为 `null` 也不会直接抛出空指针异常。
- `showStatus` 告诉用户当前选中了哪条记录。

### 8.9 加载全部和查询

```java
private void refreshAll() {
    try {
        refreshTable(repository.findAll());
        showStatus("已加载全部单词");
    } catch (SQLException exception) {
        showError("读取单词失败：" + exception.getMessage());
    }
}
```

`refreshAll` 读取仓库中的所有记录，再交给 `refreshTable` 更新表格。数据库错误会显示提示，而不是让按钮事件直接崩溃。

```java
private void refreshSearch() {
    try {
        List<Word> words = repository.search(
                searchField.getText(),
                (SearchMode) searchModeBox.getSelectedItem()
        );
        refreshTable(words);
        showStatus("查询到 " + words.size() + " 条记录");
    } catch (SQLException | IllegalArgumentException exception) {
        showError(exception.getMessage());
    }
}
```

- `getText()` 读取关键字。
- `getSelectedItem()` 读取下拉框选项。
- `(SearchMode)` 是类型转换，因为 Swing 下拉框返回的是通用 `Object`。
- `List.size()` 得到查询条数。
- 这里同时捕获数据库错误和输入模式错误，统一交给 `showError`。

### 8.10 `refreshTable()`：更新表格模型

```java
private void refreshTable(List<Word> words) {
    tableModel.setRowCount(0);
    selectedId = null;
    for (Word word : words) {
        tableModel.addRow(new Object[]{
                word.id(), word.word(), word.meaning(), word.sentence(), word.voice(), word.createdAt()
        });
    }
    wordTable.clearSelection();
}
```

1. `setRowCount(0)` 删除旧行，避免查询后新旧结果混在一起。
2. `selectedId = null` 清除旧记录的选择状态。
3. `for` 遍历查询结果。
4. `addRow` 把一条 `Word` 转成表格的一行。
5. `clearSelection` 清除界面上的旧选中状态。

每次刷新都清空选择，是为了避免用户以为仍然选中上一批查询结果中的记录。

### 8.11 `runCreate()`：新增按钮

```java
private void runCreate() {
    try {
        repository.insert(wordField.getText(), meaningField.getText(), sentenceArea.getText(), voiceField.getText());
        completeMutation("新增成功");
    } catch (SQLException | IllegalArgumentException exception) {
        showError(exception.getMessage());
    }
}
```

界面读取四个输入框，交给仓库校验并写入数据库。成功后刷新表格和清空输入框；失败时只显示错误，保留输入内容，方便用户修改后重试。

### 8.12 `runUpdate()`：修改按钮

```java
private void runUpdate() {
    if (selectedId == null) {
        showError("请选择要修改的单词");
        return;
    }
    try {
        repository.update(selectedId, wordField.getText(), meaningField.getText(), sentenceArea.getText(), voiceField.getText());
        completeMutation("修改成功");
    } catch (SQLException | IllegalArgumentException exception) {
        showError(exception.getMessage());
    }
}
```

修改必须有目标 ID。先判断 `selectedId`，可以在数据库操作前给出明确提示，也避免执行没有有效条件的修改。

### 8.13 `runDelete()`：删除按钮

```java
private void runDelete() {
    if (selectedId == null) {
        showError("请选择要删除的单词");
        return;
    }
    int option = JOptionPane.showConfirmDialog(
            this,
            "确认删除单词“" + wordField.getText() + "”吗？",
            "确认删除",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE
    );
    if (option != JOptionPane.YES_OPTION) {
        return;
    }
```

删除是不可逆操作，所以调用 `showConfirmDialog` 二次确认。用户选择“否”或关闭对话框时，不执行删除。

```java
    try {
        repository.delete(selectedId);
        completeMutation("删除成功");
    } catch (SQLException | IllegalArgumentException exception) {
        showError(exception.getMessage());
    }
}
```

只有确认后才调用仓库删除，成功后刷新列表并清空编辑区。

### 8.14 清空、播放和状态提示

```java
private void clearEditor() {
    wordTable.clearSelection();
    resetEditorFields();
    showStatus("编辑区已清空");
}

private void resetEditorFields() {
    selectedId = null;
    wordField.setText("");
    meaningField.setText("");
    sentenceArea.setText("");
    voiceField.setText("");
}
```

把真正清空字段的代码单独放进 `resetEditorFields`，因为新增、修改、删除成功后也需要清空。这样避免三处重复写相同代码。

```java
private void playVoice() {
    boolean started = audioPlayer.playAsync(
            voiceField.getText(),
            message -> SwingUtilities.invokeLater(() -> showError(message))
    );
    if (started) {
        showStatus("正在播放：" + voiceField.getText());
    }
}
```

- 把当前编辑框中的发音文件名交给播放器。
- 播放器在后台线程中报告失败，因此回调中使用 `SwingUtilities.invokeLater` 回到 Swing 线程，再显示错误框。
- 只有 `playAsync` 返回 `true` 时才显示“正在播放”，这样空文件名或缺失文件不会被误报成播放成功。

```java
private void completeMutation(String message) {
    refreshAll();
    resetEditorFields();
    showStatus(message);
}

private void showError(String message) {
    String safeMessage = message == null ? "未知错误" : message;
    JOptionPane.showMessageDialog(this, safeMessage, "提示", JOptionPane.ERROR_MESSAGE);
    showStatus(safeMessage);
}

private void showStatus(String message) {
    statusLabel.setText(message);
}
```

- `completeMutation` 统一处理新增、修改、删除成功后的刷新和清空。
- `showError` 用 `message == null ? "未知错误" : message` 防止错误信息本身为空。
- `showMessageDialog` 弹出错误提示。
- `showStatus` 更新窗口底部的状态文字。

## 9. 资源复制脚本 `scripts/compile-wordbook3.ps1`

这不是 Java 类，但它决定了包内资源能否随程序运行。

### 9.1 参数和路径

```powershell
param([switch]$Launch)

$ErrorActionPreference = 'Stop'

$projectRoot = Split-Path -Parent $PSScriptRoot
$sourceRoot = Join-Path $projectRoot 'src\wordbook3'
$binDirectory = Join-Path $projectRoot 'bin'
$binPackage = Join-Path $binDirectory 'wordbook3'
```

- `-Launch` 是可选开关；指定它时，编译完成后自动启动程序。
- `$ErrorActionPreference = 'Stop'` 让复制资源等错误立即停止脚本。
- `Split-Path -Parent` 取得脚本所在项目根目录。
- `Join-Path` 按路径规则拼接目录，不依赖手写斜杠。

### 9.2 编译 Java 文件

```powershell
$sourceFiles = @(Get-ChildItem -LiteralPath $sourceRoot -Filter '*.java' | ForEach-Object FullName)
New-Item -ItemType Directory -Force $binDirectory | Out-Null
if (Test-Path -LiteralPath $binPackage) {
    Remove-Item -LiteralPath $binPackage -Recurse -Force
}
New-Item -ItemType Directory -Force $binPackage | Out-Null

$dependencyClasspath = "$sqliteJar;$jlayerJar"
& (Join-Path $jdk 'javac.exe') -encoding UTF-8 -cp $dependencyClasspath -d $binDirectory $sourceFiles
```

- `Get-ChildItem` 找到 `wordbook3` 包中的 Java 源文件。
- `-d $binDirectory` 指定 `.class` 输出位置，Java 会按包名生成 `bin/wordbook3`。
- `-cp` 加入 SQLite JDBC 和 JLayer 两个第三方库。
- `-encoding UTF-8` 保证中文字符串按 UTF-8 编译。
- 每次编译前清理 `bin/wordbook3`，避免旧的 class 或旧资源残留。

### 9.3 复制包内资源

```powershell
Copy-Item -LiteralPath (Join-Path $sourceRoot 'data') -Destination $binPackage -Recurse -Force
Copy-Item -LiteralPath (Join-Path $sourceRoot 'audio') -Destination $binPackage -Recurse -Force
Copy-Item -LiteralPath (Join-Path $sourceRoot 'CODE_EXPLANATION.md') -Destination $binPackage -Force
```

这三条命令把源码包中的数据库、音频和说明文档复制到编译输出包。程序运行时从 `bin/wordbook3` 定位它们，所以必须在编译时同步复制。

```powershell
if ($Launch) {
    & (Join-Path $jdk 'java.exe') -cp "$binDirectory;$dependencyClasspath" wordbook3.Wordbook3Application
}
```

使用完整类名 `wordbook3.Wordbook3Application` 启动，包名和入口类名都不能省略。

## 10. 运行和功能对应关系

在项目根目录打开 PowerShell：

```powershell
.\scripts\compile-wordbook3.ps1
```

编译后启动：

```powershell
.\scripts\compile-wordbook3.ps1 -Launch
```

程序保留的功能如下：

| 功能 | 主要代码 |
| --- | --- |
| 显示全部单词 | `Wordbook3Frame.refreshAll`、`WordRepository.findAll` |
| 精确/前缀/后缀/包含查询 | `Wordbook3Frame.refreshSearch`、`WordRepository.search` |
| 新增 | `Wordbook3Frame.runCreate`、`WordRepository.insert` |
| 修改 | `Wordbook3Frame.runUpdate`、`WordRepository.update` |
| 删除 | `Wordbook3Frame.runDelete`、`WordRepository.delete` |
| 播放发音 | `Wordbook3Frame.playVoice`、`AudioPlayer.playAsync` |
| 保存数据 | `DatabaseManager` 和包内 `data/wordbook-simple.db` |

音频文件直接放在：

```text
src/wordbook3/audio/
```

然后重新运行编译脚本。发音输入框只填写文件名，例如 `apple.mp3`，不要填写目录路径。
