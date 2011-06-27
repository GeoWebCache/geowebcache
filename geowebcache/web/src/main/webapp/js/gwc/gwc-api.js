Ext.define('GWC.RestService', {
    // extend: 'Ext.Window',
    
    config: {
        disableCaching: true,
        endpoint: 'http://localhost:8080/geowebcache/rest',
        timeout: 60000,
    },
    
    initComponent: function() {

    },

	getTaskList: function(callback, failurecallback) {
		Ext.Ajax.request({
		    url: this.endpoint + '/tasklist',
		    timeout: this.timeout,
		    disableCaching: this.disableCaching, 
		    method: 'GET',
		    success: function(response) {
		    	console.log('tasklist received');
		        var text = response.responseText;
		        callback(this);
		    },
		    failure: function(response, opts) {
		    	console.log('Failed to respond to getTaskList. Error code: ' + response.status);
		    	failurecallback(this, response);
		    }
		});
	}	
});