package com.manilov.service;

import org.springframework.stereotype.Service;

@Service
public class MetricService {
    private long sumDelay;
    private int sumSize;
    private int countDelay;
    private int countSize;

    public MetricService(){
        this.sumDelay = 0L;
        this.sumSize = 0;
        this.countDelay = 0;
        this.countSize = 0;
    }

    public void updateDelay(long clientTime){
        this.sumDelay += System.currentTimeMillis() - clientTime;
        this.countDelay++;
    }

    public void updateSize(int size){
        this.sumSize += size;
        this.countSize++;
    }

    public double getAvgDelay() {
        if (countDelay == 0) {
            return 0.0;
        }
        return (double) sumDelay / countDelay;
    }

    public double getAvgSize() {
        if (countSize == 0) {
            return 0.0;
        }
        return (double) sumSize / countSize;
    }

    public double getAvgThroughput() {
        //return this.getAvgSize() / (this.getAvgDelay() * 1000);
        return this.getAvgSize() / this.getAvgDelay();
    }
}
