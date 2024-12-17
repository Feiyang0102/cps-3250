package utils;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

public class PIDGenerator {
    private static final Set<Long> assignedPids = new HashSet<>();
    private static final Random pidRandom = new Random();

    public static synchronized long forkPid() {
        long pid;
        do {
            pid = 100 + pidRandom.nextInt(65535 - 100); // 生成 100 到 65535 之间的随机数
        } while (assignedPids.contains(pid));
        assignedPids.add(pid);
        return pid;
    }
}
