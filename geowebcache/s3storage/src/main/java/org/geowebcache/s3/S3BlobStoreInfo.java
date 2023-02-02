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
 * @author Gabriel Roldan, Boundless Spatial Inc, Copyright 2015
 */
package org.geowebcache.s3;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.Protocol;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AnonymousAWSCredentials;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.S3ClientOptions;
import com.amazonaws.services.s3.model.CannedAccessControlList;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import org.apache.commons.lang3.SerializationUtils;
import org.geotools.util.logging.Logging;
import org.geowebcache.GeoWebCacheEnvironment;
import org.geowebcache.GeoWebCacheExtensions;
import org.geowebcache.config.BlobStoreInfo;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.locks.LockProvider;
import org.geowebcache.storage.BlobStore;
import org.geowebcache.storage.StorageException;

/** Plain old java object representing the configuration for an S3 blob store. */
@SuppressWarnings("deprecation")
public class S3BlobStoreInfo extends BlobStoreInfo {

    static Logger log = Logging.getLogger(S3BlobStoreInfo.class.getName());

    private static final long serialVersionUID = 9072751143836460389L;

    private String bucket;

    private String prefix;

    private String awsAccessKey;

    private String awsSecretKey;

    private Access access = Access.PUBLIC;

    private String maxConnections;

    private String useHTTPS = "true";

    private String proxyDomain;

    private String proxyWorkstation;

    private String proxyHost;

    private String proxyPort;

    private String proxyUsername;

    private String proxyPassword;

    private String useGzip;

    private String endpoint;

    public S3BlobStoreInfo() {
        super();
    }

    public S3BlobStoreInfo(String id) {
        super(id);
    }

    /** @return the name of the AWS S3 bucket where to store tiles */
    public String getBucket() {
        return bucket;
    }

    /** Sets the name of the AWS S3 bucket where to store tiles */
    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    /** @return the host of the S3-compatible server (if not AWS) */
    public String getEndpoint() {
        return endpoint;
    }

    /** Sets the host of the S3-compatible server (if not AWS) */
    public void setEndpoint(String host) {
        this.endpoint = host;
    }

    /**
     * Returns the base prefix, which is a prefix path to use as the root to store tiles under the
     * bucket.
     *
     * @return optional string for a "base prefix"
     */
    @Nullable
    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public String getAwsAccessKey() {
        return awsAccessKey;
    }

    public void setAwsAccessKey(String awsAccessKey) {
        this.awsAccessKey = awsAccessKey;
    }

    public String getAwsSecretKey() {
        return awsSecretKey;
    }

    public void setAwsSecretKey(String awsSecretKey) {
        this.awsSecretKey = awsSecretKey;
    }

    /** @return The maximum number of allowed open HTTP connections. */
    public String getMaxConnections() {
        return maxConnections;
    }

    /** Sets the maximum number of allowed open HTTP connections. */
    public void setMaxConnections(String maxConnections) {
        this.maxConnections = maxConnections;
    }

    /** @return whether to use HTTPS (true) or HTTP (false) when talking to S3 (defaults to true) */
    public String getUseHTTPS() {
        return useHTTPS;
    }

    /** @param useHTTPS whether to use HTTPS (true) or HTTP (false) when talking to S3 */
    public void setUseHTTPS(String useHTTPS) {
        this.useHTTPS = useHTTPS;
    }

    /**
     * Returns the optional Windows domain name for configuring an NTLM proxy.
     *
     * <p>If you aren't using a Windows NTLM proxy, you do not need to set this field.
     *
     * @return The optional Windows domain name for configuring an NTLM proxy.
     */
    @Nullable
    public String getProxyDomain() {
        return proxyDomain;
    }

    /**
     * Sets the optional Windows domain name for configuration an NTLM proxy.
     *
     * <p>If you aren't using a Windows NTLM proxy, you do not need to set this field.
     *
     * @param proxyDomain The optional Windows domain name for configuring an NTLM proxy.
     */
    public void setProxyDomain(String proxyDomain) {
        this.proxyDomain = proxyDomain;
    }

    /**
     * Returns the optional Windows workstation name for configuring NTLM proxy support. If you
     * aren't using a Windows NTLM proxy, you do not need to set this field.
     *
     * @return The optional Windows workstation name for configuring NTLM proxy support.
     */
    @Nullable
    public String getProxyWorkstation() {
        return proxyWorkstation;
    }

