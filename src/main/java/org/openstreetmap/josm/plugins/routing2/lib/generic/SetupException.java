// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.routing2.lib.generic;

/**
 * Thrown when there is an issue setting up a router
 */
public class SetupException extends Exception {
    public SetupException(String message) {
        super(message);
    }

    public SetupException(Throwable cause) {
        super(cause);
    }
}
