/**
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * <p>You should have received a copy of the GNU Lesser General Public License along with this program. If not, see
 * <http://www.gnu.org/licenses/>.
 *
 * @author Nuno Oliveira, GeoSolutions S.A.S., Copyright 2016
 */
package org.geowebcache.sqlite;

import static org.geowebcache.sqlite.Utils.Tuple;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.geotools.util.logging.Logging;
import org.geowebcache.filter.parameters.ParametersUtils;
import org.geowebcache.storage.TileObject;
import org.geowebcache.storage.TileRange;

/**
 * Class responsible to map GWC concepts (layer, tile, tile range, etc ...) to a filesystem file. The mapping is defined
 * by a template that can use the information associated with a tile.
 *
 * <p>
 *
 * <p>The template supported terms are:
 *
 * <ul>
 *   <li>params
 *   <li>x
 *   <li>y
 *   <li>z
 *   <li>layer
 *   <li>grid
 *   <li>format
 * </ul>
 *
 * It is also possible to use parameters referencing them by their name.
 *
 * <p>
 *
 * <p>For example a template like the following:
 *
 * <blockquote>
 *
 * <pre>
 * {grid}/{layer}/{format}/{params}/{z}/tiles_{x}_{y}.sqlite
 * </pre>
 *
 * </blockquote>
 *
 * will produce paths similar to this one:
 *
 * <blockquote>
 *
 * <pre>
 * EPSG_4326/img_states/image_png/10/tiles_350_625.sqlite
 * </pre>
 *
 * </blockquote>
 *
 * <p>
 *
 * <p>Is possible to map all tiles to a single file by defining a static template (no terms). Although, if a term is
 * used it cannot be NULL otherwise an exception will be throw. If a referenced parameter doesn't exists the string
 * 'null' will be used.
 */
final class FileManager {

    private static Logger LOGGER = Logging.getLogger(FileManager.class.getName());

    private static final Pattern PATH_TEMPLATE_ATTRIBUTE_PATTERN = Pattern.compile("\\{(.+?)\\}");

    private final File rootPath;

    private final long rowRangeCount;
    private final long columnRangeCount;

    // path builder extracted from the path template
    private final String[] pathBuilderOriginal;

    // keep track of which terms are used in the path template
    // the boolean tell us if the term was used in the path template
    // and the integer define the position of the term in the path builder
    private final Tuple<Boolean, Integer> replaceParametersId;
    private final Tuple<Boolean, Integer> replaceZoom;
    private final Tuple<Boolean, Integer> replaceRow;
    private final Tuple<Boolean, Integer> replaceColumn;
    private final Tuple<Boolean, Integer> replaceLayerName;
    private final Tuple<Boolean, Integer> replaceGridSetId;
    private final Tuple<Boolean, Integer> replaceFormat;

    // parameters used in the path template
    private final Set<Tuple<String, Integer>> replaceParameters;

    FileManager(File rootDirectory, String pathTemplate, long rowRangeCount, long columnRangeCount) {
        if (LOGGER.isLoggable(Level.INFO)) {
            LOGGER.info(String.format(
                    "Initiating file manager: [rootDirectory='%s', pathTemplate='%s', "
                            + "rowRangeCount='%d', columnRangeCount='%d'].",
                    rootDirectory, pathTemplate, rowRangeCount, columnRangeCount));
        }
        this.rootPath = rootDirectory;
        this.rowRangeCount = rowRangeCount;
        this.columnRangeCount = columnRangeCount;
        // parsing the path template and extracting the terms that are used
        Tuple<String[], Set<Tuple<String, Integer>>> parserResult =
                parsePathTemplate(rootDirectory.getPath(), pathTemplate);
        pathBuilderOriginal = parserResult.first;
        replaceParametersId = findAndRemove(parserResult.second, "params");
        replaceZoom = findAndRemove(parserResult.second, "z");
        replaceRow = findAndRemove(parserResult.second, "x");
        replaceColumn = findAndRemove(parserResult.second, "y");
        replaceLayerName = findAndRemove(parserResult.second, "layer");
        replaceGridSetId = findAndRemove(parserResult.second, "grid");
        replaceFormat = findAndRemove(parserResult.second, "format");
        replaceParameters = parserResult.second;
    }

