package io.github.wh201906.serialpipe;

import android.hardware.usb.UsbDeviceConnection;

import com.hoho.android.usbserial.driver.UsbSerialPort;

import java.io.IOException;
import java.util.Arrays;

public class UsbSerialConnection extends BaseConnection
{
    private static final int WRITE_WAIT_MILLIS = 2000;
    private static final int READ_WAIT_MILLIS = 2000;

    private int mBaudRate = 115200;
    private int mDataBits = UsbSerialPort.DATABITS_8;
    private int mStopBits = UsbSerialPort.STOPBITS_1;
    private int mParity = UsbSerialPort.PARITY_NONE;

    private UsbDeviceConnection mUsbConnection = null;
    private UsbSerialPort mUsbPort = null;

    @Override
    public boolean open()
    {
        if (mUsbConnection == null || mUsbPort == null) return false;
        try
        {
            mUsbPort.open(mUsbConnection);
            mUsbPort.setParameters(mBaudRate, mDataBits, mStopBits, mParity);
        } catch (IOException e)
        {
            mLastException = e;
            return false;
        }
        mIsOpened = true;
        return true;
    }

    @Override
    public void close()
    {
        try
        {
            if (mUsbPort != null) mUsbPort.close();
        } catch (IOException e)
        {
            mLastException = e;
            e.printStackTrace();
        } finally
        {
            mUsbPort = null;
            mIsOpened = false;
        }
    }

    @Override
    public int read(byte[] buf, int maxLength) throws IOException
    {
        if (mUsbPort == null || !mUsbPort.isOpen())
            throw new IOException("USB Serial connection not open");

        int readLen = 0;
        try
        {
            readLen = mUsbPort.read(buf, maxLength, READ_WAIT_MILLIS);

        } catch (IOException e)
        {
            mLastException = e;
            close();
            throw e;
        }
        return readLen;
    }

    @Override
    public int write(byte[] data, int length) throws IOException
    {
        if (mUsbPort == null || !mUsbPort.isOpen())
            throw new IOException("USB Serial connection not open");

        int writeLen = Math.min(data.length, length);
        try
        {
            mUsbPort.write(data, writeLen, WRITE_WAIT_MILLIS);

        } catch (IOException e)
        {
            mLastException = e;
            close();
            throw e;
        }
        return writeLen;
    }


    public int getBaudRate() {return mBaudRate;}

    public void setBaudRate(int baudRate) {this.mBaudRate = baudRate;}

    public int getDataBits() {return mDataBits;}

    public void setDataBits(int dataBits) {this.mDataBits = dataBits;}

    public int getStopBits() {return mStopBits;}

    public void setStopBits(int stopBits) {this.mStopBits = stopBits;}

    public int getParity() {return mParity;}

    public void setParity(int parity) {this.mParity = parity;}

    public UsbDeviceConnection getUsbConnection() {return mUsbConnection;}

    public void setUsbConnection(UsbDeviceConnection usbConnection) {this.mUsbConnection = usbConnection;}

    public UsbSerialPort getUsbPort() {return mUsbPort;}

    public void setUsbPort(UsbSerialPort usbPort) {this.mUsbPort = usbPort;}

}
