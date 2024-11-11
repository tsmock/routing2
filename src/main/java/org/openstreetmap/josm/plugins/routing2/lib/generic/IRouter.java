// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.routing2.lib.generic;

import org.openstreetmap.josm.data.coor.ILatLon;
import org.openstreetmap.josm.data.osm.DataSet;

/**
 * An interface for communicating with routers
 */
public interface IRouter {
    /**
     * Generate a route
     * @param dataSet The dataset to use
     * @param locations The locations (at least two locations must be specified; the start and end points)
     */
    Trip generateRoute(DataSet dataSet, ILatLon... locations);
}
