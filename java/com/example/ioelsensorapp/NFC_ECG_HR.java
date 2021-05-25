package com.example.ioelsensorapp;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.net.Uri;
import android.nfc.NfcAdapter;
import android.nfc.NfcManager;
import android.nfc.Tag;
import android.nfc.tech.NfcV;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Description;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;

import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Timer;


public class NFC_ECG_HR extends AppCompatActivity {

    private static final int CREATE_FILE = 2;

    LineChart ecg_chart;
    LineData ecg_data;

    TextView status;
    TextView value;
    TextView peak_val;

    NfcAdapter nfc;
    PendingIntent mPendingIntent;

    String f_val="00 00 00 00 00 00 00 00 00";
    String stat_text;
    byte b_val=0x01,dig_op=0x00;

    private Runnable mTimer;
    private final Handler mHandler = new Handler();

    PeakDetection peakDetection = new PeakDetection();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_nfc_ecg_hr);
        setTitle("ECG + HR (NFC)");

        ecg_chart = findViewById(R.id.chart_ecg_2);
        ecg_chart.setDrawGridBackground(false);
        ecg_chart.setBackgroundColor(Color.WHITE);
        Description desc = ecg_chart.getDescription();
        desc.setText("ECG Graph");
        ecg_data = new LineData();
        ecg_chart.setData(ecg_data);

        status = findViewById(R.id.log_conn);
        status.setText("Disconnected");

        value = findViewById(R.id.log_data);
        peak_val = findViewById(R.id.heartbeat);

        nfc = NfcAdapter.getDefaultAdapter(this);
        b_val=1;

        if (nfc != null) {
            //status.setText("Read an NFC Tag");
        } else {
            //status.setText("Phone not NFC enabled");
        }

        mPendingIntent = PendingIntent.getActivity(this, 0, new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),0);

        peakDetection.begin(48,3,0.6);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
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



    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        resolveIntent(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (nfc != null) {
            //Declare intent filters to handle the intents that you want to intercept.
            IntentFilter tech_intent = new IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED);
            IntentFilter[] intentFiltersArray = new IntentFilter[] {tech_intent, };
            //Set up an array of tag technologies that your application wants to handle
            String[][] techListsArray = new String[][] { new String[] { NfcV.class.getName() } };
            //Enable foreground dispatch to stop restart of app on detection
            nfc.enableForegroundDispatch(this, mPendingIntent, intentFiltersArray, techListsArray);
        }
        mTimer = new Runnable() {
            @Override
            public void run() {
                ecg_data = ecg_chart.getData();
                if (ecg_data != null) {
                    ILineDataSet set = ecg_data.getDataSetByIndex(0);
                    if (set == null) {
                        set = createSet();
                        ecg_data.addDataSet(set);
                    }
                    int tmp = (Integer.parseInt(f_val.substring(3,4), 16)<<4)+ (Integer.parseInt(f_val.substring(4,5), 16))+ (Integer.parseInt(f_val.substring(6,7), 16) << 12) + (Integer.parseInt(f_val.substring(7,8), 16) << 8);
                    float acc = (float)(0.9*tmp)/(float)16384;
                    value.setText("Value: " + tmp+ "  " + f_val);

                    ecg_data.addEntry(new Entry(set.getEntryCount(), acc), 0);
                    ecg_data.notifyDataChanged();
                    ecg_chart.notifyDataSetChanged();
                    ecg_chart.setVisibleXRangeMaximum(250);

                    peakDetection.add(acc);
                    double peak = peakDetection.getPeak();
                    boolean beatup = false;
                    int BPM=80;
                    long BPMmills=0;
                    boolean BPMtiming=false;
                    long currentmillis = System.currentTimeMillis();
                    if (peak == 1) {
                        if (beatup) {
                            BPM = Math.toIntExact(currentmillis - BPMmills);
                            BPM = 30/(BPM/2000);
                            BPMtiming=false;
                            beatup=false;
                            peak_val.setText(BPM);
                        }
                        if (BPMtiming==false) {
                            BPMmills=System.currentTimeMillis();
                            BPMtiming=true;
                        }

                    }
                    if ((peak==0)&&(BPMtiming)) beatup=true;
                }
                ecg_chart.moveViewToX(ecg_data.getXMax());

                status.setText(stat_text);
                if (stat_text == "Connected") {
                    status.setTextColor(Color.GREEN);
                } else {
                    status.setTextColor(Color.RED);
                }

                if(b_val>0)
                {
                }
                mHandler.postDelayed(this, 1);
            }
        };
        mHandler.postDelayed(mTimer, 2);
    }



    @Override
    protected void onPause() {
        mHandler.removeCallbacks(mTimer);
        super.onPause();
        //text_view.setText("tag disconnected!");
        //text_val="tag disconnected!";
        //Log.i("life cycle", "Called onPause");

        if (nfc != null) {
            nfc.disableForegroundDispatch(this);
        }

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        writeStorageAccessFrameworkFile(getApplicationContext());
    }

    private Tag currentTag;
    private void resolveIntent(Intent intent) {
        String action = intent.getAction();
        //check if the tag is ISO15693 and display message
        if (NfcAdapter.ACTION_TAG_DISCOVERED.equals(action) || NfcAdapter.ACTION_TECH_DISCOVERED.equals(action)) {
            currentTag = (Tag) intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
            new NfcVReaderTask().execute(currentTag);// read ADC data in background
        }
    }

    private class NfcVReaderTask extends AsyncTask<Tag, Void, String> {
        @Override
        protected void onPostExecute(String result) {
        }
        @Override
        protected String doInBackground(Tag... params) {
            Tag tag = params[0];
            readTagData(tag);
            return null;
        }

    }

    private void readTagData(Tag tag) {

        byte[] id = tag.getId();
        boolean techFound = false;
        for (String tech : tag.getTechList()) {

            // checking for NfcV
            if (tech.equals(NfcV.class.getName())) {
                techFound = true;


                // Get an instance of NfcV for the given tag:
                NfcV nfcv_senseTag = NfcV.get(tag);

                try {
                    nfcv_senseTag.connect();
                    stat_text ="Connected";
                }catch (IOException e) {
                    stat_text ="Disconnected";
                    return;
                }

                //read register test
                byte[] cmd = new byte[] {
                        //   (byte)0x18, // Always needed, everything after this 18 is sent over the air, response is given in the text box below
                        (byte)0x02, // Flags (always use same)
                        (byte)0x20, // ISO15693 command code, in this case it is Read Single Block
                        (byte)b_val, // Block number
                };

                byte[] systeminfo;
                try {
                    systeminfo = nfcv_senseTag.transceive(cmd);
                }catch (IOException e) {
                    stat_text ="Disconnected";
                    return;
                }

                //write  to block 2
                cmd = new byte[] {
                        //   (byte)0x18, // Always needed, everything after this 18 is sent over the air, response is given in the text box below
                        (byte)0x02, // Flags (always use same)
                        (byte)0x21, // ISO15693 command code, in this case it is Write Single Block
                        (byte)0x02, //block number
                        (byte)0x00, //reg1 Reference-ADC1 Configuration Register
                        (byte)0x00, //reg2 ADC2 Sensor Configuration Register
                        (byte)0x40, //reg3 ADC0 Sensor Configuration Register
                        (byte)0x00, //reg4 Internal Sensor Configuration Register
                        (byte)0x00, //reg5 Initial Delay Period Setup Register
                        (byte)0x00, //reg6  JTAG Enable Password Register
                        (byte)0x00, //reg7 Initial Delay Period Register
                        (byte)0x00, //reg8 Initial Delay Period Register
                };
                byte[] ack;
                try {
                    ack = nfcv_senseTag.transceive(cmd);
                }catch (IOException e) {
                    stat_text ="Disconnected";
                    return;
                }
                while(b_val>0) {
                    //write 01 00 04 00 01 01 00 40 to block 0
                    cmd = new byte[]{
                            //   (byte)0x18, // Always needed, everything after this 18 is sent over the air, response is given in the text box below
                            (byte) 0x02, // Flags (always use same)
                            (byte) 0x21, // ISO15693 command code, in this case it is Write Single Block
                            (byte) 0x00, //block number
                            (byte) 0x01, //Start bit is set, after this is written this starts the sampling process, interrupt enabled for On/Off
                            (byte) 0x00, //Status byte: PROTECTED
                            (byte) 0x04, //Reference resistor, thermistor, ADC0 sensor: only AC0 selected
                            (byte) 0x00, //Frequency register, this is do not care since only one sample or pass is done
                            (byte) 0x01, //only one pass is needed
                            (byte) 0x01, //No averaging selected
                            (byte) 0x00, //Interrupt enabled, push pull active high options selected
                            (byte) 0x00, //Selected using thermistor
                    };
                    ack = new byte[]{0x01};

                    try {
                        ack = nfcv_senseTag.transceive(cmd);
                    } catch (IOException e) {
                        stat_text ="Disconnected";
                        return;
                    }


                    //poll status byte 00
                    cmd = new byte[]{
                            //   (byte)0x18, // Always needed, everything after this 18 is sent over the air, response is given in the text box below
                            (byte) 0x02, // Flags (always use same)
                            (byte) 0x20, // ISO15693 command code, in this case it is Read Single Block
                            (byte) 0x00, // Block number
                    };
                    byte[] new_info;
                    do {
                        try {
                            new_info = nfcv_senseTag.transceive(cmd);
                        } catch (IOException e) {
                            stat_text ="Disconnected";
                            return;
                        }

                    } while (new_info[2] != 0x02);

                    //Read data on 09
                    cmd = new byte[]{
                            //   (byte)0x18, // Always needed, everything after this 18 is sent over the air, response is given in the text box below
                            (byte) 0x02, // Flags (always use same)
                            (byte) 0x20, // ISO15693 command code, in this case it is Read Single Block
                            (byte) 0x09, // Block number
                    };

                    byte[] reading;
                    try {
                        reading = nfcv_senseTag.transceive(cmd);
                    } catch (IOException e) {
                        stat_text ="Disconnected";
                        return;
                    }
                    f_val = bytesToHex(reading);

                    //Read data confirmation on 0A
                    cmd = new byte[]{
                            //   (byte)0x18, // Always needed, everything after this 18 is sent over the air, response is given in the text box below
                            (byte) 0x02, // Flags (always use same)
                            (byte) 0x20, // ISO15693 command code, in this case it is Read Single Block
                            (byte) 0x0A, // Block number
                    };


                    try {
                        new_info = nfcv_senseTag.transceive(cmd);
                    } catch (IOException e) {
                        stat_text ="Disconnected";
                        return;
                    }
                }
                try {
                    nfcv_senseTag.close();
                } catch (IOException e) {
                    stat_text ="Disconnected";
                    return;
                }
                stat_text ="Disconnected";
            }
        }
    }

    //parsing function
    final protected static char[] hexArray = "0123456789ABCDEF".toCharArray();
    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 3];
        for ( int j = 0; j < bytes.length; j++ ) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 3] = hexArray[v >>> 4];
            hexChars[j * 3 + 1] = hexArray[v & 0x0F];
            hexChars[j * 3 + 2] = ' ';
        }
        return new String(hexChars);
    }

    private LineDataSet createSet() {
        LineDataSet set = new LineDataSet(null, "PPG");
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