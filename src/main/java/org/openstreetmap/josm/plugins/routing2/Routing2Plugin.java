// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.routing2;

import java.util.ArrayList;
import java.util.Collection;

import org.openstreetmap.josm.data.UndoRedoHandler;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MapFrame;
import org.openstreetmap.josm.plugins.Plugin;
import org.openstreetmap.josm.plugins.PluginInformation;

public class Routing2Plugin extends Plugin {
    private final Collection<RoutingLayer> layers = new ArrayList<>();

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
            final RoutingLayer layer = new RoutingLayer("Test router"); // FIXME: Add via UI button, not automatically
            MainApplication.getLayerManager().addLayer(layer);
            UndoRedoHandler.getInstance().addCommandQueueListener(layer);
            layers.add(layer);
        } else {
            layers.forEach(UndoRedoHandler.getInstance()::removeCommandQueueListener);
            layers.clear();
        }
    }
}
