package webcrawler.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class StorageUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(StorageUtils.class);
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    public static String getOutputFilename(String rootDomain) {
        if (rootDomain == null || rootDomain.isBlank()) {
            throw new IllegalArgumentException("rootDomain cannot be null or empty");
        }

        String timestamp = LocalDateTime.now().format(FORMATTER);
        String safeDomain = rootDomain.replaceAll("[^a-zA-Z0-9]", "_");
        return safeDomain + "_" + timestamp + ".txt";
    }

    public static void saveVisitedToFile(String filename, Set<String> visited) {
        try {
            List<String> sortedVisited = new ArrayList<>(visited);
            Collections.sort(sortedVisited);

            Files.write(
                    Path.of(filename),
                    sortedVisited,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING
            );
        } catch (IOException e) {
            LOGGER.error("Failed to save visited URLs to file: {}", e.getMessage());
        }
    }
}
