package online.githuboy.lagou.course.task;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import online.githuboy.lagou.course.constants.ResourceType;
import online.githuboy.lagou.course.constants.RespCode;
import online.githuboy.lagou.course.pojo.vo.PlayInfoVo;
import online.githuboy.lagou.course.task.aliyunvod.AliyunVoDEncryptionMediaLoader;
import online.githuboy.lagou.course.support.CookieStore;
import online.githuboy.lagou.course.support.ExecutorService;
import online.githuboy.lagou.course.support.MediaLoader;
import online.githuboy.lagou.course.decrypt.alibaba.EncryptUtils;
import online.githuboy.lagou.course.utils.HttpUtils;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import static online.githuboy.lagou.course.decrypt.alibaba.AliyunApiUtils.getPlayInfoRequestUrl;
import static online.githuboy.lagou.course.support.ExecutorService.COUNTER;

/**
 * 训练营视频 Info 加载器
 *
 * @author jack
 * @since 2021年5月20日
 */
@Slf4j
public class BigCourseVideoInfoLoader implements Runnable, NamedTask {

    private static final String API_TEMPLATE = "https://gate.lagou.com/v1/neirong/edu/bigcourse/getPlayRecord?courseId={0}&weekId={1}&dayId={2}&lessonId={3}&bigCourseMediaUseType=LUBO";
    private static final String BIG_COURSE_PLAY_AUTH_API = "https://kaiwu.lagou.com/media/bigCourseAliVideoPlayAuth?fileId={0}";
    private static final String DEBUG_LESSON_ID = "19612";
    private static final String KAIWU_REFERER = "https://kaiwu.lagou.com/";
    private static final String KAIWU_DETAIL_REFERER_TEMPLATE = "https://kaiwu.lagou.com/xunlianying/index.html?courseId={0}#/detail?weekId={1}&lessonId={2}";
    private final static int MAX_RETRY_COUNT = 3;
    private final String videoName;
    private final String courseId;
    private final String weekId;
    private final String dayId;
    private final String lessonId;

    private int retryCount = 0;
    @Setter
    private String basePath;

    @Setter
    private List<MediaLoader> mediaLoaders;

    @Setter
    private CountDownLatch latch;

    @Setter
    private String type;

    @Setter
    private String resourceUrl;

    public BigCourseVideoInfoLoader(String videoName, String courseId, String weekId, String dayId, String lessonId) {
        this.videoName = videoName;
        this.courseId = courseId;
        this.weekId = weekId;
        this.dayId = dayId;
        this.lessonId = lessonId;
    }

