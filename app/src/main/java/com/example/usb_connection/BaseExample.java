package com.example.usb_connection;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private TextView textView;
    private EditText sendText;
    private Button sendButton;

    static UsbDeviceConnection connection;
    static List<UsbSerialDriver> availableDrivers;
    static UsbSerialDriver driver;
    static UsbSerialPort port;
    static UsbManager manager;
    static Map<String, UsbDevice> devices;
    static UsbDevice device= null;
    static PendingIntent pi;
    private boolean running= false;
    private static Object porting= new Object();
    private static String ACTION_USB_PERMISSION= "com.android.example.USB_PERMISSION";


    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
           String action = intent.getAction();

           if(ACTION_USB_PERMISSION.equals(action)) {

               synchronized (this) {
                   if (manager.hasPermission(device))
                       connect();
               }
           }
        }
    };

    private void connect(){

        availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager);
        driver= availableDrivers.get(0);
        connection = manager.openDevice(driver.getDevice());
        port = driver.getPorts().get(0);

        try {
            synchronized (porting){
                port.open(connection);
                port.setParameters(115200, 8, UsbSerialPort.STOPBITS_1,UsbSerialPort.PARITY_NONE);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                running =true;
                while (running){
                    synchronized (porting){
                        if(!port.isOpen())
                            break;
                    }
                    byte[] data= new byte[100];
                    synchronized (porting) {
                        try {
                            port.read(data, 2000);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                        try {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    try {
                                        textView.setText(new String(data,"UTF-8"));
                                    } catch (UnsupportedEncodingException e) {
                                        throw new RuntimeException(e);
                                    }
                                }
                            });
                            Thread.sleep(200);
                            synchronized (porting){
                                running =port.isOpen();
                            }
                        } catch ( InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }

        }).start();

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        textView = findViewById(R.id.textView);
        sendButton = findViewById(R.id.send);
        sendText = findViewById(R.id.sendText);

        IntentFilter filter= new IntentFilter(ACTION_USB_PERMISSION);
        registerReceiver(receiver, filter);

        manager = (UsbManager) getApplicationContext().getSystemService(Context.USB_SERVICE);

        sendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    synchronized (porting){
                        port.write(sendText.getText().toString().getBytes(), 2000);
                        sendText.setText("");
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        });

    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        if(intent.getAction().equals(UsbManager.ACTION_USB_DEVICE_ATTACHED)){

            devices = manager.getDeviceList();
            for(Map.Entry<String, UsbDevice>entry:devices.entrySet())
                device = entry.getValue();
            pi = PendingIntent.getBroadcast(getApplicationContext(), 0,
                    new Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_IMMUTABLE);
            manager.requestPermission(device, pi);

        }else if(intent.getAction().equals(UsbManager.ACTION_USB_DEVICE_DETACHED)){
            synchronized (porting){
                try {
                    port.close();
                    running= false;
                    connection.close();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }

    }
}