    /** Builds the complete file path associated to the provided tile. */
    File getFile(TileObject tile) {
        if (tile.getParametersId() == null && tile.getParameters() != null) {
            tile.setParametersId(ParametersUtils.getId(tile.getParameters()));
        }
        return getFile(
                tile.getParametersId(),
                tile.getXYZ(),
                tile.getLayerName(),
                tile.getGridSetId(),
                tile.getBlobFormat(),
                tile.getParameters());
    }

    /** Build a complete file path using the provided terms. */
    File getFile(
            String parametersId,
            long[] xyz,
            String layerName,
            String gridSetId,
            String format,
            Map<String, String> parameters) {
        // init this local thread path builder
        String[] pathBuilderCopy = getPathBuilderCopy();
        // replace the terms used in the path template with the respective values
        if (replaceParametersId.first) {
            pathBuilderCopy[replaceParametersId.second] =
                    normalizeAttributeValue("params", handleParametersId(parametersId, parameters));
        }
        if (replaceZoom.first) {
            pathBuilderCopy[replaceZoom.second] = String.valueOf(getLongValue(xyz, 2));
        }
        if (replaceRow.first) {
            pathBuilderCopy[replaceRow.second] = String.valueOf(computeColumnRange(getLongValue(xyz, 0)));
        }
        if (replaceColumn.first) {
            pathBuilderCopy[replaceColumn.second] = String.valueOf(computeRowRange(getLongValue(xyz, 1)));
        }
        if (replaceLayerName.first) {
            pathBuilderCopy[replaceLayerName.second] = normalizeAttributeValue("layer", layerName);
        }
        if (replaceGridSetId.first) {
            pathBuilderCopy[replaceGridSetId.second] = normalizeAttributeValue("grid", gridSetId);
        }
        if (replaceFormat.first) {
            pathBuilderCopy[replaceFormat.second] = normalizeAttributeValue("format", format);
        }
        // replace the parameters used in the path template with the respective values
        for (Tuple<String, Integer> replaceParameter : replaceParameters) {
            // searching for the parameter value in a non case sensitive way
            String value = parameters.get(replaceParameter.first.toUpperCase());
            value = value == null ? parameters.get(replaceParameter.first.toLowerCase()) : value;
            // if the parameter doesn't exits we use string 'null' as value
            value = value == null ? "null" : normalizeAttributeValue(replaceParameter.first, value);
            pathBuilderCopy[replaceParameter.second] = value;
        }
        return new File(concatStringArray(pathBuilderCopy, 0));
    }

    /** Return the files present in the root directory that correspond to a certain layer. */
    List<File> getFiles(String layerName) {
        // init the thread local path builder
        String[] pathBuilderCopy = getPathBuilderCopy();
        // we only need to replace the layer term
        if (replaceLayerName.first) pathBuilderCopy[replaceLayerName.second] = layerName;
        return getFiles(pathBuilderCopy);
    }

    /** Return the files present in the root directory that correspond to a certain layer and certain grid set. */
    List<File> getFiles(String layerName, String gridSetId) {
        // init the thread local path builder
        String[] pathBuilderCopy = getPathBuilderCopy();
        // we replace the layer and grid set terms
        if (replaceLayerName.first) pathBuilderCopy[replaceLayerName.second] = layerName;
        if (replaceGridSetId.first) pathBuilderCopy[replaceGridSetId.second] = gridSetId;
        return getFiles(pathBuilderCopy);
    }

    /** Return the files present in the root directory that correspond to a certain layer and certain grid set. */
    List<File> getParametersFiles(String layerName, String parametersId) {
        // init the thread local path builder
        String[] pathBuilderCopy = getPathBuilderCopy();
        // we replace the layer and grid set terms
        if (replaceLayerName.first) pathBuilderCopy[replaceLayerName.second] = layerName;
        if (replaceParametersId.first) pathBuilderCopy[replaceParametersId.second] = parametersId;
        return getFiles(pathBuilderCopy);
    }

