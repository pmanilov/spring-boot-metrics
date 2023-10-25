package com.manilov.metrics.service;

import org.springframework.stereotype.Service;

@Service
public class TimeService {
    private long sumDelay;
    private long count;

    public TimeService(){
        this.sumDelay = 0L;
        this.count = 0L;
    }

    public void update(long clientTime){
        this.sumDelay += System.currentTimeMillis() - clientTime;
        this.count++;
    }

    public double getAvgDelay() {
        if (count == 0L) {
            return 0.0;
        }
        return (double) sumDelay / count;
    }
}
