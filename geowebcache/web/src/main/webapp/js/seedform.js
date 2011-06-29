/*	Some of the layer properties are set from outside this script.
	The javascript that does this is generated from 
	org.geowebcache.rest.seed.SeedFormRestlet.makeHeader
*/ 

OpenLayers.DOTS_PER_INCH = layerDotsPerInch;
OpenLayers.Util.onImageLoadErrorColor = 'transparent';

var map;

function initOpenLayers() {

	var m = new OpenLayers.Map( 'map',{
			resolutions: layerResolutions,
			projection: layerProjection,
			maxExtent: maxExtents,
			units: layerUnits,
			controls: [new OpenLayers.Control.Navigation(), 
			           new OpenLayers.Control.PanZoomBar(),
		    	       new OpenLayers.Control.Scale($('mapScale')),
		        	   new OpenLayers.Control.MousePosition({element: $('mapLocation')})]
		}
	);
	
	var basemap = getBasemapLayer();
	if(!basemap) {
		basemap = getDefaultBasemap();
	}
	basemap.projection = layerProjection;
	basemap.resolutions = layerResolutions;

	var tileLayer = new OpenLayers.Layer.WMS(
	    "layer", "../../service/wms",
	    {
	        layers: layerName,
	        format: layerFormat
	    },
	    {
	        noMagic: true,
	        transparent: true,
	        tileSize: layerTileSize,
	        isBaseLayer: false
	    }
	);
	
	m.addLayers([basemap, tileLayer]);

    var vectorLayer = new OpenLayers.Layer.Vector("extent", 
    	{
    		eventListeners: {
				'featuremodified': updateBounds
			}
		});
		
	vectorLayer.addFeatures(new OpenLayers.Feature.Vector(layerExtents.toGeometry()));

	m.addLayer(vectorLayer);

    m.addControl(createToolbar(vectorLayer));

	m.zoomToExtent(layerExtents);
	
	return m;
}

function createToolbar(vlayer) {
    var boxControl = new OpenLayers.Control();
    OpenLayers.Util.extend(boxControl, {
        draw: function () {
            this.box = new OpenLayers.Handler.Box( boxControl, {"done": this.notice });
        },

        notice: function (bounds) {
            var ll = map.getLonLatFromPixel(new OpenLayers.Pixel(bounds.left, bounds.bottom)); 
            var ur = map.getLonLatFromPixel(new OpenLayers.Pixel(bounds.right, bounds.top));
            
            if(isNaN(ll.lon)) {
            	// ignore the box because it was too small to worry about or out of bounds or something.
            } else { 
			    $('minX').value = ll.lon;
			    $('minY').value = ll.lat;
			    $('maxX').value = ur.lon;
			    $('maxY').value = ur.lat;
	
	            vlayer.removeAllFeatures();
	            
				vlayer.addFeatures(new OpenLayers.Feature.Vector(new OpenLayers.Bounds(ll.lon, ll.lat, ur.lon, ur.lat).toGeometry()));
			}
        },
        
		autoActivate: true,
		displayClass: "olControlDrawFeaturePolygon",
		title: "Draw new bounding box",
		
			        
	    activate: function() {
	        this.box.activate();
	        return OpenLayers.Control.prototype.activate.apply(this,arguments);
	    },
	
	    deactivate: function() {
	        this.box.deactivate();
	        return OpenLayers.Control.prototype.deactivate.apply(this,arguments);
	    }
    });

	var sizeOptions = {
		standalone: true,
		title: "Move / resize bounding box",
		displayClass: "olControlSize",

	    activate: function() {
	    	this.selectFeature(vlayer.features[0]);
	        return OpenLayers.Control.prototype.activate.apply(this,arguments);
	    },
	
	    deactivate: function() {
	    	this.unselectFeature(vlayer.features[0]);
	        return OpenLayers.Control.prototype.deactivate.apply(this,arguments);
	    }
	};

	var buttons = {
		nav: new OpenLayers.Control.Navigation(),
		edit: boxControl,
		size: new OpenLayers.Control.ModifyFeature(vlayer, sizeOptions)
	};
	
	buttons.size.mode = OpenLayers.Control.ModifyFeature.RESIZE | OpenLayers.Control.ModifyFeature.DRAG;
	
	var toolbar = new OpenLayers.Control.Panel({
		displayClass: 'olControlEditingToolbar',
		defaultControl: buttons.nav
	});
	
	for(var key in buttons) {
		toolbar.addControls(buttons[key]);
	}
	
	return toolbar;
}

function updateFeature() {
	vlayer = map.getLayersByName('extent')[0];
	
	var minX = parseFloat($('minX').value);
	var minY = parseFloat($('minY').value);
	var maxX = parseFloat($('maxX').value);
	var maxY = parseFloat($('maxY').value);
	
	if(isNaN(minX) || isNaN(minY) || isNaN(maxX) || isNaN(maxY)) {
		alert("Invalid bounding box.");
	} else {
		bounds = new OpenLayers.Bounds(minX, minY, maxX, maxY);
	    vlayer.removeAllFeatures();
		vlayer.addFeatures(new OpenLayers.Feature.Vector(bounds.toGeometry()));
	}
}

function resetFeature() {
	vlayer = map.getLayersByName('extent')[0];
	
    vlayer.removeAllFeatures();
	vlayer.addFeatures(new OpenLayers.Feature.Vector(layerExtents.toGeometry()));
	
    $('minX').value = "";
    $('minY').value = "";
    $('maxX').value = "";
    $('maxY').value = "";
}

function updateBounds(result) {
	var bounds = result.feature.geometry.getBounds();
	
    $('minX').value = bounds.left;
    $('minY').value = bounds.bottom;
    $('maxX').value = bounds.right;
    $('maxY').value = bounds.top;
}    

function getDefaultBasemap() {
	return new OpenLayers.Layer.WMS(
	    "basemap", "../../service/wms",
	    {
	        layers: 'raster test layerz',
	        format: 'image/jpeg'
	    },
	    {
	        tiled: true,
	        buffer: 0,
	        wrapDateLine: true,
	        displayOutsideMaxExtent: true,
	        tileSize: new OpenLayers.Size(256,256)
	    }
	);
}
