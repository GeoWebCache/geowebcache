package org.geowebcache.jetty;

import java.io.File;
import java.net.BindException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;
import java.util.function.Consumer;

import org.junit.rules.TemporaryFolder;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.eclipse.jetty.server.*;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.webapp.WebAppContext;

/**
 * Rule that runs a live GWC instance inside of Jetty for integration tests
 * 
 * @author Kevin Smith, Boundless
 *
 */
public class JettyRule extends org.junit.rules.ExternalResource {
    
    private static final String JETTY_PORT_PROPERTY = "jetty.port";

    private static final Log log = LogFactory.getLog(JettyRule.class);
    
    Integer port = null;
    
    TemporaryFolder temp = new TemporaryFolder();
    Server jettyServer;

    private File confDir;

    private File cacheDir;

    private File workDir;
    
    public int getPort() {
        return ((ServerConnector)getServer().getConnectors()[0]).getLocalPort();
    }
    
    Initializer<File> confInit;
    Initializer<File> cacheInit;
    
    
    
    public JettyRule() {
        this(d -> {}, d -> {});
    }
    public JettyRule(Initializer<File> confInit, Initializer<File> cacheInit) {
        super();
        this.confInit = confInit;
        this.cacheInit = cacheInit;
    }

    public interface Initializer<T> {
        public void accept(T toInit) throws Exception;
        
        default Initializer<T> andThen(Consumer<? super T> afterInit) {
            Objects.requireNonNull(afterInit);
            return (T toInit) -> { 
                accept(toInit); 
                afterInit.accept(toInit); 
            };
        }
        
        default Consumer<T> makeSafe(Consumer<Exception> handler) {
            return (T toInit) -> {
                try {
                    accept(toInit);
                } catch (Exception e) {
                    handler.accept(e);
                }
            };
        }
    }
    
    @Override
    public Statement apply(Statement base, Description description) {
        // This rule goes inside the TemporaryFolder rule.
        return temp.apply(super.apply(base, description), description);
    }
    
    public Server getServer() {
        if(jettyServer==null) {
            throw new IllegalStateException();
        }
        return jettyServer;
    }
    
    public URI getUri() {
        try {
            return new URI("http",null,"localhost", getPort(), "/geowebcache/", null, null);
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }
    
    @Override
    protected void before() throws Exception {
        confDir = temp.newFolder("conf");
        cacheDir = temp.newFolder("cache");
        workDir = temp.newFolder("work");
        
        confInit.accept(confDir);
        cacheInit.accept(cacheDir);
        
        final Integer port = Integer.getInteger(JETTY_PORT_PROPERTY, 8080);
        
        jettyServer = new Server();
        try {
            HttpConfiguration httpConfiguration = new HttpConfiguration();
            
            ServerConnector http = new ServerConnector(jettyServer, new HttpConnectionFactory(httpConfiguration));
            http.setPort(port);
            http.setAcceptQueueSize(100);
            http.setIdleTimeout(1000 * 60 *60);
            http.setSoLingerTime(-1);
            
            jettyServer.setConnectors(new Connector[] { http });
            
            WebAppContext wah = new WebAppContext();
            wah.setContextPath("/geowebcache");
            wah.setWar("src/main/webapp");
            //wah.setResourceAlias(alias, uri); // Use this to replace the spring context files?
            wah.getInitParams().put("GEOWEBCACHE_CONF_DIR", confDir.getCanonicalPath());
            wah.getInitParams().put("GEOWEBCACHE_CACHE_DIR", cacheDir.getCanonicalPath());
            jettyServer.setHandler(wah);

            wah.setTempDirectory(workDir);
            
            // Use this to set a limit on the number of threads used to respond requests
            QueuedThreadPool tp = new QueuedThreadPool();
            tp.setMinThreads(50);
            tp.setMaxThreads(50);
            
            jettyServer.start();
        } catch (Exception e) {
            if(e instanceof BindException) {
                log.error("Could not bind to port "+port+", "+ e.getMessage()+ ", Set via property "+JETTY_PORT_PROPERTY);
            }
            if (jettyServer != null) {
                try {
                    jettyServer.stop();
                } catch (Exception e1) {
                    e1.addSuppressed(e);
                    throw e1;
                }
            }
            throw e;
        }
    }
    
    @Override
    protected void after() {
        // Jetty shutdown takes 100 seconds so do this in a thread to speed it up.
        new Thread(()->{
            try {
                jettyServer.stop();
            } catch (Exception e) {
                throw new IllegalStateException("Error while shutting down test Jetty",e);
            }
        }).start();
    }
}
