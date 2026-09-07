package com.ir.common;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class CodeGenerator {
    private final Map<String, AtomicInteger> sequences = new ConcurrentHashMap<>();
    public String next(String prefix) {
        int n = sequences.computeIfAbsent(prefix, k -> new AtomicInteger()).incrementAndGet();
        return prefix + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + String.format("%04d", n);
    }
}
