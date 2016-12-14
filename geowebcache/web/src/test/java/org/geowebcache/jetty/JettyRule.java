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
import org.mortbay.jetty.Connector;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.bio.SocketConnector;
import org.mortbay.jetty.webapp.WebAppContext;
import org.mortbay.thread.QueuedThreadPool;

/**
 * Rule that runs a live GWC instance inside of Jetty for integration tests
 * 
 * @author Kevin Smith, Boundless
 *
 */
public class JettyRule extends org.junit.rules.ExternalResource {
    
    Integer port = null;
    
    TemporaryFolder temp = new TemporaryFolder();
    Server jettyServer;

    private File confDir;

    private File cacheDir;

    private File workDir;
    
    public int getPort() {
        return getServer().getConnectors()[0].getLocalPort();
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
        
        jettyServer = new Server();
        try {
            SocketConnector conn = new SocketConnector();

            int port;
            for(port=8080; port<=8180; port++) {
                conn.setPort(port);
                try{
                    conn.open();
                } catch (BindException e) {
                    continue;
                }
            }
            conn.setPort(port);
            conn.setAcceptQueueSize(100);
            jettyServer.setConnectors(new Connector[] { conn });
            
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
            conn.setThreadPool(tp);
            
            jettyServer.start();
        } catch (Exception e) {
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
