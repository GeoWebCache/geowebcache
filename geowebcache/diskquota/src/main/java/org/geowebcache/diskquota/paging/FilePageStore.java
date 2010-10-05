/**
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * 
 * @author Gabriel Roldan (OpenGeo) 2010
 *  
 */
package org.geowebcache.diskquota.paging;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.diskquota.ConfigLoader;
import org.geowebcache.storage.blobstore.file.FilePathGenerator;

public class FilePageStore implements PageStore {

    private static final Log log = LogFactory.getLog(FilePageStore.class);

    private final ConfigLoader configLoader;

    static final byte tilePageSerialVersionId = 1;

    public FilePageStore(final ConfigLoader configLoader) {
        this.configLoader = configLoader;
    }

    /**
     * @see org.geowebcache.diskquota.paging.PageStore#getPages(java.lang.String, java.lang.String)
     */
    public List<TilePage> getPages(final String layerName, final String gridSetId)
            throws IOException {

        final String layerFileName = FilePathGenerator.filteredLayerName(layerName);
        final String fileName = fileName(layerFileName, gridSetId);
        InputStream pagesStateIn;

        pagesStateIn = configLoader.getStorageInputStream(fileName);

        DataInputStream in = new DataInputStream(pagesStateIn);
        try {
            List<TilePage> pages = new ArrayList<TilePage>();
            int magic;
            while (true) {
                try {
                    magic = in.readByte();
                } catch (EOFException e) {
                    break;
                }
                if (tilePageSerialVersionId != magic) {
                    throw new IOException(
                            "Object stream does not start with TilePage magic number: " + magic);
                }
                int x = in.readInt();
                int y = in.readInt();
                int z = in.readInt();
                int accessTimeMinutes = in.readInt();
                long numHits = in.readLong();
                long numTilesInPage = in.readLong();
                TilePage page = new TilePage(layerName, gridSetId, x, y, z, numHits,
                        numTilesInPage, accessTimeMinutes);
                pages.add(page);
            }
            log.info("Paged state for layer '" + layerName + "'/" + gridSetId + " loaded.");
            return pages;
        } finally {
            in.close();
        }
    }

    /**
     * @see org.geowebcache.diskquota.paging.PageStore#savePages(java.lang.String, java.lang.String,
     *      java.util.List)
     */
    public void savePages(String layerName, String gridSetId, List<TilePage> availablePages)
            throws IOException {

        final String layerFileName = FilePathGenerator.filteredLayerName(layerName);
        final String fileName = fileName(layerFileName, gridSetId);
        log.debug("Saving paged state for " + layerName + "/" + gridSetId + " containing "
                + availablePages.size() + " pages.");
        OutputStream fileOut = configLoader.getStorageOutputStream(fileName);
        DataOutputStream out = new DataOutputStream(fileOut);
        try {
            for (TilePage page : availablePages) {
                out.writeByte(tilePageSerialVersionId);
                out.writeInt(page.getX());
                out.writeInt(page.getY());
                out.writeInt(page.getZ());
                out.writeInt(page.getLastAccessTimeMinutes());
                out.writeLong(page.getNumHits());
                out.writeLong(page.getNumTilesInPage());
            }
            out.flush();
        } finally {
            out.close();
        }
    }

    private String fileName(String layerName, String gridSetId) {
        String fileName = layerName + "." + FilePathGenerator.filteredGridSetId(gridSetId)
                + ".pages";
        return fileName;
    }

}