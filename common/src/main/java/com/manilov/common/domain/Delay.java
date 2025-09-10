package com.manilov.common.domain;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.Instant;

@Data
@AllArgsConstructor
public class Delay {
    private Instant ts;
    private String serverId;
    private Double delayMs;
}
