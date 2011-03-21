package org.geowebcache.diskquota.rest;

import org.geowebcache.diskquota.DiskQuotaMonitor;
import org.restlet.Finder;
import org.restlet.data.Request;
import org.restlet.data.Response;
import org.restlet.resource.Resource;

public class DiskQuotaFinder extends Finder {

    private DiskQuotaMonitor monitor;

    public DiskQuotaFinder(final DiskQuotaMonitor monitor) {
        super(null, DiskQuotaConfigurationResource.class);
        this.monitor = monitor;
    }


    @Override
    public Resource findTarget(Request request, Response response) {
        DiskQuotaConfigurationResource resource;
        resource = (DiskQuotaConfigurationResource) super.findTarget(request, response);
        resource.setMonitor(monitor);
        return resource;
    }
}
