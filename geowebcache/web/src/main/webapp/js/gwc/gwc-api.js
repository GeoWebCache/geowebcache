/*
 * 
 * 
 * Relevant ExtJS 4 documentation:
 * - http://all-docs.info/extjs4/docs/api/Ext.data.Model.html
 */

Ext.define('GWC.RestService', {
    
    config: {
        disableCaching: true,
        endpoint: 'http://localhost:8080/geowebcache/rest',
        timeout: 60000,
        taskStore: null
    },
    
    constructor: function(config) {
    	this.initConfig(config);
    	
    	Ext.define('Task', {
    		extend: 'Ext.data.Model',
    	    fields: [
    	        {name: 'taskId',  						type: 'long'},
    	        {name: 'doFilterUpdate',				type: 'boolean'},
    	        {name: 'state',							type: 'string'},
    	        {name: 'type',							type: 'string'},
    	        {name: 'reseed',						type: 'boolean'},
    	        {name: 'layerName',						type: 'string'},
    	        {name: 'timeSpent',						type: 'long'},
    	        {name: 'tilesTotal',					type: 'long'},
    	        {name: 'tilesDone',						type: 'long'},
    	        {name: 'failedTileCount',				type: 'long'},
    	        {name: 'priority',						type: 'string'},
    	        {name: 'threads',						type: 'long'},
    	        {name: 'timeRemaining',					type: 'long'}
    	    ],
    	    proxy: {
    			type: 'rest',
    			url : this.endpoint + '/tasks.json',
                reader: {
                	root: 'tasks'
                	// totalProperty: 'totalCount' // if we are doing pagination
            	}
    		}
    	});
    	
    	this.taskStore = new Ext.data.Store({
    	    model: 'Task',
    	    pageSize: 0
    	});

    	return this;
    },

	loadTasks: function(callback, failurecallback) {
    	this.taskStore.load();
	}	
});