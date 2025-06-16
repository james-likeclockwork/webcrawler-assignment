package webcrawler.utils;

import okhttp3.HttpUrl;

public class UrlUtils {

    public static String normalize(String url) {
        HttpUrl parsed = HttpUrl.parse(url);
        if (parsed == null) return null;

        String normalized = parsed.newBuilder().fragment(null).build().toString();
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
