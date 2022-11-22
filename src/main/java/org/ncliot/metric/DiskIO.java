package org.ncliot.metric;

public class DiskIO {
    private final String device;
    private final long byteRead;
    private final long byteWrite;
    private final long opRead;
    private final long opWrite;

    public DiskIO(String device, long byteRead, long byteWrite, long opRead, long opWrite) {
        this.device = device;
        this.byteRead = byteRead;
        this.byteWrite = byteWrite;
        this.opRead = opRead;
        this.opWrite = opWrite;
    }

    public String getDevice() {
        return device;
    }

    public long getByteRead() {
        return byteRead;
    }

    public long getByteWrite() {
        return byteWrite;
    }

    public long getOpRead() {
        return opRead;
    }

    public long getOpWrite() {
        return opWrite;
    }
}
