package com.emias.deployer;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class BruteForceService {

    private static final int MAX_ATTEMPTS = 5;
    private static final int BLOCK_MINUTES = 15;
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("HH:mm");

    private record Info(int count, LocalDateTime blockedUntil) {}

    private final ConcurrentHashMap<String, Info> map = new ConcurrentHashMap<>();

    public boolean isBlocked(String ip) {
        Info info = map.get(ip);
        if (info == null || info.blockedUntil() == null) return false;
        if (LocalDateTime.now().isBefore(info.blockedUntil())) return true;
        map.remove(ip);
        return false;
    }

    /** Returns remaining attempts after this failure (0 = now blocked). */
    public int registerFailure(String ip) {
        Info prev = map.getOrDefault(ip, new Info(0, null));
        int count = prev.count() + 1;
        if (count >= MAX_ATTEMPTS) {
            map.put(ip, new Info(count, LocalDateTime.now().plusMinutes(BLOCK_MINUTES)));
            return 0;
        }
        map.put(ip, new Info(count, null));
        return MAX_ATTEMPTS - count;
    }

    public void registerSuccess(String ip) {
        map.remove(ip);
    }

    /** Human-readable time when the block expires, or null if not blocked. */
    public String blockedUntilText(String ip) {
        Info info = map.get(ip);
        if (info == null || info.blockedUntil() == null) return null;
        return info.blockedUntil().format(FMT);
    }
}
