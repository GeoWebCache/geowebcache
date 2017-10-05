  
  var epsg_projection = 'undefined';
  var resolutions = [];
  var maxExtent = [];
  var tileSize = 256;
  var map = 'undefined'; 
  var response = 'undefined'; 
  var request = "SERVICE=WMS&VERSION=1.1.1&REQUEST=getcapabilities&TILED=true";
	
	
		
	// Request GeCapabilities 
	// Should use a promesis  instead 
	function GetVendorWMSCapabilities(url,param,mapServiceName,idx) {
		if (response == 'undefined')
		{
			var xhr = new XMLHttpRequest();
			xhr.open('GET', url+ "?" + param);
			xhr.onload = function (e) {
				response = xhr.response;
				console.log(response);
				if (idx==1)
					loadMapFromParameter(response,url,mapServiceName);
				else				
					getAllMapName(response);				
			};
			xhr.send();
			return ;
		}
		
		if (idx==1)
			loadMapFromParameter(response,url,mapServiceName);
		else				
			getAllMapName(response);			
		
	}
	
	// Call GetVendorWMSCapabilities
	// in order to find all layer
	function addAllMapNameFromWMS(url)
	{
		GetVendorWMSCapabilities(url,request,'',0);		
	}

	
	// Call GetVendorWMSCapabilities
	function loadMap(url,mapServiceName)
	{
		GetVendorWMSCapabilities(url,request,mapServiceName,1);		
	}
	
	// decode GetCapabilities and create the map, view and layer
	function getAllMapName(capabilitiesResponse)
	{
		var Layer = $(capabilitiesResponse).find("Layer");
		for (var i=0;i<Layer.length;i++) {
				var layersName = $(Layer[i]).find("Name").text();
				var sublayers = $(Layer[i]).find("Layer");
				if (sublayers.length ==0)
				{
				
				var str = '<input type="button" value="'+layersName + '" ';
				str += 'onclick="loadMap(\'../service/wms\',\''+layersName+'\')" />'
				var $input = $(str);
				$input.appendTo($("body"));
				}
				

			}
	}
	
	
	
	// decode GetCapabilities and create the map, view and layer
	function loadMapFromParameter(capabilitiesResponse,uRLServer,mapServiceName)
	{
		var tileSet = $(capabilitiesResponse).find("TileSet");
		for (var i=0;i<tileSet.length;i++) {
			if ($(tileSet[i]).find("Layers").text()==mapServiceName)
			{
				// Resolutions
				var arrayOfReso = $(tileSet[i]).find("Resolutions").text().split(' ');
				for (var j=0;j<arrayOfReso.length;j++) {
					var b = parseFloat(arrayOfReso[j]);
					if (b!=null && b!='undefined' && !isNaN(b))
						resolutions.push(b);	
				}
				// TileSize
				var wid = $(tileSet[i]).find("Width").text();
				var b = parseInt(wid);
				if (b!=null && b!='undefined' && !isNaN(b))
						tileSize=b;	
				
				
				// EPSG_RESOLUTION
				epsg_projection = $(tileSet[i]).find("SRS").text();
				
				// MaxExtent
				var BoundingBox = $(tileSet[i]).find("BoundingBox");
				var srs = BoundingBox.attr("SRS");
				var minx = parseFloat(BoundingBox.attr("minx"));
				var miny = parseFloat(BoundingBox.attr("miny"));
				var maxx = parseFloat(BoundingBox.attr("maxx"));
				var maxy = parseFloat(BoundingBox.attr("maxy"));
				
				if (!isNaN(minx) && !isNaN(miny) && !isNaN(maxx) && !isNaN(maxy))
				{
					if (srs != epsg_projection)
					{
						var minE = ol.proj.transform([minx, miny], srs, epsg_projection);
						minx = minE[0]; miny = minE[1]; 
						var maxE = ol.proj.transform([maxx, maxx], srs, epsg_projection);
						maxx = maxE[0]; maxy = maxE[1]; 
					}
					maxExtent.push(minx);
					maxExtent.push(miny);	
					maxExtent.push(maxx);
					maxExtent.push(maxy);						
				}
			}
		}
		
		var myOrigin = maxExtent.slice(0,2);
		
		console.log(maxExtent,epsg_projection,resolutions,tileSize,myOrigin);
		var view = new ol.View({
			projection: epsg_projection,
			resolutions: resolutions,
			
		});
		
		var myLayer = new ol.layer.Tile({
			source: new ol.source.TileWMS({
					url: uRLServer,
					params: {
					'LAYERS': mapServiceName,
					'VERSION': '1.1.1'
					},
				tileGrid: new ol.tilegrid.TileGrid({
					resolutions: resolutions,
					origin: myOrigin,
					tileSize : tileSize
				}),
				projection: epsg_projection,
				extent: maxExtent
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
									projection: epsg_projection})
				]
		});

		view.fitExtent(maxExtent, map.getSize());
		view.setResolution(resolutions[0]);
		
	}
	
	