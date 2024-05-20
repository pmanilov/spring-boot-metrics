package com.manilov.service;

import org.springframework.stereotype.Service;

@Service
public class MetricService {
    private long sumDelay;
    private int sumSize;
    private int countDelay;
    private int countSize;
    private long lastDelay;
    private int lastSize;

    public MetricService(){
        this.sumDelay = 0L;
        this.sumSize = 0;
        this.countDelay = 0;
        this.countSize = 0;
    }

    public void updateDelay(long clientTime) {
        long nanoTime = System.nanoTime();
        this.sumDelay += nanoTime - clientTime;
        this.lastDelay = nanoTime - clientTime;
        this.countDelay++;
    }

    public void updateSize(int size) {
        this.sumSize += size;
        this.lastSize = size;
        this.countSize++;
    }

    public double getAvgDelay() {
        if (countDelay == 0) {
            return 0.0;
        }
        return (double) this.sumDelay / this.countDelay / 1000000;
    }

    public double getAvgSize() {
        if (countSize == 0) {
            return 0.0;
        }
        return (double) this.sumSize / this.countSize;
    }

    public double getAvgThroughput() {
        return this.getAvgSize() / this.getAvgDelay();
    }

    public double getLastDelay() {
        return (double) this.lastDelay / 1000000;
    }

    public double getLastSize() {
        return this.lastSize;
    }

    public double getLastThroughput() {
        return this.getLastSize() / this.getLastDelay();
    }
}
