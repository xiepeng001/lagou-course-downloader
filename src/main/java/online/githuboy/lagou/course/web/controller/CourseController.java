package online.githuboy.lagou.course.web.controller;

import lombok.RequiredArgsConstructor;
import online.githuboy.lagou.course.service.CourseService;
import online.githuboy.lagou.course.service.DownloadService;
import online.githuboy.lagou.course.utils.ConfigUtil;
import online.githuboy.lagou.course.web.dto.CourseListItem;
import online.githuboy.lagou.course.web.dto.DownloadProgress;
import online.githuboy.lagou.course.web.dto.LessonItem;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

@Controller
@RequiredArgsConstructor
public class CourseController {

    private final CourseService courseService;
    private final DownloadService downloadService;

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("mp4Dir", ConfigUtil.readValue("mp4_dir"));
        model.addAttribute("downloadType", ConfigUtil.readValue("downloadType"));
        return "index";
    }

    @PostMapping("/api/config")
    @ResponseBody
    public String saveConfig(@RequestParam String mp4Dir,
                             @RequestParam(defaultValue = "3") String downloadType) {
        ConfigUtil.setValue("mp4_dir", mp4Dir);
        ConfigUtil.setValue("downloadType", downloadType);
        return "ok";
    }

    @GetMapping("/api/courses")
    @ResponseBody
    public List<CourseListItem> getCourses() {
        return courseService.getCourseList();
    }

    @GetMapping("/api/courses/{courseId}/lessons")
    @ResponseBody
    public List<LessonItem> getCourseLessons(@PathVariable String courseId,
                                              @RequestParam String courseType) {
        return courseService.getCourseLessons(courseId, courseType);
    }

    @PostMapping("/api/download")
    @ResponseBody
    public String startDownload(@RequestBody Map<String, Object> payload) {
        List<String> courseIds = (List<String>) payload.getOrDefault("courseIds", List.of());
        List<String> lessonIds = (List<String>) payload.getOrDefault("lessonIds", List.of());
        Map<String, String> courseTypeMap = (Map<String, String>) payload.getOrDefault("courseTypes", Map.of());
        downloadService.submitDownload(courseIds, lessonIds, courseTypeMap);
        return "ok";
    }

    @GetMapping("/api/progress")
    @ResponseBody
    public List<DownloadProgress> getProgress() {
        return downloadService.getProgress();
    }

    @DeleteMapping("/api/progress")
    @ResponseBody
    public String clearProgress() {
        downloadService.clearProgress();
        return "ok";
    }

    @GetMapping(value = "/api/progress/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamProgress() {
        SseEmitter emitter = new SseEmitter(300_000L);

        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(() -> {
            try {
                List<DownloadProgress> progress = downloadService.getProgress();
                emitter.send(SseEmitter.event().name("progress").data(progress));
            } catch (IOException e) {
                emitter.complete();
                scheduler.shutdown();
            }
        }, 0, 2, TimeUnit.SECONDS);

        emitter.onCompletion(scheduler::shutdown);
        emitter.onTimeout(scheduler::shutdown);
        return emitter;
    }
}
