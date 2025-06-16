package webcrawler.workers;

import webcrawler.services.RobotsHandler;
import webcrawler.utils.UrlUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.Set;
import java.util.concurrent.BlockingQueue;

public class CrawlWorker implements Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(CrawlWorker.class);
    private static final String USER_AGENT = "MyCrawlerBot";

    private final BlockingQueue<String> queue;
    private final Set<String> visited;
    private final RobotsHandler robotsHandler;
    private final String rootDomain;

    public CrawlWorker(
            BlockingQueue<String> queue,
            Set<String> visited,
            RobotsHandler robotsHandler,
            String rootDomain
    ) {
        this.queue = queue;
        this.visited = visited;
        this.robotsHandler = robotsHandler;
        this.rootDomain = rootDomain;
    }

    @Override
    public void run() {
        while (true) {
            String url;
            try {
                url = queue.poll(5, java.util.concurrent.TimeUnit.SECONDS);
                if (url == null) break;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }

            processUrl(url);
        }
    }

    private void processUrl(String url) {
        LOGGER.info("Crawling: {}", url);

        if (!robotsHandler.isAllowed(url)) {
            LOGGER.warn("Disallowed by robots.txt: {}", url);
            return;
        }

        try {
            var response = Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .timeout(10_000)
                    .ignoreContentType(true)
                    .execute();

            var contentType = response.contentType();
            if (contentType == null || !(contentType.startsWith("text/html") || contentType.contains("xml"))) {
                LOGGER.debug("Skipped non-HTML content: {} ({})", url, contentType);
                return;
            }

            Document doc = response.parse();
            Elements links = doc.select("a[href]");
            for (Element link : links) {
                String absUrl = link.absUrl("href");
                if (absUrl.isEmpty()) continue;

                String normalized = UrlUtils.normalize(absUrl);
                if (normalized == null) continue;

                boolean isNew = visited.add(normalized);

                String host = URI.create(normalized).getHost();
                if (host != null && host.equalsIgnoreCase(rootDomain) && isNew) {
                    queue.add(normalized);
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to fetch {}:", url, e);
        }
    }
}
