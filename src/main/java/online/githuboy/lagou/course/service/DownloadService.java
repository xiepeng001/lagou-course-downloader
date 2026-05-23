package online.githuboy.lagou.course.service;

import lombok.extern.slf4j.Slf4j;
import online.githuboy.lagou.course.domain.DownloadType;
import online.githuboy.lagou.course.support.Downloader;
import online.githuboy.lagou.course.utils.ConfigUtil;
import online.githuboy.lagou.course.web.dto.DownloadProgress;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
public class DownloadService {

    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final ConcurrentMap<String, DownloadProgress> progressMap = new ConcurrentHashMap<>();

    public void submitDownload(List<String> courseIds) {
        String savePath = ConfigUtil.readValue("mp4_dir");
        DownloadType downloadType = DownloadType.loadByCode(
                Integer.valueOf(ConfigUtil.readValue("downloadType")));

        for (String courseId : courseIds) {
            if (progressMap.containsKey(courseId)) {
                log.info("课程 {} 已在下载中，跳过", courseId);
                continue;
            }
            DownloadProgress progress = new DownloadProgress(courseId, "", 0, 0, "下载中", "");
            progressMap.put(courseId, progress);

            executor.submit(() -> {
                try {
                    Downloader downloader = new Downloader(courseId, savePath, downloadType);
                    downloader.setProgressCallback((cid, completed, total) -> {
                        progress.setCompleted(completed);
                        progress.setTotal(total);
                    });
                    downloader.start();
                    progress.setState("完成");
                } catch (Exception e) {
                    log.error("下载课程 {} 失败", courseId, e);
                    progress.setState("失败");
                    progress.setError(e.getMessage());
                }
            });
        }
    }

    public List<DownloadProgress> getProgress() {
        return new ArrayList<>(progressMap.values());
    }

    public void clearProgress() {
        progressMap.entrySet().removeIf(e ->
                "完成".equals(e.getValue().getState()) || "失败".equals(e.getValue().getState()));
    }
}
