package online.githuboy.lagou.course.service;

import cn.hutool.core.io.FileUtil;
import lombok.extern.slf4j.Slf4j;
import online.githuboy.lagou.course.domain.PurchasedCourseRecord;
import online.githuboy.lagou.course.request.HttpAPI;
import online.githuboy.lagou.course.utils.ConfigUtil;
import online.githuboy.lagou.course.web.dto.CourseListItem;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class CourseService {

    public List<CourseListItem> getCourseList() {
        PurchasedCourseRecord record = HttpAPI.getPurchasedCourseRecord();
        String savePath = ConfigUtil.readValue("mp4_dir");
        List<CourseListItem> items = new ArrayList<>();

        for (PurchasedCourseRecord.CourseInfo info : record.getTrainingCamp()) {
            items.add(buildItem(info, "训练营", savePath));
        }
        for (PurchasedCourseRecord.CourseInfo info : record.getColumns()) {
            items.add(buildItem(info, "专栏", savePath));
        }
        return items;
    }

    private CourseListItem buildItem(PurchasedCourseRecord.CourseInfo info, String type, String savePath) {
        CourseListItem item = new CourseListItem();
        item.setCourseId(info.getId() + "");
        item.setCourseName(info.getName());
        item.setType(type);

        File dir = findCourseDir(savePath, info.getId() + "", info.getName());
        if (dir == null || !dir.exists()) {
            item.setStatus("未下载");
            item.setLocalSize("—");
            item.setLocalSizeBytes(0);
        } else {
            long size = FileUtil.size(dir);
            item.setLocalSizeBytes(size);
            item.setLocalSize(humanReadableSize(size));

            boolean hasComplete = new File(dir, "download-complete.txt").exists();
            boolean hasMp4 = FileUtil.loopFiles(dir).stream().anyMatch(f -> f.getName().endsWith(".mp4"));
            if (hasComplete) {
                item.setStatus("已下载");
            } else if (hasMp4) {
                item.setStatus("部分");
            } else {
                item.setStatus("未下载");
            }
        }
        return item;
    }

    private File findCourseDir(String savePath, String courseId, String courseName) {
        File base = new File(savePath);
        if (!base.exists()) return null;

        File[] files = base.listFiles();
        if (files == null) return null;

        for (File f : files) {
            if (f.isDirectory() && f.getName().startsWith(courseId + "_")) {
                return f;
            }
        }
        return null;
    }

    public static String humanReadableSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        char unit = "KMGTPE".charAt(exp - 1);
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), unit);
    }
}
