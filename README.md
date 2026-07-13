A pulse oximeter project that consists of hardware, firmware, and software implementation. This project is provided for educational and research purposes only, 
and is NOT authorized for medical use.
This project presents a portable pulse oximeter capable of measuring blood oxygen saturation (SpO₂) and heart rate (BPM) using the MAX30102
sensor. The measured values are displayed on an OLED screen and transmitted wirelessly to an Android application via an HC-05 Bluetooth module.

Features:
- Measurement of SpO₂ (oxygen saturation)
- Measurement of heart rate (BPM)
- Real-time display on SSD1306 OLED (128×64)
- Wireless Bluetooth communication
- Custom KiCad PCB design
- Android application for data visualization
- Powered by a 3.7 V Li-Po battery with charging circuitry

Building the Project:

Android App
The Android application was developed in Java using Android Studio. 
It connects to the HC-05 module via Bluetooth SPP and displays the received BPM and SpO₂ values in real time.
Expected data format:
BPM:72,SPO2:98
- Open Oximeter_App/PulseOxApp in Android Studio.
- Allow Gradle synchronization.
- Build and run the application.

Firmware
The firmware was developed in the Arduino IDE for the STM32 Blue Pill. 
It uses the MAX30105 library to acquire sensor data, calculate heart rate and oxygen saturation, and transmit the values over Bluetooth.
- Open BT_PulseOximeter.ino in the Arduino IDE.
- Select the STM32 board package (if not already installed, install it via Boards Manager).
- Upload the firmware using ST-Link. It might be necessary to use ST Programmer for flashing instead of doing it directly in Arduino IDE, in which case you will need
to select either a .bin or .elf file to do so.

PCB
The custom PCB was designed in KiCad as a two-layer board with separate schematic sheets for power supply, MCU, sensor module, HC-05 Bluetooth module and OLED display.
- Open Oximeter.kicad_pro in KiCad.
- Use the PCB editor or 3D viewer to inspect the design.
