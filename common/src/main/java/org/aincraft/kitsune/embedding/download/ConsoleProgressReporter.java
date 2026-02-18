package org.aincraft.kitsune.embedding.download;

import java.time.Duration;
import java.time.Instant;
import java.util.logging.Logger;

/**
 * Reports download progress to the console logger.
 * Throttles output to avoid spam (max every 2 seconds per file).
 */
public class ConsoleProgressReporter implements DownloadProgressListener {
    private final Logger logger;
    private Instant lastReportTime = Instant.EPOCH;
    private static final Duration REPORT_INTERVAL = Duration.ofSeconds(2);

    public ConsoleProgressReporter(Logger logger) {
        this.logger = logger;
    }

    @Override
    public void onStart(String filename, long totalBytes) {
        logger.info(String.format("Downloading %s (%s)...", filename, formatBytes(totalBytes)));
        lastReportTime = Instant.now();
    }

    @Override
    public void onProgress(String filename, long bytesDownloaded, long totalBytes, int percentComplete) {
        Instant now = Instant.now();
        if (Duration.between(lastReportTime, now).compareTo(REPORT_INTERVAL) >= 0) {
            logger.info(String.format("[%s] %d%% (%s / %s)",
                filename, percentComplete, formatBytes(bytesDownloaded), formatBytes(totalBytes)));
            lastReportTime = now;
        }
    }

    @Override
    public void onComplete(String filename) {
        logger.info(String.format("Downloaded %s successfully", filename));
    }

    @Override
    public void onError(String filename, Exception error) {
        logger.warning(String.format("Failed to download %s: %s", filename, error.getMessage()));
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }
}
