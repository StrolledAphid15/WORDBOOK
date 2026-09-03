package wordbook3;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;

/** wordbook3 程序入口。 */
public final class Wordbook3Application {
    private Wordbook3Application() {
    }

    public static void main(String[] args) {
        try {
            Path packageHome = resolvePackageHome();/* 返回包目录及检查data和audio文件夹 */
            Path dataDirectory = requireDirectory(packageHome.resolve("data"), "data");
            Path audioDirectory = requireDirectory(packageHome.resolve("audio"), "audio");

            DatabaseManager databaseManager = new DatabaseManager(
                    dataDirectory.resolve("wordbook-simple.db")
            );
            databaseManager.initialize();/* 建表并写入演示数据 */
            WordRepository repository = new WordRepository(databaseManager);
            AudioPlayer audioPlayer = new AudioPlayer(audioDirectory);
            configureLookAndFeel();
            SwingUtilities.invokeLater(() -> new Wordbook3Frame(repository, audioPlayer).setVisible(true));
        } catch (Exception exception) {
            exception.printStackTrace();
            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
                    null,
                    "启动失败：" + exception.getMessage(),
                    "启动失败",
                    JOptionPane.ERROR_MESSAGE
            ));
        }
    }

    static Path resolvePackageHome() {
        try {
            Path codeLocation = Path.of(Wordbook3Application.class /* 获取当前入口类的信息 */
                    .getProtectionDomain() /* 获取类的运行环境 */
                    .getCodeSource() /* 获取类文件来自哪里 */
                    .getLocation() /* 获取 bin文件夹或 JAR文件的位置 */
                    .toURI()) /* 转换成标准 URI 路径 */
                    .toAbsolutePath() /* 转换为绝对路径 */
                    .normalize(); /* 清理路径中的  . 、 .. 等内容*/
            return codeLocation.resolve("wordbook3");
        } catch (URISyntaxException exception) {
            throw new IllegalStateException("无法确定 wordbook3 包目录", exception);
        }
    }
    private static Path requireDirectory(Path directory, String name) throws IOException {
        if (!Files.isDirectory(directory)) {
            throw new IOException("wordbook3 包内缺少 " + name + " 文件夹：" + directory);
        }
        return directory;
    }
    private static void configureLookAndFeel() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // 使用 Swing 默认外观。
        }
    }
}
