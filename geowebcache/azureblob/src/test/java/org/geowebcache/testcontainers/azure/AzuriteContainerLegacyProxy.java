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
 * <p>Copyright 2024
 */
package org.geowebcache.testcontainers.azure;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.stream.Stream;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.RequestLine;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.entity.BasicHttpEntity;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.DefaultBHttpServerConnectionFactory;
import org.apache.http.impl.bootstrap.HttpServer;
import org.apache.http.impl.bootstrap.ServerBootstrap;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultConnectionKeepAliveStrategy;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpProcessor;
import org.apache.http.protocol.HttpProcessorBuilder;
import org.apache.http.protocol.HttpRequestHandler;
import org.apache.http.protocol.ResponseConnControl;
import org.apache.http.protocol.ResponseContent;
import org.apache.http.util.EntityUtils;
import org.geotools.util.logging.Logging;

/**
 * A simple HTTP proxy to adapt some Azure Blob storage protocol issues to the netty version used by
 * older {@code com.microsoft.azure:azure-storage-blob} dependencies.
 *
 * <p>For instance, re-writes the returned response headers {@code etag}, {@code last-modified}, and
 * {@code content-type}, as {@code Etag}, {@code Last-Modified}, and {@code Content-Type},
 * respectively, as expected by the Netty version the legacy {@code
 * com.microsoft.azure:azure-storage-blob} dependency transitively carries over.
 *
 * <p>Even though HTTP request and response headers should be case-insensitive, this older netty
 * version ({@code 4.1.28}, and even newer ones) fail to parse the lower-case names returned by
 * Azurite.
 */
class AzuriteContainerLegacyProxy {
    public static Logger LOGGER = Logging.getLogger(AzuriteContainerLegacyProxy.class.getName());

    private int localPort;
    private HttpServer proxyServer;

    private int targetPort;

    private final AtomicBoolean started = new AtomicBoolean();

    private boolean debug;

    AzuriteContainerLegacyProxy(int targetPort) {
        this.targetPort = targetPort;
    }

    /**
     * @return the random port where the proxy server is running
     * @throws IllegalStateException if the proxy is not {@link #start() running}
     */
    public int getLocalPort() {
        if (!started.get()) {
            throw new IllegalStateException(
                    "Proxy not running, local port is allocated at start()");
        }
        return localPort;
    }

    /**
     * Whether to print request/response debugging information to stderr.
     *
     * <p>Sample output:
     *
     * <pre>
     * <code>
     * routing GET http://localhost:44445/devstoreaccount1/testputgetblobisnotbytearrayresource/topp%3Aworld%2FEPSG%3A4326%2Fpng%2Fdefault%2F12%2F20%2F30.png to GET http://localhost:33319/devstoreaccount1/testputgetblobisnotbytearrayresource/topp%3Aworld%2FEPSG%3A4326%2Fpng%2Fdefault%2F12%2F20%2F30.png
     * 	applied request header Authorization: SharedKey devstoreaccount1:6UeSk1Qf8XRibLI1sE3tasmDxOtVxGUSMDQqRUDIW9Y=
     * 	applied request header x-ms-version: 2018-11-09
     * 	applied request header x-ms-date: Fri, 09 Aug 2024 17:08:38 GMT
     * 	applied request header host: localhost
     * 	applied request header x-ms-client-request-id: 526b726a-13af-49a3-b277-fdf645d77903
     * 	applied request header User-Agent: Azure-Storage/11.0.0 (JavaJRE 11.0.23; Linux 6.8.0-39-generic)
     * 	response: 200 OK
     * 	applied response header X-Powered-By: Express
     * 	applied response header ETag: "jzUOHaHcch36ue3TFspQaLiWSvo"
     * 	applied response header Last-Modified: Fri, 09 Aug 2024 17:08:38 GMT
     * 	applied response header x-ms-version: 2016-05-31
     * 	applied response header date: Fri, 09 Aug 2024 17:08:38 GMT
     * 	applied response header x-ms-request-id: 05130dd1-5672-11ef-a96b-c7f08f042b95
     * 	applied response header accept-ranges: bytes
     * 	applied response header x-ms-blob-type: BlockBlob
     * 	applied response header x-ms-request-server-encrypted: false
     * 	applied response header Content-Type: image/png
     * 	Content-Type: image/png
     * </code>
     * </pre>
     */
    public AzuriteContainerLegacyProxy debugRequests(boolean debug) {
        this.debug = debug;
        return this;
    }

    /** Allocates a free port and runs the proxy server on it. This method is idempotent. */
    public void start() throws IOException {
        if (started.compareAndSet(false, true)) {
            this.localPort = findFreePort();

            // this is the request handler that performs the proxying and fixes the response headers
            HttpRequestHandler proxyHandler = new ProxyHandler(localPort, targetPort, debug);

            HttpProcessor httpproc =
                    HttpProcessorBuilder.create()
                            // handles Transfer-Encoding and Content-Length
                            .add(new ResponseContent(true))
                            // handles connection keep-alive
                            .add(new ResponseConnControl())
                            .build();

            proxyServer =
                    ServerBootstrap.bootstrap()
                            .setConnectionFactory(DefaultBHttpServerConnectionFactory.INSTANCE)
                            .setHttpProcessor(httpproc)
                            .setListenerPort(localPort)
                            .registerHandler("*", proxyHandler)
                            .create();
            proxyServer.start();
        }
    }

    /** Stops the proxy server. This method is idempotent. */
    public void stop() {
        if (started.compareAndSet(true, false)) {
            proxyServer.stop();
        }
    }

