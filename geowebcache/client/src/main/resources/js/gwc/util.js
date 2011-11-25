var loadTemplate = function (templateName, templateUrl) {
	var result = null;
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

var formatSecondsElapsed = function (seconds) {
	var result = "";
	
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

	var hours = Math.floor(seconds % 86400 / 3600);
	var minutes = Math.floor(seconds % 3600 / 60);
	seconds = seconds % 60;
	
	result = result + " " + zeroPad(hours, 2) + ":" + zeroPad(minutes, 2) + ":" + zeroPad(seconds, 2);
	
	return result;
};

var formatByteString = function (bytes) {
	var sizes = ['Bytes', 'KiB', 'MiB', 'GiB', 'TiB', 'PiB', 'EiB', 'ZiB', 'YiB'];
	if (bytes == 0) return 'n/a';
	var i = parseInt(Math.floor(Math.log(bytes) / Math.log(1024)));
	return ((i == 0)? (bytes / Math.pow(1024, i)) : (bytes / Math.pow(1024, i)).toFixed(1)) + ' ' + sizes[i];
};

function zeroPad(num, count) {
	var numZeropad = num + '';
	while (numZeropad.length < count) {
		numZeropad = "0" + numZeropad;
	}
	return numZeropad;
}

var formatMimeType = function (mimeType) {
	var result = mimeType.replace("image/", "").toLowerCase();
	return result;
};

var addCommas = function (nStr) {
    nStr += '';
    var x = nStr.split('.');
    var x1 = x[0];
    var x2 = x.length > 1 ? '.' + x[1] : '';
    var rgx = /(\d+)(\d{3})/;
    while (rgx.test(x1)) {
        x1 = x1.replace(rgx, '$1' + ',' + '$2');
    }
    return x1 + x2;
};
 