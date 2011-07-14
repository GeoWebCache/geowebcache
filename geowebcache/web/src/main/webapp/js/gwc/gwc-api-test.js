runtests = function() {
	var gwcService = Ext.create('GWC.RestService');
	testEstimate(gwcService);
};

testEstimate = function(gwcService) {
	var estimate = Ext.create('estimate', {
	    layerName :		'topp:states',
	    bounds :		'509701.31637505,-1346243.9324925,1776701.316375,-128243.93249254',
	    gridSetId : 	'EPSG:2163',
	    threadCount :	2,
	    zoomStart : 	0,
	    zoomStop : 		3,
	    tilesDone : 	0,
	    tilesTotal : 	0,
	    timeRemaining :	0,
	    timeSpent : 	0
	});

	gwcService.doEstimate(estimate, 
		function(response, opts) {
			alert("Success!\n" + response);
		},
		function(response, opts) {
			alert('Failed to perform estimate\n' + response.status + ': ' + response.responseText);
		}
	);
};

runtests();