    @Override
    public void run() {
        String url = MessageFormat.format(API_TEMPLATE, this.courseId, this.weekId, this.dayId, this.lessonId);
        boolean debugLesson = DEBUG_LESSON_ID.equals(this.lessonId);
        try {
            if (ResourceType.RESOURCE.equals(this.type)) {
                if (this.resourceUrl != null) {
                    log.info("获取课程资料:【{}】资料地址成功:{}", this.lessonId, this.resourceUrl);
                    ResourceLoader resourceLoader = new ResourceLoader(this.videoName, this.lessonId, this.weekId, this.resourceUrl);
                    resourceLoader.setBasePath(this.basePath);
                    mediaLoaders.add(resourceLoader);
                    latch.countDown();
                    COUNTER.incrementAndGet();
                }
            } else if (ResourceType.MEDIA.equals(this.type)) {
                log.info("获取视频信息URL:【{}】url：{}", this.lessonId, url);
                String strContent = HttpUtils.get(url, CookieStore.getCookie()).header("x-l-req-header", "{deviceType:1}").execute().body();
                if (debugLesson) {
                    log.info("Debug getPlayRecord response: {}", strContent);
                }
                JSONObject jsonRespObject = JSONObject.parseObject(strContent);

                Integer state = jsonRespObject.getInteger("state");
                if (state == null || state != RespCode.SUCCESS) {
                    log.info("获取播放视频信息失败:【{}】,json:{}", this.videoName, strContent);
                    throw new RuntimeException("获取播放视频信息失败:" + jsonRespObject.getString("message"));
                }

                JSONObject content = jsonRespObject.getJSONObject("content");
                if (content == null) {
                    log.warn("Missing content in play record response, lessonId:{}, raw:{}", this.lessonId, strContent);
                    throw new RuntimeException("play record content missing");
                }
                JSONObject mediaPlayInfoVo = content.getJSONObject("mediaPlayInfoVo");
                if (mediaPlayInfoVo == null) {
                    log.warn("Missing mediaPlayInfoVo, lessonId:{}, raw:{}", this.lessonId, strContent);
                    throw new RuntimeException("mediaPlayInfoVo missing");
                }
                String aliPlayAuth = mediaPlayInfoVo.getString("aliPlayAuth");
                String fileId = mediaPlayInfoVo.getString("fileId");
                String playAuthFromKaiwu = null;
                String fileIdFromKaiwu = null;
                try {
                    String playAuthUrl = MessageFormat.format(BIG_COURSE_PLAY_AUTH_API, fileId);
                    String[] playAuthResult = fetchBigCoursePlayAuth(playAuthUrl, debugLesson);
                    playAuthFromKaiwu = playAuthResult[0];
                    fileIdFromKaiwu = playAuthResult[1];
                } catch (Exception e) {
                    log.warn("Failed to fetch bigCourseAliVideoPlayAuth, fallback to aliPlayAuth: {}", e.getMessage());
                }
                if (playAuthFromKaiwu != null) {
                    aliPlayAuth = playAuthFromKaiwu;
                }
                if (fileIdFromKaiwu != null) {
                    fileId = fileIdFromKaiwu;
                }

                if (debugLesson) {
                    String decodedPlayAuth = EncryptUtils.decodePlayAuth(aliPlayAuth);
                    log.info("Debug decoded PlayAuth: {}", decodedPlayAuth);
                }

                String playInfoRequestUrl = getPlayInfoRequestUrl(aliPlayAuth, fileId);
                if (debugLesson) {
                    log.info("Debug getPlayInfo request URL: {}", playInfoRequestUrl);
                }
                String playInfoContent = HttpUtils.getWithoutEncode(playInfoRequestUrl).execute().body();
                if (debugLesson) {
                    log.info("Debug getPlayInfo response: {}", playInfoContent);
                }
                JSONObject playInfoJsonObject = JSONObject.parseObject(playInfoContent);

                JSONObject playInfoList = playInfoJsonObject.getJSONObject("PlayInfoList");
                if (playInfoList == null) {
                    log.warn("Missing PlayInfoList, lessonId:{}, raw:{}", this.lessonId, playInfoContent);
                    throw new RuntimeException("PlayInfoList missing");
                }
                JSONArray playInfoJsonArray = playInfoList.getJSONArray("PlayInfo");
                List<PlayInfoVo> playInfoVoList = new ArrayList<>();
                if (playInfoJsonArray != null && !playInfoJsonArray.isEmpty()) {
                    playInfoVoList = playInfoJsonArray.toJavaList(PlayInfoVo.class);
                }

                if (!playInfoVoList.isEmpty()) {
                    // Prefer mp4 if available; otherwise fallback to m3u8.
                    PlayInfoVo selected = null;
                    for (PlayInfoVo info : playInfoVoList) {
                        if ("mp4".equalsIgnoreCase(info.getFormat())) {
                            selected = info;
                            break;
                        }
                    }
                    if (selected == null) {
                        for (PlayInfoVo info : playInfoVoList) {
                            if ("m3u8".equalsIgnoreCase(info.getFormat())) {
                                selected = info;
                                break;
                            }
                        }
                    }

                    if (selected != null && "UNRELEASE".equals(selected.getStatus())) {
                        log.info("视频:【{}】待更新", this.videoName);
                        latch.countDown();
                        COUNTER.incrementAndGet();
                        return;
                    }

                    if (selected != null) {
                        String playUrl = selected.getPlayURL();
                        if (playUrl != null) {
                            log.info("获取视频:【{}】播放地址成功:{}", this.videoName, playUrl);
                        }

                        if ("mp4".equalsIgnoreCase(selected.getFormat())) {
                            BigCourseMp4Downloader mp4Downloader = new BigCourseMp4Downloader(this.videoName, this.lessonId, playUrl);
                            mp4Downloader.setBasePath(this.basePath);
                            mediaLoaders.add(mp4Downloader);
                        } else if ("m3u8".equalsIgnoreCase(selected.getFormat())) {
                            // Use AliyunVoDEncryptionMediaLoader for private encrypted m3u8.
                            AliyunVoDEncryptionMediaLoader m3u8 =
                                    new AliyunVoDEncryptionMediaLoader(aliPlayAuth, this.videoName, this.basePath, fileId);
                            mediaLoaders.add(m3u8);
                        }

                        latch.countDown();
                        COUNTER.incrementAndGet();
                    }
                }
            }
        } catch (Exception e) {
            log.error("获取视频:【{}】信息失败:", this.videoName, e);
            if (this.retryCount < MAX_RETRY_COUNT) {
                this.retryCount += 1;
                log.info("第:{}次重试获取:{}", this.retryCount, this.videoName);
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e1) {
                    log.error("", e1);
                }
                ExecutorService.execute(this);
            } else {
                log.info(" video:【{}】最大重试结束:{}", this.videoName, MAX_RETRY_COUNT);
                COUNTER.incrementAndGet();
                this.latch.countDown();
            }
        }
    }

    @Override
    public String getTaskDescription() {
        return this.videoName;
    }

    private String[] fetchBigCoursePlayAuth(String playAuthUrl, boolean debugLesson) {
        String headerJson = buildKaiwuHeaderJson();
        String eduReferer = buildKaiwuEduReferer();
        String eduToken = extractTokenFromCookie(CookieStore.getCookie());
        String playAuthResp = HttpUtils.get(playAuthUrl, CookieStore.getCookie())
                .header("X-L-REQ-HEADER", headerJson)
                .header("edu-referer", eduReferer)
                .header("Referer", eduReferer)
                .header("Origin", KAIWU_REFERER)
                .header("Authorization", eduToken == null ? "" : eduToken)
                .setFollowRedirects(true)
                .execute()
                .body();
        if (debugLesson) {
            log.info("Debug bigCourseAliVideoPlayAuth response (json header): {}", playAuthResp);
        }
        String[] parsed = parseKaiwuPlayAuth(playAuthResp);
        if (debugLesson) {
            log.info("Debug bigCourseAliVideoPlayAuth parsed playAuth:{}, fileId:{}", parsed[0], parsed[1]);
        }
        return parsed;
    }

    private String[] parseKaiwuPlayAuth(String playAuthResp) {
        String playAuthFromKaiwu = null;
        String fileIdFromKaiwu = null;
        JSONObject playAuthJson = JSONObject.parseObject(playAuthResp);
        JSONObject playAuthContent = playAuthJson.getJSONObject("content");
        if (playAuthContent != null) {
            JSONObject data = playAuthContent.getJSONObject("data");
            if (data != null) {
                playAuthFromKaiwu = data.getString("playAuth");
                if (playAuthFromKaiwu == null) {
                    playAuthFromKaiwu = data.getString("playauth");
                }
                fileIdFromKaiwu = data.getString("fileId");
            }
            if (playAuthFromKaiwu == null) {
                playAuthFromKaiwu = playAuthContent.getString("playAuth");
                if (playAuthFromKaiwu == null) {
                    playAuthFromKaiwu = playAuthContent.getString("playauth");
                }
                fileIdFromKaiwu = playAuthContent.getString("fileId");
            }
        } else {
            playAuthFromKaiwu = playAuthJson.getString("playAuth");
            if (playAuthFromKaiwu == null) {
                playAuthFromKaiwu = playAuthJson.getString("playauth");
            }
            fileIdFromKaiwu = playAuthJson.getString("fileId");
        }
        return new String[]{playAuthFromKaiwu, fileIdFromKaiwu};
    }

    private String buildKaiwuHeaderJson() {
        String token = extractTokenFromCookie(CookieStore.getCookie());
        if (token != null && !token.isEmpty()) {
            return "{\"deviceType\":1,\"userToken\":\"" + token + "\"}";
        }
        return "{\"deviceType\":1}";
    }

    private String buildKaiwuEduReferer() {
        return MessageFormat.format(KAIWU_DETAIL_REFERER_TEMPLATE, this.courseId, this.weekId, this.lessonId);
    }

    private String extractTokenFromCookie(String cookie) {
        if (cookie == null || cookie.isEmpty()) {
            return null;
        }
        String[] parts = cookie.split(";");
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.startsWith("edu_gate_login_token=")) {
                return trimmed.substring("edu_gate_login_token=".length());
            }
            if (trimmed.startsWith("gate_login_token=")) {
                return trimmed.substring("gate_login_token=".length());
            }
        }
        return null;
    }
}
