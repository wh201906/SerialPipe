package io.github.wh201906.uartpipe;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.Process;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.Arrays;

public class IOService extends Service
{
    private static final String TAG = "IOService";

    private static final int WRITE_WAIT_MILLIS = 2000;
    private static final int READ_WAIT_MILLIS = 2000;

    private final IBinder binder = new LocalBinder();
    private Notification notification;

    private int inboundPort = 18888;
    private int outboundPort = 0;
    private InetAddress outboundAddress = null;
    private DatagramSocket udpSocket = null;
    byte[] udpReceiveBuf = new byte[4096];
    DatagramPacket udpReceivePacket = new DatagramPacket(udpReceiveBuf, udpReceiveBuf.length);

    private int baudrate = 115200;
    private UsbSerialDriver uartUsbDriver = null;
    private UsbSerialPort uartUsbPort = null;
    byte[] uartReceiveBuf = new byte[4096];

    private boolean isSocketConnected = false;
    private boolean isUartConnected = false;
    private boolean isTrafficLoggingEnabled = false;

    @Override
    public void onCreate()
    {
        super.onCreate();
        notification = createNotification();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
        startForeground(1, notification);
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent)
    {
        return binder;
    }

    public boolean startUdpSocket()
    {
        try
        {
            udpSocket = new DatagramSocket(inboundPort);
        } catch (SocketException e)
        {
            e.printStackTrace();
            return false;
        }
        isSocketConnected = true;

        new Thread(() ->
        {
//            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO);
            try
            {
                while (isSocketConnected)
                {
                    udpSocket.receive(udpReceivePacket);
                    outboundAddress = udpReceivePacket.getAddress();
                    outboundPort = udpReceivePacket.getPort();
                    byte[] receivedData = Arrays.copyOf(udpReceiveBuf, udpReceivePacket.getLength());
                    if (isTrafficLoggingEnabled)
                        Log.w(TAG, "From UDP: " + new String(receivedData , "UTF-8"));

                    if(isUartConnected)
                        uartUsbPort.write(receivedData, WRITE_WAIT_MILLIS);
                }
            } catch (Exception e)
            {
                e.printStackTrace();
                stopUdpSocket();
            }
        }).start();

        return true;
    }

    public void stopUdpSocket()
    {
        if (udpSocket != null)
        {
            udpSocket.close();
            isSocketConnected = false;
        }
    }

    public void setInboundPort(int port)
    {
        inboundPort = port;
    }

    public void setOutboundPort(int port)
    {
        outboundPort = port;
    }

    public void setOutboundAddress(InetAddress address)
    {
        outboundAddress = address;
    }

    public void setTrafficLogging(boolean enabled)
    {
        isTrafficLoggingEnabled = enabled;
    }

    public void setUartUsbDriver(UsbSerialDriver driver)
    {
        uartUsbDriver = driver;
    }

    public void setUartBaudrate(int baudrate)
    {
        this.baudrate = baudrate;
    }

    public boolean connectToUart()
    {
        UsbManager manager = (UsbManager) getSystemService(Context.USB_SERVICE);
        UsbDeviceConnection connection = manager.openDevice(uartUsbDriver.getDevice());
        if (connection == null) return false;
        uartUsbPort = uartUsbDriver.getPorts().get(0);
        try
        {
            uartUsbPort.open(connection);
            uartUsbPort.setParameters(baudrate, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
        } catch (IOException e)
        {
            e.printStackTrace();
            return false;
        }
        new Thread(() ->
        {
//            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO);
            try
            {
                while (isUartConnected)
                {
                    int receiveLen = uartUsbPort.read(uartReceiveBuf, READ_WAIT_MILLIS);
                    Log.w(TAG, "UART->UDP:recvLen" + receiveLen);
                    if (receiveLen == 0)
                        continue;

                    if (isTrafficLoggingEnabled)
                        Log.w(TAG, "From UDP: " + new String(Arrays.copyOf(uartReceiveBuf, receiveLen) , "UTF-8"));

                    if(isSocketConnected)
                    {
                        DatagramPacket sendPacket = new DatagramPacket(uartReceiveBuf, receiveLen, outboundAddress, outboundPort);
                        udpSocket.send(sendPacket);
                    }
                }
            } catch (Exception e)
            {
                e.printStackTrace();
                stopUdpSocket();
            }
        }).start();
        isUartConnected = true;
        return true;
    }

    public void disconnectFromUart()
    {
        if(uartUsbPort != null)
        {
            try
            {
                uartUsbPort.close();
            } catch (IOException e)
            {
                e.printStackTrace();
            }
            uartUsbPort = null;
        }
        isUartConnected = false;
    }

    public class LocalBinder extends Binder
    {
        IOService getService()
        {
            return IOService.this;
        }
    }

    private Notification createNotification()
    {
        String appName = getString(R.string.app_name);
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, appName)
                .setContentTitle(appName)
                .setContentText(appName)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(pendingIntent);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        {
            NotificationChannel channel = new NotificationChannel(appName, appName, NotificationManager.IMPORTANCE_DEFAULT);
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
        return builder.build();
    }

}