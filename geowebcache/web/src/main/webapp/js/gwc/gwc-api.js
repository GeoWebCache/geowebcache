/*
 * 
 * 
 * Relevant ExtJS 4 documentation:
 * - http://all-docs.info/extjs4/docs/api/Ext.data.Model.html
 */

Ext.define('GWC.RestService', {
    
    config: {
        endpoint: '/geowebcache/rest',
        timeout: 60000,
        jobStore: null,
        logStore: null
    },
    
    constructor: function (config) {
    	this.initConfig(config);
    	
    	Ext.define('job', {
    		extend: 'Ext.data.Model',
    		fields: [
    		    {name: 'jobId',				type: 'long'},
    		    {name: 'layerName',			type: 'string'},
    		    {name: 'state',				type: 'string'},
    		    {name: 'timeSpent',			type: 'long'},
    		    {name: 'timeRemaining',		type: 'long'},
    		    {name: 'tilesDone',			type: 'long'},
    		    {name: 'tilesTotal',		type: 'long'},
    		    {name: 'failedTileCount',	type: 'long'},
    		    {name: 'warnCount',			type: 'long'},
    		    {name: 'errorCount',		type: 'long'},
    		    {name: 'bounds',			type: 'string'},
    		    {name: 'gridSetId',			type: 'string'},
    		    {name: 'srs',				type: 'int'},
    		    {name: 'threadCount',		type: 'int'},
    		    {name: 'zoomStart',			type: 'int'},
    		    {name: 'zoomStop',			type: 'int'},
    		    {name: 'format',			type: 'string'},
    		    {name: 'jobType',			type: 'string'},
    		    {name: 'throughput',		type: 'float'},
    		    {name: 'maxThroughput',		type: 'int'},
    		    {name: 'priority',			type: 'string'},
    		    {name: 'schedule',			type: 'string'},
    		    {name: 'runOnce',			type: 'boolean'},
    		    {name: 'filterUpdate',		type: 'boolean'},
    		    {name: 'parameters',		type: 'string'},
    		    {name: 'timeFirstStart',	type: 'date'},
    	        {name: 'timeLatestStart',	type: 'date'}
    	    ],
    	    proxy: {
    			type: 'rest',
    			url : this.endpoint + '/jobs.json',
                reader: {
                	root: 'jobs'
            	},
            	writer: {
            		root: 'job'
            	}
    		},
    		hasntRunYet: function () {
    			return (this.data.state == 'UNSET' || this.data.state == 'READY');
    		}
    			
    	});
    	
    	Ext.define('log', {
    		extend: 'Ext.data.Model',
    	    fields: [
    	        {name: 'jobLogId',  					type: 'long'},
    	    	{name: 'jobId',  						type: 'long'},
    	        {name: 'logLevel',						type: 'string'},
    	        {name: 'logTime',						type: 'date'},
    	        {name: 'logSummary',					type: 'string'},
    	        {name: 'logText',						type: 'string'}
    	    ],
    	    proxy: {
    			type: 'rest',
    			url : this.endpoint + '/jobs/0/logs.json',
                reader: {
        			root: 'logs'
        			// totalProperty: 'totalCount' // if we are doing pagination
    			}
    		}
    	});
    	
    	this.jobStore = new Ext.data.Store({
    	    model: 'job',
    	    pageSize: 0
    	});
    	this.jobStore.sort('timeFirstStart', 'desc');

    	this.logStore = new Ext.data.Store({
    	    model: 'log',
    	    pageSize: 0
    	});
    	this.logStore.sort('logTime', 'asc');

    	return this;
    },

	loadJobs: function () {
    	this.jobStore.load();
	},

	loadLogs: function (jobId) {
    	this.logStore.proxy.url = 'rest/jobs/' + jobId + '/logs.json';
    	this.logStore.load();
	},
	
	// Going to handle delete and update outside jobStore because it makes  
	// of assumptions on how to do the restful posts and deletes.
	addJob: function (rec, success, failure) {
		Ext.Ajax.request({ 
			url: this.endpoint + '/jobs.json', method: 'PUT',
			timeout: this.timeout,
			jsonData: Ext.JSON.encode({"job": rec.data }),
			success: success,
	    	failure: failure
		});
	},
	
	updateJob: function (rec, success, failure) {
		Ext.Ajax.request({ 
			url: this.endpoint + '/jobs/' + rec.data.jobId + '.json', method: 'POST',
			timeout: this.timeout,
			jsonData: Ext.JSON.encode({"job": rec.data }),
			success: success,
	    	failure: failure
		});
	},
	
	deleteJob: function (jobId, success, failure) {
		Ext.Ajax.request({ 
			url: this.endpoint + '/jobs/' + jobId + '.json', method: 'DELETE',
			timeout: this.timeout,
			success: success,
	    	failure: failure
		});
	}
});