    /** Build the paths correspondent to a tile range. For each file we return the associated tiles range by zoom. */
    Map<File, List<long[]>> getFiles(TileRange tileRange) {
        Map<File, List<long[]>> files = new HashMap<>();
        // let's iterate of all the available zoom levels
        for (int z = tileRange.getZoomStart(); z <= tileRange.getZoomStop(); z++) {
            long[] range = tileRange.rangeBounds(z);
            if (range == null) {
                // this zoom level doesn't have any tiles associated
                continue;
            }
            // get the files and associated tiles for the current zoom level
            getFiles(
                    files,
                    tileRange.getParametersId(),
                    tileRange.getLayerName(),
                    tileRange.getGridSetId(),
                    tileRange.getMimeType().getFormat(),
                    tileRange.getParameters(),
                    z,
                    range);
        }
        return files;
    }

    /** This method will substitute any char that cannot be used in a file path with an underscore. */
    public static String normalizePathValue(String value) {
        return value.replaceAll("\\\\|/|:|(?:\\s+)", "_");
    }

    /**
     * Helper method that for a specific zoom level and a range of tiles will build all the files paths need to contains
     * those tiles.
     */
    private void getFiles(
            Map<File, List<long[]>> files,
            String parametersId,
            String layerName,
            String gridSetId,
            String format,
            Map<String, String> parameters,
            long z,
            long[] range) {
        long minRangeX = (range[0] / columnRangeCount) * columnRangeCount;
        long maxRangeX = (range[2] / columnRangeCount) * rowRangeCount;
        long minRangeY = (range[1] / rowRangeCount) * rowRangeCount;
        long maxRangeY = (range[3] / rowRangeCount) * rowRangeCount;
        for (long x = minRangeX; x <= maxRangeX; x += columnRangeCount) {
            long minx = Math.max(x, range[0]);
            long maxx = Math.min(x + columnRangeCount - 1, range[2]);
            for (long y = minRangeY; y <= maxRangeY; y += rowRangeCount) {
                long[] tile = {x, y, z};
                File file = getFile(parametersId, tile, layerName, gridSetId, format, parameters);
                long miny = Math.max(y, range[1]);
                long maxy = Math.min(y + rowRangeCount - 1, range[3]);
                List<long[]> ranges = files.get(file);
                if (ranges == null) {
                    ranges = new ArrayList<>();
                    files.put(file, ranges);
                }
                ranges.add(new long[] {minx, miny, maxx, maxy, z});
            }
        }
    }

    /** If the provided parameters id is null a new one will be build based on the provided parameters. */
    private static String handleParametersId(String parametersId, Map<String, String> parameters) {
        if (parametersId != null) {
            // the provided parameters id is ok
            return parametersId;
        }
        // computing a new parameters id based on the provided parameters
        String computedParametersId = ParametersUtils.getId(parameters);
        if (computedParametersId == null) {
            // the provided parameter are null or empty let's use the string 'null' as parameter id
            return "null";
        }
        return computedParametersId;
    }

    private static String normalizeAttributeValue(String attribute, String value) {
        Utils.check(value != null, "Path template attribute '%s' value is NULL.", attribute);
        return normalizePathValue(value);
    }

    private static long getLongValue(long[] xyz, int index) {
        Utils.check(xyz != null, "Path template attribute 'xyz' is NULL.");
        Utils.check(xyz.length == 3, "Path template attribute 'xyz' doesn't have the correct length.");
        return xyz[index];
    }

    /** Helper method that will find in the root directory the files that match the provided path builder. */
    private List<File> getFiles(String[] pathBuilderCopy) {
        // build the concrete path with the embedded regex values (.*?)
        String pathRegex = concatStringArray(pathBuilderCopy, 1);
        // separate all the path parts, useful to walk in the path hierarchy
        String[] pathRegexParts = pathRegex.split(Utils.REGEX_FILE_SEPARATOR);
        // walk the directory tree to find all the files that match the builder path
        return walkFileTreeWithRegex(rootPath, 0, pathRegexParts);
    }

