// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.routing2.lib.valhalla;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Objects;

import org.openstreetmap.josm.data.coor.ILatLon;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.plugins.routing2.lib.generic.GooglePolyline;
import org.openstreetmap.josm.plugins.routing2.lib.generic.IRouter;
import org.openstreetmap.josm.plugins.routing2.lib.generic.Legs;
import org.openstreetmap.josm.plugins.routing2.lib.generic.Locations;
import org.openstreetmap.josm.plugins.routing2.lib.generic.Maneuver;
import org.openstreetmap.josm.plugins.routing2.lib.generic.Trip;
import org.openstreetmap.josm.tools.JosmRuntimeException;
import org.openstreetmap.josm.tools.Logging;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonReader;
import jakarta.json.JsonValue;

/**
 * Make calls to a local install of Valhalla
 */
public final class ValhallaServer implements IRouter {

    @Override
    public Trip generateRoute(DataSet dataSet, ILatLon... locations) {
        JsonObjectBuilder builder = Json.createObjectBuilder();
        builder.add("costing", "auto").add("directions_options", Json.createObjectBuilder().add("units", "miles"));
        JsonArrayBuilder locationsArray = Json.createArrayBuilder();
        for (ILatLon location : locations) {
            locationsArray.add(Json.createObjectBuilder().add("lat", location.lat()).add("lon", location.lon()));
        }
        builder.add("locations", locationsArray);
        Process p;
        try {
            ProcessBuilder processBuilder = new ProcessBuilder("/usr/local/bin/valhalla_service", // TODO autodetect
                    "valhalla.json",
                    "route",
                    builder.build().toString());
            processBuilder.directory(new File("/Users/tsmock/workspace/josm/plugins/routing2")); // FIXME remove
            p = processBuilder.start();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        try (BufferedReader errors = p.errorReader()) {
            errors.lines().forEach(Logging::error);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        final JsonObject data;
        try (JsonReader reader = Json.createReader(p.inputReader())) {
            data = reader.readObject();
        }
        // check if error
        if (data.containsKey("status_code") && 200 != data.getInt("status_code")) {
            throw new JosmRuntimeException(data.toString());
        }
        final JsonObject trip = data.getJsonObject("trip");
        final Locations[] locations1 = trip.getJsonArray("locations").stream().map(ValhallaServer::parseLocation)
                .filter(Objects::nonNull).toArray(Locations[]::new);
        final Legs[] legs = trip.getJsonArray("legs").stream().map(ValhallaServer::parseLeg).toArray(Legs[]::new);
        final Trip.Summary summary = parseSummary(trip.getJsonObject("summary"));
        return new Trip(locations1, legs, summary);
    }

    private static Trip.Summary parseSummary(JsonObject summary) {
        return new Trip.Summary(summary.getBoolean("has_time_restrictions", false),
                summary.getBoolean("has_toll", false),
                summary.getBoolean("has_highway", false),
                summary.getBoolean("has_ferry", false),
                summary.getJsonNumber("min_lat").doubleValue(),
                summary.getJsonNumber("min_lon").doubleValue(),
                summary.getJsonNumber("max_lat").doubleValue(),
                summary.getJsonNumber("max_lon").doubleValue(),
                summary.getJsonNumber("time").doubleValue(),
                summary.getJsonNumber("length").doubleValue(),
                summary.getJsonNumber("cost").doubleValue());
    }

    private static Locations parseLocation(JsonValue value) {
        if (value instanceof JsonObject loc) {
            return new Locations(loc.getJsonNumber("lat").doubleValue(),
                    loc.getJsonNumber("lon").doubleValue(),
                    null, Double.NaN , Double.NaN, null, 0L, 0, Double.NaN, // FIXME
                    false, null, Double.NaN, Double.NaN, Double.NaN, Double.NaN,
                    Double.NaN, null, null, null, null, null, null, null, null, null, null, null);
        }
        return null;
    }
    private static Legs parseLeg(JsonValue value) {
        if (value instanceof JsonObject leg) {
            final Maneuver[] maneuvers = leg.getJsonArray("maneuvers").stream().map(ValhallaServer::parseManeuver)
                    .filter(Objects::nonNull).toArray(Maneuver[]::new);
            final double[] shape = GooglePolyline.decode(leg.getString("shape"), 1e6);
            final Trip.Summary summary = parseSummary(leg.getJsonObject("summary"));
            return new Legs(maneuvers, summary, shape);
        }
        return new Legs(new Maneuver[0], null, new double[0]);
    }

    private static Maneuver parseManeuver(JsonValue value) {
        if (value instanceof JsonObject maneuver) {
            return new Maneuver(Maneuver.Type.values()[maneuver.getInt("type")],
                    maneuver.getString("instruction", ""),
                    maneuver.getString("verbal_succinct_transition_instruction", ""),
                    maneuver.getString("verbal_pre_transition_instruction", ""),
                    maneuver.getString("verbal_post_transition_instruction", ""),
                    maneuver.getJsonNumber("time").doubleValue(),
                    maneuver.getJsonNumber("length").doubleValue(),
                    maneuver.getJsonNumber("cost").doubleValue(),
                    maneuver.getInt("begin_shape_index"),
                    maneuver.getInt("end_shape_index"),
                    maneuver.getBoolean("verbal_multi_cue", false),
                    maneuver.getString("travel_mode", ""),
                    maneuver.getString("travel_type", ""));
        }
        return null;
    }
}
