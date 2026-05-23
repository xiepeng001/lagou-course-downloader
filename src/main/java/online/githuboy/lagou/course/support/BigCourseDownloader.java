package online.githuboy.lagou.course.support;

import cn.hutool.core.io.FileUtil;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import online.githuboy.lagou.course.constants.RespCode;
import online.githuboy.lagou.course.constants.ResourceType;
import online.githuboy.lagou.course.pojo.dto.BigCourseLessonDto;
import online.githuboy.lagou.course.pojo.vo.CourseDayInfoVo;
import online.githuboy.lagou.course.pojo.vo.CourseStageVo;
import online.githuboy.lagou.course.pojo.vo.LessonInfoVo;
import online.githuboy.lagou.course.pojo.vo.StageModuleVo;
import online.githuboy.lagou.course.task.BigCourseVideoInfoLoader;
import online.githuboy.lagou.course.utils.ConfigUtil;
import online.githuboy.lagou.course.utils.HttpUtils;

import java.io.File;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Big course downloader.
 */
@Slf4j
public class BigCourseDownloader {
    private static final int DEFAULT_API_RETRY_COUNT = 3;
    private static final int DEFAULT_WEEK_GENERAL_RETRY_COUNT = 3;

    /** Get big course outline. */
    private static final String COURSE_OUTLINE_API = "https://gate.lagou.com/v1/neirong/edu/bigcourse/getCourseOutline?courseId={0}";

    /** Get module list for a stage. */
    private static final String STAGE_WEEKS_API = "https://gate.lagou.com/v1/neirong/edu/bigcourse/getStageWeeks?courseId={0}&stageId={1}";

    /** Get lessons for a week. */
    private static final String WEEK_LESSONS_API = "https://gate.lagou.com/v1/neirong/edu/bigcourse/getWeekLessons?courseId={0}&weekId={1}";

    private static final String DEBUG_LESSON_ID = null;

    @Getter
    private final String courseId;

    /** Video output path. */
    @Getter
    private final String savePath;

    /** Filter: only download lessons whose ID is in this list. Empty means all. */
    @Setter
    private List<String> lessonFilter = List.of();

    @Setter
    private Downloader.ProgressCallback progressCallback;

    private final String courseOutlineUrl;
    private CountDownLatch latch;
    private volatile List<MediaLoader> mediaLoaders;
    private long startTime;

    private List<CourseStageVo> courseStageVoList;

    // stageId -> module list
    private Map<Integer, List<StageModuleVo>> stageNModuleVoMap;

    // weekId(moduleId) -> day list(include lessons)
    private Map<Integer, List<CourseDayInfoVo>> moduleNLessonVoMap;

    public BigCourseDownloader(String courseId, String savePath) {
        this.courseId = courseId;
        this.savePath = savePath;
        this.courseOutlineUrl = MessageFormat.format(COURSE_OUTLINE_API, courseId);

        this.courseStageVoList = new ArrayList<>();
        this.stageNModuleVoMap = new LinkedHashMap<>();
        this.moduleNLessonVoMap = new LinkedHashMap<>();
    }

    public void start() throws IOException, InterruptedException {
        this.startTime = System.currentTimeMillis();
        log.info("Start downloader. courseId:{}, savePath:{}, httpTimeoutMs:{}, apiRetryCount:{}",
                this.courseId,
                this.savePath,
                ConfigUtil.readInt("http_timeout_ms", 20000),
                ConfigUtil.readInt("api_retry_count", DEFAULT_API_RETRY_COUNT));

        this.parseCourseOutline();
        this.parseStageModules();
        this.parseWeekLessons();

        List<BigCourseLessonDto> bigCourseLessonDtoList = this.parseBigCourseLessonInfo();

        int videoSize = this.parseVideoInfo(bigCourseLessonDtoList);
        if (videoSize > 0) {
            this.downloadMedia(videoSize);
        }
    }

