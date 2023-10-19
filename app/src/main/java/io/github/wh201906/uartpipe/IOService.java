package io.github.wh201906.uartpipe;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;

public class IOService extends Service
{
    private static final String TAG = "IOService";

    private final IBinder binder = new LocalBinder();
    private Notification notification;

    private int inboundPort = 18888;
    private int outboundPort = 0;
    private InetAddress outboundAddress = null;
    private DatagramSocket udpSocket = null;

    private int baudrate = 115200;
    private UsbSerialDriver uartUsbDriver = null;
    private UsbSerialPort uartUsbPort = null;

    private boolean isSocketConnected = false;
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

        new Thread(new Runnable()
        {
            @Override
            public void run()
            {
                try
                {
                    while (isSocketConnected)
                    {
                        byte[] receiveData = new byte[1024];
                        DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                        udpSocket.receive(receivePacket);

                        if (isTrafficLoggingEnabled)
                            Log.w(TAG, "From UDP: " + new String(receivePacket.getData(), "UTF-8"));

                        DatagramPacket sendPacket = new DatagramPacket(receivePacket.getData(), receivePacket.getLength(), receivePacket.getAddress(), receivePacket.getPort());

                        udpSocket.send(sendPacket);
                    }
                } catch (Exception e)
                {
                    e.printStackTrace();
                    stopUdpSocket();
                }
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
        return true;
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
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, appName).setContentTitle(appName).setContentText(appName).setSmallIcon(R.mipmap.ic_launcher);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        {
            NotificationChannel channel = new NotificationChannel(appName, appName, NotificationManager.IMPORTANCE_DEFAULT);
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
        return builder.build();
    }

}