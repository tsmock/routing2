// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.routing2;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyEvent;
import java.util.Collections;
import java.util.Objects;
import java.util.function.Consumer;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.ListCellRenderer;
import javax.swing.text.JTextComponent;

import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.coor.conversion.LatLonParser;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.SideButton;
import org.openstreetmap.josm.gui.dialogs.ToggleDialog;
import org.openstreetmap.josm.gui.util.GuiHelper;
import org.openstreetmap.josm.gui.widgets.AbstractTextComponentValidator;
import org.openstreetmap.josm.gui.widgets.JosmTextField;
import org.openstreetmap.josm.plugins.routing2.lib.generic.Legs;
import org.openstreetmap.josm.plugins.routing2.lib.generic.Maneuver;
import org.openstreetmap.josm.plugins.routing2.lib.generic.Trip;
import org.openstreetmap.josm.tools.GBC;
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
        // TODO add routing methods/option button here, see https://valhalla.openstreetmap.de/ for sample
        final JosmTextField start = new JosmTextField();
        final JosmTextField end = new JosmTextField();
        final RouteInstructions instructions = new RouteInstructions();
        final SideButton doRouting = new SideButton(new JosmAction(tr("Calculate route"), "dialogs/routing",
                tr("Calculate route"), Shortcut.registerShortcut("routing:calculate", tr("Calculate route"),
                        KeyEvent.CHAR_UNDEFINED, Shortcut.NONE),
                false, false) {
            @Override
            public void actionPerformed(ActionEvent e) {
                final RoutingLayer layer = new RoutingLayer("Route", LatLonParser.parse(start.getText()),
                        LatLonParser.parse(end.getText()));
                layer.addTripListener(instructions);
                MainApplication.getLayerManager().addLayer(layer);
            }
        });
        final JPanel dataPanel = new JPanel(new GridBagLayout());
        start.addFocusListener(new HintListener(start, tr("Starting point")));
        end.addFocusListener(new HintListener(end, tr("Destination")));
        dataPanel.add(start, GBC.eol().fill(GBC.HORIZONTAL));
        dataPanel.add(end, GBC.eol().fill(GBC.HORIZONTAL));
        dataPanel.add(instructions, GBC.eol().fill(GBC.BOTH));
        this.createLayout(dataPanel, false, Collections.singleton(doRouting));
        new LatLonValidator(doRouting, start);
        new LatLonValidator(doRouting, end);
    }

    private static class RouteInstructions extends JPanel implements Consumer<Trip> {
        public RouteInstructions() {
            super(new GridBagLayout());
        }

        @Override
        public void accept(Trip trip) {
            if (trip != null) {
                GuiHelper.runInEDT(() -> rebuildTrip(trip));
            }
        }

        private void rebuildTrip(Trip trip) {
            this.removeAll();
            JPanel scroller = new JPanel(new GridBagLayout());
            for (Legs leg : trip.legs()) {
                scroller.add(new LegPanel(leg), GBC.eol().anchor(GBC.LINE_START).fill(GBC.BOTH));
            }
            this.add(GuiHelper.embedInVerticalScrollPane(scroller), GBC.eol().fill(GBC.BOTH));
        }
    }

    private static class HintListener implements FocusListener {
        private final String hint;
        private final JTextComponent textComponent;

        public HintListener(JTextComponent textComponent, String hint) {
            Objects.requireNonNull(textComponent);
            Objects.requireNonNull(hint);
            this.textComponent = textComponent;
            this.hint = hint;
        }

        @Override
        public void focusGained(FocusEvent e) {
            if (hint.equals(this.textComponent.getText())) {
                this.textComponent.setText("");
                this.textComponent.setForeground(Color.BLACK);
            }
        }

        @Override
        public void focusLost(FocusEvent e) {
            if (this.textComponent.getText().isBlank() || this.hint.equals(this.textComponent.getText())) {
                this.textComponent.setText(this.hint);
                this.textComponent.setForeground(Color.GRAY);
            }
        }
    }

    private static class LegPanel extends JPanel {
        public LegPanel(Legs leg) {
            super(new GridBagLayout());
            this.add(new ManeuverTable(leg.maneuvers()), GBC.eol().fill(GBC.BOTH));
        }
    }

    private static class ManeuverTable extends JList<Maneuver> {
        public ManeuverTable(Maneuver[] maneuvers) {
            super(maneuvers);
            this.setCellRenderer(new ManeuverCellRenderer());
            this.getSelectionModel().addListSelectionListener(e -> {
                if (!e.getValueIsAdjusting()) {
                    MainApplication.getLayerManager().getLayersOfType(RoutingLayer.class)
                            .forEach(l -> l.setHighlightedManeuver(getSelectedValue()));
                }
            });
        }
    }

    private static class ManeuverCellRenderer extends JPanel implements ListCellRenderer<Maneuver> {
        private final Color defaultColor;
        private final JLabel header;
        private final JLabel preVerbalTransitionInstruction;
        private final JLabel verbalTransitionInstruction;
        private final JLabel postVerbalTransitionInstruction;
        private final JLabel time;
        private final JLabel length;
        private final JLabel cost;
        private final JLabel travelMode;
        private final JLabel travelType;
        private final JLabel type;

        ManeuverCellRenderer() {
            super(new GridBagLayout());
            this.setAlignmentX(JPanel.LEFT_ALIGNMENT);
            this.header = new JLabel();
            this.preVerbalTransitionInstruction = new JLabel();
            this.verbalTransitionInstruction = new JLabel();
            this.postVerbalTransitionInstruction = new JLabel();
            this.cost = new JLabel();
            this.length = new JLabel();
            this.time = new JLabel();
            this.travelMode = new JLabel();
            this.travelType = new JLabel();
            this.type = new JLabel();

            final GridBagConstraints gbc = GBC.eol().anchor(GBC.LINE_START);
            this.add(this.header, gbc);
            this.add(this.preVerbalTransitionInstruction, gbc);
            this.add(this.verbalTransitionInstruction, gbc);
            this.add(this.postVerbalTransitionInstruction, gbc);
            this.add(new JSeparator(), GBC.eol());
            this.add(this.cost, GBC.std().insets(1, 0, 1, 0));
            this.add(this.length, GBC.std().insets(5, 0, 1, 0));
            this.add(this.time, GBC.eol().insets(5, 0, 1, 0));
            this.add(new JSeparator(), GBC.eol());
            this.add(this.travelMode, GBC.std().insets(1, 0, 1, 0));
            this.add(this.travelType, GBC.std().insets(5, 0, 1, 0));
            this.add(this.type, GBC.eol().insets(5, 0, 1, 0));

            this.defaultColor = this.getBackground();
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends Maneuver> list, Maneuver value, int index,
                boolean isSelected, boolean cellHasFocus) {
            if (value instanceof Maneuver maneuver) {
                this.header.setText((index + 1) + ". " + maneuver.instruction() + " (" + maneuver.travelType() + ")");
                this.preVerbalTransitionInstruction.setText(
                        tr("Pre-verbal Transition Instruction: {0}", maneuver.preVerbalTransitionInstruction()));
                this.verbalTransitionInstruction
                        .setText(tr("Verbal Transition Instruction: {0}", maneuver.verbalTransitionInstruction()));
                this.postVerbalTransitionInstruction.setText(
                        tr("Post-verbal Transition Instruction: {0}", maneuver.postVerbalTransitionInstruction()));
                this.cost.setText(tr("Cost: {0}", maneuver.cost()));
                this.length.setText(tr("Length: {0}", maneuver.length()));
                this.time.setText(tr("Time: {0}", maneuver.time()));
                this.travelMode.setText(tr("Travel mode: {0}", maneuver.travelMode()));
                this.travelType.setText(tr("Travel type: {0}", maneuver.travelType()));
                this.type.setText(tr("Type: {0}", maneuver.type()));
                if (cellHasFocus || isSelected) {
                    this.setBackground(Color.RED);
                } else {
                    this.setBackground(defaultColor);
                }
                // I'm not certain why doing something similar didn't work in the constructor.
                JPanel forceLeftAlign = new JPanel(new FlowLayout(FlowLayout.LEADING));
                forceLeftAlign.add(this);
                return forceLeftAlign;
            }
            return null;
        }
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
