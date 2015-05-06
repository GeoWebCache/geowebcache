package org.geowebcache.s3;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.geowebcache.io.ByteArrayResource;
import org.geowebcache.io.Resource;
import org.geowebcache.mime.MimeException;
import org.geowebcache.mime.MimeType;
import org.geowebcache.storage.BlobStoreListener;
import org.geowebcache.storage.StorageException;
import org.geowebcache.storage.TileObject;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/**
 * Integration tests for {@link S3BlobStore}.
 * <p>
 * For the tests to be run, a properties file
 * {@code $HOME/.gwc_s3_tests.properties} must exist and contain entries for
 * {@code bucket}, {@code accessKey}, and {@code secretKey}.
 */
public class S3BlobStoreIntegrationTest {

	public PropertiesLoader testConfigLoader = new PropertiesLoader();

	@Rule
	public TemporaryS3Folder tempFolder = new TemporaryS3Folder(
			testConfigLoader.getProperties());

	private S3BlobStore blobStore;

	@Before
	public void before() {
		Assume.assumeTrue(tempFolder.isConfigured());
		blobStore = new S3BlobStore(tempFolder.getConfig());
	}

	@After
	public void after() {

	}

	@Test
	public void testPutGet() throws MimeException, StorageException {
		String layerName = "topp:world";
		long[] xyz = { 20, 30, 12 };
		String gridSetId = "EPSG:4326";
		String format = MimeType.createFromExtension("png").getFormat();
		Map<String, String> parameters = new HashMap<>();
		byte[] bytes = new byte[1024];
		Arrays.fill(bytes, (byte) 0xaf);

		Resource blob = new ByteArrayResource(bytes);
		TileObject tile = TileObject.createCompleteTileObject(layerName, xyz,
				gridSetId, format, parameters, blob);
		blobStore.put(tile);

		TileObject queryTile = TileObject.createQueryTileObject(layerName, xyz,
				gridSetId, format, parameters);
		boolean found = blobStore.get(queryTile);
		assertTrue(found);
		Resource resource = queryTile.getBlob();
		assertNotNull(resource);
		assertEquals(bytes.length, resource.getSize());
	}

	@Test
	public void testPutWithListener() throws MimeException, StorageException {

		String layerName = "topp:world";
		long[] xyz = { 20, 30, 12 };
		String gridSetId = "EPSG:4326";
		String format = MimeType.createFromExtension("png").getFormat();
		Map<String, String> parameters = new HashMap<>();
		byte[] bytes = new byte[1024];
		Arrays.fill(bytes, (byte) 0xaf);

		Resource blob = new ByteArrayResource(bytes);
		TileObject tile = TileObject.createCompleteTileObject(layerName, xyz,
				gridSetId, format, parameters, blob);

		BlobStoreListener listener = mock(BlobStoreListener.class);
		blobStore.addListener(listener);
		blobStore.put(tile);

		verify(listener).tileStored(eq(layerName), eq(gridSetId), eq(format),
				anyString(), eq(20L), eq(30L), eq(12), eq((long) bytes.length));

		// update tile
		tile = TileObject.createCompleteTileObject(layerName, xyz, gridSetId,
				format, parameters, new ByteArrayResource(new byte[512]));

		blobStore.put(tile);

		verify(listener).tileUpdated(eq(layerName), eq(gridSetId), eq(format),
				anyString(), eq(20L), eq(30L), eq(12), eq(512L), eq(1024L));

	}

	@Test
	public void testDelete() throws MimeException, StorageException {
		String layerName = "topp:world";
		long[] xyz = { 20, 30, 12 };
		String gridSetId = "EPSG:4326";
		String format = MimeType.createFromExtension("png").getFormat();
		Map<String, String> parameters = new HashMap<>();
		byte[] bytes = new byte[1024];
		Arrays.fill(bytes, (byte) 0xaf);

		Resource blob = new ByteArrayResource(bytes);
		TileObject tile = TileObject.createCompleteTileObject(layerName, xyz,
				gridSetId, format, parameters, blob);

		blobStore.put(tile);

		tile.getXYZ()[0] = 21;
		blobStore.put(tile);

		tile.getXYZ()[0] = 22;
		blobStore.put(tile);

		tile = TileObject.createQueryTileObject(layerName, new long[] { 20, 30,
				12 }, gridSetId, format, parameters);

		assertTrue(blobStore.delete(tile));
		assertFalse(blobStore.delete(tile));

		tile.getXYZ()[0] = 21;
		assertTrue(blobStore.delete(tile));
		assertFalse(blobStore.delete(tile));

		BlobStoreListener listener = mock(BlobStoreListener.class);
		blobStore.addListener(listener);
		tile.getXYZ()[0] = 22;
		assertTrue(blobStore.delete(tile));
		assertFalse(blobStore.delete(tile));

		verify(listener, times(1)).tileDeleted(eq(layerName), eq(gridSetId),
				eq(format), anyString(), eq(22L), eq(30L), eq(12), eq(1024L));
	}

	@Test
	public void testDeleteLayer() throws Exception {
		String layerName = "topp:world";
		long[] xyz = { 20, 30, 12 };
		String gridSetId = "EPSG:4326";
		String format = MimeType.createFromExtension("png").getFormat();
		Map<String, String> parameters = new HashMap<>();
		byte[] bytes = new byte[1024];
		Arrays.fill(bytes, (byte) 0xaf);

		Resource blob = new ByteArrayResource(bytes);
		TileObject tile = TileObject.createCompleteTileObject(layerName, xyz,
				gridSetId, format, parameters, blob);

		blobStore.put(tile);

		tile.getXYZ()[0] = 21;
		blobStore.put(tile);

		tile.getXYZ()[0] = 22;
		blobStore.put(tile);

		BlobStoreListener listener = mock(BlobStoreListener.class);
		blobStore.addListener(listener);
		blobStore.delete(layerName);
		blobStore.delete(layerName);
		verify(listener, times(1)).layerDeleted(eq(layerName));
	}

	@Test
	public void testLayerMetadata() {
		blobStore.putLayerMetadata("test:layer", "prop1", "value1");
		blobStore.putLayerMetadata("test:layer", "prop2", "value2");

		assertNull(blobStore.getLayerMetadata("test:layer", "nonExistingKey"));
		assertEquals("value1",
				blobStore.getLayerMetadata("test:layer", "prop1"));
		assertEquals("value2",
				blobStore.getLayerMetadata("test:layer", "prop2"));
	}

}
