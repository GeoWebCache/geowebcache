Ext.require([
	'Ext.grid.*',
    'Ext.data.*',
    'Ext.util.*',
    'Ext.ModelManager'
]);

Ext.BLANK_IMAGE_URL = 'images/s.gif';

var joblist = null;
var gwc = null;

var showLogs = function (jobId) {
	var logGrid = Ext.create('GWC.JobLogGrid');
	logGrid.jobId = jobId;
	logGrid.refreshLogs();
    var logWindow = Ext.create('widget.window', {
        width: 800,
        height: 600,
        title: 'Logs for job ' + jobId,
        closable: true,
        plain: true,
        layout: 'fit',
        modal: true,
        tools: [{
            type:'refresh',
            tooltip: 'Refresh Logs',
            // hidden:true,
            handler: function(event, toolEl, panel){
        		logGrid.refreshLogs();
            }
        }],
        items: [logGrid]
	});
	logWindow.show();	
};

var showHelp = function () {
    var helpWindow = Ext.create('widget.window', {
        width: 800,
        height: 400,
        title: 'Job Manager Help',
        closable: true,
        plain: true,
        layout: 'fit',
        items: []
	});
	helpWindow.show();	
};

var setupMenu = function () {
	var viewLogsAction = Ext.create('Ext.Action', {
	    icon: 'images/logs.png', 
	    text: 'View Logs',
	    disabled: false,
	    handler: function(widget, event) {
			var rec = joblist.getSelectionModel().getSelection()[0];
			if (rec) {
				showLogs(rec.data.jobId);
			}
		}
	});

	var cloneAction = Ext.create('Ext.Action', {
	    icon: 'images/clone.png', 
	    text: 'Clone',
	    disabled: false,
	    handler: function(widget, event) {
			var rec = joblist.getSelectionModel().getSelection()[0];
			if (rec) {
				var newrec = rec.copy(-1); 
				newrec.set('jobId',-1);
				newrec.set('state','UNSET');
				newrec.set('timeSpent',-1);
				newrec.set('timeRemaining',-1);
				newrec.set('tilesDone',-1);
				newrec.set('tilesTotal',-1);
				newrec.set('failedTileCount',0);
				newrec.set('warnCount',0);
				newrec.set('errorCount',0);
				newrec.set('throughput',0);
				gwc.addJob(newrec, 
					function(response, opts) {
						gwc.loadJobs();
		    		},
		    		function(response, opts) {
		    			// an alert may be too confronting for an API to do
		    			alert('Failed to add job\n' + response.status + ': ' + response.responseText);
		    		}
				);
			}
	    }
	});
	
	var stopAction = Ext.create('Ext.Action', {
	    icon: 'images/stop.png', 
	    text: 'Stop',
	    disabled: true,
	    handler: function(widget, event) {
			var rec = joblist.getSelectionModel().getSelection()[0];
			if (rec) {
				var newrec = rec.copy(-1); 
				newrec.set('state','KILLED');
				gwc.updateJob(newrec, 
					function(response, opts) {
						gwc.loadJobs();
		    		},
		    		function(response, opts) {
		    			alert('Failed to stop job\n' + response.status + ': ' + response.responseText);
		    		}
				);
			}
		}
	});
	
	var deleteAction = Ext.create('Ext.Action', {
	    icon: 'images/delete.png', 
	    text: 'Delete',
	    disabled: true,
	    handler: function(widget, event) {
			var rec = joblist.getSelectionModel().getSelection()[0];
			if (rec) {
				gwc.deleteJob(rec.data.jobId,
						function(response, opts) {
							gwc.loadJobs();
			    		},
			    		function(response, opts) {
				    		alert('Failed to delete job ' + jobId + '\n' + response.status + ': ' + response.responseText);
			    		}
					);
			}
	    }
	});

	return Ext.create('Ext.menu.Menu', {
	    items: [
	        viewLogsAction,
	        cloneAction,
	        stopAction,
	        deleteAction
	    ]
	});
};

