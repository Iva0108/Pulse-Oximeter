#include <Wire.h>
#include <Adafruit_GFX.h>
#include <Adafruit_SSD1306.h>

#include "MAX30105.h"
#include "heartRate.h"
#include "spo2_algorithm.h"

// ---------------- OLED SETTINGS ----------------
#define SCREEN_WIDTH 128
#define SCREEN_HEIGHT 64
#define OLED_RESET -1

Adafruit_SSD1306 display(SCREEN_WIDTH, SCREEN_HEIGHT, &Wire, OLED_RESET);

// ---------------- MAX30102 ----------------
MAX30105 particleSensor;

#if defined(__AVR_ATmega328P__) || defined(__AVR_ATmega168__)
uint16_t irBuffer[100];
uint16_t redBuffer[100];
#else
uint32_t irBuffer[100];
uint32_t redBuffer[100];
#endif

int32_t  spo2;
int8_t   validSPO2;
int32_t  heartRate;
int8_t   validHeartRate;

unsigned long lastUpdate = 0;

// -----------------------------------------------
// HC-05 is on Serial2: PA2 = TX,  PA3 = RX
// HC-05 default baud rate is 9600
// -----------------------------------------------
#define BT_SERIAL Serial2
#define BT_BAUD   9600
HardwareSerial Serial2(PA3, PA2);
void setup()
{
  Serial.begin(115200);

  // ── Bluetooth UART ──────────────────────────────
  BT_SERIAL.begin(BT_BAUD);
  Serial.println("HC-05 serial started on Serial2 (PA2/PA3) at 9600");

  // -------- OLED INIT --------
  if (!display.begin(SSD1306_SWITCHCAPVCC, 0x3C))
  {
    Serial.println("SSD1306 failed");
    while (1);
  }

  display.clearDisplay();
  display.setTextColor(SSD1306_WHITE);

  display.setTextSize(2);
  display.setCursor(10, 10);
  display.println("MAX30102");

  display.setTextSize(1);
  display.setCursor(18, 40);
  display.println("Initializing...");
  display.display();

  // -------- SENSOR INIT --------
  if (!particleSensor.begin(Wire, I2C_SPEED_STANDARD))
  {
    Serial.println("MAX30102 not found");

    display.clearDisplay();
    display.setTextSize(1);
    display.setCursor(0, 20);
    display.println("MAX30102");
    display.println("NOT FOUND");
    display.display();

    while (1);
  }

  particleSensor.setup(
    50,    // LED brightness
    8,     // sample average
    2,     // LED mode
    50,    // sample rate
    411,   // pulse width
    4096   // ADC range
  );

  display.clearDisplay();
  display.setTextSize(1);
  display.setCursor(0, 20);
  display.println("Place finger");
  display.println("on sensor");
  display.display();

  delay(2000);
}

void loop()
{
  // Collect 100 samples
  for (byte i = 0; i < 100; i++)
  {
    while (!particleSensor.available())
      particleSensor.check();

    redBuffer[i] = particleSensor.getRed();
    irBuffer[i]  = particleSensor.getIR();

    particleSensor.nextSample();
  }

  // Calculate HR and SpO2
  maxim_heart_rate_and_oxygen_saturation(
    irBuffer,
    100,
    redBuffer,
    &spo2,
    &validSPO2,
    &heartRate,
    &validHeartRate
  );

  // Update every second
  if (millis() - lastUpdate > 1000)
  {
    lastUpdate = millis();

    // ── BLUETOOTH OUTPUT ─────────────────────────────────────────────────────
    // Format: "BPM:72,SPO2:98\n" (what DataParser.java expects).
    // Only send when BOTH readings are valid; otherwise send zeroes
    // so the app can show "--" instead of stale data.
    if (validHeartRate && validSPO2)
    {
      BT_SERIAL.print("BPM:");
      BT_SERIAL.print((int)heartRate);
      BT_SERIAL.print(",SPO2:");
      BT_SERIAL.print((int)spo2);
      BT_SERIAL.print("\n");   
    }
    else
    {
      // Send zeroes — DataParser will mark them invalid and the app shows "--"
      BT_SERIAL.print("BPM:0,SPO2:0\n");
    }

    // ── USB/DEBUG OUTPUT (for Serial Monitor) ─────────────────────
    Serial.print("HR: ");
    if (validHeartRate) Serial.print(heartRate);
    else                Serial.print("Invalid");

    Serial.print(" BPM | SpO2: ");
    if (validSPO2) { Serial.print(spo2); Serial.println("%"); }
    else             Serial.println("Invalid");

    // -------- OLED OUTPUT --------
    display.clearDisplay();

    display.setTextSize(1);
    display.setCursor(0, 0);
    display.println("Pulse Oximeter");

    display.setTextSize(2);
    display.setCursor(0, 18);
    if (validHeartRate) 
    { display.print(heartRate); display.println(" BPM");
    }
    else                  
      display.println("HR --");

    display.setCursor(0, 45);
    if (validSPO2) 
    { display.print(spo2); display.println("%"); 
    }
    else             
      display.println("SpO2 --");

    display.display();
  }
}
