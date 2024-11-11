// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.routing2.lib.generic;

public record Trip(Locations[] locations, Legs[] legs, Summary summary) {
    public record Summary(boolean has_time_restrictions, boolean has_toll, boolean has_highway, boolean has_ferry,
                          double min_lat, double min_lon, double max_lat, double max_lon,
                          double time, double length, double cost) {}
}
