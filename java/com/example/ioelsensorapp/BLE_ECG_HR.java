package com.example.ioelsensorapp;

//BLE connection process is based from PunchThrough and converted to Java
// at https://punchthrough.com/android-ble-guide/

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.ParcelFileDescriptor;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;

import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.UUID;


public class BLE_ECG_HR extends AppCompatActivity {

    private static final int ENABLE_BLUETOOTH_REQUEST_CODE = 1;
    private static final int GATT_MAX_MTU_SIZE = 230;
    private static final int CREATE_FILE = 2;


    private BluetoothAdapter bluetoothAdapter;
    private BluetoothManager bluetoothManager;
    private BluetoothLeScanner bleScanner;
    private boolean isScanning = false;
    ArrayList<BluetoothDevice> bluetoothDevices = new ArrayList<BluetoothDevice>();
    BluetoothGatt connectGatt;

    Button start_btn;
    Button stop_btn;
    TextView log_scan;

    Button conn_btn;
    Button disc_btn;
    TextView log_data;

    UUID device_uuid = UUID.fromString("f6b40301-e27a-8e15-386f-5a655916ffe6");
    UUID service_uuid = UUID.fromString("19b10010-e8f2-537e-4f6c-d104768a1214");
    UUID ecg_char_uuid = UUID.fromString("19b10021-e8f2-537e-4f6c-d104768a1214");
    UUID char_desc = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    UUID hr_char_uuid = UUID.fromString("19b10022-e8f2-537e-4f6c-d104768a1214");

    public final static String ACTION_DATA_AVAILABLE = "com.example.bluetooth.le.ACTION_DATA_AVAILABLE";

    BluetoothGattService ecg_service;
    BluetoothGattCharacteristic ecg_char;
    BluetoothGattCharacteristic hr_char;
    BluetoothGattDescriptor desc_ecg;
    BluetoothGattDescriptor desc_hr;

    TextView hr_value;

    LineChart ecg_chart;
    LineData ecg_data;

