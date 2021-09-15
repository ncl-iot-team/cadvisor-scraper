package org.ncliot.metric;

public class CPU {
    private final long user;
    private final long system;
    private final long total;

    public CPU(long user, long system, long total) {
        this.user = user;
        this.system = system;
        this.total = total;
    }

    public long getUser() {
        return user;
    }

    public long getSystem() {
        return system;
    }

    public long getTotal() {
        return total;
    }

}
