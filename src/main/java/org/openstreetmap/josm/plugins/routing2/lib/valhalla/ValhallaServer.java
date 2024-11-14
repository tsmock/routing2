// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.routing2.lib.valhalla;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.concurrent.ForkJoinPool;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.openstreetmap.josm.data.coor.ILatLon;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.plugins.pbf.io.PbfExporter;
import org.openstreetmap.josm.plugins.routing2.lib.generic.GooglePolyline;
import org.openstreetmap.josm.plugins.routing2.lib.generic.IRouter;
import org.openstreetmap.josm.plugins.routing2.lib.generic.Legs;
import org.openstreetmap.josm.plugins.routing2.lib.generic.Locations;
import org.openstreetmap.josm.plugins.routing2.lib.generic.Maneuver;
import org.openstreetmap.josm.plugins.routing2.lib.generic.Trip;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.tools.JosmRuntimeException;
import org.openstreetmap.josm.tools.Logging;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonReader;
import jakarta.json.JsonValue;
import org.openstreetmap.josm.tools.PlatformManager;

/**
 * Make calls to a local install of Valhalla
 */
public final class ValhallaServer implements IRouter {

    @Override
    public Trip generateRoute(OsmDataLayer layer, ILatLon... locations) {
        final Path config = generateConfig();
        final Path dataPath = writeDataSet(layer);
        try {
            if (!Files.isDirectory(getCacheDir().resolve("valhalla_tiles"))) {
                Files.createDirectory(getCacheDir().resolve("valhalla_tiles"));
            }
        } catch (IOException ioException) {
            throw new UncheckedIOException(ioException);
        }
        generateTimezones(config.resolveSibling("valhalla_tiles").resolve("timezones.sqlite"));
        generateAdmins(config, dataPath);
        generateTiles(config, dataPath);
        generateExtract(config);
        JsonObjectBuilder builder = Json.createObjectBuilder();
        builder.add("costing", "auto").add("directions_options", Json.createObjectBuilder().add("units", "miles"));
        JsonArrayBuilder locationsArray = Json.createArrayBuilder();
        for (ILatLon location : locations) {
            locationsArray.add(Json.createObjectBuilder().add("lat", location.lat()).add("lon", location.lon()));
        }
        builder.add("locations", locationsArray);
        Process p;
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(getPath("valhalla_service"), config.toString(), "route",
                    builder.build().toString());
            processBuilder.directory(getCacheDir().toFile()); // FIXME remove
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

    private Path getCacheDir() throws IOException {
        final Path dir = Config.getDirs().getUserDataDirectory(true).toPath().resolve("routing2");
        if (!Files.isDirectory(dir)) {
            Files.createDirectory(dir);
        }
        return dir;
    }

    private String getPath(String binary) throws IOException {
        final Path dir = getCacheDir().resolve("bin").resolve("valhalla");
        if (!Files.isDirectory(dir)) {
            Files.createDirectories(dir);
        }
        final Path binDir = dir.resolve("bin");
        final Path binaryPath = binDir.resolve(binary);
        if (!Files.isDirectory(binDir) && !Files.isExecutable(binaryPath)) {
            if (PlatformManager.isPlatformOsx()) {
                extractBinariesMacOS(dir);
            } else {
                throw new UnsupportedOperationException("Your platform is not currently supported"); // FIXME: Add other platforms
            }
        }
        return binaryPath.toString();
    }

    private static void extractBinariesMacOS(Path dir) throws IOException {
        Objects.requireNonNull(dir);
        // FIXME: Don't hardcode location
        try (InputStream fis = Files.newInputStream(Paths.get("/Users/tsmock/Downloads/macosx-build-valhalla-fat.zip"));
                ZipInputStream zis = new ZipInputStream(fis)) {
            ZipEntry zipEntry;
            byte[] bytes = new byte[1024];
            while ((zipEntry = zis.getNextEntry()) != null) {
                if (zipEntry.isDirectory()) {
                    Files.createDirectories(dir.resolve(zipEntry.getName()));
                } else if (zipEntry.getName().endsWith(".tar.gz")) {
                    try (TarArchiveInputStream tais = new TarArchiveInputStream(zis)) {
                        TarArchiveEntry tarArchiveEntry;
                        while ((tarArchiveEntry = tais.getNextEntry()) != null) {
                            Path saveLocation = dir.resolve(
                                    tarArchiveEntry.getName().replaceFirst("^\\./valhalla-[0-9.]*-Darwin/", ""));
                            if (tarArchiveEntry.isDirectory()) {
                                if (!Files.isDirectory(saveLocation)) {
                                    Files.createDirectories(saveLocation);
                                }
                            } else {
                                try (OutputStream fos = Files.newOutputStream(saveLocation)) {
                                    int len;
                                    while ((len = tais.read(bytes)) > 0) {
                                        fos.write(bytes, 0, len);
                                    }
                                }
                                if (saveLocation.getParent().endsWith("bin") && !Files.isExecutable(saveLocation)) {
                                    saveLocation.toFile().setExecutable(true, true);
                                }
                            }
                        }
                    }
                } else {
                    try (OutputStream fos = Files.newOutputStream(dir.resolve(zipEntry.getName()))) {
                        int len;
                        while ((len = zis.read(bytes)) > 0) {
                            fos.write(bytes, 0, len);
                        }
                    }
                }
            }
        }
    }

    private Path generateConfig() {
        try {
            final Path dataDir = getCacheDir();
            final Path config = dataDir.resolve("valhalla.json").toAbsolutePath();
            if (!Files.exists(config)) {
                try (InputStream is = runCommand(getPath("valhalla_build_config"), "--mjolnir-tile-dir",
                        dataDir.resolve("valhalla_tiles").toString(), "--mjolnir-tile-extract",
                        dataDir.resolve("valhalla_tiles.tar").toString(), "--mjolnir-timezone",
                        dataDir.resolve("valhalla_tiles").resolve("timezones.sqlite").toString(), "--mjolnir-admin",
                        dataDir.resolve("valhalla_tiles").resolve("admins.sqlite").toString())) {
                    Files.copy(is, config);
                }
            }
            return config;
        } catch (IOException ioException) {
            throw new UncheckedIOException(ioException);
        }
    }

    private void generateTimezones(Path output) {
        if (!Files.exists(output)) {
            try (InputStream is = runCommand(getPath("valhalla_build_timezones"))) {
                Files.copy(is, output);
            } catch (IOException ioException) {
                throw new UncheckedIOException(ioException);
            }
        }
    }

    private void generateAdmins(Path config, Path input) {
        try (InputStream is = runCommand(getPath("valhalla_build_admins"), "--config", config.toString(),
                input.toString())) {
            printStdOut(is);
        } catch (IOException ioException) {
            throw new UncheckedIOException(ioException);
        }
    }

    private void generateTiles(Path config, Path input) {
        try (InputStream is = runCommand(getPath("valhalla_build_tiles"), "--config", config.toString(),
                input.toString())) {
            printStdOut(is);
        } catch (IOException ioException) {
            throw new UncheckedIOException(ioException);
        }
    }

    private void generateExtract(Path config) {
        try (InputStream is = runCommand(getPath("valhalla_build_extract"), "--config", config.toString(), "-v",
                "--overwrite")) {
            printStdOut(is);
        } catch (IOException ioException) {
            throw new UncheckedIOException(ioException);
        }
    }

    private static Trip.Summary parseSummary(JsonObject summary) {
        return new Trip.Summary(summary.getBoolean("has_time_restrictions", false),
                summary.getBoolean("has_toll", false), summary.getBoolean("has_highway", false),
                summary.getBoolean("has_ferry", false), summary.getJsonNumber("min_lat").doubleValue(),
                summary.getJsonNumber("min_lon").doubleValue(), summary.getJsonNumber("max_lat").doubleValue(),
                summary.getJsonNumber("max_lon").doubleValue(), summary.getJsonNumber("time").doubleValue(),
                summary.getJsonNumber("length").doubleValue(), summary.getJsonNumber("cost").doubleValue());
    }

    private static Locations parseLocation(JsonValue value) {
        if (value instanceof JsonObject loc) {
            return new Locations(loc.getJsonNumber("lat").doubleValue(), loc.getJsonNumber("lon").doubleValue(), null,
                    Double.NaN, Double.NaN, null, 0L, 0, Double.NaN, // FIXME
                    false, null, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, null, null, null, null,
                    null, null, null, null, null, null, null);
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
            return new Maneuver(Maneuver.Type.values()[maneuver.getInt("type")], maneuver.getString("instruction", ""),
                    maneuver.getString("verbal_succinct_transition_instruction", ""),
                    maneuver.getString("verbal_pre_transition_instruction", ""),
                    maneuver.getString("verbal_post_transition_instruction", ""),
                    maneuver.getJsonNumber("time").doubleValue(), maneuver.getJsonNumber("length").doubleValue(),
                    maneuver.getJsonNumber("cost").doubleValue(), maneuver.getInt("begin_shape_index"),
                    maneuver.getInt("end_shape_index"), maneuver.getBoolean("verbal_multi_cue", false),
                    maneuver.getString("travel_mode", ""), maneuver.getString("travel_type", ""));
        }
        return null;
    }

    private Path writeDataSet(OsmDataLayer layer) {
        try {
            Path saveLocation = getCacheDir().resolve(layer.getName() + ".pbf");
            new PbfExporter().exportData(saveLocation.toFile(), layer);
            saveLocation.toFile().deleteOnExit(); // Not perfect, but should reduce amount of space used long-term.
            return saveLocation;
        } catch (IOException ioException) {
            throw new UncheckedIOException(ioException);
        }
    }

    private static void printStdOut(InputStream is) {
        try (InputStreamReader isr = new InputStreamReader(is); BufferedReader br = new BufferedReader(isr)) {
            br.lines().forEach(Logging::info);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static InputStream runCommand(String... args) throws IOException {
        ProcessBuilder builder = new ProcessBuilder(args);
        Process p = builder.start();
        if (false) {
            try {
                p.waitFor();
            } catch (InterruptedException interruptedException) {
                Logging.error(interruptedException);
                Thread.currentThread().interrupt();
                throw new JosmRuntimeException(interruptedException);
            }
        }
        // Do not block here.
        ForkJoinPool.commonPool().submit(() -> {
            try (BufferedReader errors = p.errorReader()) {
                errors.lines().forEach(Logging::error);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
        return p.getInputStream();
    }
}
