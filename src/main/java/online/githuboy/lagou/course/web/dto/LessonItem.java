package online.githuboy.lagou.course.web.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LessonItem {
    private String lessonId;
    private String lessonName;
    /** RELEASE / UNRELEASE */
    private String status;
    /** 视频 / 文章 / 视频+文章 */
    private String type;
    /** 是否已下载 */
    private boolean downloaded;
}
