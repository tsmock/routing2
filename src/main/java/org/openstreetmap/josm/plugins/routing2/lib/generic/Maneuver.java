// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.routing2.lib.generic;

/**
 * A maneuver (originally taken from valhalla output)
 * @param type
 * @param instruction
 * @param verbalTransitionInstruction
 * @param preVerbalTransitionInstruction
 * @param postVerbalTransitionInstruction
 * @param time
 * @param length
 * @param cost
 * @param startShape
 * @param endShape
 * @param multiVerbalCue
 * @param travelMode
 * @param travelType
 */
public record Maneuver(Type type, String instruction, String verbalTransitionInstruction, String preVerbalTransitionInstruction,
                       String postVerbalTransitionInstruction, double time, double length, double cost, int startShape, int endShape,
                       boolean multiVerbalCue, String travelMode, String travelType) {

    /** The maneuver types */
    public enum Type {
        NONE,
        START,
        START_RIGHT,
        START_LEFT,
        DESTINATION,
        DESTINATION_RIGHT,
        DESTINATION_LEFT,
        BECOMES,
        CONTINUE,
        SLIGHT_RIGHT,
        RIGHT,
        SHARP_RIGHT,
        U_TURN_RIGHT,
        U_TURN_LEFT,
        SHARP_LEFT,
        LEFT,
        SLIGHT_LEFT,
        RAMP_STRAIGHT,
        RAMP_LEFT,
        EXIT_RIGHT,
        EXIT_LEFT,
        STAY_STRAIGHT,
        STAY_RIGHT,
        STAY_LEFT,
        MERGE,
        ROUNDABOUT_ENTER,
        ROUNDABOUT_EXIT,
        FERRY_ENTER,
        FERRY_EXIT,
        TRANSIT,
        TRANSIT_TRANSFER,
        TRANSIT_REMAIN_ON,
        TRANSIT_CONNECTION_START,
        TRANSIT_CONNECTION_TRANSFER,
        TRANSIT_CONNECTION_DESTINATION,
        POST_TRANSIT_CONNECTION_DESTINATION,
        MERGE_RIGHT,
        MERGE_LEFT,
        ELEVATOR_ENTER,
        STEPS_ENTER,
        ESCALATOR_ENTER,
        BUILDING_ENTER,
        BUILDING_EXIT
    }
}