    private String getRespContent(String apiUrl) {
        int maxRetry = Math.max(1, ConfigUtil.readInt("api_retry_count", DEFAULT_API_RETRY_COUNT));
        Exception lastError = null;
        for (int attempt = 1; attempt <= maxRetry; attempt++) {
            long start = System.currentTimeMillis();
            try {
                String body = HttpUtils
                        .get(apiUrl, CookieStore.getCookie())
                        .header("x-l-req-header", "{deviceType:1}")
                        .execute()
                        .body();
                long cost = System.currentTimeMillis() - start;
                if (attempt > 1) {
                    log.info("API recovered after retry. url:{}, attempt:{}/{}, cost:{}ms", apiUrl, attempt, maxRetry, cost);
                } else {
                    log.debug("API success. url:{}, cost:{}ms", apiUrl, cost);
                }
                return body;
            } catch (Exception e) {
                long cost = System.currentTimeMillis() - start;
                lastError = e;
                log.warn("API request failed. url:{}, attempt:{}/{}, cost:{}ms, error:{}", apiUrl, attempt, maxRetry, cost, e.toString());
                if (attempt < maxRetry) {
                    try {
                        Thread.sleep(400L * attempt);
                    } catch (InterruptedException interruptedException) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Interrupted while retrying API request: " + apiUrl, interruptedException);
                    }
                }
            }
        }
        throw new RuntimeException("API request failed after retries: " + apiUrl, lastError);
    }

    private void parseCourseOutline() {
        String strContent = this.getRespContent(this.courseOutlineUrl);
        JSONObject jsonRespObject = JSONObject.parseObject(strContent);

        if (jsonRespObject.getInteger("state") != RespCode.SUCCESS) {
            throw new RuntimeException("Failed to load course outline: " + strContent);
        }

        JSONObject jsonContentObject = jsonRespObject.getJSONObject("content");
        JSONArray jsonCourseStageVos = jsonContentObject.getJSONArray("courseStageVos");
        if (jsonCourseStageVos != null && !jsonCourseStageVos.isEmpty()) {
            this.courseStageVoList = jsonCourseStageVos.toJavaList(CourseStageVo.class);
            // Filter invalid part.
            this.courseStageVoList.removeIf(courseStageVo -> courseStageVo.getStageId() == 2086);
        }
    }

    private void parseStageModules() {
        List<StageModuleVo> stageModuleVoList;
        if (this.courseStageVoList != null && !this.courseStageVoList.isEmpty()) {
            for (CourseStageVo courseStageVo : this.courseStageVoList) {
                String stageWeeksApi = MessageFormat.format(STAGE_WEEKS_API, this.courseId, courseStageVo.getStageId().toString());
                String moduleContent = this.getRespContent(stageWeeksApi);
                JSONObject jsonModuleRespObject = JSONObject.parseObject(moduleContent);
                if (jsonModuleRespObject.getInteger("state") != RespCode.SUCCESS) {
                    throw new RuntimeException("Failed to load stage modules: " + moduleContent);
                }
                JSONArray jsonModuleContents = jsonModuleRespObject.getJSONArray("content");
                if (jsonModuleContents != null && !jsonModuleContents.isEmpty()) {
                    stageModuleVoList = jsonModuleContents.toJavaList(StageModuleVo.class);
                    this.stageNModuleVoMap.put(courseStageVo.getStageId(), stageModuleVoList);
                }
            }
        }
    }

    private void parseWeekLessons() {
        List<CourseDayInfoVo> courseDayInfoVoList;

        if (!this.stageNModuleVoMap.isEmpty()) {
            for (List<StageModuleVo> stageModuleVoList : this.stageNModuleVoMap.values()) {
                if (stageModuleVoList != null && !stageModuleVoList.isEmpty()) {
                    for (StageModuleVo stageModuleVo : stageModuleVoList) {
                        String api = MessageFormat.format(WEEK_LESSONS_API, this.courseId, stageModuleVo.getWeekId().toString());
                        int weekRetry = Math.max(1, ConfigUtil.readInt("week_general_retry_count", DEFAULT_WEEK_GENERAL_RETRY_COUNT));
                        boolean handled = false;
                        for (int retry = 1; retry <= weekRetry; retry++) {
                            String respContent = this.getRespContent(api);
                            JSONObject jsonObject = JSONObject.parseObject(respContent);
                            Integer state = jsonObject.getInteger("state");

                            if (RespCode.SUCCESS == state) {
                                JSONObject content = jsonObject.getJSONObject("content");
                                JSONArray jsonCourseDayInfoVos = content.getJSONArray("courseDayInfoVos");
                                if (jsonCourseDayInfoVos == null) {
                                    log.warn("Missing courseDayInfoVos in week response, weekId:{}, raw:{}", stageModuleVo.getWeekId(), respContent);
                                } else if (!jsonCourseDayInfoVos.isEmpty()) {
                                    courseDayInfoVoList = JSONArray.parseArray(jsonCourseDayInfoVos.toJSONString(), CourseDayInfoVo.class);
                                    this.moduleNLessonVoMap.put(stageModuleVo.getWeekId(), courseDayInfoVoList);
                                }
                                handled = true;
                                break;
                            }

                            if (Integer.valueOf(108).equals(state)) {
                                log.warn("Skip locked week. weekId:{}, response:{}", stageModuleVo.getWeekId(), respContent);
                                handled = true;
                                break;
                            }

                            if (Integer.valueOf(1002).equals(state)) {
                                log.warn("Week API GENERAL(1002). weekId:{}, attempt:{}/{}, response:{}",
                                        stageModuleVo.getWeekId(), retry, weekRetry, respContent);
                                if (retry < weekRetry) {
                                    try {
                                        Thread.sleep(600L * retry);
                                    } catch (InterruptedException interruptedException) {
                                        Thread.currentThread().interrupt();
                                        throw new RuntimeException("Interrupted while retrying week lessons, weekId:" + stageModuleVo.getWeekId(), interruptedException);
                                    }
                                    continue;
                                }
                                log.warn("Skip week after GENERAL retries exhausted. weekId:{}", stageModuleVo.getWeekId());
                                handled = true;
                                break;
                            }

                            throw new RuntimeException("Failed to load week lessons: " + respContent + ", weekId: " + stageModuleVo.getWeekId());
                        }
                        if (!handled) {
                            log.warn("Skip week due to unresolved state. weekId:{}", stageModuleVo.getWeekId());
                        }
                    }
                }
            }
        }
    }

    private void createFilePath(String parentPath, String childPath) {
        File dir = new File(parentPath, childPath);
        if (!dir.exists()) {
            dir.mkdirs();
            log.info("Create output directory: {}", dir.getAbsolutePath());
        }
    }

    private List<BigCourseLessonDto> parseBigCourseLessonInfo() {
        List<BigCourseLessonDto> bigCourseLessonDtoList = new ArrayList<>();
        boolean filterActive = !lessonFilter.isEmpty();
        if (!this.courseStageVoList.isEmpty()) {
            // Level 1: stage
            for (CourseStageVo courseStageVo : this.courseStageVoList) {
                Integer stageId = courseStageVo.getStageId();
                String topPathName = stageId + "_" + courseStageVo.getStageName();
                this.createFilePath(this.savePath, topPathName);

                // Level 2: module
                List<StageModuleVo> stageModuleVoList = this.stageNModuleVoMap.get(stageId);
                if (stageModuleVoList != null && !stageModuleVoList.isEmpty()) {
                    for (StageModuleVo stageModuleVo : stageModuleVoList) {
                        Integer weekId = stageModuleVo.getWeekId();
                        String modulePathName = weekId + "_" + stageModuleVo.getWeekTag() + "_" + stageModuleVo.getWeekName();
                        this.createFilePath(this.savePath + File.separator + topPathName, modulePathName);

                        // Level 3: day
                        List<CourseDayInfoVo> courseDayInfoVoList = this.moduleNLessonVoMap.get(weekId);
                        if (courseDayInfoVoList != null && !courseDayInfoVoList.isEmpty()) {
                            for (CourseDayInfoVo courseDayInfoVo : courseDayInfoVoList) {
                                if (courseDayInfoVo == null) {
                                    log.warn("Skip null day. weekId:{}", weekId);
                                    continue;
                                }
                                Integer dayId = courseDayInfoVo.getDayId();
                                String subModulePathName = dayId + "_" + courseDayInfoVo.getDayName();
                                this.createFilePath(this.savePath + File.separator + topPathName + File.separator + modulePathName, subModulePathName);

                                // Level 4: lesson
                                List<LessonInfoVo> lessonInfoVoList = courseDayInfoVo.getLessonInfoVos();
                                if (lessonInfoVoList != null && !lessonInfoVoList.isEmpty()) {
                                    for (int i = 0; i < lessonInfoVoList.size(); i++) {
                                        LessonInfoVo lessonInfoVo = lessonInfoVoList.get(i);
                                        if (lessonInfoVo == null) {
                                            log.warn("Skip null lesson. weekId:{}, dayId:{}", weekId, dayId);
                                            continue;
                                        }
                                        String lessonName = lessonInfoVo.getLessonName();
                                        String lessonIdStr = String.valueOf(lessonInfoVo.getLessonId());
                                        if (filterActive && !lessonFilter.contains(lessonIdStr)) {
                                            continue;
                                        }
                                        if (lessonName != null && lessonName.contains("????")) {
                                            log.info("Skip lesson by name: {}", lessonName);
                                            continue;
                                        }
                                        // Skip non-video lessons: 开班典礼 etc.
                                        if (lessonName != null && (lessonName.contains("开班典礼") || lessonName.contains("開班典禮"))) {
                                            log.info("Skip non-video lesson by name: {}", lessonName);
                                            continue;
                                        }
                                        // Only download MEDIA (video) type; skip RESOURCE (资料/课件), CLASSWORK, TEST
                                        if (ResourceType.MEDIA.equals(lessonInfoVo.getType())) {
                                            String lessonParentPath = this.savePath + File.separator + topPathName + File.separator + modulePathName + File.separator + subModulePathName;
                                            String videoName = (i + 1) + "_" + lessonInfoVo.getLessonId() + "_" + lessonName;
                                            bigCourseLessonDtoList.add(new BigCourseLessonDto(this.courseId, stageId.toString(), weekId.toString(),
                                                    dayId.toString(), lessonInfoVo.getLessonId().toString(), lessonName, videoName,
                                                    lessonParentPath, lessonInfoVo.getType(), lessonInfoVo.getResourceUrl()));
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        return bigCourseLessonDtoList;
    }

    /** Check if a lesson's file already exists on disk, same logic as CourseService. */
    private boolean isLessonDownloaded(BigCourseLessonDto lesson) {
        String parentPath = lesson.getPathName();
        String videoName = lesson.getVideoName();
        if (ResourceType.MEDIA.equals(lesson.getType())) {
            String mp4Path = parentPath + File.separator + videoName + ".mp4";
            String mp4TempPath = parentPath + File.separator + videoName + ".!mp4";
            return FileUtil.exist(mp4Path) || FileUtil.exist(mp4TempPath);
        } else if (ResourceType.RESOURCE.equals(lesson.getType())) {
            File dir = new File(parentPath);
            if (!dir.exists()) return false;
            return FileUtil.loopFiles(dir).stream()
                    .anyMatch(f -> f.getName().contains(lesson.getLessonId()));
        }
        return false;
    }

    private int parseVideoInfo(List<BigCourseLessonDto> bigCourseLessonDtoList) {
        AtomicInteger videoSize = new AtomicInteger();
        this.latch = new CountDownLatch(bigCourseLessonDtoList.size());
        this.mediaLoaders = new Vector<>();

        bigCourseLessonDtoList.forEach(lessonInfo -> {
            if (isLessonDownloaded(lessonInfo)) {
                log.info("Skip already downloaded: lessonId={}, name={}, type={}",
                        lessonInfo.getLessonId(), lessonInfo.getLessonName(), lessonInfo.getType());
                latch.countDown();
                ExecutorService.COUNTER.incrementAndGet();
                return;
            }

            BigCourseVideoInfoLoader videoInfoLoader = new BigCourseVideoInfoLoader(lessonInfo.getVideoName(), lessonInfo.getCourseId(), lessonInfo.getWeekId(), lessonInfo.getDayId(), lessonInfo.getLessonId());
            videoInfoLoader.setMediaLoaders(mediaLoaders);
            videoInfoLoader.setBasePath(lessonInfo.getPathName());
            videoInfoLoader.setLatch(this.latch);
            videoInfoLoader.setType(lessonInfo.getType());
            videoInfoLoader.setResourceUrl(lessonInfo.getResourceUrl());
            ExecutorService.execute(videoInfoLoader);
            videoSize.getAndIncrement();
        });
        return videoSize.intValue();
    }

    /**
     * @param total number of media items to download
     */
    private void downloadMedia(int total) throws InterruptedException {
        log.info("Waiting for media metadata tasks...");
        System.out.println(ExecutorService.COUNTER);
        BlockingQueue<Runnable> queue = ExecutorService.getExecutor().getQueue();
        System.out.println(queue.size());
        this.latch.await();

        if (this.mediaLoaders.size() != total) {
            log.info("Media metadata not fully loaded. success:{}, total:{}", this.mediaLoaders.size(), total);
            ExecutorService.tryTerminal();
            return;
        }

        log.info("All media metadata loaded. total:{}", this.mediaLoaders.size());
        CountDownLatch all = new CountDownLatch(this.mediaLoaders.size());

        for (MediaLoader loader : this.mediaLoaders) {
            loader.setLatch(all);
            ExecutorService.getExecutor().execute(loader);
        }

        all.await();

        if (progressCallback != null) {
            progressCallback.onProgress(courseId, this.mediaLoaders.size(), total);
        }

        long end = System.currentTimeMillis();
        log.info("All media processed in {} s", (end - startTime) / 1000);
        log.info("Media output directory: {}", this.savePath);

        File file = new File(this.savePath, "download-complete.txt");
        try {
            file.createNewFile();
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (!Stats.isEmpty()) {
            log.info("\nFailed summary:\n");
            Stats.failedCount.forEach((key, value) -> System.out.println(key + " -> " + value.get()));
        }
    }
}