Ext.Loader.onReady(function () {
    
	Ext.define('GWC.JobGrid', {
		extend: 'Ext.grid.Panel',
		initComponent : function () {
	        this.jobTemplate = loadTemplate('Job Title', 'js/gwc/jobtemplate.html');
	        this.regionTemplate = loadTemplate('Region', 'js/gwc/regiontemplate.html');
	        this.title = 'Job List';
	        this.store = gwc.getJobStore();
	        this.disableSelection = false;
	        this.loadMask = true;
	        this.viewConfig = {
	            id: 'gv',
	            trackOver: false,
	            stripeRows: true,
	            listeners: {
                	itemcontextmenu: function (view, rec, node, index, e) {
	        			var contextMenu = setupMenu();
                    	e.stopEvent();
                		contextMenu.items.get(2).disabled = (rec.data.state != "RUNNING" || rec.data.jobType == "TRUNCATE");
                		contextMenu.items.get(3).disabled = (rec.data.state == "RUNNING");
                		if(rec.hasntRunYet() || rec.data.state == "RUNNING") {
                			contextMenu.items.get(1).icon = 'images/clone.png', 
                			contextMenu.items.get(1).text = "Clone";
                			contextMenu.items.get(3).text = "Cancel";
                		} else {
                			contextMenu.items.get(1).icon = 'images/rerun.png', 
                			contextMenu.items.get(1).text = "Rerun";
                			contextMenu.items.get(3).text = "Delete";
                		}
                    	contextMenu.showAt(e.getXY());
                    	return false;
                	}
	        	}
	        };

	        this.columns = [{
	            text: "",
	            dataIndex: 'state',
	            width: 28,
	            align: "center",
	            renderer: renderState,
	            sortable: true
	        },{
	            text: "Job ID",
	            dataIndex: 'jobId',
	            width: 28,
	            hidden: true,
	            sortable: false
	        },{
	            text: "Job",
	            dataIndex: 'jobId',
	            flex: 10,
	            renderer: renderJob,
	            sortable: false
	        },{
	            text: "Region",
	            dataIndex: 'bounds',
	            flex: 4,
	            align: "center",
	            renderer: renderRegion,
	            sortable: false
	        },{
	            text: "Start Time",
	            dataIndex: 'timeFirstStart',
	            flex: 3,
	            align: "center",
	            hidden: true,
	            sortable: true
	        },{
	            text: "Time",
	            dataIndex: 'timeRemaining',
	            flex: 3,
	            renderer: renderTime,
	            align: "center",
	            sortable: true
	        },{
	            text: "Tiles",
	            dataIndex: 'tilesLeft',
	            flex: 3,
	            renderer: renderTileCounts,
	            align: "center",
	            sortable: true
	        },{
	            text: "Threads",
	            dataIndex: 'threadCount',
	            width: 55,
	            align: "center",
	            sortable: true
	        },{
	            text: "Throughput",
	            dataIndex: 'maxThroughput',
	            width: 80,
	            renderer: renderThroughput,
	            align: "center",
	            sortable: true
	        },{
	            text: "Schedule",
	            dataIndex: 'schedule',
	            width: 55,
	            renderer: renderSchedule,
	            align: "center",
	            sortable: true
	        }];
	        this.callParent();
		}
    });
	
	Ext.define('GWC.JobLogGrid', {
		extend: 'Ext.grid.Panel',
		initComponent : function() {
	        this.disableSelection = true;
	        this.loadMask = true;
	        this.jobId = -1;
	        this.store = gwc.getLogStore();
	        this.viewConfig = {
	            id: 'lv',
	            trackOver: false,
	            layout: 'fit',
	            stripeRows: true
	        };

	        this.columns = [{
                text: "",
                dataIndex: 'logLevel',
                align: "center",
                width: 28,
                renderer: renderLogLevel,
                sortable: true
            },{
                text: "Timestamp",
                dataIndex: 'logTime',
                flex: 4,
                sortable: true
            },{
                text: "Summary",
                dataIndex: 'logSummary',
                flex: 5,
                sortable: false
            },{
                text: "Text",
                dataIndex: 'logText',
                flex: 13,
                renderer: renderWrapText,
                sortable: true
            }];
	        this.callParent();
		},
	    
		refreshLogs : function () {
			gwc.loadLogs(this.jobId);
	    }
	});

});

Ext.onReady(function () {
	gwc = Ext.create('GWC.RestService');
	joblist = new GWC.JobGrid({
        tools: [{
            type:'refresh',
            tooltip: 'Refresh Job List',
            handler: function(event, toolEl, panel){
        		gwc.loadJobs();
            }
        },{
            type:'help',
            tooltip: 'Get Help',
            handler: function(event, toolEl, panel){
        		showHelp();
            }
        }]
	});

	Ext.create('Ext.container.Viewport', {
	    layout: 'border',
	    renderTo: Ext.getBody(),
	    items: [{
	    	region: 'north',
		    border: 0,
	        xtype: 'panel',
	        items: {
	            html: '<a id="logo" href="home"><img src="rest/web/geowebcache_logo.png"height="70" width="247" border="0"/></a>'
	    	}
        },{
	    	region: 'center',
		    border: 0,
	        layout: 'fit',
	        xtype: 'panel',
	        items: [
	            joblist
	    	]
	    }]
	});
	
	gwc.loadJobs();
});
