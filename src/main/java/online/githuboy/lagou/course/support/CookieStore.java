package online.githuboy.lagou.course.support;

import online.githuboy.lagou.course.utils.ConfigUtil;

/**
 * CookieStore
 */
public class CookieStore {
    /**
     * Set browser cookie after successful login.
     * Keep real cookie out of version control.
     */
    private static String cookie = sanitizeCookie(ConfigUtil.readValue("cookie"));

    public static String getCookie() {
        return cookie;
    }

    public static void setCookie(String cookieStr) {
        cookie = sanitizeCookie(cookieStr);
    }

    private static String sanitizeCookie(String cookieStr) {
        if (cookieStr == null) {
            return "";
        }
        String trimmed = cookieStr.trim();
        if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed;
    }
}
