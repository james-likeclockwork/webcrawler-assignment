package webcrawler;

import java.io.File;
import java.net.URI;
import java.util.Set;
import java.util.Scanner;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import com.google.common.util.concurrent.RateLimiter;
import webcrawler.services.RobotsHandler;
import webcrawler.utils.StorageUtils;
import webcrawler.utils.UrlUtils;
import webcrawler.workers.CrawlWorker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WebCrawler {
    private static final Logger LOGGER = LoggerFactory.getLogger(WebCrawler.class);
    private static final String USER_AGENT = "SednaCrawler";
    private static final int MAX_THREADS = 20;

    private final Set<String> visited = ConcurrentHashMap.newKeySet();
    private final BlockingQueue<String> queue = new LinkedBlockingQueue<>();
    private final RobotsHandler robotsHandler = new RobotsHandler(USER_AGENT);
    private final String rootDomain;
    private final int maxThreads;

    public WebCrawler(String rootUrl, int maxThreads) {
        var uri = URI.create(rootUrl);
        this.rootDomain = uri.getHost().toLowerCase();
        this.maxThreads = maxThreads;

        var normalized = UrlUtils.normalize(rootUrl);
        if (normalized == null) throw new IllegalArgumentException("Invalid URL: " + rootUrl);

        queue.add(normalized);
        visited.add(normalized);
    }

    public void crawl(RateLimiter rateLimiter) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(maxThreads);
        try {
            for (int i = 0; i < maxThreads; i++) {
                executor.submit(new CrawlWorker(queue, visited, robotsHandler, rootDomain, USER_AGENT, rateLimiter));
            }
        } finally {
            executor.shutdown();
            if (!executor.awaitTermination(10, TimeUnit.MINUTES)) {
                executor.shutdownNow();
            }
        }
    }


    public static void main(String[] args) {
        try (Scanner scanner = new Scanner(System.in)) {
            while (true) {
                System.out.print("Enter a URL (or 'exit' to quit): ");
                String inputUrl = scanner.nextLine().trim();
                if (inputUrl.equalsIgnoreCase("exit")) {
                    System.out.println("Exiting...");
                    break;
                }

                System.out.print("Enter number of threads (1–" + MAX_THREADS + "): ");
                int threads = 4;
                try {
                    String input = scanner.nextLine().trim();
                    if (!input.isEmpty()) {
                        int parsed = Integer.parseInt(input);
                        if (parsed >= 1 && parsed <= MAX_THREADS) {
                            threads = parsed;
                        } else {
                            System.out.println("Out of range. Using default (4).");
                        }
                    }
                } catch (NumberFormatException e) {
                    System.out.println("Invalid number. Using default (4).");
                }

                System.out.printf("Crawling %s with %d thread(s)...%n", inputUrl, threads);
                try {
                    WebCrawler crawler = new WebCrawler(inputUrl, threads);
                    RateLimiter rateLimiter = RateLimiter.create(1.0); // 1 request/sec

                    long startTime = System.nanoTime();
                    crawler.crawl(rateLimiter);
                    long endTime = System.nanoTime();

                    long elapsedMillis = (endTime - startTime) / 1_000_000;
                    System.out.printf("Crawl finished in %d ms (%.2f seconds)%n", elapsedMillis, elapsedMillis / 1000.0);

//                    crawler.visited.forEach(System.out::println);

                    File outputDir = new File("output");
                    if (!outputDir.exists()) {
                        outputDir.mkdirs();
                    }

                    String filename = "output/" + StorageUtils.getOutputFilename(new URI(inputUrl).getHost());
                    StorageUtils.saveVisitedToFile(filename, crawler.visited);
                    System.out.println("Saved visited URLs to: " + filename);
                } catch (Exception e) {
                    LOGGER.error("Error: {}", e.getMessage());
                }
            }
        }
    }
}
