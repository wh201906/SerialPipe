package io.github.wh201906.serialpipe;

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
import android.os.Process;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;

import org.jctools.queues.SpscArrayQueue;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

public class IOService extends Service
{
    private static final String TAG = "IOService";
    private static final int BUFFER_SIZE = 4096;
    private static final int MESSAGE_NUM = 512;

    private final IBinder binder = new LocalBinder();
    private Notification notification;

    private SpscArrayQueue<byte[]> udpReceiveQueue = new SpscArrayQueue<>(MESSAGE_NUM);
    private byte[] udpReceiveBuf = new byte[BUFFER_SIZE];
    private Connection udpConnection = new UdpConnection();

    private SpscArrayQueue<byte[]> serialReceiveQueue = new SpscArrayQueue<>(MESSAGE_NUM);
    private byte[] serialReceiveBuf = new byte[BUFFER_SIZE];
    private UsbSerialDriver serialUsbDriver = null;
    private Connection usbSerialConnection = new UsbSerialConnection();

    private boolean ignoreSocketError = true;
    private boolean ignoreSerialError = true;
    private boolean isTrafficLoggingEnabled = false;

    private final List<WeakReference<OnErrorListener>> onErrorListenerList = new ArrayList<>();
    private final Handler uiHandler = new Handler(Looper.getMainLooper());

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
    public IBinder onBind(Intent intent) {return binder;}

    public boolean startUdpSocket()
    {
        ignoreSocketError = false;
        ((UdpConnection) udpConnection).setIsServerMode(true);
        ((UdpConnection) udpConnection).setAlwaysUpdateOutboundSocketAddress(true);
        if (!udpConnection.open())
        {
            udpConnection.getLastException().printStackTrace();
            return false;
        }

        new Thread(() ->
        {
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_DISPLAY);
            while (udpConnection.isOpened())
            {
                int receiveLen;
                try
                {
                    receiveLen = udpConnection.read(udpReceiveBuf);
                    if (receiveLen == 0) continue;
                    byte[] buf = Arrays.copyOf(udpReceiveBuf, receiveLen);
                    while (!udpReceiveQueue.offer(buf)) ;
                } catch (Exception e)
                {
                    e.printStackTrace();
                    // stopUdpSocket() should be called before calling onUdpError() of every listener
                    // Because the listener calls syncIoServiceState() to get a proper UI state
                    stopUdpSocket();
                    if (!ignoreSocketError) uiHandler.post(() ->
                    {
                        for (WeakReference<OnErrorListener> listenerRef : onErrorListenerList)
                        {
                            OnErrorListener listener = listenerRef.get();
                            if (listener != null) listener.onUdpError(e);
                        }
                    });
                    break;
                }
            }
        }).start();

        new Thread(() ->
        {
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_DISPLAY);
            while (udpConnection.isOpened())
            {
                try
                {
                    byte[] data = null;
                    while (data == null) data = serialReceiveQueue.poll();
                    udpConnection.write(data);
                } catch (Exception e)
                {
                    e.printStackTrace();
                    // stopUdpSocket() should be called before calling onUdpError() of every listener
                    // Because the listener calls syncIoServiceState() to get a proper UI state
                    stopUdpSocket();
                    if (!ignoreSocketError) uiHandler.post(() ->
                    {
                        for (WeakReference<OnErrorListener> listenerRef : onErrorListenerList)
                        {
                            OnErrorListener listener = listenerRef.get();
                            if (listener != null) listener.onUdpError(e);
                        }
                    });
                    break;
                }
            }
        }).start();

        return true;
    }

    public void stopUdpSocket()
    {
        ignoreSocketError = true;
        udpConnection.close();
    }

    public void setTrafficLogging(boolean enabled) {isTrafficLoggingEnabled = enabled;}

    public void setInboundPort(int inboundPort) {((UdpConnection) udpConnection).setInboundPort(inboundPort);}

    public void setSerialUsbDriver(UsbSerialDriver driver) {serialUsbDriver = driver;}

    public void setSerialBaudrate(int baudrate) {((UsbSerialConnection) usbSerialConnection).setBaudRate(baudrate);}

    public boolean getIsSocketConnected() {return udpConnection.isOpened();}

    public boolean getIsSerialConnected() {return usbSerialConnection.isOpened();}

    public boolean connectToSerial()
    {
        UsbManager manager = (UsbManager) getSystemService(Context.USB_SERVICE);
        UsbDeviceConnection connection = manager.openDevice(serialUsbDriver.getDevice());
        if (connection == null) return false;
        ignoreSerialError = false;
        UsbSerialPort usbSerialPort = serialUsbDriver.getPorts().get(0);
        ((UsbSerialConnection) usbSerialConnection).setUsbConnection(connection);
        ((UsbSerialConnection) usbSerialConnection).setUsbPort(usbSerialPort);
        if (!usbSerialConnection.open())
        {
            usbSerialConnection.getLastException().printStackTrace();
            return false;
        }
        new Thread(() ->
        {
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_DISPLAY);
            while (usbSerialConnection.isOpened())
            {
                int receiveLen;
                try
                {
                    receiveLen = usbSerialConnection.read(serialReceiveBuf);
                    if (receiveLen == 0) continue;
                    byte[] buf = Arrays.copyOf(serialReceiveBuf, receiveLen);
                    while (!serialReceiveQueue.offer(buf)) ;
                } catch (Exception e)
                {
                    e.printStackTrace();
                    // disconnectFromSerial() should be called before calling onSerialError() of every listener
                    // Because the listener calls syncIoServiceState() to get a proper UI state
                    disconnectFromSerial(false);
                    if (!ignoreSerialError) uiHandler.post(() ->
                    {
                        for (WeakReference<OnErrorListener> listenerRef : onErrorListenerList)
                        {
                            OnErrorListener listener = listenerRef.get();
                            if (listener != null) listener.onSerialError(e);
                        }
                    });
                    break;
                }
            }
        }).start();

        new Thread(() ->
        {
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_DISPLAY);
            while (usbSerialConnection.isOpened())
            {
                try
                {
                    byte[] data = null;
                    while (data == null) data = udpReceiveQueue.poll();
                    usbSerialConnection.write(data);
                } catch (Exception e)
                {
                    e.printStackTrace();
                    // disconnectFromSerial() should be called before calling onSerialError() of every listener
                    // Because the listener calls syncIoServiceState() to get a proper UI state
                    disconnectFromSerial(false);
                    if (!ignoreSerialError) uiHandler.post(() ->
                    {
                        for (WeakReference<OnErrorListener> listenerRef : onErrorListenerList)
                        {
                            OnErrorListener listener = listenerRef.get();
                            if (listener != null) listener.onSerialError(e);
                        }
                    });
                    break;
                }
            }
        }).start();

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

    public void disconnectFromSerial()
    {
        // default: user triggered
        disconnectFromSerial(true);
    }

    public void disconnectFromSerial(boolean userTriggered)
    {
        if (userTriggered) ignoreSerialError = true;
        usbSerialConnection.close();
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

        void onSerialError(Exception e);
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
        if (!exist) onErrorListenerList.add(new WeakReference<>(listener));
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
