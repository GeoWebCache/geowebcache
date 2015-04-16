package org.geowebcache.s3;

import javax.annotation.Nullable;

import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;

/**
 * Plain old java object representing the configuration for an S3 blob store.
 */
public class S3BlobStoreConfig {

    private String storeId;

    private String bucket;

    private String prefix;

    private String awsAccessKey;

    private String awsSecretKey;

    private int maxConnections;

    private boolean useHTTPS = true;

    private String proxyDomain;

    private String proxyWorkstation;

    private String proxyHost;

    private int proxyPort;

    private String proxyUsername;

    private String proxyPassword;

    private boolean useGzip;

    public String getStoreId() {
        return storeId;
    }

    public void setStoreId(String storeId) {
        this.storeId = storeId;
    }

    public String getBucket() {
        return bucket;
    }

    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

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

    /**
     * @return The maximum number of allowed open HTTP connections.
     */
    public int getMaxConnections() {
        return maxConnections;
    }

    /**
     * Sets the maximum number of allowed open HTTP connections.
     */
    public void setMaxConnections(int maxConnections) {
        this.maxConnections = maxConnections;
    }

    /**
     * @return whether to use HTTPS (true) or HTTP (false) when talking to S3 (defaults to true)
     */
    public boolean isUseHTTPS() {
        return useHTTPS;
    }

    /**
     * @param useHTTPS whether to use HTTPS (true) or HTTP (false) when talking to S3
     */
    public void setUseHTTPS(boolean useHTTPS) {
        this.useHTTPS = useHTTPS;
    }

    /**
     * Returns the optional Windows domain name for configuring an NTLM proxy.
     * <p>
     * If you aren't using a Windows NTLM proxy, you do not need to set this field.
     *
     * @return The optional Windows domain name for configuring an NTLM proxy.
     */
    @Nullable
    public String getProxyDomain() {
        return proxyDomain;
    }

    /**
     * Sets the optional Windows domain name for configuration an NTLM proxy.
     * <p>
     * If you aren't using a Windows NTLM proxy, you do not need to set this field.
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
     *        support.
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
    public int getProxyPort() {
        return proxyPort;
    }

    /**
     * Sets the optional proxy port the client will connect through.
     *
     * @param proxyPort The proxy port the client will connect through.
     */
    public void setProxyPort(int proxyPort) {
        this.proxyPort = proxyPort;
    }

    /**
     * Returns the optional proxy user name to use if connecting through a proxy.
     *
     * @return The optional proxy user name the configured client will use if connecting through a
     *         proxy.
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
     * Checks if gzip compression is used
     *
     * @return if gzip compression is used
     */
    public boolean isUseGzip() {
        return useGzip;
    }

    /**
     * Sets whether gzip compression should be used
     *
     * @param use whether gzip compression should be used
     */
    public void setUseGzip(boolean use) {
        this.useGzip = use;
    }

    @Override
    public boolean equals(Object o) {
        return EqualsBuilder.reflectionEquals(this, o);
    }

    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this);
    }

}
