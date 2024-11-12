// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.routing2.lib.generic;

import java.util.Map;

import org.openstreetmap.josm.data.coor.ILatLon;

public record Locations(
        /* We first have the items that affect routing */
        double lat, double lon, Type type, double heading, double heading_tolerance, String street,
                        long way_id, int minimum_reachability, double radius, boolean rank_candidates,
                        Side preferred_side, double display_lat, double display_lon, double search_cutoff,
                        double node_snap_tolerance, double street_side_max_distance,
                        StreetType street_side_cutoff, Map<SearchFilter, Object> search_filter,
                        Object preferred_layer,
                        /* Then we have the items that only affect returns for convenience */
                        String name, String city, String state, String postal_code, String country,
                        String phone, String url, Double waiting) implements ILatLon {
    enum Type {
        BREAK,
        THROUGH,
        VIA,
        BREAK_THROUGH
    }
    enum Side {
        SAME,
        OPPOSITE,
        EITHER
    }
    enum StreetType {
        MOTORWAY, TRUNK, PRIMARY, SECONDARY, TERTIARY, UNCLASSIFIED, RESIDENTIAL, SERVICE_OTHER
    }
    enum SearchFilter {
        EXCLUDE_TUNNEL,
        EXCLUDE_BRIDGE,
        EXCLUDE_RAMP,
        EXCLUDE_CLOSURES,
        MIN_ROAD_CLASS,
        MAX_ROAD_CLASS
    }
}
