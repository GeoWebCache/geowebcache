/**
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * @author Nuno Oliveira, GeoSolutions S.A.S., Copyright 2016
 */
package org.geowebcache.sqlite;

import org.apache.commons.io.FileUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.config.BlobStoreInfo;
import org.geowebcache.config.ServerConfiguration;
import org.geowebcache.config.XMLConfiguration;
import org.geowebcache.layer.TileLayerDispatcher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.geowebcache.config.BlobStoreConfiguration;

@Controller
@RequestMapping("**/sqlite")
public class OperationsRest {

    private static Log LOGGER = LogFactory.getLog(OperationsRest.class);

    @Autowired
    private TileLayerDispatcher tileLayerDispatcher;

    @Autowired
    private BlobStoreConfiguration blobConfiguration;
    @Autowired
    private ServerConfiguration gwcConfiguration;

    @RequestMapping(value = "/replace", method = RequestMethod.POST)
    public
    @ResponseBody
    ResponseEntity<String> replace(@RequestParam(value = "layer") String layer,
                                   @RequestParam(value = "destination", required = false) String destination,
                                   @RequestParam(value = "source", required = false) String source,
                                   @RequestParam(value = "file", required = false) MultipartFile uploadedFile) {
        // we need to close this resources at the end
        File workingDirectory = null;
        File file = null;
        try {
            // create a temporary working directory (may not be used)
            workingDirectory = Files.createTempDirectory("replace-operation-").toFile();
            // finding the blobstore associated t our layer
            SqliteBlobStore blobStore = getBlobStoreForLayer(layer);
            if (blobStore == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("No SQLite store could be associated with provided layer.");
            }
            // finding the file or the directory that will be used to replace
            if (uploadedFile != null && !uploadedFile.isEmpty()) {
                // it was an upload file
                file = handleFileUpload(uploadedFile, workingDirectory);
            } else if (source != null) {
                // the file is already present
                file = new File(source);

            }
            if (file == null || !file.exists()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Provided file is NULL or doesn't exists.");
            }
            // if we have a zip file we need to unzip it
            file = unzipFileIfNeeded(file, workingDirectory);
            if (file.isDirectory()) {
                // we invoke the replace directory variant
                blobStore.replace(file);
            } else {
                if (destination == null) {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Destination is required for single files.");
                }
                // we replace the single file
                blobStore.replace(file, destination);
            }
        } catch (Exception exception) {
            if (LOGGER.isErrorEnabled()) {
                LOGGER.error("Error executing the replace operation.", exception);
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(exception.getMessage());
        } finally {
            // cleaning everything
            FileUtils.deleteQuietly(workingDirectory);
        }
        return ResponseEntity.status(HttpStatus.OK).body(null);
    }

    private File handleFileUpload(MultipartFile uploadedFile, File workingDirectory) throws Exception {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Handling file upload.");
        }
        // getting the uploaded file content
        File outputFile = new File(workingDirectory, UUID.randomUUID().toString());
        byte[] bytes = uploadedFile.getBytes();
        try (BufferedOutputStream stream = new BufferedOutputStream(new FileOutputStream(outputFile))) {
            stream.write(bytes);
        }
        return outputFile;
    }

    private File unzipFileIfNeeded(File file, File workingDirectory) throws Exception {
        if (file.isDirectory()) {
            // is not a zip file so nothing to do
            return file;
        }
        try (FileInputStream fileInput = new FileInputStream(file);
             ZipInputStream zipInput = new ZipInputStream(fileInput)) {
            ZipEntry zipEntry = zipInput.getNextEntry();
            if (zipEntry == null) {
                // is not a zip file nothing to do
                return file;
            }
            // is a zip file we need to extract is content
            return unzip(zipInput, zipEntry, workingDirectory);
        }
    }

    private File unzip(ZipInputStream zipInputStream, ZipEntry zipEntry, File workingDirectory) throws Exception {
        // output directory for our zip file content
        File outputDirectory = new File(workingDirectory, UUID.randomUUID().toString());
        outputDirectory.mkdir();
        // unzipping all zip file entries
        while (zipEntry != null) {
            File outputFile = new File(outputDirectory, zipEntry.getName());
            if (zipEntry.isDirectory()) {
                // this entry is a directory, so we only need to create the directory
                outputFile.mkdir();
            } else {
                // is a file we need to extract is content
                extractFile(zipInputStream, outputFile);
            }
            zipInputStream.closeEntry();
            zipEntry = zipInputStream.getNextEntry();
        }
        return outputDirectory;
    }

    private void extractFile(ZipInputStream inputStream, File outputFile) throws Exception {
        // extracting zip entry file content
        byte[] bytes = new byte[1024];
        try (FileOutputStream outputStream = new FileOutputStream(outputFile)) {
            int read;
            while ((read = inputStream.read(bytes)) != -1) {
                outputStream.write(bytes, 0, read);
            }
        }
    }

    private SqliteBlobStore getBlobStoreForLayer(String layerName) throws Exception {
        // let's find layer associated store
        String blobStoreId = tileLayerDispatcher.getTileLayer(layerName).getBlobStoreId();
        BlobStoreInfo blobStoreConfig = null;
        for (BlobStoreInfo candidateBlobStoreConfig : blobConfiguration.getBlobStores()) {
            if (blobStoreId == null) {
                // we need to find the default configuration
                if (candidateBlobStoreConfig.isDefault()) {
                    // this is the default configuration, we are done
                    blobStoreConfig = candidateBlobStoreConfig;
                    break;
                }
            }
            if (candidateBlobStoreConfig.getName().equals(blobStoreId)) {
                // we need to find a specific store by is id
                blobStoreConfig = candidateBlobStoreConfig;
                break;
            }
        }
        if (blobStoreConfig == null || !(blobStoreConfig instanceof SqliteInfo)) {
            // no store found or the store is not an sqlite store
            return null;
        }
        // returning an instance of found store
        return (SqliteBlobStore) blobStoreConfig.createInstance(tileLayerDispatcher, gwcConfiguration.getLockProvider());
    }
}
