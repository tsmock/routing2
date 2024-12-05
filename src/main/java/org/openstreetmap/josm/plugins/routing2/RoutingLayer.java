// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.routing2;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.util.Arrays;
import java.util.function.Consumer;
import java.util.stream.Stream;

import javax.swing.Action;
import javax.swing.Icon;

import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.UndoRedoHandler;
import org.openstreetmap.josm.data.coor.ILatLon;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.visitor.BoundingXYVisitor;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MapView;
import org.openstreetmap.josm.gui.layer.Layer;
import org.openstreetmap.josm.gui.progress.swing.PleaseWaitProgressMonitor;
import org.openstreetmap.josm.plugins.routing2.lib.generic.Legs;
import org.openstreetmap.josm.plugins.routing2.lib.generic.Locations;
import org.openstreetmap.josm.plugins.routing2.lib.generic.Maneuver;
import org.openstreetmap.josm.plugins.routing2.lib.generic.SetupException;
import org.openstreetmap.josm.plugins.routing2.lib.generic.Trip;
import org.openstreetmap.josm.plugins.routing2.lib.valhalla.ValhallaServer;
import org.openstreetmap.josm.tools.JosmRuntimeException;
import org.openstreetmap.josm.tools.ListenerList;

public class RoutingLayer extends Layer implements UndoRedoHandler.CommandQueueListener {
    private final ListenerList<Consumer<Trip>> tripConsumers = ListenerList.create();
    private final ILatLon start;
    private final ILatLon end;
    private Trip trip;
    private Maneuver maneuver;

