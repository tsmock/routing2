// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.routing2;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.Collections;

import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.text.JTextComponent;

import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.data.coor.ILatLon;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.coor.conversion.LatLonParser;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.SideButton;
import org.openstreetmap.josm.gui.dialogs.ToggleDialog;
import org.openstreetmap.josm.gui.widgets.AbstractTextComponentValidator;
import org.openstreetmap.josm.gui.widgets.JosmTextField;
import org.openstreetmap.josm.tools.Logging;
import org.openstreetmap.josm.tools.Shortcut;

/**
 * Create a new dialog for routing
 */
public class RoutingDialog extends ToggleDialog {
    /** Create the dialog */
    public RoutingDialog() {
        super(tr("Routing"), "routing", tr("Generate routes between points"), Shortcut
                .registerShortcut("routing:dialog", tr("Routing Dialog"), KeyEvent.CHAR_UNDEFINED, Shortcut.NONE), 200,
                false, null, false);
        build();
    }

    private void build() {
        final JosmTextField start = new JosmTextField();
        final JosmTextField end = new JosmTextField();
        final SideButton doRouting = new SideButton(new JosmAction(tr("Calculate route"), "dialogs/routing",
                tr("Calculate route"), Shortcut.registerShortcut("routing:calculate", tr("Calculate route"),
                        KeyEvent.CHAR_UNDEFINED, Shortcut.NONE),
                false, false) {
            @Override
            public void actionPerformed(ActionEvent e) {
                final RoutingLayer layer = new RoutingLayer("Route", LatLonParser.parse(start.getText()),
                        LatLonParser.parse(end.getText()));
                MainApplication.getLayerManager().addLayer(layer);
            }
        });
        final JPanel dataPanel = new JPanel();
        start.setHint(tr("Copy coordinates to paste here ({0})",
                MainApplication.getMainFrame().getMenu().copyCoordinates.getShortcut()));
        end.setHint(start.getHint());
        dataPanel.add(start);
        dataPanel.add(end);
        this.createLayout(dataPanel, false, Collections.singleton(doRouting));
        final ILatLon llStart = new LatLon(39.077652, -108.458828);
        final ILatLon llStop = new LatLon(39.067613, -108.560153);
        start.setText(llStart.lat() + ", " + llStart.lon());
        end.setText(llStop.lat() + ", " + llStop.lon());
        new LatLonValidator(doRouting, start);
        new LatLonValidator(doRouting, end);
    }

    private static class LatLonValidator extends AbstractTextComponentValidator {

        private final JButton toEnable;

        protected LatLonValidator(JButton toEnable, JTextComponent tc) {
            super(tc);
            this.toEnable = toEnable;
        }

        @Override
        public void validate() {
            LatLon latLon;
            try {
                latLon = LatLonParser.parse(this.getComponent().getText());
                if (!LatLon.isValidLat(latLon.lat()) || !LatLon.isValidLon(latLon.lon())) {
                    latLon = null;
                }
            } catch (IllegalArgumentException e) {
                Logging.trace(e);
                latLon = null;
            }
            if (latLon == null) {
                feedbackInvalid(tr("Please enter GPS coordinates"));
                this.toEnable.setEnabled(false);
            } else {
                feedbackValid(null);
                this.toEnable.setEnabled(true);
            }
        }

        @Override
        public boolean isValid() {
            throw new UnsupportedOperationException();
        }
    }
}
