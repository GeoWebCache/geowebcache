Ext.require([
	'Ext.grid.*',
    'Ext.data.*',
    'Ext.util.*',
    'Ext.ModelManager'
]);

Ext.application({
    name: 'GWCJobList',
    appFolder: 'js/gwc',
    gwc: null,
    grid: null,
    
    launch: function() {
		this.gwc = Ext.create('GWC.RestService');
		this.gwc.loadJobs();
		this.grid = this.createGrid();
    },
    
    /* renderers for various columns of the grid */
    renderState: function(value, p, record) {
    	state_img = 'state_gray.png'; // UNSET
    	
    	if(record.data.state == 'READY') {
    		state_img = 'state_lightgreen.png';
    	} else if(record.data.state == 'RUNNING') {
        	if(record.data.failedTileCount > 0) {
        		state_img = 'state_yellow.png';
        	} else {
        		state_img = 'state_green.png';
        	}
    	} else if(record.data.state == 'DONE') {
        	if(record.data.failedTileCount > 0) {
        		state_img = 'state_yellowblue.png';
        	} else {
        		state_img = 'state_blue.png';
        	}
    	} else if(record.data.state == 'INTERRUPTED') {
    		state_img = 'state_interrupted.png';
    	} else if(record.data.state == 'KILLED') {
    		state_img = 'state_red.png';
    	} else if(record.data.state == 'DEAD') {
    		state_img = 'state_black.png';
    	}
    	
	    return Ext.String.format(
	    	'<img src="images/{0}" title="{1}" />',
	        state_img,
	        record.data.state
	    );
	},
	
    renderJob: function(value, p, record) {
    	if(record.data.reseed && record.data.jobType == "SEED") {
    		jobType = "RESEED";
    	} else {
    		jobType = record.data.jobType;
    	}
    	
	    return Ext.String.format(
	    	this.jobTemplate,
	        jobType,
	        record.data.layerName,
	        formatMimeType(record.data.format)
	    );
	},
	
    renderRegion: function(value, p, record) {
	    return Ext.String.format(
	    	this.regionTemplate,
	    	record.data.zoomStart,
	    	record.data.zoomStop,
	        record.data.srs,
	        record.data.bounds
	    );
	},
	
    renderTime: function(value, p, record) {
		if(record.data.state == 'RUNNING') {
			if(record.data.timeSpent == -1 || record.data.timeRemaining == -1) {
				return "unknown";
			} else {
				return Ext.String.format(
			    	"elapsed: {0}<br />to go: {1}",
			        formatSecondsElapsed(record.data.timeSpent),
			        formatSecondsElapsed(record.data.timeRemaining)
			    );
			}
		} else {
			if(record.data.timeSpent == -1) {
				return "-";
			} else {
				return Ext.String.format(
			    	"elapsed: {0}",
			        formatSecondsElapsed(record.data.timeSpent)
			    );
			}
		}
	},

    renderTileCounts: function(value, p, record) {
		if(record.data.jobType == 'TRUNCATE') {
			return "-";
		} else if(record.data.tilesDone == -1 || record.data.tilesTotal == -1) {
			return "unknown";
		} else {
		    return Ext.String.format(
		    	"<div style='float: left;'><b>{0}%</b>&nbsp;</div>" + 
		    	"<div style='float: left;'>&nbsp;{1} of<br /> {2}</div>",
		    	Ext.Number.toFixed((record.data.tilesDone / record.data.tilesTotal) * 100, 2),
		        addCommas(record.data.tilesDone),
		        addCommas(record.data.tilesTotal)
		    );
		}
	},

    renderSchedule: function(value, p, record) {
		if(record.data.runOnce) {
			return Ext.String.format(
			    	"{0}<br />(once)",
			        record.data.schedule
			    );
		} else {
			return record.data.schedule;
		}
	},

	renderThroughput: function(value, p, record) {
		// alert(record.data.throughput);
		if(record.data.jobType == 'TRUNCATE') {
			return "-";
		} else if(record.data.state != 'UNSET' && record.data.state != 'READY') {
			tput = Ext.Number.toFixed(record.data.throughput, 1);
			var result = tput + " / sec";
			if(record.data.maxThroughput != -1) {
				result = result + "<br />(max " + record.data.maxThroughput + ")";
			}
			return result;
		} else {
			if(record.data.maxThroughput == -1) {
				return "no limit";
			} else {
				return "max " + record.data.maxThroughput + " / sec";
			}
		}
	},
	
	createGrid: function() {
		var g = Ext.create('Ext.grid.Panel', {
	        jobTemplate: loadJobTemplate(),
	        regionTemplate: loadRegionTemplate(),
	        title: 'Job List',
	        store: this.gwc.getJobStore(),
	        disableSelection: true,
	        loadMask: true,
	        viewConfig: {
	            id: 'gv',
	            trackOver: false,
	            stripeRows: true
	        },
	        // grid columns
	        columns:[{
	            text: "",
	            dataIndex: 'state',
	            width: 28,
	            align: "center",
	            renderer: this.renderState,
	            sortable: true
	        },{
	            text: "Job",
	            dataIndex: 'jobId',
	            flex: 10,
	            renderer: this.renderJob,
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
	            renderer: this.renderRegion,
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
	            renderer: this.renderTime,
	            align: "center",
	            sortable: true
	        },{
	            text: "Tiles",
	            dataIndex: 'tilesLeft',
	            flex: 3,
	            renderer: this.renderTileCounts,
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
	            renderer: this.renderThroughput,
	            align: "center",
	            sortable: true
	        },{
	            text: "Schedule",
	            dataIndex: 'schedule',
	            width: 80,
	            renderer: this.renderSchedule,
	            align: "center",
	            sortable: true
	        }],
	        renderTo: 'joblist'
	    });
		
		return g;
	}
});

