package com.example.ioelsensorapp.ui.main_menu;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.example.ioelsensorapp.NFC_ECG_HR;
import com.example.ioelsensorapp.NFC_ECG_PPG;
import com.example.ioelsensorapp.NFC_PPG;
import com.example.ioelsensorapp.R;

public class FragmentNFC extends Fragment {

    public FragmentNFC () {

    }

    Button ecg_hr_nfc_btn;
    Button ppg_nfc_btn;
    Button ecg_ppg_nfc_btn;

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_main_menu_nfc, container, false);

        ecg_hr_nfc_btn = root.findViewById(R.id.button4);
        ecg_hr_nfc_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(getActivity(), NFC_ECG_HR.class);
                startActivity(intent);
            }
        });

        ppg_nfc_btn = root.findViewById(R.id.button5);
        ppg_nfc_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(getActivity(), NFC_PPG.class);
                startActivity(intent);
            }
        });

        ecg_ppg_nfc_btn = root.findViewById(R.id.button6);
        ecg_ppg_nfc_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(getActivity(), NFC_ECG_PPG.class);
                startActivity(intent);
            }
        });


        return root;
    }
}