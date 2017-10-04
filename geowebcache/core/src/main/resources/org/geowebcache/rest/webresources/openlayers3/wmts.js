
 
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
	
	// Get the limits of each tile matrix
	function getTileMatrixLimit(TileMatrixLimits,name)
	{
		var TileLimitList =[];
	
		for (var i=0;i<TileMatrixLimits.length;i++) {
			var myTileMatrix = $(TileMatrixLimits[i]).find("TileMatrix").text();	
			if (name==myTileMatrix)
			{
				var MinTileRow = $(TileMatrixLimits[i]).find("MinTileRow").text();	
				var MinTileCol = $(TileMatrixLimits[i]).find("MinTileCol").text();	
				var MaxTileRow = $(TileMatrixLimits[i]).find("MaxTileRow").text();	
				var MaxTileCol = $(TileMatrixLimits[i]).find("MaxTileCol").text();	
				
				TileLimitList = [MinTileRow,MinTileCol,MaxTileRow,MaxTileCol];
			}
		}
		return TileLimitList;
	}
	
	// Get the tileSize by level
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
	
	
	//Get the origins of each level
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
	
	 function TileToWGS84( xTile, yTile,  level)
        {
		   var m= Math.pow(2.0, level);
           var n = Math.PI - ((2.0 * Math.PI * yTile) / m);

           var lon = (xTile / m * 360.0) - 180.0;
           var lat = 180.0 / Math.PI * Math.atan(Math.sinh(n));
		   return [lon,lat]; 
        }
	
	// decode GetCapabilities and create the map, view and layer
	// for mapServiceName layer
	function loadMapFromParameter(capabilitiesResponse,uRLServer,mapServiceName)
	{
	
		// Get All Layers from GetCapabilities
		var Layers = $(capabilitiesResponse).find("Layer");
		
		// Retrieve my layer index from the selected name 
		var i = getLayerIdx(Layers,mapServiceName);
		
		
		var myFormat = $(Layers[i]).find("Format").text();		
		var TileMatrix = $(Layers[i]).find("TileMatrix");
	
		var TileMatrixIds = [];
		for (var t=0;t<TileMatrix.length;t++)
			{
				TileMatrixIds.push(	$(TileMatrix[t]).text());
			}
		
		var myTileMatrixSet = $(Layers[i]).find("TileMatrixSet").text();		
		var epsg_projection = getProjection(capabilitiesResponse,myTileMatrixSet);
		var projection = ol.proj.get(epsg_projection);
		var projectionExtent = projection.getExtent();
		
		
		var tileSizeListe = getTileSizes(capabilitiesResponse,myTileMatrixSet);
		var origines = getOrigines(capabilitiesResponse,myTileMatrixSet);
	
		// Retrieve 256 as TileSize in the GetCapabilities
		var widthE =ol.extent.getWidth(projectionExtent) ; 
		
		var nbReso = TileMatrixIds.length;
		var resolutions = new Array(nbReso);
		var matrixIds = new Array(nbReso);
		for (var z = 0; z < nbReso; ++z) {
			// generate resolutions and matrixIds arrays for this WMTS
			var size = widthE  / tileSizeListe[z];
			resolutions[z] = size / Math.pow(2, z);
		}
		//var resolutions=[132291.9312505292, 66145.9656252646, 26458.386250105836, 19843.789687579378, 13229.193125052918, 5291.677250021167, 2645.8386250105837, 1984.3789687579376, 1322.9193125052918, 529.1677250021168, 264.5838625010584, 132.2919312505292];
		

		
		
		
		/* Not Use Yet */ 
		var TileMatrixLimits = $(Layers[i]).find("TileMatrixLimits");
		getTileMatrixLimit(TileMatrixLimits,TileMatrixIds[1]);
		
		
	   
		var tileGrg  = new ol.tilegrid.WMTS({
					  origins: origines,
					  resolutions: resolutions,
					  matrixIds: TileMatrixIds,
					  tileSizes:tileSizeListe
					});
					
		var WMTSsource = new ol.source.WMTS({
					url: uRLServer ,
					layer: mapServiceName,
					matrixSet: myTileMatrixSet,
					format: myFormat,
					projection: projection,
					tileGrid: tileGrg,
					style: 'default'
				  });
				  
		var myLayer =  new ol.layer.Tile({
				  extent: projectionExtent,
				  source: WMTSsource
				});
		 
		
		var view = new ol.View({});
	
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
		
		// Get the coordinate for the tile 10,10 of the level 2 
		//var pt = TileToWGS84(10,10,2);
		//console.log(pt); 
		
		// Center the view on a point and a zoom 
		// How to be sure of the position seen on a tile. 
		view.setCenter(ol.proj.transform([0,0], 'EPSG:4326',epsg_projection ));
		view.setZoom(10);
	}
	
