// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.routing2.lib.generic;

import java.util.Arrays;

/**
 * A java implementation of Google's encoded
 * <a href="https://developers.google.com/maps/documentation/utilities/polylinealgorithm">polyline</a>.
 */
public final class GooglePolyline {
    private GooglePolyline() {
        // Hide constructor
    }

    /**
     * Convert a series of latitude/longitude points
     * @param doubles The series of points in lat/lon format
     * @return The encoded polyline
     */
    static String encode(double... doubles) {
        if (doubles.length % 2 != 0) {
            throw new IllegalArgumentException("Coordinate points must come in pairs");
        }
        int lastLat1e5 = 0;
        int lastLon1e5 = 0;
        StringBuilder sb = new StringBuilder(4 * doubles.length);
        for (int i = 0; i < doubles.length; i += 2) {
            // Get the 1e5 value
            final int e5lat = Math.toIntExact(Math.round(1e5 * doubles[i]));
            final int e5lon = Math.toIntExact(Math.round(1e5 * doubles[i + 1]));
            sb.append(convertE5(e5lat - lastLat1e5));
            sb.append(convertE5(e5lon - lastLon1e5));
            lastLat1e5 = e5lat;
            lastLon1e5 = e5lon;
        }
        // Finally, convert the chunks to a string
        return sb.toString();
    }

    /**
     * Perform the encoding of a 1e5 value
     * @param e5 The value to encode
     * @return The encoded value as a char array
     */
    private static char[] convertE5(int e5) {
        // Java is already 2 complement, so we don't have to specially handle negative numbers here.
        int shift = e5 << 1; // we do lose the leading binary
        if (e5 < 0) { // If original was negative, invert the encoding
            shift = ~shift;
        }
        // Convert to binary
        final String binary = Integer.toBinaryString(shift);
        // Split into 5 bit chunks, starting from right side (reverse order)
        final byte overflow = (byte) (binary.length() % 5 > 0 ? 1 : 0);
        final char[] chunks = new char[binary.length() / 5 + overflow];
        final String[] cc = new String[chunks.length];
        for (int i = binary.length(); i > 0; i -= 5) {
            int j = chunks.length - i / 5 - overflow;
            cc[j] = binary.substring(Math.max(0, i - 5), i);
            chunks[j] = (char) Short.parseShort(cc[j], 2);
            if (j != chunks.length - 1) {
                chunks[j] |= 0x20; // If not the last chunk or it with 0x20
            }
            chunks[j] += 63; // Add 63 to each chunk
        }
        return chunks;
    }

    /**
     * Decode with a default precision of 1e5
     * @param polyline The polyline to decode
     * @return The decoded polyline
     */
    static double[] decode(String polyline) {
        return decode(polyline, 1e5);
    }

    /**
     * Decode with a given precision
     * @param polyline The polyline to decode
     * @param precision The precision to use
     * @return The decoded polyline
     */
    public static double[] decode(String polyline, double precision) {
        // This is the absolute "maximum" number of points. Could be optimized, probably not high traffic code path.
        double[] points = new double[polyline.length()];
        // Start performing char operations
        char[] chars = polyline.toCharArray();
        int point = 0;
        int current = 0;
        char[] chunks = new char[8]; // 8 * 4 = 32 bits
        int lastLat1e5 = 0;
        int lastLon1e5 = 0;
        for (int i = 0; i < chars.length; i++) {
            // First, remove 63 from each char value
            chars[i] -= 63;
            // Check if this is the last one
            boolean isLast = (chars[i] & 0x20) == 0;
            chars[i] &= (char) (~0x20 & chars[i]);
            chunks[current++] = chars[i];
            if (isLast) {
                int value = 0;
                while (current > 0) {
                    value = (value << 5) + chunks[--current];
                }
                if ((chunks[0] & 1) != 0) {
                    // Assume it was negative
                    value = ~value;
                }
                value = value >> 1;
                if (point % 2 == 0) {
                    value += lastLat1e5;
                    lastLat1e5 = value;
                } else {
                    value += lastLon1e5;
                    lastLon1e5 = value;
                }
                points[point++] = value / precision;
                current = 0;
            }
        }

        return Arrays.copyOf(points, point);
    }
}