    /**
     * Create the layer and fill in the necessary components.
     *
     * @param name Layer name
     * @param start The start of the route
     * @param end The end of the route
     */
    protected RoutingLayer(String name, ILatLon start, ILatLon end) {
        super(name);
        UndoRedoHandler.getInstance().addCommandQueueListener(this);
        this.start = start;
        this.end = end;
        this.commandChanged(0, 0);
        this.setOpacity(.5);
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
            Path2D.Double maneuverShape = new Path2D.Double();
            for (Legs leg : current.legs()) {
                double[] shape = leg.shape();
                final Path2D.Double drawShape = new Path2D.Double(Path2D.WIND_NON_ZERO, shape.length / 2);
                for (int i = 0; i < shape.length; i += 2) {
                    final double lat = shape[i];
                    final double lon = shape[i + 1];
                    Point2D p = mv.getPoint2D(new LatLon(lat, lon));
                    if (this.maneuver != null && i / 2 >= this.maneuver.startShape()
                            && i / 2 <= this.maneuver.endShape()) {
                        if (i / 2 == this.maneuver.startShape()) {
                            maneuverShape.moveTo(p.getX(), p.getY());
                        } else {
                            maneuverShape.lineTo(p.getX(), p.getY());
                        }
                    }
                    if (i == 0) {
                        drawShape.moveTo(p.getX(), p.getY());
                    } else {
                        drawShape.lineTo(p.getX(), p.getY());
                    }
                }
                g.setColor(Color.GREEN);
                g.setStroke(new BasicStroke(10));
                g.draw(drawShape);
                g.setStroke(new BasicStroke(5));
                g.setColor(Color.RED);
                g.draw(maneuverShape);
                // Draw maneuver locations
                for (Maneuver t : leg.maneuvers()) {
                    final int i = t.startShape();
                    final double lat = shape[2 * i];
                    final double lon = shape[2 * i + 1];
                    final Point2D p = mv.getPoint2D(new LatLon(lat, lon));
                    final Point2D previous;
                    final Point2D next;
                    if (i == 0 && shape.length <= 2 * (i + 1) + 1) {
                        previous = null;
                        next = null;
                    } else if (i == 0) {
                        previous = null;
                        next = mv.getPoint2D(new LatLon(shape[2 * (i + 1)], shape[2 * (i + 1) + 1]));
                    } else if (shape.length <= 2 * (i + 1) + 1) {
                        previous = mv.getPoint2D(new LatLon(shape[2 * (i - 1)], shape[2 * (i - 1) + 1]));
                        next = null;
                    } else {
                        previous = mv.getPoint2D(new LatLon(shape[2 * (i - 1)], shape[2 * (i - 1) + 1]));
                        next = mv.getPoint2D(new LatLon(shape[2 * (i + 1)], shape[2 * (i + 1) + 1]));
                    }
                    g.setColor(Color.ORANGE);
                    g.drawRect((int) (p.getX() - 4), (int) (p.getY() - 4), 8, 8);
                    paintArrow(g, t.type(), previous, p, next);
                }
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

    private void paintArrow(Graphics2D g, Maneuver.Type type, Point2D previous, Point2D current, Point2D next) {
        final Polygon arrowHead = new Polygon();
        arrowHead.addPoint(0, -5);
        arrowHead.addPoint(-5, 5);
        arrowHead.addPoint(5, 5);
        final AffineTransform original = g.getTransform();
        final AffineTransform transform = AffineTransform.getTranslateInstance(current.getX(), current.getY());
        final double angle;
        if (previous != null) {
            angle = Math.atan2(current.getY() - previous.getY(), current.getX() - previous.getX()) + Math.PI / 2;
        } else if (next != null) {
            angle = Math.atan2(current.getY() - next.getY(), current.getX() - next.getX()) + Math.PI / 2;
        } else {
            angle = 0;
        }
        transform.rotate(angle);
        transform.preConcatenate(original);
        // Rotate relative to previous point
        g.setColor(Color.YELLOW);
        final double turnAngle = switch (type) {
        case RIGHT, DESTINATION_RIGHT, EXIT_RIGHT, START_RIGHT -> Math.PI / 2;
        case LEFT, DESTINATION_LEFT, EXIT_LEFT, START_LEFT -> 3 * Math.PI / 2;
        // Don't bother painting arrows
        case NONE -> Double.NaN;
        default -> Double.NaN;
        };
        if (Double.isNaN(turnAngle))
            return;
        transform.rotate(turnAngle);
        try {
            g.setTransform(transform);
            g.fill(arrowHead);
        } finally {
            g.setTransform(original);
        }
    }

    /**
     * Set the trip for this layer
     * @param newTrip The trip to show the user
     */
    public void setTrip(Trip newTrip) {
        this.trip = newTrip;
        this.tripConsumers.fireEvent(c -> c.accept(newTrip));
        this.invalidate();
    }

    /**
     * Get the current trip shown
     * @return The current trip
     */
    public Trip getTrip() {
        return this.trip;
    }

    /**
     * Add a listener for when a trip updates
     * @param tripConsumer The consumer to notify
     */
    public void addTripListener(Consumer<Trip> tripConsumer) {
        this.tripConsumers.addListener(tripConsumer);
    }

    @Override
    public void commandChanged(int queueSize, int redoSize) {
        MainApplication.worker.execute(() -> {
            ValhallaServer valhallaServer = new ValhallaServer();
            final PleaseWaitProgressMonitor monitor = new PleaseWaitProgressMonitor(
                    tr("Downloading configured router"));
            if (valhallaServer.shouldPerformSetup()) {
                try {
                    monitor.beginTask(tr("Download"), 1);
                    valhallaServer.performSetup(monitor);
                } catch (SetupException setupException) {
                    throw new JosmRuntimeException(setupException);
                } finally {
                    monitor.close();
                }
            }
            if (!monitor.isCanceled()) {
                this.setTrip(valhallaServer.generateRoute(MainApplication.getLayerManager().getActiveDataLayer(),
                        this.start, this.end));
            }
        });
    }

    @Override
    public synchronized void destroy() {
        super.destroy();
        UndoRedoHandler.getInstance().removeCommandQueueListener(this);
    }

    /**
     * Set the currently highlighted maneuver
     * @param maneuver The maneuver to highlight
     */
    public void setHighlightedManeuver(Maneuver maneuver) {
        if (this.trip != null && maneuver != null
                && Stream.of(this.trip.legs()).flatMap(l -> Arrays.stream(l.maneuvers())).anyMatch(maneuver::equals)) {
            this.maneuver = maneuver;
            this.invalidate();
        } else if (maneuver == null) {
            this.maneuver = null;
            this.invalidate();
        }
    }

    /**
     * Get the currently highlighted maneuver
     * @return The highlighted maneuver
     */
    public Maneuver getHighlightedManeuver() {
        return this.maneuver;
    }
}
