package wordbook3;

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

/** 在包内 audio 目录中异步播放 MP3。 */
public final class AudioPlayer {
    private final Path audioDirectory;

    public AudioPlayer(Path audioDirectory) {
        this.audioDirectory = audioDirectory.toAbsolutePath().normalize();
    }

    public boolean playAsync(String voiceFileName, Consumer<String> onFailure) {
        Objects.requireNonNull(onFailure, "onFailure");
        Path audioFile;
        try {
            audioFile = resolveAudioFile(voiceFileName);
        } catch (IllegalArgumentException exception) {
            onFailure.accept(exception.getMessage());
            return false;
        }

        Thread playerThread = new Thread(
                () -> playSafely(audioFile, onFailure),
                "wordbook3-audio"
        );
        playerThread.setDaemon(true);
        playerThread.start();
        return true;
    }

    Path resolveAudioFile(String voiceFileName) {
        String normalizedName = voiceFileName == null ? "" : voiceFileName.trim();
        if (normalizedName.isEmpty()) {
            throw new IllegalArgumentException("请先填写发音文件名");
        }

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
    }

    private void playSafely(Path audioFile, Consumer<String> onFailure) {
        try (BufferedInputStream audioInput = new BufferedInputStream(Files.newInputStream(audioFile))) {
            new Player(audioInput).play();
        } catch (IOException | JavaLayerException exception) {
            String detail = exception.getMessage();
            onFailure.accept("无法播放发音文件：" + (detail == null ? "MP3 文件或音频设备不可用" : detail));
        }
    }
}
