# BLE-NFC-SensorApp

Android App to show data on real time from BLE and NFC supported microcontrollers. Supports RT data graph, storage and specialized sensor display (ECG/PPG).
Made to support Arduino microcontrollers in BLE communication of Analog signals. NFC is supported for Texas Instruments RF430FRL152H microcontroller.

## Common Features

- Real time data display
- Real Time data line graph. Supported with MPAndroidChart by PhilJay
- Data conversion in CSV for analysis

## Bluetooth Low Energy

- BLE support for Arduino Nano 33 BLE. Arduino code can be found in
- Support for analog sensors in ports A0 and A1
- Support for digital sensors using I2C (future release)

## Near Field Communication

- Works with Texas Instruments RF430FRL152H chip
- NFC tested with the microntroller Evaluation Board
- 8 samples/sec speed
- Support for analog sensor in ADC0
- Support for digital sensors using I2C (future release)
