package online.githuboy.lagou.course.service;

import cn.hutool.core.io.FileUtil;
import lombok.extern.slf4j.Slf4j;
import online.githuboy.lagou.course.domain.CourseInfo;
import online.githuboy.lagou.course.domain.PurchasedCourseRecord;
import online.githuboy.lagou.course.pojo.vo.CourseDayInfoVo;
import online.githuboy.lagou.course.pojo.vo.CourseStageVo;
import online.githuboy.lagou.course.pojo.vo.LessonInfoVo;
import online.githuboy.lagou.course.pojo.vo.StageModuleVo;
import online.githuboy.lagou.course.request.HttpAPI;
import online.githuboy.lagou.course.utils.ConfigUtil;
import online.githuboy.lagou.course.utils.FileUtils;
import online.githuboy.lagou.course.web.dto.CourseListItem;
import online.githuboy.lagou.course.web.dto.LessonItem;
import online.githuboy.lagou.course.support.BigCourseProgressStore;
import online.githuboy.lagou.course.support.Mp4History;
import online.githuboy.lagou.course.constants.ResourceType;
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

    /**
     * 获取课程课时列表（专栏或训练营）
     */
    public List<LessonItem> getCourseLessons(String courseId, String courseType) {
        if ("训练营".equals(courseType)) {
            return getTrainingCampLessons(courseId);
        } else {
            return getColumnLessons(courseId);
        }
    }

    private List<LessonItem> getColumnLessons(String courseId) {
        List<LessonItem> items = new ArrayList<>();
        CourseInfo courseInfo = HttpAPI.getCourseInfo(courseId);
        if (courseInfo.getCourseSectionList() == null) return items;

        String savePath = ConfigUtil.readValue("mp4_dir");
        String courseName = courseInfo.getCourseName();

        for (CourseInfo.Section section : courseInfo.getCourseSectionList()) {
            // 章节作为分组头
            LessonItem sectionItem = new LessonItem();
            sectionItem.setLessonId("");
            sectionItem.setLessonName(section.getSectionName());
            sectionItem.setLevel(0);
            sectionItem.setGroupPath(section.getSectionName());
            items.add(sectionItem);

            if (section.getCourseLessons() == null) continue;
            for (CourseInfo.Lesson lesson : section.getCourseLessons()) {
                LessonItem item = new LessonItem();
                item.setLessonId(lesson.getId() + "");
                item.setLessonName(lesson.getTheme());
                item.setStatus("RELEASE".equals(lesson.getStatus()) ? "已发布" : "未发布");
                item.setLevel(1);
                item.setGroupPath(section.getSectionName());

                boolean hasVideo = lesson.getVideoMediaDTO() != null && lesson.getVideoMediaDTO().getFileId() != null;
                boolean hasText = lesson.getTextContent() != null || lesson.getTextUrl() != null;
                if (hasVideo && hasText) {
                    item.setType("视频+文章");
                } else if (hasVideo) {
                    item.setType("视频");
                } else if (hasText) {
                    item.setType("文章");
                } else {
                    item.setType("—");
                }

                String lessonName = FileUtils.getCorrectFileName(lesson.getTheme());
                String mp4Path = String.join(File.separator, savePath,
                        courseId + "_" + courseName,
                        "[" + lesson.getId() + "] " + lessonName + ".mp4");
                item.setDownloaded(FileUtil.exist(mp4Path));

                items.add(item);
            }
        }
        return items;
    }

    private List<LessonItem> getTrainingCampLessons(String courseId) {
        List<LessonItem> items = new ArrayList<>();
        try {
            String savePath = ConfigUtil.readValue("mp4_xunlianying_dir");

            com.alibaba.fastjson2.JSONObject outlineResp = com.alibaba.fastjson2.JSONObject.parseObject(
                    HttpAPI.getBigCourseOutline(courseId));
            if (outlineResp.getInteger("state") != 1) return items;

            com.alibaba.fastjson2.JSONArray stageVos = outlineResp.getJSONObject("content").getJSONArray("courseStageVos");
            if (stageVos == null) return items;
            List<CourseStageVo> stages = stageVos.toJavaList(CourseStageVo.class);

            for (CourseStageVo stage : stages) {
                // 阶段 (level 0) — 对应目录: savePath/stageId_stageName/
                String stageDirName = stage.getStageId() + "_" + stage.getStageName();
                LessonItem stageItem = new LessonItem();
                stageItem.setLessonId("");
                stageItem.setLessonName(stage.getStageName());
                stageItem.setLevel(0);
                stageItem.setGroupPath(stageDirName);
                items.add(stageItem);

                String stageWeeksResp = HttpAPI.getStageWeeks(courseId, stage.getStageId().toString());
                com.alibaba.fastjson2.JSONObject weeksJson = com.alibaba.fastjson2.JSONObject.parseObject(stageWeeksResp);
                if (weeksJson.getInteger("state") != 1) continue;
                com.alibaba.fastjson2.JSONArray moduleArray = weeksJson.getJSONArray("content");
                if (moduleArray == null) continue;
                List<StageModuleVo> modules = moduleArray.toJavaList(StageModuleVo.class);

                for (StageModuleVo module : modules) {
                    // 模块/周 (level 1) — 对应目录: .../stageDir/weekId_weekTag_weekName/
                    String moduleDirName = module.getWeekId() + "_" + module.getWeekTag() + "_" + module.getWeekName();
                    LessonItem moduleItem = new LessonItem();
                    moduleItem.setLessonId("");
                    moduleItem.setLessonName(module.getWeekTag() + " " + module.getWeekName());
                    moduleItem.setLevel(1);
                    moduleItem.setGroupPath(stageDirName + "/" + moduleDirName);
                    items.add(moduleItem);

                    String weekResp = HttpAPI.getWeekLessons(courseId, module.getWeekId().toString());
                    com.alibaba.fastjson2.JSONObject weekJson = com.alibaba.fastjson2.JSONObject.parseObject(weekResp);
                    if (weekJson.getInteger("state") != 1) continue;
                    com.alibaba.fastjson2.JSONObject weekContent = weekJson.getJSONObject("content");
                    if (weekContent == null) continue;
                    com.alibaba.fastjson2.JSONArray dayArray = weekContent.getJSONArray("courseDayInfoVos");
                    if (dayArray == null) continue;
                    List<CourseDayInfoVo> days = dayArray.toJavaList(CourseDayInfoVo.class);

                    for (CourseDayInfoVo day : days) {
                        // 天 (level 2) — 对应目录: .../moduleDir/dayId_dayName/
                        String dayDirName = day.getDayId() + "_" + day.getDayName();
                        LessonItem dayItem = new LessonItem();
                        dayItem.setLessonId("");
                        dayItem.setLessonName(day.getDayName());
                        dayItem.setLevel(2);
                        dayItem.setGroupPath(stageDirName + "/" + moduleDirName + "/" + dayDirName);
                        items.add(dayItem);

                        if (day.getLessonInfoVos() == null) continue;
                        for (int i = 0; i < day.getLessonInfoVos().size(); i++) {
                            LessonInfoVo lesson = day.getLessonInfoVos().get(i);
                            if (lesson == null) continue;

                            String lessonParentPath = savePath + File.separator + stageDirName
                                    + File.separator + moduleDirName + File.separator + dayDirName;
                            String videoName = (i + 1) + "_" + lesson.getLessonId() + "_" + lesson.getLessonName();

                            LessonItem item = new LessonItem();
                            item.setLessonId(lesson.getLessonId().toString());
                            item.setLessonName(lesson.getLessonName());
                            item.setLevel(3);
                            item.setGroupPath(stageDirName + "/" + moduleDirName + "/" + dayDirName);

                            if (ResourceType.MEDIA.equals(lesson.getType())) {
                                item.setType("视频");
                                String mp4Path = lessonParentPath + File.separator + videoName + ".mp4";
                                String mp4TempPath = lessonParentPath + File.separator + videoName + ".!mp4";
                                item.setDownloaded(FileUtil.exist(mp4Path) || FileUtil.exist(mp4TempPath));
                            } else if (ResourceType.RESOURCE.equals(lesson.getType())) {
                                item.setType("资料");
                                // 资料文件名不确定，检查目录下是否有文件包含 lessonId
                                item.setDownloaded(checkResourceDownloaded(lessonParentPath, lesson.getLessonId().toString()));
                            } else {
                                item.setType("—");
                                item.setDownloaded(false);
                            }
                            items.add(item);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("获取训练营课时失败: {}", e.getMessage());
        }
        return items;
    }

    private boolean checkResourceDownloaded(String parentPath, String lessonId) {
        File dir = new File(parentPath);
        if (!dir.exists()) return false;
        return FileUtil.loopFiles(dir).stream()
                .anyMatch(f -> f.getName().contains(lessonId));
    }
}
