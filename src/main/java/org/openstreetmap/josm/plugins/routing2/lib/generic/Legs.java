// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.routing2.lib.generic;

public record Legs(Maneuver[] maneuvers, Trip.Summary summary, double[] shape) {
}
