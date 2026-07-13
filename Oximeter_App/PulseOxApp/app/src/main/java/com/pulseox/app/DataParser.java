package com.pulseox.app;

/**
 * Parses the serial string sent by the STM32.
 *
 * Expected format from STM32:  "BPM:72,SPO2:98\n"
 * Both fields are integers. SpO2 is in %, BPM is beats per minute.
 */
public class DataParser {

    public static class Reading {
        public final int bpm;
        public final int spo2;
        public final boolean valid;

        Reading(int bpm, int spo2, boolean valid) {
            this.bpm   = bpm;
            this.spo2  = spo2;
            this.valid = valid;
        }
    }

    /**
     * Parse a line like "BPM:72,SPO2:98"
     * Returns an invalid Reading if the format doesn't match.
     */
    public static Reading parse(String line) {
        try {
            // Split on comma
            String[] parts = line.split(",");
            if (parts.length != 2) return invalid();

            // Parse BPM
            String bpmPart = parts[0].trim(); // "BPM:72"
            if (!bpmPart.startsWith("BPM:")) return invalid();
            int bpm = Integer.parseInt(bpmPart.substring(4).trim());

            // Parse SpO2
            String spo2Part = parts[1].trim(); // "SPO2:98"
            if (!spo2Part.startsWith("SPO2:")) return invalid();
            int spo2 = Integer.parseInt(spo2Part.substring(5).trim());

            // Sanity-check physiological ranges
            if (bpm < 20 || bpm > 250) return invalid();
            if (spo2 < 0  || spo2 > 100) return invalid();

            return new Reading(bpm, spo2, true);

        } catch (NumberFormatException e) {
            return invalid();
        }
    }

    private static Reading invalid() {
        return new Reading(0, 0, false);
    }
}