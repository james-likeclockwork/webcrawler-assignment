package webcrawler.services;

import crawlercommons.robots.BaseRobotRules;
import crawlercommons.robots.SimpleRobotRules;
import crawlercommons.robots.SimpleRobotRulesParser;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class RobotsHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(RobotsHandler.class);
    private final String userAgent;
    private final ConcurrentHashMap<String, BaseRobotRules> cache = new ConcurrentHashMap<>();
    private final SimpleRobotRulesParser parser = new SimpleRobotRulesParser();
    private final OkHttpClient client;

    public RobotsHandler(String userAgent) {
        this.userAgent = userAgent;
        this.client = new OkHttpClient.Builder()
                .callTimeout(Duration.ofSeconds(5))
                .build();
    }

    public boolean isAllowed(String urlStr) {
        try {
            URI uri = URI.create(urlStr);
            String host = uri.getHost();
            if (host == null) {
                LOGGER.warn("URL has no host: {}", urlStr);
                return false;
            }
            host = host.toLowerCase().replaceFirst("^www\\.", "");
            String key = uri.getScheme().toLowerCase() + "://" + host;

            BaseRobotRules rules = cache.computeIfAbsent(key, this::fetchRules);
            return rules.isAllowed(urlStr);
        } catch (Exception e) {
            LOGGER.warn("robots.txt check failed for {}:", urlStr, e);
            return false;
        }
    }

    private BaseRobotRules fetchRules(String robotsUrlBase) {
        String robotsUrl = robotsUrlBase + "/robots.txt";
        Request request = new Request.Builder()
                .url(robotsUrl)
                .header("User-Agent", userAgent)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                ResponseBody body = response.body();
                byte[] content = body.bytes();
                return parser.parseContent(
                        robotsUrl,
                        content,
                        "text/plain",
                        List.of(userAgent)
                );
            } else {
                LOGGER.info("robots.txt not available at {} (HTTP {})", robotsUrl, response.code());
                return new SimpleRobotRules(SimpleRobotRules.RobotRulesMode.ALLOW_ALL);
            }
        } catch (IOException e) {
            LOGGER.warn("robots.txt fetch failed at {}:", robotsUrl, e);
            return new SimpleRobotRules(SimpleRobotRules.RobotRulesMode.ALLOW_ALL);
        }
    }
}
