package io.github.wh201906.uartpipe;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity
{
    private IOService ioService = null;
    private boolean isIoServiceBound = false;
    private ServiceConnection ioServiceConn = new ServiceConnection()
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
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button startServerButton = findViewById(R.id.startServerButton);
        Button stopServerButton = findViewById(R.id.stopServerButton);
        TextView statusTextView = findViewById(R.id.statusTextView);
        EditText inboundPortEdit = findViewById(R.id.portEditText);
        CheckBox loggingTrafficCheckBox = findViewById(R.id.loggingTrafficCheckBox);

        startServerButton.setOnClickListener(v ->
        {

            if (isIoServiceBound)
            {
                ioService.setInboundPort(Integer.parseInt(inboundPortEdit.getText().toString()));
                if (isIoServiceBound && ioService.startUdpSocket())
                {
                    statusTextView.setText("Server Status: Running");
                    startServerButton.setEnabled(false);
                    stopServerButton.setEnabled(true);
                    inboundPortEdit.setEnabled(false);
                }
            }
        });

        stopServerButton.setOnClickListener(v ->
        {
            if (isIoServiceBound)
            {
                ioService.stopUdpSocket();
                stopServerButton.setEnabled(false);
                startServerButton.setEnabled(true);
                inboundPortEdit.setEnabled(true);
                statusTextView.setText("Server Status: Stopped");
            }

        });

        loggingTrafficCheckBox.setOnClickListener(v ->
        {
            if (isIoServiceBound)
            {
                ioService.setTrafficLogging(((CheckBox)v).isChecked());
            }
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
        if(bindService(serviceIntent, ioServiceConn, 0))
            isIoServiceBound = true;
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