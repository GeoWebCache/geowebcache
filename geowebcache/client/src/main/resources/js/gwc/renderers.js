/* renderers for various columns of the grid */
var renderState = function (value, p, record) {
	var state_img = 'state_gray.png'; // UNSET
	
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
};

var renderJob = function (value, p, record) {
	var jobType = "";
	if(record.data.reseed && record.data.jobType == "SEED") {
		jobType = "RESEED";
	} else {
		jobType = record.data.jobType;
	}
	
	var errorInfo = "";
	if(record.data.errorCount + record.data.warnCount != 0) {
		if(record.data.errorCount > 0) {
			errorInfo = "<img style=\"border: 0\" src=\"images/error.png\" title=\"Errors: " + record.data.errorCount + "\"/>";
		}
		if(record.data.warnCount > 0) {
			errorInfo += "<img style=\"border: 0\" src=\"images/warning.png\" title=\"Warnings: " + record.data.warnCount + "\"/>";
		}
	}
	
    return Ext.String.format(
    	this.jobTemplate,
    	record.data.jobId,
        jobType,
        record.data.layerName,
        formatMimeType(record.data.format),
        record.data.jobId,
        errorInfo
    );
};

var renderRegion = function (value, p, record) {
    return Ext.String.format(
    	this.regionTemplate,
    	record.data.zoomStart,
    	record.data.zoomStop,
        record.data.srs,
        record.data.bounds
    );
};

var renderTime = function (value, p, record) {
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
};

var renderTileCounts = function (value, p, record) {
	if(record.data.jobType == 'TRUNCATE') {
		return "-";
	} else if(record.data.tilesDone == -1 || record.data.tilesTotal == -1) {
		return "unknown";
	} else {
	    return Ext.String.format(
	    	"<b>{0}%</b> {1} of {2}",
	    	Ext.Number.toFixed((record.data.tilesDone / record.data.tilesTotal) * 100, 2),
	        addCommas(record.data.tilesDone),
	        addCommas(record.data.tilesTotal)
	    );
	}
};

var renderSchedule = function (value, p, record) {
	if(record.data.runOnce && record.data.schedule) {
		return "<img style=\"border: 0\" src=\"images/clock.png\" title=\"Run Once: " + record.data.schedule + "\"/>";
	} else if(record.data.schedule) {
		return "<img style=\"border: 0\" src=\"images/time.png\" title=\"Repeats: " + record.data.schedule + "\"/>";
	} else if(record.data.spawnedBy != -1) {
		return "<img style=\"border: 0\" src=\"images/time_link.png\" title=\"Spawned by scheduled job " + record.data.spawnedBy + "\"/>";
	} else {
		return "";
	}
};

var renderThroughput = function (value, p, record) {
	// alert(record.data.throughput);
	if(record.data.jobType == 'TRUNCATE') {
		return "-";
	} else if(record.data.state != 'UNSET' && record.data.state != 'READY') {
		var result = "";
		if(record.data.throughput == 0.0) {
			result = "";
		} else {
			var tput = Ext.Number.toFixed(record.data.throughput, 1);
			result = tput + " / sec";
		}
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
};

var renderLogLevel = function (value, p, record) {
	var imageFile = "information.png";
	if(record.data.logLevel == 'ERROR') {
		imageFile = "error.png";
	} else if(record.data.logLevel == 'WARN') {
		imageFile = "warning.png";
	}
	return "<img style=\"border: 0\" src=\"images/" + imageFile + "\" title=\"" + record.data.logLevel + "\"/>";
};

var renderWrapText = function (val, p, record) {
    return '<div style="white-space:normal !important;">'+ val +'</div>';
}
