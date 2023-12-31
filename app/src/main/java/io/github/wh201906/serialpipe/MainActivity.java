package io.github.wh201906.serialpipe;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
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
    private static final String TAG = "MainActivity";
    private static final String ACTION_USB_PERMISSION = "io.github.wh201906.serialpipe.ACTION_USB_PERMISSION";
    public static final String ACTION_LOAD_MAINACTIVITY = "io.github.wh201906.serialpipe.ACTION_LOAD_MAINACTIVITY";
    public static final String ACTION_EXIT = "io.github.wh201906.serialpipe.ACTION_EXIT";

    private static final String KEY_SERIAL_BAUDRATE = "io.github.wh201906.serialpipe.KEY_SERIAL_BAUDRATE";
    private static final String KEY_NET_PORT_INBOUND = "io.github.wh201906.serialpipe.KEY_NET_PORT_INBOUND";

    private IOService ioService = null;
    private boolean isIoServiceBound = false;

    private UsbSerialDriver pendingPermissionUsbDriver = null;

    Button connectDisconnectSerialButton = null;
    Button startStopServerButton = null;
    EditText baudrateEdit = null;
    EditText inboundPortEdit = null;

    SharedPreferences activityPreferences = null;

    private long lastBackPressTime = 0;

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
                        connectSerial(pendingPermissionUsbDriver);
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

        Log.w(TAG, "onCreate: " + getIntent());
        ProcessIntent();

        activityPreferences = getPreferences(Context.MODE_PRIVATE);

        startStopServerButton = findViewById(R.id.startStopServerButton);
        connectDisconnectSerialButton = findViewById(R.id.connectDisconnectSerialButton);
        Button aboutButton = findViewById(R.id.aboutButton);
        Button exitButton = findViewById(R.id.exitButton);
        inboundPortEdit = findViewById(R.id.portEditText);
        CheckBox loggingTrafficCheckBox = findViewById(R.id.loggingTrafficCheckBox);
        baudrateEdit = findViewById(R.id.baudrateEditText);

        inboundPortEdit.setText(String.valueOf(activityPreferences.getInt(KEY_NET_PORT_INBOUND, 18888)));
        baudrateEdit.setText(String.valueOf(activityPreferences.getInt(KEY_SERIAL_BAUDRATE, 115200)));

        startStopServerButton.setOnClickListener(v ->
        {
            if (!isIoServiceBound) return;

            if (!ioService.getIsSocketConnected())
            {
                int inboundPort = Integer.parseInt(inboundPortEdit.getText().toString());
                ioService.setInboundPort(inboundPort);
                activityPreferences.edit().putInt(KEY_NET_PORT_INBOUND, inboundPort).apply();
                openUdpServer();
            }
            else
            {
                ioService.stopUdpSocket();
                syncIoServiceState();
            }
        });

        connectDisconnectSerialButton.setOnClickListener(v ->
        {
            if (!isIoServiceBound) return;

            if (!ioService.getIsSerialConnected())
            {
                // disconnected->connected
                UsbManager manager = (UsbManager) getSystemService(Context.USB_SERVICE);
                List<UsbSerialDriver> availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager);
                if (!availableDrivers.isEmpty())
                {
                    int baudrate = Integer.parseInt(baudrateEdit.getText().toString());
                    ioService.setSerialBaudrate(baudrate);
                    activityPreferences.edit().putInt(KEY_SERIAL_BAUDRATE, baudrate).apply();
                    UsbSerialDriver driver = availableDrivers.get(0);
                    UsbDevice device = driver.getDevice();
                    if (manager.hasPermission(device))
                    {
                        connectSerial(driver);
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
                    Toast.makeText(MainActivity.this, getString(R.string.toast_serial) + getString(R.string.toast_no_device_found), Toast.LENGTH_SHORT).show();
                }
            }
            else
            {
                // connected->disconnected
                ioService.disconnectFromSerial();
                syncIoServiceState();
            }
        });

        loggingTrafficCheckBox.setOnClickListener(v ->
        {
            if (isIoServiceBound) return;

            ioService.setTrafficLogging(((CheckBox) v).isChecked());
        });

        aboutButton.setOnClickListener(v ->
        {
            Intent intent = new Intent(this, AboutActivity.class);
            startActivity(intent);
        });

        exitButton.setOnClickListener(v -> exit());

        Intent serviceIntent = new Intent(this, IOService.class);

        // If the IOService is already running, calling start(Foreground)Service has no side effect.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(serviceIntent);
        else startService(serviceIntent);

        bindService(serviceIntent, ioServiceConn, 0);
        // isIoServiceBound will be set to true in ServiceConnection.onServiceConnected();

        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        registerReceiver(usbPermissionReceiver, filter);
    }

    private void openUdpServer()
    {
        if (ioService.startUdpSocket())
        {
            Toast.makeText(MainActivity.this, getString(R.string.toast_udp) + getString(R.string.toast_udp_bound), Toast.LENGTH_SHORT).show();
            syncIoServiceState();
        }
        else
        {
            Toast.makeText(MainActivity.this, getString(R.string.toast_udp) + getString(R.string.toast_udp_failed_to_bind), Toast.LENGTH_SHORT).show();
        }
    }

    private void connectSerial(UsbSerialDriver driver)
    {
        ioService.setSerialUsbDriver(driver);
        if (ioService.connectToSerial())
        {
            Toast.makeText(MainActivity.this, getString(R.string.toast_serial) + getString(R.string.toast_connected), Toast.LENGTH_SHORT).show();
            syncIoServiceState();
        }
        else
        {
            Toast.makeText(MainActivity.this, getString(R.string.toast_serial) + getString(R.string.toast_failed_to_connect), Toast.LENGTH_SHORT).show();
        }
    }

    private void syncIoServiceState()
    {
        if (!isIoServiceBound) return;

        if (ioService.getIsSerialConnected())
        {
            connectDisconnectSerialButton.setText(R.string.activity_main_disconnect);
            baudrateEdit.setEnabled(false);
        }
        else
        {
            connectDisconnectSerialButton.setText(R.string.activity_main_connect);
            baudrateEdit.setEnabled(true);
        }
        if (ioService.getIsSocketConnected())
        {
            startStopServerButton.setText(R.string.activity_main_stop_server);
            inboundPortEdit.setEnabled(false);
        }
        else
        {
            startStopServerButton.setText(R.string.activity_main_start_server);
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
        Toast.makeText(MainActivity.this, getString(R.string.toast_udp_error) + e.getMessage(), Toast.LENGTH_SHORT).show();
        syncIoServiceState();
    }

    @Override
    public void onSerialError(Exception e)
    {
        Toast.makeText(MainActivity.this, getString(R.string.toast_serial_error) + e.getMessage(), Toast.LENGTH_SHORT).show();
        syncIoServiceState();
    }

    @Override
    protected void onNewIntent(Intent intent)
    {
        super.onNewIntent(intent);
        Log.w(TAG, "onNewIntent: " + intent.toString());
        setIntent(intent);
        ProcessIntent();
    }

    private void ProcessIntent()
    {
        Intent intent = getIntent();
        String action = intent.getAction();
        if (action == null) action = "(none)";

        Log.w(TAG, "ProcessIntent: " + intent + ", action: " + action);
        if (action.equals(ACTION_EXIT))
        {
            exit();
        }
    }

    private void exit()
    {
        stopService(new Intent(this, IOService.class));
        finish();
    }

    @Override
    public void onBackPressed() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastBackPressTime < 2000) {
            exit();
        } else {
            Toast.makeText(this, "Press Back again to exit", Toast.LENGTH_SHORT).show();
            lastBackPressTime = currentTime;
        }
    }
}
