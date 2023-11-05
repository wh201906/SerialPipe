package io.github.wh201906.serialpipe;

import java.io.IOException;

public interface Connection
{
    boolean open();

    void close();

    int read(byte[] buf, int maxLength) throws IOException;

    default int read(byte[] buf) throws IOException {return read(buf, buf.length);}

    int write(byte[] data, int length) throws IOException;

    default int write(byte[] data) throws IOException {return write(data, data.length);}

    Exception getLastException();

    boolean isOpened();
}
