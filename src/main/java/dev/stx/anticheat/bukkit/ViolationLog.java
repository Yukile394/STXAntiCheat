package dev.stx.anticheat.bukkit;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import java.util.stream.Stream;

/** Asenkron JSON-lines yazıcı: ana iş parçacığını disk G/Ç ile bloklamaz; kuyruk dolarsa kayıt düşürür (sayılır). */
final class ViolationLog {
    private final Path dir;
    private final BlockingQueue<String> queue;
    private final Logger logger;
    private final int keepDays;
    private final AtomicLong dropped = new AtomicLong();
    private volatile boolean running;
    private Thread thread;

    ViolationLog(Path dir, int capacity, int keepDays, Logger logger) {
        this.dir = dir;
        this.queue = new LinkedBlockingQueue<>(Math.max(100, capacity));
        this.keepDays = keepDays;
        this.logger = logger;
    }

    void start() {
        try { Files.createDirectories(dir); } catch (IOException e) { logger.warning("Log klasörü oluşturulamadı: " + e.getMessage()); }
        cleanup();
        running = true;
        thread = new Thread(this::loop, "STX-Log");
        thread.setDaemon(true);
        thread.start();
    }

    void stop() {
        running = false;
        if (thread != null) {
            thread.interrupt();
            try { thread.join(2000); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
        }
        flush();
    }

    void write(String line) {
        if (!queue.offer(line)) dropped.incrementAndGet();
    }

    long dropped() { return dropped.get(); }

    private void loop() {
        while (running) {
            try {
                String first = queue.poll(1, TimeUnit.SECONDS);
                if (first == null) continue;
                List<String> batch = new ArrayList<>();
                batch.add(first);
                queue.drainTo(batch, 200);
                append(batch);
            } catch (InterruptedException e) {
                break;
            }
        }
    }

    private void flush() {
        List<String> rest = new ArrayList<>();
        queue.drainTo(rest);
        if (!rest.isEmpty()) append(rest);
    }

    private void append(List<String> lines) {
        Path f = dir.resolve("stx-" + LocalDate.now() + ".jsonl");
        try {
            Files.writeString(f, String.join("\n", lines) + "\n", StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            logger.warning("Log yazılamadı: " + e.getMessage());
        }
    }

    private void cleanup() {
        if (keepDays <= 0) return;
        try (Stream<Path> s = Files.list(dir)) {
            long cutoff = System.currentTimeMillis() - keepDays * 86_400_000L;
            s.filter(p -> p.getFileName().toString().startsWith("stx-")).forEach(p -> {
                try { if (Files.getLastModifiedTime(p).toMillis() < cutoff) Files.deleteIfExists(p); } catch (IOException ignored) { }
            });
        } catch (IOException ignored) { }
    }

    static String esc(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> { if (c < 0x20) sb.append(String.format("\\u%04x", (int) c)); else sb.append(c); }
            }
        }
        return sb.toString();
    }
}