    /**
     * Sets the optional Windows workstation name for configuring NTLM proxy support. If you aren't
     * using a Windows NTLM proxy, you do not need to set this field.
     *
     * @param proxyWorkstation The optional Windows workstation name for configuring NTLM proxy
     *     support.
     */
    public void setProxyWorkstation(String proxyWorkstation) {
        this.proxyWorkstation = proxyWorkstation;
    }

    /**
     * Returns the optional proxy host the client will connect through.
     *
     * @return The proxy host the client will connect through.
     */
    @Nullable
    public String getProxyHost() {
        return proxyHost;
    }

    /**
     * Sets the optional proxy host the client will connect through.
     *
     * @param proxyHost The proxy host the client will connect through.
     */
    public void setProxyHost(String proxyHost) {
        this.proxyHost = proxyHost;
    }

    /**
     * Returns the optional proxy port the client will connect through.
     *
     * @return The proxy port the client will connect through.
     */
    public String getProxyPort() {
        return proxyPort;
    }

    /**
     * Sets the optional proxy port the client will connect through.
     *
     * @param proxyPort The proxy port the client will connect through.
     */
    public void setProxyPort(String proxyPort) {
        this.proxyPort = proxyPort;
    }

    /**
     * Returns the optional proxy user name to use if connecting through a proxy.
     *
     * @return The optional proxy user name the configured client will use if connecting through a
     *     proxy.
     */
    @Nullable
    public String getProxyUsername() {
        return proxyUsername;
    }

    /**
     * Sets the optional proxy user name to use if connecting through a proxy.
     *
     * @param proxyUsername The proxy user name to use if connecting through a proxy.
     */
    public void setProxyUsername(String proxyUsername) {
        this.proxyUsername = proxyUsername;
    }

    /**
     * Returns the optional proxy password to use when connecting through a proxy.
     *
     * @return The password to use when connecting through a proxy.
     */
    @Nullable
    public String getProxyPassword() {
        return proxyPassword;
    }

    /**
     * Sets the optional proxy password to use when connecting through a proxy.
     *
     * @param proxyPassword The password to use when connecting through a proxy.
     */
    public void setProxyPassword(String proxyPassword) {
        this.proxyPassword = proxyPassword;
    }

    /**
     * Checks access type
     *
     * @return public or private access
     */
    public CannedAccessControlList getAccessControlList() {
        CannedAccessControlList accessControlList;
        if (access == Access.PRIVATE) {
            accessControlList = CannedAccessControlList.BucketOwnerFullControl;
        } else {
            accessControlList = CannedAccessControlList.PublicRead;
        }
        return accessControlList;
    }

    /**
     * Sets whether access should be private or public
     *
     * @param access whether access is private or public
     */
    public void setAccess(Access access) {
        this.access = access;
    }

    /**
     * Gets whether access should be private or public
     *
     * @return whether access is private or public
     */
    public Access getAccess() {
        return this.access;
    }

    /**
     * Checks if gzip compression is used
     *
     * @return if gzip compression is used
     */
    public String getUseGzip() {
        return useGzip;
    }

    /**
     * Sets whether gzip compression should be used
     *
     * @param use whether gzip compression should be used
     */
    public void setUseGzip(String use) {
        this.useGzip = use;
    }

    @Override
    public BlobStore createInstance(TileLayerDispatcher layers, LockProvider lockProvider)
            throws StorageException {

        checkNotNull(layers);
        checkState(getName() != null);
        checkState(
                isEnabled(),
                "Can't call S3BlobStoreConfig.createInstance() is blob store is not enabled");
        final GeoWebCacheEnvironment gwcEnvironment =
                GeoWebCacheExtensions.bean(GeoWebCacheEnvironment.class);
        return new S3BlobStore(this.clone(gwcEnvironment, true), layers, lockProvider);
    }

