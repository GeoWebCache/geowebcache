package org.geowebcache.arcgis.layer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.URISyntaxException;
import java.net.URL;

import org.geowebcache.config.GridSetConfiguration;
import org.geowebcache.config.XMLConfigurationGridsetConformanceTest;
import org.geowebcache.config.XMLConfigurationProvider;

public class XMLConfigurationGridSetConformanceWithArcGisLayersTest
        extends XMLConfigurationGridsetConformanceTest {
    
    @Override
    public void setUpTestUnit() throws Exception {
        extensions.addBean("ArcGISLayerConfigProvider", 
                new ArcGISLayerXMLConfigurationProvider(), XMLConfigurationProvider.class);
        GridSetConfiguration gsConfig = new ArcGISCacheGridsetConfiguration();
        extensions.addBean(gsConfig.getIdentifier(), gsConfig,
                GridSetConfiguration.class, ArcGISCacheGridsetConfiguration.class);
        super.setUpTestUnit();
    }

    @Override
    protected void makeConfigFile() throws IOException {
        if(configFile==null) {
            configDir = temp.getRoot();
            configFile = temp.newFile("geowebcache.xml");
            
            try(OutputStream os = new FileOutputStream(configFile);
                    PrintWriter writer = new PrintWriter(os)) {
                writer.println("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" + 
                        "<gwcConfiguration xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" + 
                        "  xmlns=\"http://geowebcache.org/schema/1.12.0\"\n" + 
                        "  xsi:schemaLocation=\"http://geowebcache.org/schema/1.12.0 http://geowebcache.org/schema/1.12.0/geowebcache.xsd\">\n" + 
                        "  <version>1.12.0</version>\n" + 
                        "  <backendTimeout>120</backendTimeout>\n" + 
                        "  <serviceInformation>\n" + 
                        "    <title>GeoWebCache</title>\n" + 
                        "    <keywords>\n" + 
                        "    </keywords>\n" + 
                        "    <serviceProvider>\n" + 
                        "    </serviceProvider>\n" + 
                        "    <fees>NONE</fees>\n" + 
                        "    <accessConstraints>NONE</accessConstraints>\n" + 
                        "  </serviceInformation>\n" + 
                        "\n" + 
                        "  <layers>\n" + 
                        "    <arcgisLayer>\n" + 
                        "      <name>naturalearth</name>\n" + 
                        "      <tilingScheme>"+resourceAsFile("/compactcache/Conf.xml").getAbsolutePath()+"</tilingScheme>\n" + 
                        "    </arcgisLayer>\n" + 
                        "  </layers>\n" + 
                        "\n" + 
                        "</gwcConfiguration>");
            }
        }
    }

    File resourceAsFile(String resource) {
        URL url = getClass().getResource(resource);
        File f;
        try {
          f = new File(url.toURI());
        } catch(URISyntaxException e) {
          f = new File(url.getPath());
        }
        return f;
    }

    @Override
    protected String getExistingInfo() {
        return "naturalearth";
    }

}