    private int findFreePort() {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static class ProxyHandler implements HttpRequestHandler {
        private final int sourcePort;
        private final int targetPort;
        private boolean debug;

        final CloseableHttpClient client;
        Function<String, String> responseHeaderNameTransform = Function.identity();

        ProxyHandler(int sourcePort, int targetPort, boolean debug) {
            this.sourcePort = sourcePort;
            this.targetPort = targetPort;
            this.debug = debug;

            @SuppressWarnings("PMD.CloseResource")
            PoolingHttpClientConnectionManager connManager =
                    new PoolingHttpClientConnectionManager();
            client =
                    HttpClients.custom()
                            .setConnectionManager(connManager)
                            .setKeepAliveStrategy(new DefaultConnectionKeepAliveStrategy())
                            .build();
        }

        @Override
        public void handle(HttpRequest request, HttpResponse response, HttpContext context)
                throws HttpException, IOException {
            HttpUriRequest proxyRequest = proxify(request);
            logRequest(request, proxyRequest);

            try (CloseableHttpResponse proxyResponse = client.execute(proxyRequest)) {
                response.setStatusLine(proxyResponse.getStatusLine()); // status and reason phrase
                logResponseStatus(response);

                Header[] headers = proxyResponse.getAllHeaders();
                applyResponseHeaders(response, headers);
                transferResponseEntity(response, proxyResponse);
            }
        }

        private void transferResponseEntity(HttpResponse localResponse, HttpResponse remoteResponse)
                throws IOException {
            final HttpEntity remoteResponseEntity = remoteResponse.getEntity();
            HttpEntity entity;
            if (null == remoteResponseEntity) {
                entity = emptyBodyEntity(remoteResponse);
            } else {
                entity = extractResponseBody(remoteResponseEntity);
            }
            EntityUtils.updateEntity(localResponse, entity);
        }

        private HttpEntity extractResponseBody(final HttpEntity remoteResponseEntity)
                throws IOException {
            ContentType contentType = ContentType.get(remoteResponseEntity);
            byte[] rawContent = EntityUtils.toByteArray(remoteResponseEntity);
            logResponseBody(contentType, rawContent);
            return new ByteArrayEntity(rawContent, 0, rawContent.length, contentType);
        }

        private HttpEntity emptyBodyEntity(HttpResponse remoteResponse) {
            BasicHttpEntity entity = new BasicHttpEntity();
            Optional.ofNullable(remoteResponse.getFirstHeader("Content-Length"))
                    .map(Header::getValue)
                    .map(Long::parseLong)
                    .ifPresent(cl -> entity.setContentLength(cl));
            Header contentType = remoteResponse.getFirstHeader("Content-Type");
            entity.setContentType(contentType);
            return entity;
        }

        private void logResponseStatus(HttpResponse response) {
            StatusLine statusLine = response.getStatusLine();
            info("\tresponse: %d %s", statusLine.getStatusCode(), statusLine.getReasonPhrase());
        }

        private void logResponseBody(ContentType contentType, byte[] rawContent) {
            if (null != contentType) {
                info("\tContent-Type: %s", contentType);
                if (contentType.getMimeType().startsWith("application/xml")
                        || contentType.getMimeType().contains("json")) {
                    info("\tcontent:\t%s", new String(rawContent));
                }
            }
        }

        private void logRequest(HttpRequest request, HttpUriRequest proxyRequest) {
            info(
                    "routing %s %s to %s %s",
                    request.getRequestLine().getMethod(),
                    request.getRequestLine().getUri(),
                    proxyRequest.getRequestLine().getMethod(),
                    proxyRequest.getRequestLine().getUri());

            Stream.of(proxyRequest.getAllHeaders())
                    .forEach(
                            header ->
                                    info(
                                            "\tapplied request header %s: %s",
                                            header.getName(), header.getValue()));
        }

        private void applyResponseHeaders(HttpResponse response, Header[] headers) {
            if (null == headers || headers.length == 0) return;

            Stream.of(headers)
                    .forEach(
                            header -> {
                                String name = header.getName();
                                String value = header.getValue();
                                name = responseHeaderNameTransform.apply(name);
                                if ("Connection".equalsIgnoreCase(name)
                                        || "Transfer-Encoding".equalsIgnoreCase(name)
                                        || "Content-Length".equalsIgnoreCase(name)) {
                                    // these will produce a 'Connection reset by peer', let the
                                    // proxy handle them
                                    return;
                                }
                                // Fix the problematic response header names
                                if ("etag".equalsIgnoreCase(name)) {
                                    name = "ETag";
                                } else if ("last-modified".equalsIgnoreCase(name)) {
                                    name = "Last-Modified";
                                } else if ("content-type".equalsIgnoreCase(name)) {
                                    name = "Content-Type";
                                }
                                response.addHeader(name, value);
                                info("\tapplied response header %s: %s", name, value);
                            });
        }

        private HttpUriRequest proxify(HttpRequest request) {

            RequestLine requestLine = request.getRequestLine();

            String uri =
                    requestLine
                            .getUri()
                            .replace(
                                    "http://localhost:" + sourcePort,
                                    "http://localhost:" + targetPort);

            HttpUriRequest proxyRequest =
                    RequestBuilder.copy(request)
                            .setUri(uri)
                            // these will produce a 'Connection reset by peer', let the
                            // proxy handle them
                            .removeHeaders("Connection")
                            .removeHeaders("Transfer-Encoding")
                            .removeHeaders("Content-Length")
                            .build();
            return proxyRequest;
        }

        @SuppressWarnings("PMD.SystemPrintln")
        private void info(String msg, Object... params) {
            if (debug) {
                System.err.printf(msg + "%n", params);
            }
        }
    }
}
