package online.githuboy.lagou.course.utils;

import cn.hutool.core.net.url.UrlBuilder;
import cn.hutool.http.Header;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.Method;
import online.githuboy.lagou.course.support.CookieStore;

import java.io.File;

public class HttpUtils {
    private static final int DEFAULT_HTTP_TIMEOUT_MS = 20000;

    private static int requestTimeoutMs() {
        return ConfigUtil.readInt("http_timeout_ms", DEFAULT_HTTP_TIMEOUT_MS);
    }

    public static byte[] getContent(String url) {
        return HttpRequest.get(url).execute().bodyBytes();
    }

    public static void download(String url, File saveTo) {
        HttpRequest.get(url).execute().writeBody(saveTo);
    }

    public static byte[] getContentWithCookie(String url, String cookie) {
        return HttpRequest.get(url).header(Header.COOKIE, cookie).execute().bodyBytes();
    }

    public static HttpRequest get(String url, String cookie) {
        return HttpRequest.get(url).timeout(requestTimeoutMs()).header(Header.COOKIE, cookie);
    }

    public static HttpRequest get(String url) {
        return HttpRequest.get(url).timeout(requestTimeoutMs()).header(Header.COOKIE, CookieStore.getCookie());
    }

    /**
     * Use raw URL without additional encoding. Helpful for pre-signed URLs.
     */
    public static HttpRequest getWithoutEncode(String url) {
        return new HttpRequest(UrlBuilder.ofHttpWithoutEncode(url))
                .method(Method.GET)
                .timeout(requestTimeoutMs());
    }
}
