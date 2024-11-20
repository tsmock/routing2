// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.routing2.lib.generic;

/**
 * Thrown when there is an issue calculating a trip
 */
public class TripException extends Exception {
    public TripException(String message) {
        super(message);
    }
}
