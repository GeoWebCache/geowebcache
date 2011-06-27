success = function(gwcService) {
	alert('success!');
}

failure = function(gwcService, response) {
	alert('failure!');
}

runtests = function() {
	gwc = Ext.create('GWC.RestService');
	gwc.setEndpoint('http://localhost:8080/geowebcache/rest');
	gwc.getTaskList(success, failure);
}

