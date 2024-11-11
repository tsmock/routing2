// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.routing2;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.sql.Array;
import java.util.ArrayList;
import java.util.List;

import javax.swing.Action;
import javax.swing.Icon;

import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.UndoRedoHandler;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmDataManager;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.data.osm.visitor.BoundingXYVisitor;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MapView;
import org.openstreetmap.josm.gui.layer.Layer;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.plugins.routing2.lib.generic.Legs;
import org.openstreetmap.josm.plugins.routing2.lib.generic.Locations;
import org.openstreetmap.josm.plugins.routing2.lib.generic.Trip;
import org.openstreetmap.josm.plugins.routing2.lib.valhalla.ValhallaServer;

public class RoutingLayer extends Layer implements UndoRedoHandler.CommandQueueListener {
    private Trip trip;

    /**
     * Create the layer and fill in the necessary components.
     *
     * @param name Layer name
     */
    protected RoutingLayer(String name) {
        super(name);
    }

    @Override
    public Icon getIcon() {
        return null;
    }

    @Override
    public String getToolTipText() {
        return "Routing layer";
    }

    @Override
    public void mergeFrom(Layer from) {
        // Yeah, no
    }

    @Override
    public boolean isMergable(Layer other) {
        return false;
    }

    @Override
    public void visitBoundingBox(BoundingXYVisitor v) {

    }

    @Override
    public Object getInfoComponent() {
        return null;
    }

    @Override
    public Action[] getMenuEntries() {
        return new Action[0];
    }

    @Override
    public void paint(Graphics2D g, MapView mv, Bounds bbox) {
        final Trip current = this.trip;
        if (current != null) {
            for (Legs leg : current.legs()) {
                double[] shape = leg.shape();
                final Path2D.Double drawShape = new Path2D.Double();
                for (int i = 0; i < shape.length; i += 2) {
                    final double lat = shape[i];
                    final double lon = shape[i + 1];
                    Point2D p = mv.getPoint2D(new LatLon(lat, lon));
                    if (i == 0) {
                        drawShape.moveTo(p.getX(), p.getY());
                    } else {
                        drawShape.lineTo(p.getX(), p.getY());
                    }
                }
                g.setColor(Color.GREEN);
                g.setStroke(new BasicStroke(10));
                g.draw(drawShape);
            }
            if (current.locations() != null) {
                for (Locations loc : current.locations()) {
                    g.setColor(Color.RED);
                    Point2D point = mv.getPoint2D(loc);
                    g.drawRect((int) point.getX(), (int) point.getY(), 3, 3);
                }
            }
        }
    }

    /**
     * Set the trip for this layer
     * @param trip The trip to show the user
     */
    public void setTrip(Trip trip) {
        this.trip = trip;
        // FIXME remove debug code
        if (MainApplication.getLayerManager().getLayersOfType(OsmDataLayer.class).stream().noneMatch(l -> "Test".equals(l.getName()))) {
            final DataSet test = new DataSet();
            for (Legs leg : trip.legs()) {
                final double[] shape = leg.shape();
                final List<Node> nodes = new ArrayList<>(shape.length / 2);
                for (int i = 0; i < shape.length; i += 2) {
                    final double lat = shape[i];
                    final double lon = shape[i + 1];
                    nodes.add(new Node(new LatLon(lat, lon)));
                }
                final Way way = new Way();
                way.setNodes(nodes);
                test.addPrimitiveRecursive(way);
            }
            MainApplication.getLayerManager().addLayer(new OsmDataLayer(test, "Test", null));
        }
    }

    @Override
    public void commandChanged(int queueSize, int redoSize) {
        // FIXME: send actual data and locations...
        this.setTrip(new ValhallaServer().generateRoute(OsmDataManager.getInstance().getActiveDataSet(), new LatLon(39.077652, -108.458828), new LatLon(39.067613, -108.560153)));
    }
}
