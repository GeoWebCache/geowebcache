package org.geowebcache.s3;

import static com.google.common.base.Preconditions.checkState;

import java.util.List;
import java.util.Properties;
import java.util.UUID;

import org.junit.rules.ExternalResource;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.iterable.S3Objects;
import com.amazonaws.services.s3.model.DeleteObjectsRequest;
import com.amazonaws.services.s3.model.DeleteObjectsRequest.KeyVersion;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.google.common.base.Function;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

/**
 * The TemporaryS3Folder provides a path prefix for S3 storage and deletes all
 * resources under the given prefix at shutdown.
 */
public class TemporaryS3Folder extends ExternalResource {

	private String bucket;

	private String accessKey;

	private String secretKey;

	private String temporaryPrefix;

	private AmazonS3Client s3;

	public TemporaryS3Folder(Properties properties) {
		this(properties.getProperty("bucket"), properties
				.getProperty("accessKey"), properties.getProperty("secretKey"));
	}

	public TemporaryS3Folder(String bucket, String accessKey, String secretKey) {
		this.bucket = bucket;
		this.accessKey = accessKey;
		this.secretKey = secretKey;
	}

	@Override
	protected void before() throws Throwable {
		if (!isConfigured()) {
			return;
		}
		this.temporaryPrefix = "tmp_"
				+ UUID.randomUUID().toString().replace("-", "");
		AWSCredentials awsCredentials = new BasicAWSCredentials(accessKey,
				secretKey);
		s3 = new AmazonS3Client(awsCredentials);
	}

	@Override
	protected void after() {
		if (!isConfigured()) {
			return;
		}
		try {
			delete();
		} finally {
			temporaryPrefix = null;
			s3.shutdown();
		}
	}

	public AmazonS3 getClient() {
		checkState(isConfigured(), "client not configured.");
		return s3;
	}

	public S3BlobStoreConfig getConfig() {
		checkState(isConfigured(), "client not configured.");
		S3BlobStoreConfig config = new S3BlobStoreConfig();
		config.setBucket(bucket);
		config.setAwsAccessKey(accessKey);
		config.setAwsSecretKey(secretKey);
		config.setPrefix(temporaryPrefix);
		config.setUseGzip(true);
		return config;
	}

	public void delete() {
		checkState(isConfigured(), "client not configured.");
		if (temporaryPrefix == null) {
			return;
		}

		Iterable<S3ObjectSummary> objects = S3Objects.withPrefix(s3, bucket,
				temporaryPrefix);
		Iterable<List<S3ObjectSummary>> partition = Iterables.partition(
				objects, 1000);
		for (List<S3ObjectSummary> os : partition) {
			List<KeyVersion> keys = Lists.transform(os,
					new Function<S3ObjectSummary, KeyVersion>() {
						@Override
						public KeyVersion apply(S3ObjectSummary input) {
							KeyVersion k = new KeyVersion(input.getKey());
							return k;
						}
					});
			DeleteObjectsRequest deleteRequest = new DeleteObjectsRequest(
					bucket);
			deleteRequest.setKeys(keys);
			s3.deleteObjects(deleteRequest);
		}
	}

	public String getBucket() {
		return bucket;
	}

	public String getAccessKey() {
		return accessKey;
	}

	public String getSecretKey() {
		return secretKey;
	}

	public String getPrefix() {
		return temporaryPrefix;
	}

	public boolean isConfigured() {
		return bucket != null && accessKey != null && secretKey != null;
	}
}
