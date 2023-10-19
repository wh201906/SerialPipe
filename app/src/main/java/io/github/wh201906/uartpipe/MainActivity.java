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
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialProber;

import java.util.List;

public class MainActivity extends AppCompatActivity
{
    private static final String TAG = "IOService";
    private static final String ACTION_USB_PERMISSION = "io.github.wh201906.uartpipe.USB_PERMISSION";

    private IOService ioService = null;
    private boolean isIoServiceBound = false;

    EditText editTextTextMultiLine;

    private final BroadcastReceiver usbReceiver = new BroadcastReceiver()
    {

        public void onReceive(Context context, Intent intent)
        {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action))
            {
                synchronized (this)
                {
                    UsbDevice device = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false))
                    {
                        if (device != null && isIoServiceBound)
                        {
                            // find the driver
                            UsbManager manager = (UsbManager) getSystemService(Context.USB_SERVICE);
                            List<UsbSerialDriver> availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager);
                            UsbSerialDriver driver = null;
                            for (UsbSerialDriver drv : availableDrivers)
                            {
                                if (drv.getDevice().equals(device))
                                {
                                    driver = drv;
                                    break;
                                }
                            }
                            if (driver != null)
                            {
                                // found corresponding driver, connect
                                ioService.setUartUsbDriver(driver);
                                if (ioService.connectToUart())
                                    Toast.makeText(MainActivity.this, "UART Connected", Toast.LENGTH_SHORT).show();
                                else
                                    Toast.makeText(MainActivity.this, "Failed to connect to UART", Toast.LENGTH_SHORT).show();
                            }

                        }
                    }
                    else
                    {
                        Log.d(TAG, "Permission denied for device " + device);
                    }
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
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName)
        {
            isIoServiceBound = false;
            Intent serviceIntent = new Intent(MainActivity.this, IOService.class);
            if (bindService(serviceIntent, ioServiceConn, 0)) isIoServiceBound = true;
            else Log.e(TAG, "Failed to re-bind IOService");
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button startServerButton = findViewById(R.id.startServerButton);
        Button stopServerButton = findViewById(R.id.stopServerButton);
        Button connectUartButton = findViewById(R.id.connectUartButton);
        TextView statusTextView = findViewById(R.id.statusTextView);
        EditText inboundPortEdit = findViewById(R.id.portEditText);
        CheckBox loggingTrafficCheckBox = findViewById(R.id.loggingTrafficCheckBox);
        EditText baudrateEdit = findViewById(R.id.baudrateEditText);
        editTextTextMultiLine = findViewById(R.id.editTextTextMultiLine);

        startServerButton.setOnClickListener(v ->
        {
            if (!isIoServiceBound)
            {
                return;
            }
            ioService.setInboundPort(Integer.parseInt(inboundPortEdit.getText().toString()));
            if (isIoServiceBound && ioService.startUdpSocket())
            {
                statusTextView.setText("Status: Running");
                startServerButton.setEnabled(false);
                stopServerButton.setEnabled(true);
                inboundPortEdit.setEnabled(false);
            }

        });

        stopServerButton.setOnClickListener(v ->
        {
            if (!isIoServiceBound)
            {
                return;
            }
            ioService.stopUdpSocket();
            stopServerButton.setEnabled(false);
            startServerButton.setEnabled(true);
            inboundPortEdit.setEnabled(true);
            statusTextView.setText("Status: Stopped");


        });

        connectUartButton.setOnClickListener(v ->
        {
            if (!isIoServiceBound)
            {
                return;
            }
            UsbManager manager = (UsbManager) getSystemService(Context.USB_SERVICE);
            List<UsbSerialDriver> availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager);
            if (!availableDrivers.isEmpty())
            {
                ioService.setUartBaudrate(Integer.parseInt(baudrateEdit.getText().toString()));
                UsbSerialDriver driver = availableDrivers.get(0);
                UsbDevice device = driver.getDevice();
                if (manager.hasPermission(device))
                {
                    ioService.setUartUsbDriver(driver);
                    if (ioService.connectToUart())
                        Toast.makeText(MainActivity.this, "UART Connected", Toast.LENGTH_SHORT).show();
                    else
                        Toast.makeText(MainActivity.this, "Failed to connect to UART", Toast.LENGTH_SHORT).show();
                }
                else
                {
                    PendingIntent permissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_IMMUTABLE);
                    manager.requestPermission(device, permissionIntent);
                }
            }
        });

        loggingTrafficCheckBox.setOnClickListener(v ->
        {
            if (isIoServiceBound)
            {
                return;
            }
            ioService.setTrafficLogging(((CheckBox) v).isChecked());

        });

        Intent serviceIntent = new Intent(this, IOService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        {
            startForegroundService(serviceIntent);
        }
        else
        {
            startService(serviceIntent);
        }
        if (bindService(serviceIntent, ioServiceConn, 0)) isIoServiceBound = true;


        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        registerReceiver(usbReceiver, filter);

    }

    @Override
    protected void onDestroy()
    {
        if (isIoServiceBound)
        {
            unbindService(ioServiceConn);
            isIoServiceBound = false;
        }
        super.onDestroy();
    }
}