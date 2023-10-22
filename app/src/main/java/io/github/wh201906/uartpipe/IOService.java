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
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

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
    private boolean ignoreSocketError = true;
    private boolean ignoreUartError = true;
    private boolean isTrafficLoggingEnabled = false;

    private List<WeakReference<OnErrorListener>> onErrorListenerList = new ArrayList<>();
    private Handler uiHandler = new Handler(Looper.getMainLooper());

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
        ignoreSocketError = false;
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
            while (isSocketConnected)
            {
                try
                {
                    udpSocket.receive(udpReceivePacket);
                } catch (IOException e)
                {
                    e.printStackTrace();
                    stopUdpSocket(); // this should be called before calling onUdpError() of every listener
                    if(!ignoreSocketError) uiHandler.post(() ->
                    {
                        for (WeakReference<OnErrorListener> listenerRef : onErrorListenerList)
                        {
                            OnErrorListener listener = listenerRef.get();
                            if (listener != null) listener.onUdpError(e);
                        }
                    });
                }
                outboundAddress = udpReceivePacket.getAddress();
                outboundPort = udpReceivePacket.getPort();
                byte[] receivedData = Arrays.copyOf(udpReceiveBuf, udpReceivePacket.getLength());
                if (isTrafficLoggingEnabled) logTraffic("UDP", receivedData);

                try
                {
                    if (isUartConnected) uartUsbPort.write(receivedData, WRITE_WAIT_MILLIS);
                } catch (IOException e)
                {
                    e.printStackTrace();
                    disconnectFromUart(); // this should be called before calling onUartError() of every listener
                    if(!ignoreUartError) uiHandler.post(() ->
                    {
                        for (WeakReference<OnErrorListener> listenerRef : onErrorListenerList)
                        {
                            OnErrorListener listener = listenerRef.get();
                            if (listener != null) listener.onUartError(e);
                        }
                    });
                }
            }
        }).start();

        return true;
    }

    public void stopUdpSocket()
    {
        if (udpSocket != null)
        {
            ignoreSocketError = true;
            udpSocket.close();
            isSocketConnected = false;
        }
    }

    public void setInboundPort(int port) {inboundPort = port;}

    public void setOutboundPort(int port) {outboundPort = port;}

    public void setOutboundAddress(InetAddress address) {outboundAddress = address;}

    public void setTrafficLogging(boolean enabled) {isTrafficLoggingEnabled = enabled;}

    public void setUartUsbDriver(UsbSerialDriver driver) {uartUsbDriver = driver;}

    public void setUartBaudrate(int baudrate) {this.baudrate = baudrate;}

    public boolean getIsSocketConnected() {return isSocketConnected;}

    public boolean getIsUartConnected() {return isUartConnected;}

    public boolean connectToUart()
    {
        UsbManager manager = (UsbManager) getSystemService(Context.USB_SERVICE);
        UsbDeviceConnection connection = manager.openDevice(uartUsbDriver.getDevice());
        if (connection == null) return false;
        ignoreUartError = false;
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
            while (isUartConnected)
            {
                int receiveLen = 0;
                try
                {
                    receiveLen = uartUsbPort.read(uartReceiveBuf, READ_WAIT_MILLIS);
                } catch (IOException e)
                {
                    e.printStackTrace();
                    disconnectFromUart(); // this should be called before calling onUartError() of every listener
                    if(!ignoreUartError) uiHandler.post(() ->
                    {
                        for (WeakReference<OnErrorListener> listenerRef : onErrorListenerList)
                        {
                            OnErrorListener listener = listenerRef.get();
                            if (listener != null) listener.onUartError(e);
                        }
                    });
                }
                if (receiveLen == 0) continue;

                if (isTrafficLoggingEnabled)
                    logTraffic("USB", Arrays.copyOf(uartReceiveBuf, receiveLen));

                if (isSocketConnected)
                {
                    DatagramPacket sendPacket = new DatagramPacket(uartReceiveBuf, receiveLen, outboundAddress, outboundPort);
                    try
                    {
                        udpSocket.send(sendPacket);
                    } catch (IOException e)
                    {
                        e.printStackTrace();
                        stopUdpSocket(); // this should be called before calling onUdpError() of every listener
                        if(!ignoreSocketError) uiHandler.post(() ->
                        {
                            for (WeakReference<OnErrorListener> listenerRef : onErrorListenerList)
                            {
                                OnErrorListener listener = listenerRef.get();
                                if (listener != null) listener.onUdpError(e);
                            }
                        });
                    }
                }
            }

        }).start();
        isUartConnected = true;
        return true;
    }

    private void logTraffic(String source, byte[] data)
    {
        try
        {
            Log.w(TAG, "From " + source + ": " + new String(data, "UTF-8"));
        } catch (Exception e)
        {
            e.printStackTrace();
        }

    }

    public void disconnectFromUart()
    {
        if (uartUsbPort != null)
        {
            ignoreUartError = true;
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
        IOService getService() {return IOService.this;}
    }

    private Notification createNotification()
    {
        String appName = getString(R.string.app_name);
        Intent intent = new Intent(this, MainActivity.class);
        intent.setAction(MainActivity.ACTION_LOAD_MAINACTIVITY);
        PendingIntent loadActivityPendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        intent.setAction(MainActivity.ACTION_EXIT);
        PendingIntent exitPendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, appName).setContentTitle(appName).setContentText(appName).setSmallIcon(R.mipmap.ic_launcher).setContentIntent(loadActivityPendingIntent).addAction(R.mipmap.ic_launcher, getString(R.string.notification_exit), exitPendingIntent);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        {
            NotificationChannel channel = new NotificationChannel(appName, appName, NotificationManager.IMPORTANCE_DEFAULT);
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
        return builder.build();
    }

    public interface OnErrorListener
    {
        void onUdpError(Exception e);

        void onUartError(Exception e);
    }

    public void addOnErrorListener(OnErrorListener listener)
    {
        if (listener == null) return;

        boolean exist = false;
        Iterator<WeakReference<OnErrorListener>> iterator = onErrorListenerList.iterator();
        while (iterator.hasNext())
        {
            WeakReference<OnErrorListener> ref = iterator.next();
            OnErrorListener existingElement = ref.get();
            if (existingElement == null) iterator.remove(); // clean up: remove null
            else if (existingElement.equals(listener)) exist = true;
        }
        if (!exist) onErrorListenerList.add(new WeakReference<OnErrorListener>(listener));
    }

    public void removeOnErrorListener(OnErrorListener listener)
    {
        // this will also remove null listener
        Iterator<WeakReference<OnErrorListener>> iterator = onErrorListenerList.iterator();
        while (iterator.hasNext())
        {
            WeakReference<OnErrorListener> ref = iterator.next();
            OnErrorListener existingElement = ref.get();
            if (existingElement == null || existingElement.equals(listener)) iterator.remove();
        }
    }
}
