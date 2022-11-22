package org.ncliot.metric;

public class Memory {
    private final long usage;
    private final long maxUsage;

    public Memory(long usage, long maxUsage) {
        this.usage = usage;
        this.maxUsage = maxUsage;
    }

    public long getUsage() {
        return usage;
    }

    public long getMaxUsage() {
        return maxUsage;
    }
}
