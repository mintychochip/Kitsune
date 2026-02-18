package org.aincraft.kitsune.di;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class EmbeddingDimensionHolder {
    private int dimension = -1;
    private final CountDownLatch latch = new CountDownLatch(1);

    public void setDimension(int dimension) {
        this.dimension = dimension;
        latch.countDown();
    }

    public int getDimension() {
        try {
            if (!latch.await(60, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timeout waiting for embedding dimension");
            }
            return dimension;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted waiting for embedding dimension", e);
        }
    }

    public boolean isSet() {
        return dimension > 0;
    }
}