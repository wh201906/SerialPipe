package io.github.wh201906.serialpipe;

import android.net.LocalServerSocket;
import android.net.LocalSocket;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

// Useless
// https://developer.android.com/about/versions/pie/android-9.0-changes-28#per-app-selinux
// Unix socket is disabled for apps with targetApi >= 28
public class LocalServerSocketConnection extends BaseConnection
{
    private String mSocketName = "SPSock";

    private LocalServerSocket mServerSocket = null;
    private LocalSocket mSocket = null;
    private InputStream mInputStream = null;
    private OutputStream mOutputStream = null;


    @Override
    public boolean open()
    {
        try
        {
            mServerSocket = new LocalServerSocket(mSocketName);
        } catch (IOException e)
        {
            mLastException = e;
            mServerSocket = null;
            return false;
        }
        new Thread(() ->
        {
            try
            {
                mSocket = mServerSocket.accept();
                mInputStream = mSocket.getInputStream();
                mOutputStream = mSocket.getOutputStream();
            } catch (IOException e)
            {
                mLastException = e;
                close();
            }
            mIsOpened = true;
        }).start();
        return true;
    }

    @Override
    public void close()
    {
        closeResource(mInputStream);
        closeResource(mOutputStream);
        closeResource(mSocket);
        try
        {
            if (mServerSocket != null) mServerSocket.close();
        } catch (IOException e)
        {
            mLastException = e;
            e.printStackTrace();
        } finally
        {
            mSocket = null;
            mServerSocket = null;
            mIsOpened = false;
        }
    }

    private void closeResource(Closeable resource)
    {
        if (resource != null)
        {
            try
            {
                resource.close();
            } catch (IOException e)
            {
                mLastException = e;
                e.printStackTrace();
            }
        }
    }

    @Override
    public int read(byte[] buf, int maxLength) throws IOException
    {
        if (mSocket == null) throw new IOException("LocalServerSocket connection not open");
        return mInputStream.read(buf, 0, Math.min(buf.length, maxLength));
    }

    @Override
    public int write(byte[] data, int length) throws IOException
    {
        if (mSocket == null) throw new IOException("LocalServerSocket connection not open");
        int writeLen = Math.min(data.length, length);
        mOutputStream.write(data, 0, writeLen);
        return writeLen;
    }

    public String getSocketName()
    {
        return mSocketName;
    }

    public void setSocketName(String socketName)
    {
        this.mSocketName = socketName;
    }
}
