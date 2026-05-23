package online.githuboy.lagou.course.web.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CourseListItem {
    private String courseId;
    private String courseName;
    /** 专栏 / 训练营 */
    private String type;
    /** 已下载 / 部分 / 未下载 */
    private String status;
    /** 本地已下载大小，人类可读格式如 "1.2 GB"，未下载为 "—" */
    private String localSize;
    /** 本地已下载字节数，未下载为 0 */
    private long localSizeBytes;
}
