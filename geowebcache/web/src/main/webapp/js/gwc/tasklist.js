Ext.require([
	'Ext.grid.*',
    'Ext.data.*',
    'Ext.util.*',
    'Ext.ModelManager'
]);

Ext.application({
    name: 'GWCTaskList',
    appFolder: 'js/gwc',
    gwc: null,
    grid: null,
    
    launch: function() {
		this.gwc = Ext.create('GWC.RestService');
		this.gwc.loadTasks();
		this.grid = this.createGrid();
    },
    
    /* renderers for various columns of the grid */
    renderState: function(value, p, record) {
    	state_img = 'state_gray.png'; // other states, UNSET and READY
    	
    	if(record.data.state == 'RUNNING') {
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
    	} else if(record.data.state == 'DEAD') {
    		state_img = 'state_red.png';
    	}
    	
	    return Ext.String.format(
	    	'<img src="images/{0}" title="{1}" />',
	        state_img,
	        record.data.state
	    );
	},
	
    renderTask: function(value, p, record) {
    	if(record.data.reseed && record.data.type == "SEED") {
    		type = "RESEED";
    	} else {
    		type = record.data.type;
    	}
    	
	    return Ext.String.format(
	    	this.taskTemplate,
	        type,
	        record.data.layerName
	    );
	},
	
    renderTime: function(value, p, record) {
		if(record.data.timeSpent == -1 || record.data.timeRemaining == -1) {
			return "n/a";
		} else {
			return Ext.String.format(
		    	"elapsed: {0}<br />to go: {1}",
		        formatSecondsElapsed(record.data.timeSpent),
		        formatSecondsElapsed(record.data.timeRemaining)
		    );
		}
	},

    renderTileCounts: function(value, p, record) {
		if(record.data.tilesDone == -1 || record.data.tilesTotal == -1) {
			return "too many to count";
		} else {
		    return Ext.String.format(
		    	"<div style='float: left;'><b>{0}%</b>&nbsp;</div>" + 
		    	"<div style='float: left;'>&nbsp;{1} of<br /> {2}</div>",
		    	Ext.Number.toFixed(record.data.tilesDone / record.data.tilesTotal, 2),
		        addCommas(record.data.tilesDone),
		        addCommas(record.data.tilesTotal)
		    );
		}
	},

	createGrid: function() {
		var g = Ext.create('Ext.grid.Panel', {
	        taskTemplate: loadTaskTemplate(),
	        title: 'Task List',
	        store: this.gwc.getTaskStore(),
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
	            text: "Task",
	            dataIndex: 'taskId',
	            flex: 20,
	            renderer: this.renderTask,
	            sortable: false
	        },{
	            text: "Priority",
	            dataIndex: 'priority',
	            width: 50,
	            align: "center",
	            sortable: true
	        },{
	            text: "Region",
	            dataIndex: 'region',
	            width: 100,
	            flex: 4,
	            sortable: false
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
	            dataIndex: 'threads',
	            width: 55,
	            align: "center",
	            sortable: true
	        },{
	            text: "Throughput",
	            dataIndex: 'throughput',
	            width: 80,
	            align: "center",
	            sortable: true
	        },{
	            text: "Schedule",
	            dataIndex: 'schedule',
	            width: 80,
	            align: "center",
	            sortable: true
	        }],
	        renderTo: 'tasklist'
	    });
		
		return g;
	}
});

var loadTaskTemplate = function() {
	result = null;
	Ext.Ajax.request({
		url: 'js/gwc/tasktemplate.html',
		success: function(response) {
    		result = response.responseText;
    	},
    	failure: function(response, opts) {
    		alert('Couldn\'t load task template: ' + response.status);
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
