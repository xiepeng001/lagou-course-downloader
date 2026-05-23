package online.githuboy.lagou.course.web.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DownloadProgress {
    private String courseId;
    private String courseName;
    /** 已完成课时数 */
    private int completed;
    /** 总课时数 */
    private int total;
    /** 下载中 / 完成 / 失败 */
    private String state;
    /** 错误信息 */
    private String error;
}
