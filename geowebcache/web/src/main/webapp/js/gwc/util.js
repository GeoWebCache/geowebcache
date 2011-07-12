var loadTemplate = function(templateName, templateUrl) {
	result = null;
	Ext.Ajax.request({
		url: templateUrl,
		success: function(response) {
    		result = response.responseText;
    	},
    	failure: function(response, opts) {
    		alert('Couldn\'t load ' + templateName + ' template: ' + response.status);
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
 