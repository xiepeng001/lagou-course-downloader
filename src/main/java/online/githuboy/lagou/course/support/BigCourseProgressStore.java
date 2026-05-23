package online.githuboy.lagou.course.support;

import cn.hutool.core.io.FileUtil;
import lombok.extern.slf4j.Slf4j;
import online.githuboy.lagou.course.constants.ResourceType;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Local progress store for xunlianying downloads.
 *
 * Line format:
 * lessonId|MEDIA
 * lessonId|RESOURCE
 */
@Slf4j
public final class BigCourseProgressStore {
    private static final String PROGRESS_FILE_NAME = ".xunlianying-progress.txt";
    private static final Pattern LESSON_ID_IN_FILE_NAME = Pattern.compile("\\[(\\d+)]");
    private static final Set<String> COMPLETED = ConcurrentHashMap.newKeySet();

    private static volatile boolean initialized = false;
    private static File progressFile;

    private BigCourseProgressStore() {
    }

    public static synchronized void init(String savePath) {
        if (initialized) {
            return;
        }
        File root = new File(savePath);
        if (!root.exists()) {
            root.mkdirs();
        }
        progressFile = new File(root, PROGRESS_FILE_NAME);

        if (progressFile.exists()) {
            COMPLETED.addAll(FileUtil.readLines(progressFile, StandardCharsets.UTF_8));
            log.info("Loaded progress file: {}, records: {}", progressFile.getAbsolutePath(), COMPLETED.size());
        } else {
            Set<String> recovered = recoverFromExistingFiles(root);
            if (!recovered.isEmpty()) {
                COMPLETED.addAll(recovered);
                FileUtil.writeLines(recovered, progressFile, StandardCharsets.UTF_8);
            } else {
                FileUtil.touch(progressFile);
            }
            log.info("Initialized progress file: {}, recovered records: {}", progressFile.getAbsolutePath(), recovered.size());
        }

        initialized = true;
    }

    public static boolean isCompleted(String lessonId, String type) {
        return COMPLETED.contains(buildKey(lessonId, type));
    }

    public static synchronized void markCompleted(String lessonId, String type) {
        String key = buildKey(lessonId, type);
        if (COMPLETED.add(key)) {
            FileUtil.appendUtf8String(key + System.lineSeparator(), progressFile);
        }
    }

    private static Set<String> recoverFromExistingFiles(File root) {
        Set<String> recovered = new HashSet<>();
        for (File file : FileUtil.loopFiles(root)) {
            if (!file.isFile()) {
                continue;
            }
            String name = file.getName();
            Matcher matcher = LESSON_ID_IN_FILE_NAME.matcher(name);
            if (!matcher.find()) {
                continue;
            }
            String lessonId = matcher.group(1);
            if (name.endsWith(".mp4")) {
                recovered.add(buildKey(lessonId, ResourceType.MEDIA));
            } else {
                recovered.add(buildKey(lessonId, ResourceType.RESOURCE));
            }
        }
        return recovered;
    }

    private static String buildKey(String lessonId, String type) {
        String normalizedType = type == null ? ResourceType.MEDIA : type.trim().toUpperCase();
        return lessonId + "|" + normalizedType;
    }
}
