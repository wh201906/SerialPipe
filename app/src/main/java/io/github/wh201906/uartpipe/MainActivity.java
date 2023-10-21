package io.github.wh201906.uartpipe;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialProber;

import java.util.List;

public class MainActivity extends AppCompatActivity implements IOService.OnErrorListener
{
    private static final String TAG = "IOService";
    private static final String ACTION_USB_PERMISSION = "io.github.wh201906.uartpipe.USB_PERMISSION";

    private IOService ioService = null;
    private boolean isIoServiceBound = false;

    private UsbSerialDriver pendingPermissionUsbDriver = null;

    Button connectDisconnectUartButton = null;
    Button startStopServerButton = null;
    EditText baudrateEdit = null;
    EditText inboundPortEdit = null;


    private final BroadcastReceiver usbPermissionReceiver = new BroadcastReceiver()
    {
        public void onReceive(Context context, Intent intent)
        {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action))
            {
                synchronized (this)
                {
                    UsbManager manager = (UsbManager) getSystemService(Context.USB_SERVICE);
                    if (pendingPermissionUsbDriver != null && manager.hasPermission(pendingPermissionUsbDriver.getDevice()))
                        connectUart(pendingPermissionUsbDriver);
                    else Log.d(TAG, "Permission denied for driver: " + pendingPermissionUsbDriver);

                    pendingPermissionUsbDriver = null;
                }
            }
        }
    };

    private final ServiceConnection ioServiceConn = new ServiceConnection()
    {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service)
        {
            IOService.LocalBinder binder = (IOService.LocalBinder) service;
            ioService = binder.getService();
            isIoServiceBound = true;
            syncIoServiceState();
            ioService.addOnErrorListener(MainActivity.this);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName)
        {
            if (isIoServiceBound)
            {
                // unexpected disconnected state, try to reconnect
                Intent serviceIntent = new Intent(MainActivity.this, IOService.class);
                if (!bindService(serviceIntent, ioServiceConn, 0))
                {
                    Log.e(TAG, "Failed to re-bind IOService");
                    isIoServiceBound = false;
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        startStopServerButton = findViewById(R.id.startStopServerButton);
        connectDisconnectUartButton = findViewById(R.id.connectDisconnectUartButton);
        Button exitButton = findViewById(R.id.exitButton);
        inboundPortEdit = findViewById(R.id.portEditText);
        CheckBox loggingTrafficCheckBox = findViewById(R.id.loggingTrafficCheckBox);
        baudrateEdit = findViewById(R.id.baudrateEditText);


        startStopServerButton.setOnClickListener(v ->
        {
            if (!isIoServiceBound) return;

            if (!ioService.getIsSocketConnected())
            {
                ioService.setInboundPort(Integer.parseInt(inboundPortEdit.getText().toString()));
                ioService.startUdpSocket();
                syncIoServiceState();

            }
            else
            {
                ioService.stopUdpSocket();
                syncIoServiceState();
            }
        });

        connectDisconnectUartButton.setOnClickListener(v ->
        {
            if (!isIoServiceBound) return;

            if (!ioService.getIsUartConnected())
            {
                // disconnected->connected
                UsbManager manager = (UsbManager) getSystemService(Context.USB_SERVICE);
                List<UsbSerialDriver> availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager);
                if (!availableDrivers.isEmpty())
                {
                    ioService.setUartBaudrate(Integer.parseInt(baudrateEdit.getText().toString()));
                    UsbSerialDriver driver = availableDrivers.get(0);
                    UsbDevice device = driver.getDevice();
                    if (manager.hasPermission(device))
                    {
                        connectUart(driver);
                    }
                    else
                    {
                        PendingIntent permissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_IMMUTABLE);
                        pendingPermissionUsbDriver = driver;
                        manager.requestPermission(device, permissionIntent);
                    }
                }
                else
                {
                    Toast.makeText(MainActivity.this, "UART: No device found", Toast.LENGTH_SHORT).show();
                }
            }
            else
            {
                // connected->disconnected
                ioService.disconnectFromUart();
                syncIoServiceState();
            }
        });

        loggingTrafficCheckBox.setOnClickListener(v ->
        {
            if (isIoServiceBound) return;

            ioService.setTrafficLogging(((CheckBox) v).isChecked());
        });

        exitButton.setOnClickListener(v ->
        {
            stopService(new Intent(this, IOService.class));
            finish();
        });

        Intent serviceIntent = new Intent(this, IOService.class);

        // If the IOService is already running, calling start(Foreground)Service has no side effect.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(serviceIntent);
        else startService(serviceIntent);

        bindService(serviceIntent, ioServiceConn, 0);
        // isIoServiceBound will be set to true in ServiceConnection.onServiceConnected();

        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        registerReceiver(usbPermissionReceiver, filter);
    }

    private void connectUart(UsbSerialDriver driver)
    {
        ioService.setUartUsbDriver(driver);
        if (ioService.connectToUart())
        {
            Toast.makeText(MainActivity.this, "UART Connected", Toast.LENGTH_SHORT).show();
            syncIoServiceState();
        }
        else
        {
            Toast.makeText(MainActivity.this, "Failed to connect to UART", Toast.LENGTH_SHORT).show();
        }
    }

    private void syncIoServiceState()
    {
        if (!isIoServiceBound) return;

        if (ioService.getIsUartConnected())
        {
            connectDisconnectUartButton.setText("Disconnect");
            baudrateEdit.setEnabled(false);
        }
        else
        {
            connectDisconnectUartButton.setText("Connect");
            baudrateEdit.setEnabled(true);
        }
        if (ioService.getIsSocketConnected())
        {
            startStopServerButton.setText("Stop Server");
            inboundPortEdit.setEnabled(false);
        }
        else
        {
            startStopServerButton.setText("Start Server");
            inboundPortEdit.setEnabled(true);
        }
    }

    @Override
    protected void onDestroy()
    {
        if (isIoServiceBound)
        {
            isIoServiceBound = false;
            ioService.removeOnErrorListener(this);
            unbindService(ioServiceConn);
        }
        super.onDestroy();
    }

    @Override
    public void onUdpError(Exception e)
    {
        Toast.makeText(MainActivity.this, "UDP Error:" + e.getMessage(), Toast.LENGTH_SHORT).show();
        syncIoServiceState();
    }

    @Override
    public void onUartError(Exception e)
    {
        Toast.makeText(MainActivity.this, "UART Error:" + e.getMessage(), Toast.LENGTH_SHORT).show();
        syncIoServiceState();
    }
}
