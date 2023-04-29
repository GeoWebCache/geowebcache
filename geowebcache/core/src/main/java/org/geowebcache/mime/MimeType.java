/**
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * <p>You should have received a copy of the GNU Lesser General Public License along with this
 * program. If not, see <http://www.gnu.org/licenses/>.
 *
 * @author Arne Kepp, The Open Planning Project, Copyright 2008
 */
package org.geowebcache.mime;

import java.io.IOException;
import java.util.logging.Logger;
import org.geotools.util.logging.Logging;
import org.geowebcache.io.Resource;

public class MimeType {
    protected String mimeType;

    protected String format;

    protected String fileExtension;

    protected String internalName;

    protected boolean supportsTiling;

    private static Logger log = Logging.getLogger(MimeType.class.getName());

    protected MimeType(
            String mimeType,
            String fileExtension,
            String internalName,
            String format,
            boolean supportsTiling) {
        this.mimeType = mimeType;
        this.fileExtension = fileExtension;
        this.internalName = internalName;
        this.format = format;
        this.supportsTiling = supportsTiling;
    }

    /**
     * Checks if mime type is a binary type
     *
     * @param value mime type
     * @return true if mime type is binary
     * @throws MimeException if mime type is not supported
     */
    public static boolean isBinary(String value) throws MimeException {
        MimeType mt = MimeType.createFromFormat(value);
        return mt.isBinary();
    }

    protected boolean isBinary() {
        return false;
    }

    /** The MIME identifier string for this format. */
    public String getMimeType() {
        return mimeType;
    }

    /** The MIME identifier string for this format. */
    public String getMimeType(Resource resource) throws IOException {
        return mimeType;
    }

    /**
     * Returns the format string, which can be different from
     *
     * @return format or mimetype
     */
    public String getFormat() {
        if (format != null) {
            return format;
        }
        return mimeType;
    }

    /** The conventional file extension most commonly used with this format. */
    public String getFileExtension() {
        return fileExtension;
    }

    // Used for internal purposes, like picking image renderer
    public String getInternalName() {
        return internalName;
    }

    /**
     * The format allows for being broken into smaller pieces or being combined into larger ones.
     *
     * <p>In practice this means it must be a lossless raster image.
     *
     * @return true if the format can be tiles, false otherwise.
     */
    public boolean supportsTiling() {
        return supportsTiling;
    }

    /**
     * Indicates whether this is a vector format rather than a raster format. Output in vector
     * formats should have no guttering.
     *
     * @return {@code true} if represents a vector or other kind of non raster format where applying
     *     a gutter to the request originating the tile would lead to an incorrect result.
     */
    public boolean isVector() {
        return false;
    }

    /** Get the MIME type object for a given MIME type string */
    public static MimeType createFromFormat(String formatStr) throws MimeException {
        if (formatStr == null) {
            throw new MimeException("formatStr was not set");
        }
        MimeType mimeType = ImageMime.checkForFormat(formatStr);
        if (mimeType != null) {
            return mimeType;
        }
        mimeType = XMLMime.checkForFormat(formatStr);
        if (mimeType != null) {
            return mimeType;
        }

        mimeType = TextMime.checkForFormat(formatStr);
        if (mimeType != null) {
            return mimeType;
        }

        mimeType = ApplicationMime.checkForFormat(formatStr);
        if (mimeType != null) {
            return mimeType;
        }

        throw new MimeException("Unsupported format request: " + formatStr);
    }

    /** Get the MIME type object for a given file extension */
    public static MimeType createFromExtension(String fileExtension) throws MimeException {

        MimeType mimeType = ImageMime.checkForExtension(fileExtension);
        if (mimeType != null) {
            return mimeType;
        }

        mimeType = XMLMime.checkForExtension(fileExtension);
        if (mimeType != null) {
            return mimeType;
        }

        mimeType = TextMime.checkForExtension(fileExtension);
        if (mimeType != null) {
            return mimeType;
        }

        mimeType = ApplicationMime.checkForExtension(fileExtension);
        if (mimeType != null) {
            return mimeType;
        }

        log.fine("Unsupported MIME type: " + fileExtension + ", returning null");
        return null;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj != null && obj.getClass() == this.getClass()) {
            MimeType mimeObj = (MimeType) obj;
            if (this.format.equalsIgnoreCase(mimeObj.format)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int hashCode() {
        return format.hashCode();
    }

    /**
     * Determine whether otherMimeType is compatible with this MimeType. They're compatible if
     * they're identical, or presumably if otherMimeType has this mime type as a prefix.
     *
     * @param otherMimeType the mime type to check for compatibility
     * @return whether otherMimeType is "compatible" with this mime type
     */
    public boolean isCompatible(String otherMimeType) {
        return mimeType.equalsIgnoreCase(otherMimeType)
                || (otherMimeType != null
                        && otherMimeType.toLowerCase().startsWith(mimeType.toLowerCase()));
    }

    @Override
    public String toString() {
        return mimeType;
    }
}
