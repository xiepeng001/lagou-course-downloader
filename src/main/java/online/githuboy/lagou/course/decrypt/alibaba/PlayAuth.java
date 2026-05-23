package online.githuboy.lagou.course.decrypt.alibaba;

import com.alibaba.fastjson2.JSON;
import lombok.Data;

/**
 * @author suchu
 * @date 2020/8/7
 */
@Data
public class PlayAuth {
    private String AccessKeyId;
    private String AccessKeySecret;
    //@JSONField(name = "AuthInfo",deserializeUsing = online.githuboy.lagou.course.decrypt.alibaba.PlayAuth.AuthInfo.AuthInfoDeserializer.class)
    private String AuthInfo;
    private String CustomerId;
    private String PlayDomain;
    private String Region;
    private String SecurityToken;
    private AuthInfo authObj;
    private VideoMeta VideoMeta;


    @Data
    static class AuthInfo {
        private String CI;
        private String Caller;
        private String ExpireTime;
        private String MediaId;
        private String PlayDomain;
        private String Signature;
    }

    @Data
    static class VideoMeta {
        private String CoverURL;
        private Double Duration;
        private String Status;
        private String Title;
        private String videoId;
    }

    public static PlayAuth from(String jsonText) {
        PlayAuth playAuth = JSON.parseObject(jsonText, PlayAuth.class);
        PlayAuth.AuthInfo authInfo = JSON.parseObject(playAuth.getAuthInfo(), PlayAuth.AuthInfo.class);
        playAuth.setAuthObj(authInfo);
        return playAuth;
    }
}
