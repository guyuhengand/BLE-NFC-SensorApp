package com.example.ioelsensorapp.ui.main_menu;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatCallback;
import androidx.fragment.app.Fragment;

import com.example.ioelsensorapp.BLE_ECG_HR;
import com.example.ioelsensorapp.BLE_ECG_PPG;
import com.example.ioelsensorapp.BLE_PPG;
import com.example.ioelsensorapp.MainMenu;
import com.example.ioelsensorapp.R;

public class FragmentBLE extends Fragment {

    public FragmentBLE () {
    }

    Button ecg_hr_ble_btn;
    Button ppg_ble_btn;
    Button ecg_ppg_ble_btn;

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_main_menu_ble, container, false);

        ecg_hr_ble_btn = root.findViewById(R.id.button);
        ecg_hr_ble_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(getActivity(), BLE_ECG_HR.class);
                startActivity(intent);
            }
        });

        ppg_ble_btn = root.findViewById(R.id.button2);
        ppg_ble_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(getActivity(), BLE_PPG.class);
                startActivity(intent);
            }
        });

        ecg_ppg_ble_btn = root.findViewById(R.id.button3);
        ecg_ppg_ble_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(getActivity(), BLE_ECG_PPG.class);
                startActivity(intent);
            }
        });

        return root;
    }

}