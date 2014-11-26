
 
  var response = 'undefined'; 

	// Request GeCapabilities 
	// Should use a promesis  instead 
	function GetVendorWMTSCapabilities(url,mapServiceName,idx) {
		var request = "SERVICE=WMTS&REQUEST=getcapabilities&TILED=true";
	
		if (response == 'undefined')
		{
			var xhr = new XMLHttpRequest();
			xhr.open('GET', url+ "?" + request);
			xhr.onload = function (e) {
				response = xhr.response;
				if (idx==1)
					loadMapFromParameter(response,url,mapServiceName);
				else				
					getAllMapNameFromWMTS(response);				
			};
			xhr.send();
			return ;
		}
		
		if (idx==1)
			loadMapFromParameter(response,url,mapServiceName);
		else				
			getAllMapNameFromWMTS(response);			
		
	}
	
	
	// Call GetVendorCapabilities
	function addAllMapNameFromWMTS(url)
	{
		response = 'undefined';
		GetVendorWMTSCapabilities(url,"",0);		
	}
	
	// trick cause find don't work when name has :
	function getNameInNodeList(nodeList,i,nodeName)
	{
		var child =$(nodeList[i]).children();
		for (var j=0;j<child.length;j++) 
			if($(child[j]).context.nodeName==nodeName)
				return $(child[j]).text();
		return "Null";
	}
	
	// Call GetVendorWMSCapabilities
	function loadWMTSMap(url,mapServiceName)
	{
		GetVendorWMTSCapabilities(url,mapServiceName,1);		
	}

