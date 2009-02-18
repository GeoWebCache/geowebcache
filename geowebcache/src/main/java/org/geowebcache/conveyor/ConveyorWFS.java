package org.geowebcache.conveyor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.storage.StorageBroker;

public class ConveyorWFS extends Conveyor {
    protected ConveyorWFS(StorageBroker sb, HttpServletRequest srq,
            HttpServletResponse srp) {
        super(sb, srq, srp);
        
        // TODO Auto-generated constructor stub
    }

    private static Log log = LogFactory.getLog(org.geowebcache.conveyor.ConveyorWFS.class);
    
}
