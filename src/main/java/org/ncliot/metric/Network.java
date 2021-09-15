package org.ncliot.metric;

public class Network {
    private final String name;
    private final long rxBytes;
    private final long rxPackets;
    private final long rxErrors;
    private final long rxDropped;
    private final long txBytes;
    private final long txPackets;
    private final long txErrors;
    private final long txDropped;

    public Network(String name, long rxBytes, long rxPackets, long rxErrors, long rxDropped, long txBytes, long txPackets, long txErrors, long txDropped) {
        this.name = name;
        this.rxBytes = rxBytes;
        this.rxPackets = rxPackets;
        this.rxErrors = rxErrors;
        this.rxDropped = rxDropped;
        this.txBytes = txBytes;
        this.txPackets = txPackets;
        this.txErrors = txErrors;
        this.txDropped = txDropped;
    }

    public String getName() {
        return name;
    }

    public long getRxBytes() {
        return rxBytes;
    }

    public long getRxPackets() {
        return rxPackets;
    }

    public long getRxErrors() {
        return rxErrors;
    }

    public long getRxDropped() {
        return rxDropped;
    }

    public long getTxBytes() {
        return txBytes;
    }

    public long getTxPackets() {
        return txPackets;
    }

    public long getTxErrors() {
        return txErrors;
    }

    public long getTxDropped() {
        return txDropped;
    }
}