    public S3BlobStoreInfo clone(
            GeoWebCacheEnvironment gwcEnvironment, Boolean allowEnvParametrization) {
        S3BlobStoreInfo blobStore = SerializationUtils.clone(this);

        if (allowEnvParametrization && gwcEnvironment != null) {
            blobStore.setName(getName());
            blobStore.setEnabled(isEnabled());
            blobStore.setDefault(isDefault());
            blobStore.setAccess(getAccess());
            blobStore.setPrefix(nullSafeResolveString(getPrefix(), gwcEnvironment));
            blobStore.setUseHTTPS(nullSafeResolveString(getUseHTTPS(), gwcEnvironment));
            blobStore.setUseGzip(nullSafeResolveString(getUseGzip(), gwcEnvironment));
            blobStore.setMaxConnections(nullSafeResolveString(getMaxConnections(), gwcEnvironment));
            blobStore.setProxyPort(nullSafeResolveString(getProxyPort(), gwcEnvironment));
            blobStore.setBucket(nullSafeResolveString(getBucket(), gwcEnvironment));
            blobStore.setAwsAccessKey(nullSafeResolveString(getAwsAccessKey(), gwcEnvironment));
            blobStore.setAwsSecretKey(nullSafeResolveString(getAwsSecretKey(), gwcEnvironment));
            blobStore.setProxyDomain(nullSafeResolveString(getProxyDomain(), gwcEnvironment));
            blobStore.setProxyWorkstation(
                    nullSafeResolveString(getProxyWorkstation(), gwcEnvironment));
            blobStore.setProxyHost(nullSafeResolveString(getProxyHost(), gwcEnvironment));
            blobStore.setProxyUsername(nullSafeResolveString(getProxyUsername(), gwcEnvironment));
            blobStore.setProxyPassword(nullSafeResolveString(getProxyPassword(), gwcEnvironment));
            blobStore.setEndpoint(nullSafeResolveString(getEndpoint(), gwcEnvironment));
        }
        return blobStore;
    }

    private String nullSafeResolveString(String value, GeoWebCacheEnvironment gwcEnvironment) {
        if (value == null) {
            return null;
        }
        return gwcEnvironment.resolveValue(value).toString();
    }

