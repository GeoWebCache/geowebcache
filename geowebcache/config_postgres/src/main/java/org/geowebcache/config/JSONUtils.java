package org.geowebcache.config;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonWriter;

/**
 * Helper class for JSON handling
 * 
 * @author ez
 *
 */
public class JSONUtils {

    /**
     * Default encoding
     */
    private static final String UTF_8 = "UTF-8";

    private final static GsonBuilder BUILDER;

    private final static Gson GSON_ISO8601_UTC;

    /**
     * Will parse dates in system default date format
     */
    private final static Gson GSON_DEFAULT;

    /**
     * value={@value #ISO8601_FULL_UTC_STRING}
     */
    private final static String ISO8601_FULL_UTC_STRING = "yyyy-MM-dd'T'HH:mm:ss'Z'";

    static {
        // handle null..
        BUILDER = new GsonBuilder();
        BUILDER.serializeSpecialFloatingPointValues();

        // Build with default date format
        GSON_DEFAULT = BUILDER.create();

        // Default date format (iso8601 + utc)
        BUILDER.setDateFormat(ISO8601_FULL_UTC_STRING);

        // Build with ISO8601 UTC format
        GSON_ISO8601_UTC = BUILDER.create();
    }

    /**
     * Creates a string of an object with UTF-8 encoding. Same as
     * {@link #stringify(Object, boolean)}
     * 
     * @param object
     * @param pretty
     *            false
     * @return object as json string or null
     * 
     * @see #stringify(Object, boolean)
     */
    public static String stringify(Object object) {
        return stringify(object, false);
    }

    /**
     * Creates a string of an object with UTF-8 encoding
     * 
     * @param object
     * @param pretty
     *            pretty printing
     * @return object as json string or null
     * 
     * @see GsonBuilder#setPrettyPrinting()
     */
    public static String stringify(Object object, boolean pretty) {
        if (object == null) {
            return null;
        }

        if (pretty) {
            return createGson(true, ISO8601_FULL_UTC_STRING, true).toJson(object);
        }

        ByteArrayOutputStream out = null;
        JsonWriter writer = null;
        try {
            out = new ByteArrayOutputStream();
            writer = new JsonWriter(new OutputStreamWriter(out, UTF_8));

            GSON_ISO8601_UTC.toJson(object, TypeToken.get(object.getClass()).getType(), writer);

            writer.flush();

            return out.toString(UTF_8);
        } catch (JsonIOException io) {
            throw new RuntimeException("Failed stringify due to IO error", io);
        } catch (IOException io) {
            throw new RuntimeException("Failed stringify due to IO error", io);
        } finally {
            IOUtils.closeQuietly(out);
            closeQuietly(writer);
        }
    }

    /**
     * Takes an object and parses it into given class.
     * 
     * @param o
     * @param cls
     * @return
     */
    public static <T> T parseObject(Object o, Class<T> cls) {
        return parse(stringify(o), cls);
    }

    /**
     * Will return a Object of type cls passed in. Be aware that generic list will not work with
     * this method, see method with TypeToken for parsing generic lists
     * 
     * @param json
     * @param cls
     *            the class type of object to be returned
     * @throws IllegalStateException
     *             if string is not of json format
     * @see #stringify(Object)
     * @see #parse(String, TypeToken)
     * @return
     */
    public static <T> T parse(String json, Class<T> cls) {
        try {
            // #10725 Old formats was with system default now we use ISO8601 UTC
            // for dates
            try {
                return GSON_ISO8601_UTC.fromJson(json, cls);
            } catch (JsonSyntaxException pe) {
                // try with default
                return GSON_DEFAULT.fromJson(json, cls);
            }
        } catch (JsonSyntaxException e) {
            throw new IllegalStateException(e.getCause());
        }
    }

    public static void closeQuietly(JsonWriter writer) {
        if (writer != null) {
            try {
                writer.close();
            } catch (IOException io) {
            }
        }
    }

    private static Gson createGson(boolean serializeSpecialFloatingPoint, String dateFormat,
            boolean pretty) {
        GsonBuilder b = new GsonBuilder();
        if (serializeSpecialFloatingPoint) {
            b.serializeSpecialFloatingPointValues();
        }

        if (!StringUtils.isEmpty(dateFormat)) {
            // GT default date format (iso8601 + utc)
            b.setDateFormat(dateFormat);
        }

        if (pretty) {
            b.setPrettyPrinting();
        }

        return b.create();
    }

}
