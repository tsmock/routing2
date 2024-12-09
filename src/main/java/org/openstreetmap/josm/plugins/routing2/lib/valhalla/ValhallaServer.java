// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.routing2.lib.valhalla;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ForkJoinPool;
import java.util.zip.GZIPInputStream;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.openstreetmap.josm.data.coor.ILatLon;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.gui.progress.ProgressMonitor;
import org.openstreetmap.josm.io.ProgressInputStream;
import org.openstreetmap.josm.plugins.pbf.io.PbfExporter;
import org.openstreetmap.josm.plugins.routing2.Routing2Plugin;
import org.openstreetmap.josm.plugins.routing2.lib.generic.GooglePolyline;
import org.openstreetmap.josm.plugins.routing2.lib.generic.IRouter;
import org.openstreetmap.josm.plugins.routing2.lib.generic.Legs;
import org.openstreetmap.josm.plugins.routing2.lib.generic.Locations;
import org.openstreetmap.josm.plugins.routing2.lib.generic.Maneuver;
import org.openstreetmap.josm.plugins.routing2.lib.generic.SetupException;
import org.openstreetmap.josm.plugins.routing2.lib.generic.Trip;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.tools.HttpClient;
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
    private static final String valhallaVersion = "3.5.1";

    @Override
    public boolean shouldPerformSetup() {
        try {
            final Path dir = getCacheDir().resolve("bin").resolve("valhalla");
            if (!Files.isDirectory(dir)) {
                return true;
            }
            final Path binDir = dir.resolve("bin");
            final Path versionFile = dir.resolve("version");
            if (!Files.isRegularFile(versionFile) || !valhallaVersion.equals(Files.readString(versionFile))) {
                return true;
            }
            if (!Files.isDirectory(binDir) && !PlatformManager.isPlatformWindows()) { // Windows doesn't have a bin dir
                return true;
            }
        } catch (IOException ioException) {
            throw new UncheckedIOException(ioException);
        }
        return false;
    }

    @Override
    public void performSetup(ProgressMonitor progressMonitor) throws SetupException {
        try {
            realPerformSetup(progressMonitor);
        } catch (IOException ioException) {
            throw new SetupException(ioException);
        }
    }

    /**
     * Perform the actual setup steps in a synchronized block, to avoid downloading stuff multiple times
     * @param updateable The object to use for progress updates
     * @throws IOException If there is an issue performing setup
     */
    private static synchronized void realPerformSetup(ProgressMonitor updateable) throws IOException {
        final Path dir = getCacheDir().resolve("bin").resolve("valhalla");
        if (!Files.isDirectory(dir)) {
            Files.createDirectories(dir);
        }
        final Path binDir = dir.resolve("bin");
        final Path versionFile = dir.resolve("version");
        if (!Files.isRegularFile(versionFile) || !valhallaVersion.equals(Files.readString(versionFile))) {
            updateable.indeterminateSubTask(tr("Deleting old valhalla binaries"));
            // Delete old binaries
            Files.walkFileTree(dir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (updateable.isCanceled()) {
                        return FileVisitResult.TERMINATE;
                    }
                    Files.delete(file);
                    updateable.worked(1);
                    return super.visitFile(file, attrs);
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    if (updateable.isCanceled()) {
                        return FileVisitResult.TERMINATE;
                    }
                    Files.delete(dir);
                    updateable.worked(1);
                    return super.postVisitDirectory(dir, exc);
                }
            });
            Files.createDirectories(dir);
        }
        if (!Files.isDirectory(binDir)) {
            updateable.subTask(tr("Downloading valhalla binaries"));
            if (PlatformManager.isPlatformOsx()) {
                extractBinaries(updateable, "Darwin", dir);
            } else if (PlatformManager.isPlatformUnixoid()) {
                extractBinaries(updateable, "Linux", dir);
            } else if (PlatformManager.isPlatformWindows()) {
                extractBinaries(updateable, "Windows", dir);
            } else {
                throw new UnsupportedOperationException("Your platform is not currently supported");
            }
            // Do this last in case of cancellation
            if (!updateable.isCanceled()) {
                Files.writeString(versionFile, valhallaVersion);
            }
        }
    }

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
            if (data.getInt("error_code") == 442) {
                return null; // No route found // FIXME: Throw RouteException with message?
            } // FIXME: Look through https://valhalla.github.io/valhalla/api/turn-by-turn/api-reference/#http-status-codes-and-conditions for other "valid" problems.
            throw new JosmRuntimeException(data.toString());
        }
        final JsonObject trip = data.getJsonObject("trip");
        final Locations[] locations1 = trip.getJsonArray("locations").stream().map(ValhallaServer::parseLocation)
                .filter(Objects::nonNull).toArray(Locations[]::new);
        final Legs[] legs = trip.getJsonArray("legs").stream().map(ValhallaServer::parseLeg).toArray(Legs[]::new);
        final Trip.Summary summary = parseSummary(trip.getJsonObject("summary"));
        return new Trip(locations1, legs, summary);
    }

    private static Path getCacheDir() throws IOException {
        final Path dir = Config.getDirs().getCacheDirectory(true).toPath().resolve("routing2");
        if (!Files.isDirectory(dir)) {
            Files.createDirectory(dir);
        }
        return dir;
    }

    private static String getPath(String binary) throws IOException {
        final Path dir = getCacheDir().resolve("bin").resolve("valhalla");
        if (PlatformManager.isPlatformWindows()) {
            final Path exe = dir.resolve(binary + ".exe");
            if (Files.isExecutable(exe)) {
                return exe.toString();
            }
            return dir.resolve(binary).toString();
        }
        final Path binDir = dir.resolve("bin");
        final Path binaryPath = binDir.resolve(binary);
        return binaryPath.toString();
    }

    private static void extractBinaries(ProgressMonitor updateable, String platform, Path dir) throws IOException {
        Objects.requireNonNull(dir);
        final String version = Optional.ofNullable(Routing2Plugin.getInfo().version).orElse("SNAPSHOT");
        final String linkStart = Optional.ofNullable(Routing2Plugin.getInfo().link)
                .orElse("https://github.com/tsmock/routing2");
        final URI downloadLocation;
        if ("latest".equals(version) || "SNAPSHOT".equals(version)) {
            downloadLocation = URI.create(
                    linkStart + "/releases/latest/download/valhalla-" + valhallaVersion + '-' + platform + ".tar.gz");
        } else {
            downloadLocation = URI.create(linkStart + "/releases/download/v" + version + "/valhalla-" + valhallaVersion
                    + '-' + platform + ".tar.gz");
        }
        HttpClient client = HttpClient.create(downloadLocation.toURL());
        try {
            HttpClient.Response response = client.connect();
            if (response.getResponseCode() != 200) {
                Logging.error(response.fetchContent());
                throw new IllegalStateException("Valhalla server download location returned HTTP error code "
                        + response.getResponseCode() + ": " + response.getResponseMessage());
            }
            updateable.setTicks(0);
            try (InputStream is = response.getContent();
                    ProgressInputStream pis = new ProgressInputStream(is, response.getContentLength(), updateable);
                    InputStream gis = new GZIPInputStream(pis);
                    TarArchiveInputStream tais = new TarArchiveInputStream(gis)) {
                byte[] bytes = new byte[1024];
                TarArchiveEntry tarArchiveEntry;
                while (!updateable.isCanceled() && (tarArchiveEntry = tais.getNextEntry()) != null) {
                    Path saveLocation = dir.resolve(tarArchiveEntry.getName());
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
        } finally {
            client.disconnect();
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
        // FIXME: This needs to have full boundary information
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