    StringBuilder hr_val;
    String aux_hr;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ble_ecg_hr);
        setTitle("ECG + HR (BLE)");

        //The bleScanner property is initialized only when needed. By deferring the initialization
        // of bluetoothAdapter and also bleScanner to when we actually need them, we avoid a crash
        // that would happen if bluetoothAdapter was initialized before onCreate() has returned.
        bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();


        start_btn = findViewById(R.id.start_btn);
        start_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startBleScan();
            }
        });

        //The button is not needed since the scan stops automatically
        stop_btn = findViewById(R.id.stop_btn);
        stop_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopBleScan();
            }
        });

        log_scan = findViewById(R.id.log_scan);
        log_scan.setMovementMethod(new ScrollingMovementMethod());

        log_data = findViewById(R.id.log_data);
        log_data.setMovementMethod(new ScrollingMovementMethod());

        conn_btn = findViewById(R.id.conn_btn);
        conn_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                connectDevice();
            }
        });

        disc_btn = findViewById(R.id.disc_btn);
        disc_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                writeStorageAccessFrameworkFile(getApplicationContext());
                disconnectDevice();
            }
        });

        hr_value = findViewById(R.id.hr_value);


        ecg_chart = findViewById(R.id.chart_ecg);
        ecg_chart.setDrawGridBackground(false);
        ecg_chart.setBackgroundColor(Color.WHITE);
        ecg_data = new LineData();
        ecg_chart.setData(ecg_data);
    }

    //we want to check in the Activity’s onResume() if Bluetooth is enabled; if it’s not,
    // we display an alert
    @Override
    protected void onResume() {
        super.onResume();
        bluetoothAdapter = bluetoothManager.getAdapter();
        if(!bluetoothAdapter.isEnabled()) {
            promptEnableBluetooth();
        }
    }

    private void promptEnableBluetooth() {
        if (!bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, ENABLE_BLUETOOTH_REQUEST_CODE);
        }
    }

    //Since the app requires Bluetooth to be enabled before it can do anything, we want to be able
    // to react to the user’s selection for the system alert.
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case ENABLE_BLUETOOTH_REQUEST_CODE:
                if (resultCode != Activity.RESULT_OK) {
                    promptEnableBluetooth();
                }
                break;
            case CREATE_FILE:
                if (data != null) {
                    Toast.makeText(getApplicationContext(), "Document successfully created", Toast.LENGTH_SHORT).show();
                    try {
                        documentUri = data.getData();
                        ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(data.getData(), "w");
                        FileOutputStream fileOutputStream = new FileOutputStream(pfd.getFileDescriptor());
                        fileOutputStream.write(dataGenerator(ecg_data).getBytes());
                        fileOutputStream.close();
                        pfd.close();
                        fileOutputStream.close();
                    } catch (Exception e) {
                        e.printStackTrace();
                        Toast.makeText(getApplicationContext(), "Document not written", Toast.LENGTH_SHORT).show();
                    }
                }
                break;
        }
    }

    //BLE Scanning
    private void startBleScan() {

        //We can create a ScanFilter using the ScanFilter.Builder class and calling its methods to
        // set the filtering criteria before finally calling build()
        ScanFilter filter = new ScanFilter.Builder().setDeviceName("ECG Monitor").build();
        List<ScanFilter> filter_list = new ArrayList<>();
        filter_list.add(filter);
        bleScanner = bluetoothAdapter.getBluetoothLeScanner();

        //Before we can start scanning, we need to specify the scan settings.
        ScanSettings scanSettings = new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build();

        //Scan
        log_scan.setText("");
        bluetoothDevices.clear();
        bleScanner.startScan(filter_list, scanSettings, scanCallback);
        log_scan.setText("Scanning...\n");
        isScanning = true;
        log_scan.setTextColor(Color.RED);

        //Stops the scanning after 1000 ms
        Handler handler = new Handler();
        if (isScanning == true) {
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    stopBleScan();
                }
            }, 1000);
        }

    }

    //we need to create an object that implements the functions in ScanCallback so that
    // we’ll be notified when a scan result is available
    private ScanCallback scanCallback;
    {
        scanCallback = new ScanCallback() {
            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                super.onScanResult(callbackType, result);
                bluetoothDevices.add(result.getDevice());
                log_scan.setText(result.getDevice().getName().toString() + "\n");
            }
        };
    }

    //BLE Scanning Stop
    private void stopBleScan() {
        bleScanner.stopScan(scanCallback);
        isScanning = false;
        log_scan.setTextColor(Color.GREEN);
        //log_scan.setText("Scan Stopped\n");
    }

    //Connect to the device index 0
    private void connectDevice() {
        log_scan.setText("Connecting to: " + bluetoothDevices.get(0).getName().toString() + "\n");
        connectGatt = bluetoothDevices.get(0).connectGatt(this, false, gattCallback);
    }

    private void disconnectDevice() {
        log_scan.setText("Disconnecting...");
        setCharoff(connectGatt);
        connectGatt.disconnect();
    }

    //Callback for connecting to a device
    private BluetoothGattCallback gattCallback;
    {
        gattCallback = new BluetoothGattCallback() {
            //Since service discovery is an essential operation to perform after a BLE connection
            // has been established, it’s often ideal to consider it as part of the connection flow
            @Override
            public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                super.onConnectionStateChange(gatt, status, newState);
                String deviceAddress = gatt.getDevice().getAddress();
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        log_scan.setText("Connected to: " + deviceAddress.toString() + "\n");
                        log_scan.setTextColor(Color.BLACK);
                        //store a ref
                        gatt.discoverServices();
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                        log_scan.setText("Disconnected from: " + deviceAddress.toString() + "\n");
                        gatt.close();
                    }
                } else {
                    log_scan.setText("ERROR: " + Integer.toString(status) + "\n");
                    gatt.close();
                }
            }

            @Override
            public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                super.onServicesDiscovered(gatt, status);
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    log_scan.setText("Discovered " + Integer.toString(gatt.getServices().size()) + " service(s) for " + gatt.getDevice().getName().toString() + "\n");
                    ecg_service = gatt.getService(service_uuid);
                    ecg_char = ecg_service.getCharacteristic(ecg_char_uuid);
                    //printGattTable(gatt);
                    gatt.requestMtu(GATT_MAX_MTU_SIZE);
                }else {
                    log_scan.setText("ERROR: " + Integer.toString(status) + "\n");
                    gatt.close();
                }

            }

            //The maximum length of an ATT data packet is determined by the ATT MTU, which is
            // typically negotiated between Android and the BLE device as part of the connection
            // process.
            @Override
            public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
                super.onMtuChanged(gatt, mtu, status);
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    log_scan.setText("MTU is: " + Integer.toString(mtu) + "\n");
                    gatt.readCharacteristic(ecg_char);
                }else {
                    log_scan.setText("ERROR: " + Integer.toString(status) + "\n");
                    gatt.close();
                }
            }

            @Override
            public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
                super.onDescriptorWrite(gatt, descriptor, status);
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    log_scan.setText("Notifications Enabled!\n");
                }else {
                    log_scan.setText("ERROR: " + Integer.toString(status) + "\n");
                    gatt.close();
                }
            }


            @Override
            public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                super.onCharacteristicRead(gatt, characteristic, status);
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    descriptors(gatt);
                    broadcastUpdate(ACTION_DATA_AVAILABLE + characteristic);
                }else {
                    log_scan.setText("ERROR: " + Integer.toString(status) + "\n");
                    gatt.close();
                }
            }

            @Override
            public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
                super.onCharacteristicChanged(gatt, characteristic);
                readECG(characteristic);
                pullHR(characteristic);
            }
        };
    }

    //function that prints out all the UUIDs of available services and characteristics that the
    // BluetoothGatt of a BLE device has to offer
    private void printGattTable(BluetoothGatt gatt) {
        if (gatt.getServices() == null) return;

        for (BluetoothGattService gattService : gatt.getServices()) {
            List<BluetoothGattCharacteristic> gattCharacteristics = gattService.getCharacteristics();
            for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {
                log_scan.setText("Service: " + gattService.getUuid().toString() + " with characteristic: " + gattCharacteristic.getUuid().toString() + "\n");
            }
        }
    }

    private void descriptors(BluetoothGatt gatt) {
        log_scan.setText("ECG data will be read from: " + ecg_char_uuid.toString() + "\n");
        gatt.setCharacteristicNotification(ecg_char, true);
        log_scan.setText(ecg_char.getDescriptors().get(0).toString());
        desc_ecg = ecg_char.getDescriptor(char_desc);
        desc_ecg.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        gatt.writeDescriptor(desc_ecg);
    }

    private void setCharoff(BluetoothGatt gatt) {
        gatt.setCharacteristicNotification(ecg_char, false);
    }

    //Data Handling
    private void broadcastUpdate(final String intentAction) {
        final Intent intent = new Intent(intentAction);
        sendBroadcast(intent);
    }

    private void pullHR(BluetoothGattCharacteristic characteristic) {
        //log_data.setText("HR is being displayed");
        hr_val = new StringBuilder("");
        if (characteristic.getValue().length == 218) {
            hr_val.append(((char) characteristic.getValue()[216]));
            hr_val.append(((char) characteristic.getValue()[217]));
        }else if (characteristic.getValue().length == 219) {
            hr_val.append(((char) characteristic.getValue()[216]));
            hr_val.append(((char) characteristic.getValue()[217]));
            hr_val.append(((char) characteristic.getValue()[218]));
        }
        aux_hr = hr_val.toString();
        hr_value.setText(aux_hr);
    }

    private void readECG(BluetoothGattCharacteristic characteristic) {
        log_data.setText(Arrays.toString(arrangestring(characteristic.getValue())));
        int[] value = arrangestring(characteristic.getValue());

        ecg_data = ecg_chart.getData();
        if (ecg_data != null) {
            ILineDataSet set = ecg_data.getDataSetByIndex(0);
            if (set == null) {
                set = createSet();
                ecg_data.addDataSet(set);
            }
            for (int j=0; j<36; j++) {
                ecg_data.addEntry(new Entry(set.getEntryCount(), value[j]), 0);
            }
            ecg_data.notifyDataChanged();
            ecg_chart.notifyDataSetChanged();
            ecg_chart.setVisibleXRangeMaximum(250);

        }
        ecg_chart.moveViewToX(ecg_data.getXMax());
    }

    private Uri documentUri;
    private void writeStorageAccessFrameworkFile(Context context) {
        SimpleDateFormat ft = new SimpleDateFormat("yyyyMMdd_HHmmss");
        String date_now = ft.format(new Date());
        String filename = "ecg_"+ date_now +".csv";
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/csv");
        intent.putExtra(Intent.EXTRA_TITLE, filename);
        startActivityForResult(intent, CREATE_FILE);
    }

    private String dataGenerator (LineData data) {
        StringBuilder data_build = new StringBuilder();
        ILineDataSet set = data.getDataSetByIndex(0);
        int max_values = set.getEntryCount();

        for (int val=0; val<max_values; val++) {
            Entry entry = set.getEntryForIndex(val);
            data_build.append(entry.getX());
            data_build.append(",");
            data_build.append(entry.getY());
            data_build.append("\n");
        }

        return data_build.toString();
    }

    //arranges the string of data from the ble characteristic to an array of integers
    private int[] arrangestring(byte[] aux) {
        int[] str = new int[36];
        for (int ij=0; ij<=210; ij+=6) {
            StringBuilder output = new StringBuilder("");
            output.append(((char) aux[ij]));
            output.append(((char) aux[ij+1]));
            output.append(((char) aux[ij+2]));
            str[ij/6]= Integer.parseInt(output.toString());
        }
        return str;
    }

    //This creates the dataset if there is none
    private LineDataSet createSet() {
        LineDataSet set = new LineDataSet(null, "ECG");
        set.setAxisDependency(YAxis.AxisDependency.LEFT);
        set.setColor(Color.RED);
        set.setLineWidth(2f);
        set.setDrawCircles(false);
        set.setFillAlpha(65);
        set.setFillColor(Color.RED);
        set.setHighLightColor(Color.rgb(244, 117, 117));
        set.setValueTextColor(Color.WHITE);
        set.setValueTextSize(9f);
        set.setDrawValues(false);
        return set;
    }

}