    /** Helper method that will walk recursively the directory hierarchy based on the provided path parts. */
    private static List<File> walkFileTreeWithRegex(File path, int level, String[] pathParts) {
        // filter the current directory files that match the current path part
        File[] files = path.listFiles((directory, name) -> {
            String pathPart = pathParts[level];
            // if need the current path will be interpreted as a regex (.*?)
            return pathPart.equals(name) || name.matches(pathPart);
        });
        if (Objects.isNull(files)) {
            return Collections.emptyList();
        }
        if (level != pathParts.length - 1) {
            // let's walk recursively in the matched files
            List<File> matchedFiles = new ArrayList<>();
            for (File file : files) {
                matchedFiles.addAll(walkFileTreeWithRegex(file, level + 1, pathParts));
            }
            return matchedFiles;
        }
        // we are in the last directory before the path end so we simply return the matched files
        return Arrays.asList(files);
    }

    private static String concatStringArray(String[] array, int startIndex) {
        StringBuilder result = new StringBuilder();
        for (int i = startIndex; i < array.length; i++) {
            result.append(array[i]);
        }
        return result.toString();
    }

    private String[] getPathBuilderCopy() {
        String[] pathBuilderCopy = new String[pathBuilderOriginal.length];
        System.arraycopy(pathBuilderOriginal, 0, pathBuilderCopy, 0, pathBuilderOriginal.length);
        return pathBuilderCopy;
    }

    private static Tuple<Boolean, Integer> findAndRemove(Set<Tuple<String, Integer>> attributes, String attribute) {
        Tuple<String, Integer> found = null;
        for (Tuple<String, Integer> candidateAttribute : attributes) {
            if (candidateAttribute.first.equals(attribute)) {
                if (found != null) {
                    throw Utils.exception("Term '%s' appears multiple times in the path template.", attribute);
                }
                found = candidateAttribute;
            }
        }
        if (found != null) {
            attributes.remove(found);
            return Tuple.tuple(true, found.second);
        }
        return Tuple.tuple(false, -1);
    }

    /**
     * Helper method that will parse a path template and return a path builder and the found terms and the used
     * parameters.
     */
    private static Tuple<String[], Set<Tuple<String, Integer>>> parsePathTemplate(
            String rootPath, String pathTemplate) {
        // replacing chars '\' and '/' with the current os path separator
        pathTemplate = pathTemplate.replaceAll("(\\\\)|/", Utils.REGEX_FILE_SEPARATOR);
        List<String> pathBuilder = new ArrayList<>();
        // the first element of the path builder is the root directory
        pathBuilder.add(rootPath + File.separator);
        Set<Tuple<String, Integer>> attributes = new HashSet<>();
        // matching all the available terms in the path template
        Matcher matcher = PATH_TEMPLATE_ATTRIBUTE_PATTERN.matcher(pathTemplate);
        int lastMatchIndex = 0;
        int pathBuilderIndex = 1;
        while (matcher.find()) {
            // keeping track of the found term and is position on the path builder
            pathBuilder.add(pathTemplate.substring(lastMatchIndex, matcher.start()));
            // adding the match all regex expression to the path builder (match all files at that
            // level)
            pathBuilder.add(".*?");
            String attribute = matcher.group(1).toLowerCase();
            attributes.add(Tuple.tuple(attribute, pathBuilderIndex + 1));
            pathBuilderIndex += 2;
            lastMatchIndex = matcher.end();
        }
        pathBuilder.add(pathTemplate.substring(lastMatchIndex, pathTemplate.length()));
        return Tuple.tuple(pathBuilder.toArray(new String[pathBuilder.size()]), attributes);
    }

    private long computeRowRange(long tileRow) {
        return (tileRow / rowRangeCount) * rowRangeCount;
    }

    private long computeColumnRange(long tileRow) {
        return (tileRow / columnRangeCount) * columnRangeCount;
    }
}