// decode GetCapabilities and create the map, view and layer
	function getAllMapNameFromWMTS(capabilitiesResponse)
	{
		
		var Layer = $(capabilitiesResponse).find("Layer");
		for (var i=0;i<Layer.length;i++) {
		
				var layerName =getNameInNodeList(Layer,i,"OWS:IDENTIFIER");
				var sublayers = $(Layer[i]).find("Layer");
				if (sublayers.length ==0)
				{								
					var str = '<input type="button" value="'+layerName + '" ';
					str += 'onclick="loadWMTSMap(\'../service/wmts\',\''+layerName+'\')" />'			
					var $input = $(str);
					$input.appendTo($("body"));
				}
				

			}
	}
	
	// Get the Index of the Layer in the layer set which has mapServiceName as name
	function getLayerIdx(Layer,mapServiceName)
	{
		for (var i=0;i<Layer.length;i++) {
			var layerName =getNameInNodeList(Layer,i,"OWS:IDENTIFIER");
			var sublayers = $(Layer[i]).find("Layer");
			if (sublayers.length ==0 &&  layerName==mapServiceName)
				return i;	
		}
		return -1
	}
	
	// Get the projection
	function getProjection(capabilitiesResponse,myTileMatrixSet)
	{
		var TileMatrixSet = $(capabilitiesResponse).find("TileMatrixSet");
		for (var i=0;i<TileMatrixSet.length;i++) {
			var TileMatrixSetName =getNameInNodeList(TileMatrixSet,i,"OWS:IDENTIFIER");
			if (TileMatrixSetName==myTileMatrixSet)
			{
				var supportedCRS =getNameInNodeList(TileMatrixSet,i,"OWS:SUPPORTEDCRS");
				var codeEPSG = supportedCRS.split(':');
				return epsgCode = "EPSG:" + codeEPSG[codeEPSG.length-1];
			}
		}
		// Usual Default for Tile
		return  "EPSG:3857";
	}
	
	// retourne le nombre de TileSize
	function getTileSizes(capabilitiesResponse,myTileMatrixSet)
	{
		var TileSizeList =[];
		var TileMatrixSet = $(capabilitiesResponse).find("TileMatrixSet");
		for (var i=0;i<TileMatrixSet.length;i++) {
			var TileMatrixSetName =getNameInNodeList(TileMatrixSet,i,"OWS:IDENTIFIER");
			if (TileMatrixSetName==myTileMatrixSet)
			{
			
			    var tms = $(TileMatrixSet[i]).find('TileWidth');
				for (var k=0;k<tms.length;k++)
				{
					var b = parseInt($(tms[k]).text());
					TileSizeList.push(b);
				}
			}
		}
	
		return  TileSizeList;
	}
	
		// retourne les origins
	function getOrigines(capabilitiesResponse,myTileMatrixSet)
	{
		var origins =[];
		var TileMatrixSet = $(capabilitiesResponse).find("TileMatrixSet");
		for (var i=0;i<TileMatrixSet.length;i++) {
			var TileMatrixSetName =getNameInNodeList(TileMatrixSet,i,"OWS:IDENTIFIER");
			if (TileMatrixSetName==myTileMatrixSet)
			{
			
			    var tms = $(TileMatrixSet[i]).find('TopLeftCorner');
				for (var k=0;k<tms.length;k++)
				{
					var ori = $(tms[k]).text().split(' ');
					var left = parseFloat(ori[0]);
					var top = parseFloat(ori[1]);
					origins.push([left,top]);
				}
			}
		}
	
		return  origins;
	}
	
	// decode GetCapabilities and create the map, view and layer
	function loadMapFromParameter(capabilitiesResponse,uRLServer,mapServiceName)
	{
		var Layer = $(capabilitiesResponse).find("Layer");
		var i = getLayerIdx(Layer,mapServiceName);
		var myFormat = $(Layer[i]).find("Format").text();		
		var myTileMatrixSet = $(Layer[i]).find("TileMatrixSet").text();			
		var TileMatrix = $(Layer[i]).find("TileMatrix");
	
		var TileMatrixIds = [];
		for (var t=0;t<TileMatrix.length;t++)
			{
				TileMatrixIds.push(	$(TileMatrix[t]).text());
			}
		
		var i = getLayerIdx(Layer,mapServiceName);
		var epsg_projection = getProjection(capabilitiesResponse,myTileMatrixSet);
		var projection = ol.proj.get(epsg_projection);
		var projectionExtent = projection.getExtent();
		var size = ol.extent.getWidth(projectionExtent) / 256;
		var nbReso = TileMatrixIds.length;
		var resolutions = new Array(nbReso);
		var matrixIds = new Array(nbReso);
		for (var z = 0; z < nbReso; ++z) {
			// generate resolutions and matrixIds arrays for this WMTS
			resolutions[z] = size / Math.pow(2, z);
		}
		var tileSizeListe = getTileSizes(capabilitiesResponse,myTileMatrixSet);
		var origines = getOrigines(capabilitiesResponse,myTileMatrixSet);
		console.log (origines);
		 var view = new ol.View({

		});
		
		var myLayer =  new ol.layer.Tile({
			  extent: projectionExtent,
			  source: new ol.source.WMTS({
				url: uRLServer ,
				layer: mapServiceName,
				matrixSet: myTileMatrixSet,
				format: myFormat,
				projection: projection,
				tileGrid: new ol.tilegrid.WMTS({
				  origins: origines,
				  resolutions: resolutions,
				  matrixIds: TileMatrixIds,
				  tileSizes:tileSizeListe
				}),
				style: 'default'
			  })
			});
		 
		  
		map = new ol.Map({
		target: 'map',
		renderer: 'canvas',
		view: view,
		layers: [myLayer],
		controls: [
					new ol.control.Zoom(),
					new ol.control.ScaleLine(),
					new ol.control.MousePosition({
									coordinateFormat: ol.coordinate.createStringXY(4),
									projection: 'EPSG:4326'})
				]
		});
		view.fitExtent(projectionExtent, map.getSize());
	//	view.setCenter(ol.proj.transform([10,10], 'EPSG:4326','EPSG:3857' ));
		view.setZoom(10);
	}
	
