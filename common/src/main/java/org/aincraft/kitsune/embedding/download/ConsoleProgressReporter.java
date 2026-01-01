package org.aincraft.kitsune.embedding.download;

import java.util.logging.Logger;

/**
 * Reports download progress to the console logger.
 * Throttles output to avoid spam (max every 2 seconds per file).
 */
public class ConsoleProgressReporter implements DownloadProgressListener {
    private final Logger logger;
    private long lastReportTime = 0;
    private static final long REPORT_INTERVAL_MS = 2000;

    public ConsoleProgressReporter(Logger logger) {
        this.logger = logger;
    }

    @Override
    public void onStart(String filename, long totalBytes) {
        logger.info(String.format("Downloading %s (%s)...", filename, formatBytes(totalBytes)));
        lastReportTime = System.currentTimeMillis();
    }

    @Override
    public void onProgress(String filename, long bytesDownloaded, long totalBytes, int percentComplete) {
        long now = System.currentTimeMillis();
        if (now - lastReportTime >= REPORT_INTERVAL_MS) {
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
