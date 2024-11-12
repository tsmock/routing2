// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.routing2;

import java.util.ArrayList;
import java.util.List;

import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MapFrame;
import org.openstreetmap.josm.plugins.Plugin;
import org.openstreetmap.josm.plugins.PluginInformation;
import org.openstreetmap.josm.tools.Destroyable;

public class Routing2Plugin extends Plugin implements Destroyable {
    /**
     * Creates the plugin
     *
     * @param info the plugin information describing the plugin.
     */
    public Routing2Plugin(PluginInformation info) {
        super(info);
    }

    @Override
    public void mapFrameInitialized(MapFrame oldFrame, MapFrame newFrame) {
        super.mapFrameInitialized(oldFrame, newFrame);
        if (newFrame != null) {
            MainApplication.getMap().addToggleDialog(new RoutingDialog());
        }
    }

    @Override
    public void destroy() {
        final List<RoutingLayer> layerList = new ArrayList<>(
                MainApplication.getLayerManager().getLayersOfType(RoutingLayer.class));
        layerList.forEach(MainApplication.getLayerManager()::removeLayer);
    }
}
