/**
 * Done by Aleksandar Damnjanovic aka Kind Spirit from Kind Spirit Technology YouTube Channel
 * February 29. 2024.
 */


package com.example.usb_connection;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;

import java.io.IOException;
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

    LineData data;
    LineDataSet f;
    LineChart lineChart;

    boolean isInteger(String text){
        char[] chars= text.toCharArray();
        for(char c: chars)
            if(c!='0' && c!='1' && c!='2' && c!='3' && c!='4' && c!='5' && c!='6' && c!='7' && c!='8' && c!='9')
                return false;

        return true;
    }


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
                while (true) {

                    byte[] message= new byte[100];
                    try {
                        port.read(message, 2000);
                        int value = -1;
                        String t = new String(message, "UTF-8").trim();
                        if(isInteger(t) && t.length()>0) {

                            textView.setText(t);
                            value= Integer.valueOf(t);
                            f.addEntry(new Entry(data.getEntryCount(), value));
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    data.removeDataSet(0);
                                    data.addDataSet(f);
                                    lineChart.setData(data);
                                    lineChart.moveViewToX(data.getEntryCount());
                                    data.notifyDataChanged();
                                    lineChart.invalidate();
                                    lineChart.animate();
                                }
                            });
                            Thread.sleep(1000);
                        }
                    } catch (IOException | InterruptedException e) {
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
        lineChart = findViewById(R.id.lineChart);

        lineChart.getDescription().setEnabled(true);
        lineChart.getDescription().setText("USB Serial Data Plotter");
        lineChart.setTouchEnabled(false);
        lineChart.setDragEnabled(false);

        data = new LineData();
        data.setValueTextColor(Color.BLACK);
        lineChart.setData(data);

        LineDataSet set = new LineDataSet(null, "first set");
        set.setAxisDependency(YAxis.AxisDependency.LEFT);
        set.setLineWidth(1.5f);
        set.setColor(Color.GREEN);
        set.setMode(LineDataSet.Mode.CUBIC_BEZIER);
        set.setCubicIntensity(.1f);
        data.addDataSet(set);
        f= (LineDataSet) data.getDataSetByLabel("first set", true);
        data.notifyDataChanged();


        IntentFilter filter= new IntentFilter(ACTION_USB_PERMISSION);
        registerReceiver(receiver, filter);

        manager = (UsbManager) getApplicationContext().getSystemService(Context.USB_SERVICE);
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

        }
    }
}