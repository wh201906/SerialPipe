package io.github.wh201906.serialpipe;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;

public class UdpConnection extends BaseConnection
{
    private int mInboundPort = 0;
    private int mOutboundPort = 0;
    private InetAddress mInboundAddress = null;
    private InetAddress mOutboundAddress = null;
    private boolean mIsServerMode = false;
    private boolean mAlwaysUpdateOutboundSocketAddress = false;

    private DatagramSocket mSocket = null;

    @Override
    public boolean open()
    {
        if (!mIsServerMode && (mOutboundAddress == null || mOutboundPort == 0))
            // in "client" mode, the outboundAddress and outboundPort must be set first
            return false;
        else if (mIsServerMode)
        {
            // unspecified yet
            mOutboundAddress = null;
            mOutboundPort = 0;
        }

        InetSocketAddress inboundSocketAddress;
        if (mInboundAddress != null && !mInboundAddress.isAnyLocalAddress())
            // specific local address
            inboundSocketAddress = new InetSocketAddress(mInboundAddress, mInboundPort);
        else
            // wildcard
            inboundSocketAddress = new InetSocketAddress(mInboundPort);
        try
        {
            mSocket = new DatagramSocket(null);
            mSocket.setReuseAddress(true);
            mSocket.bind(inboundSocketAddress);
        } catch (SocketException e)
        {
            mLastException = e;
            mSocket = null;
            return false;
        }
        mIsOpened = true;
        return true;
    }

    @Override
    public void close()
    {
        if (mSocket != null) mSocket.close();
        mSocket = null;
        mIsOpened = false;
    }

    @Override
    public int read(byte[] buf, int maxLength) throws IOException
    {
        if (mSocket == null) throw new IOException("UDP connection not open");

        DatagramPacket receivePacket = new DatagramPacket(buf, Math.min(buf.length, maxLength));
        try
        {
            mSocket.receive(receivePacket);
        } catch (IOException e)
        {
            mLastException = e;
            close();
            throw e;
        }
        if (mIsServerMode && (mAlwaysUpdateOutboundSocketAddress || mOutboundAddress == null))
        {
            mOutboundAddress = receivePacket.getAddress();
            mOutboundPort = receivePacket.getPort();
        }
        return receivePacket.getLength();
    }

    @Override
    public int write(byte[] data, int length) throws IOException
    {
        if (mSocket == null) throw new IOException("UDP connection not open");

        int writeLen = Math.min(data.length, length);
        DatagramPacket sendPacket = new DatagramPacket(data, writeLen, mOutboundAddress, mOutboundPort);
        try
        {
            mSocket.send(sendPacket);
        } catch (IOException e)
        {
            mLastException = e;
            close();
            throw e;
        }
        return writeLen;
    }

    public int getInboundPort() {return mInboundPort;}

    public void setInboundPort(int inboundPort) {this.mInboundPort = inboundPort;}

    public int getOutboundPort() {return mOutboundPort;}

    public void setOutboundPort(int outboundPort) {this.mOutboundPort = outboundPort;}

    public InetAddress getInboundAddress() {return mInboundAddress;}

    public void setInboundAddress(InetAddress inboundAddress) {this.mInboundAddress = inboundAddress;}

    public InetAddress getOutboundAddress() {return mOutboundAddress;}

    public void setOutboundAddress(InetAddress outboundAddress) {this.mOutboundAddress = outboundAddress;}

    public boolean isServerMode() {return mIsServerMode;}

    public void setIsServerMode(boolean isServerMode) {this.mIsServerMode = isServerMode;}

    public boolean isAlwaysUpdateOutboundSocketAddress() {return mAlwaysUpdateOutboundSocketAddress;}

    public void setAlwaysUpdateOutboundSocketAddress(boolean alwaysUpdateOutboundSocketAddress) {this.mAlwaysUpdateOutboundSocketAddress = alwaysUpdateOutboundSocketAddress;}

}
