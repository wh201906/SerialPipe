package io.github.wh201906.uartpipe;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.Arrays;

public class MainActivity extends AppCompatActivity
{

    private DatagramSocket serverSocket;
    private boolean isServerRunning = false;
    private EditText portEditText; // 新增的输入框用于指定端口号

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button startServerButton = findViewById(R.id.startServerButton);
        Button stopServerButton = findViewById(R.id.stopServerButton);
        TextView statusTextView = findViewById(R.id.statusTextView);
        portEditText = findViewById(R.id.portEditText); // 关联输入框

        startServerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startServerButton.setEnabled(false);
                stopServerButton.setEnabled(true);
                statusTextView.setText("Server Status: Running");

                int port = Integer.parseInt(portEditText.getText().toString()); // 获取用户输入的端口号
                try
                {
                    startUDPServer(port);
                } catch (SocketException e)
                {
                    throw new RuntimeException(e);
                }
            }
        });

        stopServerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopServerButton.setEnabled(false);
                startServerButton.setEnabled(true);
                statusTextView.setText("Server Status: Stopped");

                stopUDPServer();
            }
        });
    }

    private void startUDPServer(final int port) throws SocketException
    {
        isServerRunning = true;
        serverSocket = new DatagramSocket(port); // 使用用户指定的端口
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    while (isServerRunning) {
                        byte[] receiveData = new byte[1024];
                        DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                        serverSocket.receive(receivePacket);

                        Log.w("UDP", new String(receivePacket.getData(), "UTF-8"));

                        DatagramPacket sendPacket = new DatagramPacket(
                                receivePacket.getData(),
                                receivePacket.getLength(),
                                receivePacket.getAddress(),
                                receivePacket.getPort()
                        );

                        serverSocket.send(sendPacket);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private void stopUDPServer() {
        if (serverSocket != null) {
            serverSocket.close();
            isServerRunning = false;
        }
    }
}