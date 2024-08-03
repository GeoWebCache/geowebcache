
window.onload = function() {

    function getValue(id) {
        return document.getElementById('hidden_' + id).value;
    }

    function ScaleControl(opt_options) {
        var options = opt_options || {};
        var element = document.createElement('div');
        element.setAttribute('id', 'scale');
        element.className = 'ol-scale-value';
        ol.control.Control.call(this, {
            element: element,
            target: options.target
        });
    };
    ol.inherits(ScaleControl, ol.control.Control);
    ScaleControl.prototype.setMap = function(map) {
        map.on('postrender', function() {
            var view = map.getView();
            var resolution = view.getResolution();
            var dpi = parseFloat(getValue('dpi'));
            var mpu = map.getView().getProjection().getMetersPerUnit();
            var scale = resolution * mpu * 39.37 * dpi;
            if (scale >= 9500 && scale <= 950000) {
                scale = Math.round(scale / 1000) + 'K';
            } else if (scale >= 950000) {
                scale = Math.round(scale / 1000000) + 'M';
            } else {
                scale = Math.round(scale);
            }
            document.getElementById('scale').innerHTML =  'Scale = 1 : ' + scale;
        }, this);
        ol.control.Control.prototype.setMap.call(this, map);
    }

    function ZoomControl(opt_options) {
        var options = opt_options || {};
        var element = document.createElement('div');
        element.setAttribute('id', 'zoom');
        element.className = 'ol-zoom-value';
        ol.control.Control.call(this, {
            element: element,
            target: options.target
        });
    };
    ol.inherits(ZoomControl, ol.control.Control);
    ZoomControl.prototype.setMap = function(map) {
        map.on('moveend', function() {
            var view = map.getView();
            document.getElementById('zoom').innerHTML =  'Zoom level = ' + view.getZoom();
        }, this);
        ol.control.Control.prototype.setMap.call(this, map);
    }

    var gridsetName = getValue('gridsetName');
    var gridNamesNumeric = getValue('gridNamesNumeric') === 'true' ? true : false ;
    var gridNames = JSON.parse(getValue('gridNames'));
    var baseUrl = '../service/wmts';
    var style = '';
    var format = getValue('format')
    var infoFormat = 'text/html';
    var layerName = getValue('layerName')
    var projection = new ol.proj.Projection({
        code: getValue('SRS'),
        units: getValue('unit'),
        axisOrientation: 'neu'
    });
    var resolutions = JSON.parse(getValue('resolutions'));

    if (getValue('isVector') == 'true') {
        var params = {
            'REQUEST': 'GetTile',
            'SERVICE': 'WMTS',
            'VERSION': '1.0.0',
            'LAYER': layerName,
            'STYLE': style,
            'TILEMATRIX': gridNamesNumeric ? '{z}' : gridsetName + ':{z}',
            'TILEMATRIXSET': gridsetName,
            'FORMAT': format,
            'TILECOL': '{x}',
            'TILEROW': '{y}'
        };

        function constructSource() {
            var url = baseUrl+'?'
            for (var param in params) {
                url = url + param + '=' + params[param] + '&';
            }
            url = url.slice(0, -1);

            var sourceParams = {
                url: url,
                projection: projection,
                tileGrid: new ol.tilegrid.WMTS({
                    tileSize: [
                        parseInt(getValue('tileWidth')),
                        parseInt(getValue('tileHeight'))
                    ],
                    origin: [
                        parseFloat(getValue('minX')),
                        parseFloat(getValue('maxY'))
                    ],
                    resolutions: resolutions,
                    matrixIds: gridNames
                }),
                wrapX: true
            };
            if (getValue('vtFormatName') == 'MVT') {
                sourceParams.format = new ol.format.MVT({});
            } else if (getValue('vtFormatName') == 'TopoJSON') {
                sourceParams.format = new ol.format.TopoJSON({});
            } else if (getValue('vtFormatName') == 'GeoJSON') {
                sourceParams.format = new ol.format.GeoJSON({});
            }
            var source = new ol.source.VectorTile(sourceParams);
            return source;
        }

        var layer = new ol.layer.VectorTile({
            source: constructSource()
        });
    } else {
        baseParams = ['VERSION','LAYER','STYLE','TILEMATRIX','TILEMATRIXSET','SERVICE','FORMAT'];

        var params = {
            'VERSION': '1.0.0',
            'LAYER': layerName,
            'STYLE': style,
            'TILEMATRIX': gridNames,
            'TILEMATRIXSET': gridsetName,
            'SERVICE': 'WMTS',
            'FORMAT': format
        };

        function constructSource() {
            var url = baseUrl+'?'
            for (var param in params) {
                if (baseParams.indexOf(param.toUpperCase()) < 0) {
                    url = url + param + '=' + params[param] + '&';
                }
            }
            url = url.slice(0, -1);

            var tileGridParameters = {
                tileSize: [
                    parseInt(getValue('tileWidth')),
                    parseInt(getValue('tileHeight'))
                ],
                extent: [
                    parseFloat(getValue('minX')),
                    parseFloat(getValue('minY')),
                    parseFloat(getValue('maxX')),
                    parseFloat(getValue('maxY'))
                ],
                resolutions: resolutions,
                matrixIds: params['TILEMATRIX']
            };
            if (getValue('fullGrid') == 'true') {
                tileGridParameters.origins = JSON.parse(getValue('origins'));
            } else {
                tileGridParameters.origin = JSON.parse(getValue('origin'));
            }
            var source = new ol.source.WMTS({
                url: url,
                layer: params['LAYER'],
                matrixSet: params['TILEMATRIXSET'],
                format: params['FORMAT'],
                projection: projection,
                tileGrid: new ol.tilegrid.WMTS(tileGridParameters),
                style: params['STYLE'],
                wrapX: true
            });
            return source;
        }

        var layer = new ol.layer.Tile({
            source: constructSource()
        });
    }

    var view = new ol.View({
        center: [0, 0],
        zoom: 2,
        resolutions: resolutions,
        projection: projection,
        extent: JSON.parse(getValue('bbox'))
    });

    var map = new ol.Map({
        controls: ol.control.defaults({attribution: false}).extend([
            new ol.control.MousePosition(),
            new ScaleControl(),
            new ZoomControl()
        ]),
        layers: [layer],
        target: 'map',
        view: view
    });
    map.getView().fit(JSON.parse(getValue('zoomBounds')), map.getSize());

    function setParam(name, value) {
        if (name == 'STYLES') {
            name = 'STYLE'
        }
        params[name] = value;
        layer.setSource(constructSource());
        map.updateSize();
    }

    var tooltip = document.getElementById('tooltip');
    var tooltipContent = document.getElementById('tooltip-content');
    var closeButton = document.getElementById('close-button');

    if (getValue('isVector') != 'true') {
        map.on('singleclick', function(evt) {
            document.getElementById('info').innerHTML = '';
    
            var source = layer.getSource();
            var resolution = view.getResolution();
            var tilegrid = source.getTileGrid();
            var tileResolutions = tilegrid.getResolutions();
            var zoomIdx, diff = Infinity;
    
            for (var i = 0; i < tileResolutions.length; i++) {
                var tileResolution = tileResolutions[i];
                var diffP = Math.abs(resolution-tileResolution);
                if (diffP < diff) {
                    diff = diffP;
                    zoomIdx = i;
                }
                if (tileResolution < resolution) {
                    break;
                }
            }
            var tileSize = tilegrid.getTileSize(zoomIdx);
            var tileOrigin = tilegrid.getOrigin(zoomIdx);
    
            var fx = (evt.coordinate[0] - tileOrigin[0]) / (resolution * tileSize[0]);
            var fy = (tileOrigin[1] - evt.coordinate[1]) / (resolution * tileSize[1]);
            var tileCol = Math.floor(fx);
            var tileRow = Math.floor(fy);
            var tileI = Math.floor((fx - tileCol) * tileSize[0]);
            var tileJ = Math.floor((fy - tileRow) * tileSize[1]);
            var matrixIds = tilegrid.getMatrixIds()[zoomIdx];
            var matrixSet = source.getMatrixSet();
    
            var url = baseUrl+'?'
            for (var param in params) {
                if (param.toUpperCase() == 'TILEMATRIX') {
                    url = url + 'TILEMATRIX='+matrixIds+'&';
                } else {
                    url = url + param + '=' + params[param] + '&';
                }
            }
    
            url = url
                + 'SERVICE=WMTS&REQUEST=GetFeatureInfo'
                + '&INFOFORMAT=' +  infoFormat
                + '&TileCol=' +  tileCol
                + '&TileRow=' +  tileRow
                + '&I=' +  tileI
                + '&J=' +  tileJ;
            document.getElementById('info').innerHTML =
                '<iframe seamless src="' + url.replace(/"/g, "&quot;") + '"></iframe>';
       });
    } else {
        var isPinned = false;
        
        function updateTooltipContent(properties) {
          tooltipContent.innerHTML = Object.keys(properties).map(function(key) {
            return key + ': ' + properties[key];
          }).join('<br>');
        }
        
        map.on('pointermove', function(evt) {
          if (isPinned) {
            return; // Do nothing if the tooltip is pinned
          }

          var pixel = evt.pixel;
          var feature = map.forEachFeatureAtPixel(pixel, function(feature) {
            return feature;
          });

          if (feature) {
            var coordinates = evt.coordinate;
            var properties = feature.getProperties();

            // Display the feature properties in the tooltip
            updateTooltipContent(properties);

            tooltip.style.left = (evt.originalEvent.clientX + 10) + 'px';
            tooltip.style.top = (evt.originalEvent.clientY + 10) + 'px';
            tooltip.style.display = 'block';
          } else {
            tooltip.style.display = 'none';
          }
        });

        map.on('singleclick', function(evt) {
          if (isPinned) {
            // Unpin the tooltip if it's already pinned
            isPinned = false;
            tooltip.style.display = 'none';
            tooltipContent.scrollTop = 0; // Reset scroll position to top
          } else {
            // Pin the tooltip
            var pixel = evt.pixel;
            var feature = map.forEachFeatureAtPixel(pixel, function(feature) {
              return feature;
            });

            if (feature) {
              var coordinates = evt.coordinate;
              var properties = feature.getProperties();

              // Display the feature properties in the tooltip
              updateTooltipContent(properties);

              tooltip.style.left = (evt.originalEvent.clientX + 10) + 'px';
              tooltip.style.top = (evt.originalEvent.clientY + 10) + 'px';
              tooltip.style.display = 'block';

              isPinned = true;
            }
          }
        });

        closeButton.addEventListener('click', function() {
          tooltip.style.display = 'none';
          isPinned = false;
        });
    }
    
    
    // set event handlers
    function paramHandler(event) {
        setParam(event.target.name, event.target.value);
    }
    var inputs = document.getElementsByTagName('select');
    for (var i = 0; i < inputs.length; i++) {
        inputs[i].onchange = paramHandler;
    }
    inputs = document.getElementsByTagName('input');
    for (i = 0; i < inputs.length; i++) {
        if (inputs[i].type == 'text') {
            inputs[i].onblur = paramHandler;
        }
    }

};
