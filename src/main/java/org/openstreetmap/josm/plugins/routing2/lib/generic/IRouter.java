// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.routing2.lib.generic;

import org.openstreetmap.josm.data.coor.ILatLon;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.gui.progress.ProgressMonitor;

/**
 * An interface for communicating with routers
 */
public interface IRouter {
    /**
     * Check if setup should be performed
     * @return {@code true} if {@link #performSetup(ProgressMonitor)} should be called
     */
    boolean shouldPerformSetup();

    /**
     * Perform setup steps
     * @param progressMonitor The progress monitor to update
     * @throws SetupException when something fails during setup
     */
    void performSetup(ProgressMonitor progressMonitor) throws SetupException;

    /**
     * Generate a route
     * @param layer The layer to do routing on
     * @param locations The locations (at least two locations must be specified; the start and end points)
     * @throws TripException when trip calculations fail
     */
    Trip generateRoute(OsmDataLayer layer, ILatLon... locations) throws TripException;
}