    private Integer toInteger(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException e) {
            log.log(Level.WARNING, "Unable to parse S3BlobStoreInfo value: " + value, e);
            return null;
        }
    }

    private Boolean toBoolean(String value) {
        if (value == null) {
            return null;
        }
        return Boolean.valueOf(value);
    }

    @Override
    public String getLocation() {
        String bucket = this.getBucket();
        String prefix = this.getPrefix();
        if (prefix == null) {
            return String.format("bucket: %s", bucket);
        } else {
            return String.format("bucket: %s prefix: %s", bucket, prefix);
        }
    }

    /** @return {@link AmazonS3Client} constructed from this {@link S3BlobStoreInfo}. */
    public AmazonS3Client buildClient() {
        ClientConfiguration clientConfig = new ClientConfiguration();
        Boolean useHTTPS = toBoolean(this.getUseHTTPS());
        if (null != useHTTPS) {
            clientConfig.setProtocol(useHTTPS ? Protocol.HTTPS : Protocol.HTTP);
        }
        Integer maxConnections = toInteger(this.getMaxConnections());
        if (null != maxConnections && maxConnections > 0) {
            clientConfig.setMaxConnections(maxConnections);
        }
        clientConfig.setProxyDomain(proxyDomain);
        clientConfig.setProxyWorkstation(proxyWorkstation);
        clientConfig.setProxyHost(proxyHost);
        Integer proxyPort = toInteger(this.getProxyPort());
        if (null != proxyPort) {
            clientConfig.setProxyPort(proxyPort);
        }
        clientConfig.setProxyUsername(proxyUsername);
        clientConfig.setProxyPassword(proxyPassword);
        Boolean useGzip = toBoolean(this.getUseGzip());
        if (null != useGzip) {
            clientConfig.setUseGzip(useGzip);
        }
        log.fine("Initializing AWS S3 connection");
        AmazonS3Client client = new AmazonS3Client(getCredentialsProvider(), clientConfig);
        if (endpoint != null && !"".equals(endpoint)) {
            S3ClientOptions s3ClientOptions = new S3ClientOptions();
            s3ClientOptions.setPathStyleAccess(true);
            client.setS3ClientOptions(s3ClientOptions);
            client.setEndpoint(endpoint);
        }
        if (!client.doesBucketExist(bucket)) {
            client.createBucket(bucket);
        }
        return client;
    }

    private AWSCredentialsProvider getCredentialsProvider() {
        if (null != awsSecretKey && null != awsAccessKey) {
            return new AWSCredentialsProvider() {

                @Override
                public AWSCredentials getCredentials() {
                    if ("".equals(awsAccessKey) && "".equals(awsSecretKey)) {
                        return new AnonymousAWSCredentials();
                    }
                    return new BasicAWSCredentials(awsAccessKey, awsSecretKey);
                }

                @Override
                public void refresh() {}
            };
        }
        return new DefaultAWSCredentialsProviderChain();
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + ((access == null) ? 0 : access.hashCode());
        result = prime * result + ((awsAccessKey == null) ? 0 : awsAccessKey.hashCode());
        result = prime * result + ((awsSecretKey == null) ? 0 : awsSecretKey.hashCode());
        result = prime * result + ((bucket == null) ? 0 : bucket.hashCode());
        result = prime * result + ((endpoint == null) ? 0 : endpoint.hashCode());
        result = prime * result + ((maxConnections == null) ? 0 : maxConnections.hashCode());
        result = prime * result + ((prefix == null) ? 0 : prefix.hashCode());
        result = prime * result + ((proxyDomain == null) ? 0 : proxyDomain.hashCode());
        result = prime * result + ((proxyHost == null) ? 0 : proxyHost.hashCode());
        result = prime * result + ((proxyPassword == null) ? 0 : proxyPassword.hashCode());
        result = prime * result + ((proxyPort == null) ? 0 : proxyPort.hashCode());
        result = prime * result + ((proxyUsername == null) ? 0 : proxyUsername.hashCode());
        result = prime * result + ((proxyWorkstation == null) ? 0 : proxyWorkstation.hashCode());
        result = prime * result + ((useGzip == null) ? 0 : useGzip.hashCode());
        result = prime * result + ((useHTTPS == null) ? 0 : useHTTPS.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!super.equals(obj)) return false;
        if (getClass() != obj.getClass()) return false;
        S3BlobStoreInfo other = (S3BlobStoreInfo) obj;
        if (access != other.access) return false;
        if (awsAccessKey == null) {
            if (other.awsAccessKey != null) return false;
        } else if (!awsAccessKey.equals(other.awsAccessKey)) return false;
        if (awsSecretKey == null) {
            if (other.awsSecretKey != null) return false;
        } else if (!awsSecretKey.equals(other.awsSecretKey)) return false;
        if (bucket == null) {
            if (other.bucket != null) return false;
        } else if (!bucket.equals(other.bucket)) return false;
        if (endpoint == null) {
            if (other.endpoint != null) return false;
        } else if (!endpoint.equals(other.endpoint)) return false;
        if (maxConnections == null) {
            if (other.maxConnections != null) return false;
        } else if (!maxConnections.equals(other.maxConnections)) return false;
        if (prefix == null) {
            if (other.prefix != null) return false;
        } else if (!prefix.equals(other.prefix)) return false;
        if (proxyDomain == null) {
            if (other.proxyDomain != null) return false;
        } else if (!proxyDomain.equals(other.proxyDomain)) return false;
        if (proxyHost == null) {
            if (other.proxyHost != null) return false;
        } else if (!proxyHost.equals(other.proxyHost)) return false;
        if (proxyPassword == null) {
            if (other.proxyPassword != null) return false;
        } else if (!proxyPassword.equals(other.proxyPassword)) return false;
        if (proxyPort == null) {
            if (other.proxyPort != null) return false;
        } else if (!proxyPort.equals(other.proxyPort)) return false;
        if (proxyUsername == null) {
            if (other.proxyUsername != null) return false;
        } else if (!proxyUsername.equals(other.proxyUsername)) return false;
        if (proxyWorkstation == null) {
            if (other.proxyWorkstation != null) return false;
        } else if (!proxyWorkstation.equals(other.proxyWorkstation)) return false;
        if (useGzip == null) {
            if (other.useGzip != null) return false;
        } else if (!useGzip.equals(other.useGzip)) return false;
        if (useHTTPS == null) {
            if (other.useHTTPS != null) return false;
        } else if (!useHTTPS.equals(other.useHTTPS)) return false;
        return true;
    }

    @Override
    public String toString() {
        return "S3BlobStoreInfo [bucket="
                + bucket
                + ", prefix="
                + prefix
                + ", awsAccessKey="
                + awsAccessKey
                + ", awsSecretKey="
                + awsSecretKey
                + ", access="
                + access
                + ", maxConnections="
                + maxConnections
                + ", useHTTPS="
                + useHTTPS
                + ", proxyDomain="
                + proxyDomain
                + ", proxyWorkstation="
                + proxyWorkstation
                + ", proxyHost="
                + proxyHost
                + ", proxyPort="
                + proxyPort
                + ", proxyUsername="
                + proxyUsername
                + ", proxyPassword="
                + proxyPassword
                + ", useGzip="
                + useGzip
                + ", endpoint="
                + endpoint
                + ", getName()="
                + getName()
                + ", getId()="
                + getId()
                + ", isEnabled()="
                + isEnabled()
                + ", isDefault()="
                + isDefault()
                + "]";
    }
}
