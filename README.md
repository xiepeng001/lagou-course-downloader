# lagou-course-downloader

拉勾网课程视频和文章下载工具，基于 [SweetInk/lagou-course-downloader](https://github.com/SweetInk/lagou-course-downloader) 二次开发。

主要更新：**aliPlayAuth 解密算法已适配最新版本**，可正常解析拉勾教育当前使用的阿里云点播私有加密视频。

## 免责声明

本项目仅供学习研究，请勿用于商业或非法目的。您必须已购买对应课程才能使用本工具下载。如果任何单位或个人认为本项目可能涉嫌侵犯其权利，请联系删除。

## 使用方法

### 1. 环境要求

- JDK 8+
- Maven

### 2. 配置

编辑 `src/main/resources/config/config.properties`：

```properties
# 登录后从浏览器请求头中复制 Cookie 填入
cookie=

# 下载类型：0=视频 1=文章 3=两者都下载
downloadType=3

# 视频保存目录
mp4_dir=~/Downloads/lagou-courses

# 训练营视频保存目录
mp4_xunlianying_dir=~/Downloads/lagou-xunlianying-mp4

# 指定课程ID（逗号分隔，留空=全部已购课程）
courseIds=

# 排除课程ID（逗号分隔）
remove_course=
```

### 3. 运行

```bash
mvn compile exec:java -Dexec.mainClass="online.githuboy.lagou.course.App"
```

或在 IDEA 中直接运行 `App#main()`（普通课程）或 `App_XunLianYing#main()`（训练营课程）。

## 说明

- 视频托管在阿里云点播，采用私有加密，本工具已完成 aliPlayAuth 解密算法的更新适配
- 默认下载 FHD 全高清视频源
- 支持断点续传，已下载的视频会自动跳过
- 下载未完成的文件以 `!` 标记，方便识别
