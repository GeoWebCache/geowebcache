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

	var stopAction = Ext.create('Ext.Action', {
	    icon: 'images/stop.png', 
	    text: 'Stop Job',
	    disabled: true,
	    handler: function(widget, event) {
//	        var rec = grid.getSelectionModel().getSelection()[0];
//	        if (rec) {
//	            alert("Sell " + rec.get('company'));
//	        } else {
//	            alert('Please select a company from the grid');
//	        }
			alert("stop");
	    }
	});
	
	var deleteAction = Ext.create('Ext.Action', {
	    icon: 'images/delete.png', 
	    text: 'Delete Job',
	    disabled: true,
	    handler: function(widget, event) {
			var rec = joblist.getSelectionModel().getSelection()[0];
			if (rec) {
				gwc.getJobStore().remove(rec);
				gwc.deleteJob(rec.data.jobId);
			}
	    }
	});

	return Ext.create('Ext.menu.Menu', {
	    items: [
	        viewLogsAction,
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
                		contextMenu.items.get(1).disabled = (rec.data.state != "RUNNING");
                		contextMenu.items.get(2).disabled = (rec.data.state == "RUNNING");
                		if(rec.hasntRunYet()) {
                			contextMenu.items.get(2).text = "Cancel";
                		} else {
                			contextMenu.items.get(2).text = "Delete";
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
	            flex: 10,
	            hidden: true,
	            sortable: false
	        },{
	            text: "Job",
	            dataIndex: 'jobId',
	            flex: 10,
	            renderer: renderJob,
	            sortable: false
	        },{
	            text: "Priority",
	            dataIndex: 'priority',
	            width: 50,
	            align: "center",
	            sortable: true
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
	            width: 80,
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
                flex: 2,
                sortable: true
            },{
                text: "Summary",
                dataIndex: 'logSummary',
                flex: 2,
                sortable: false
            },{
                text: "Text",
                dataIndex: 'logText',
                flex: 6,
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
        }],
	    renderTo: 'joblist'
	});
	gwc.loadJobs();
});
