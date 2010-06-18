package org.geowebcache.diskquota.paging;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.diskquota.ConfigLoader;
import org.geowebcache.storage.blobstore.file.FilePathGenerator;

public class FilePageStore implements PageStore {

    private static final Log log = LogFactory.getLog(FilePageStore.class);

    private final ConfigLoader configLoader;

    public FilePageStore(final ConfigLoader configLoader) {
        this.configLoader = configLoader;
    }

    @SuppressWarnings("unchecked")
    public List<TilePage> getPages(String layerName, String gridSetId) throws IOException {

        String fileName = fileName(layerName, gridSetId);
        InputStream pagesStateIn;

        pagesStateIn = configLoader.getStorageInputStream(fileName);

        ObjectInputStream in = new ObjectInputStream(pagesStateIn);
        try {
            List<TilePage> pages;
            try {
                pages = (List<TilePage>) in.readObject();
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
            log.info("Paged state for layer '" + layerName + "'/" + gridSetId + " loaded.");
            return pages;
        } finally {
            in.close();
        }
    }

    public void savePages(String layerName, String gridSetId, List<TilePage> availablePages)
            throws IOException {

        String fileName = fileName(layerName, gridSetId);
        log.debug("Saving paged state for " + layerName + "/" + gridSetId + " containing "
                + availablePages.size() + " pages.");
        OutputStream fileOut = configLoader.getStorageOutputStream(fileName);
        ObjectOutputStream out = new ObjectOutputStream(fileOut);
        try {
            out.writeObject(availablePages);
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