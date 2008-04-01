package org.geowebcache.util;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geotools.data.ows.Layer;
import org.geotools.data.ows.WMSCapabilities;
import org.geotools.data.wms.WebMapServer;
import org.geotools.ows.ServiceException;
import org.geowebcache.cache.CacheFactory;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.util.wms.BBOX;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;
import org.springframework.core.io.UrlResource;

public class GetCapabilitiesConfiguration implements Configuration {
	private static Log log = LogFactory
	.getLog(org.geowebcache.util.GetCapabilitiesConfiguration.class);
	
	private CacheFactory cacheFactory = null;
	
	private String url = null;
	
	public GetCapabilitiesConfiguration(
			CacheFactory cacheFactory, String url) {
		this.cacheFactory = cacheFactory;
		this.url = url;
		log.info("Constructing from url " + url);
	}
	
	/**
	 * Identifier for this Configuration instance
	 * 
	 * @return the URL given to the constructor
	 */
	public String getIdentifier() {
		return url;
	}

	/**
	 * Gets the XML document and parses it, creates
	 * WMSLayers for the relevant 
	 * 
	 * @return the layers described at the given URL 
	 */
	public Map getTileLayers() {		
		HashMap layerMap = new HashMap();
		
		WebMapServer wms = getWMS();

		WMSCapabilities capabilities = wms.getCapabilities();
		List<Layer> layerList = capabilities.getLayerList();
		
		Iterator<Layer> layerIter = layerList.iterator();
		while(layerIter.hasNext()){
			Layer layer = layerIter.next();
			String name = layer.getName();
			if(name != null) {
				double minX = layer.getLatLonBoundingBox().getMinX();
				double minY = layer.getLatLonBoundingBox().getMinY();
				double maxX = layer.getLatLonBoundingBox().getMaxX();
				double maxY = layer.getLatLonBoundingBox().getMaxY();
				
				BBOX bbox = new BBOX(minX, minY, maxX, maxY);
				log.info("Found layer: " + layer.getName()
						+ " with LatLon bbox " + bbox.toString());
			}
		}
		return layerMap;
	}
	
//	/**
//	 * Finds all the layers, verifies the WMS server
//	 * supports 4326 and 900913
//	 * 
//	 * 
//	 * @param doc the GetCapabilites document
//	 * @return all the described WMSLayers 
//	 */
//	private Map processDocument(Document doc) {
//		HashMap layerMap = new HashMap();
//		Element rootEl 			= doc.getRootElement();
//		Element capabilityEl 	= rootEl.getChild("Capability");
//		Element layerEl 		= capabilityEl.getChild("Layer");
//		List childrenList = layerEl.getChildren();
//		List layerList = layerEl.getChildren("Layer");
//		
//		
//		
//		return layerMap;
//	}
	
	private TileLayer processLayer(Element el) {
		return null;
	}

	private Document getDocument(UrlResource urlResource) {
		SAXBuilder builder = new SAXBuilder();
		Document doc = null;
		try {
			doc = builder.build(urlResource.getInputStream());
		} catch (IOException ioe) {
			log.error("IOException while reading "+ url);
		}  catch (JDOMException jde) {
			log.error("Problem with document from " + url + " " +jde.getMessage());
		}
		return doc;
	}
	
	private WebMapServer getWMS() {
		try{
			return new WebMapServer(new URL(url));
		} catch(IOException ioe) {
			log.error(url +" -> "+ ioe.getMessage());
		} catch(ServiceException se) {
			log.error(se.getMessage());
		}
		return null;
	}
	
//	private UrlResource getUrlResource() {	
//		UrlResource urlResource = null;
//		try {
//			urlResource = new UrlResource(url);
//		} catch (MalformedURLException mue) {
//			log.error(mue.getMessage());
//			mue.printStackTrace();
//		}
//		return urlResource;
//	}
	


}