var loadJobTemplate = function() {
	result = null;
	Ext.Ajax.request({
		url: 'js/gwc/jobtemplate.html',
		success: function(response) {
    		result = response.responseText;
    	},
    	failure: function(response, opts) {
    		alert('Couldn\'t load job template: ' + response.status);
    	},
    	async: false
	});
	return result;
};

var loadRegionTemplate = function() {
	result = null;
	Ext.Ajax.request({
		url: 'js/gwc/regiontemplate.html',
		success: function(response) {
    		result = response.responseText;
    	},
    	failure: function(response, opts) {
    		alert('Couldn\'t load region template: ' + response.status);
    	},
    	async: false
	});
	return result;
};

var formatSecondsElapsed = function(seconds) {
	result = "";
	
	if(Math.floor(seconds / 604800) > 1) { // weeks
		result = Math.floor(seconds / 604800) + " weeks ";
	} else if(Math.floor(seconds / 604800) > 0) {
		result = "1 week ";
	}
	
	if(Math.floor(seconds % 604800 / 86400) > 1) { // days
		result += Math.floor(seconds % 604800 / 86400) + " days ";
	} else if(Math.floor(seconds / 604800) > 0) {
		result += "1 day ";
	}

	hours = Math.floor(seconds % 86400 / 3600);
	minutes = Math.floor(seconds % 3600 / 60);
	seconds = seconds % 60;
	
	result = Ext.String.format("{0} {1}:{2}:{3}",
			result, 
			Ext.String.leftPad(hours, 2, '0'), 
			Ext.String.leftPad(minutes, 2, '0'), 
			Ext.String.leftPad(seconds, 2, '0'));
	
	return result;
};

var formatMimeType = function(mimeType) {
	var result = mimeType.replace("image/", "").toLowerCase();
	return result;
};

var addCommas = function(nStr) {
    nStr += '';
    x = nStr.split('.');
    x1 = x[0];
    x2 = x.length > 1 ? '.' + x[1] : '';
    var rgx = /(\d+)(\d{3})/;
    while (rgx.test(x1)) {
        x1 = x1.replace(rgx, '$1' + ',' + '$2');
    }
    return x1 + x2;
};
