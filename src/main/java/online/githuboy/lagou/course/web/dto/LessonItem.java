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
    /** 视频 / 文章 / 视频+文章 / 资料 */
    private String type;
    /** 是否已下载 */
    private boolean downloaded;
    /** 层级：0=阶段, 1=模块, 2=课时 */
    private int level;
    /** 分组路径，如 "第一阶段 / 模块一" */
    private String groupPath;
}
