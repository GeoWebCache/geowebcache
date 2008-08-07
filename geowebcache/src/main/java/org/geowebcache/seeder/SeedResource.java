package org.geowebcache.seeder;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.RESTDispatcher;
import org.restlet.Context;
import org.restlet.data.MediaType;
import org.restlet.data.Request;
import org.restlet.data.Response;
import org.restlet.resource.Representation;
import org.restlet.resource.StringRepresentation;
import org.restlet.resource.Resource;
import org.restlet.resource.Variant; 

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.xml.DomDriver;
import java.io.IOException;
import java.util.concurrent.*;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import java.util.Iterator; 

public class SeedResource extends Resource {
    private static List statusList = Collections.synchronizedList(new ArrayList<String>());
    private static Log log = LogFactory
            .getLog(org.geowebcache.seeder.SeedResource.class);

    public SeedResource(Context context, Request request, Response response) {
        super(context, request, response);
        getVariants().add(new Variant(MediaType.TEXT_PLAIN));
    }
   
    public Representation getRepresentation(Variant variant){
        Representation rep = null; 
        if (variant.getMediaType().equals(MediaType.TEXT_PLAIN)){ 
            StringBuilder sb = new StringBuilder();
            List list = getStatusList();
            synchronized(list) {
                Iterator i = list.iterator();
                while (i.hasNext())
                   sb.append( i.next() + "\n"); 
            }
            rep = new StringRepresentation(sb);
        }
        return rep; 
    }

    @Override
    public void post(Representation entity) {
        log.info("Received seed request from  "
                + getRequest().getHostRef().getHostIdentifier());

        try {
            String xmltext = entity.getText();
            XStream xs = new XStream(new DomDriver());
            xs.alias("seedRequest", SeedRequest.class);
            xs.alias("format", String.class);

            SeedRequest rq = (SeedRequest) xs.fromXML(xmltext);
            
            Future<SeedTask> futureObject = getExecutor().submit(
                    new MTSeeder(new SeedTask(rq)));
            
            Object[] array = getExecutor().getQueue().toArray();
            System.out.println(array.length);
            for(int i=0; i< array.length; i++)
                System.out.println(array[i].toString());
            
            
            //this is just a simple test: asks the future object if it is done right after it was
            //submitted to the thread pool. Naturally it will return FALSE.
            //then the current thread sleeps for 1.5 seconds - enough time for the executor to complete
            //the task, which right now is just a printout of the seed request.
            //then future object is then asked again if it has completed, and indeed the return is TRUE
            
           // System.out.println(futureObject.isDone());
            
           // Thread.currentThread().sleep(1500L);
           // System.out.println(futureObject.isDone());
            
            
            
            //this is another simple test. the future object can be sent a cancel message. if this message
            //reaches the object before the executor begins execution, it will never execute.
            //else t will try to exit "gracefully" from execution
            
            /*
            futureObject.cancel(true);
            System.out.println(futureObject.isDone());
            System.out.println(futureObject.isCancelled());
            */
        } catch (IOException ioex) {
        } /*catch (InterruptedException iex) {
            iex.printStackTrace();
        } /*catch (ExecutionException execEx) {
            execEx.printStackTrace();
        }*/
    }

    public ThreadPoolExecutor getExecutor() {
        return RESTDispatcher.getExecutor();
    }
    
    public static List getStatusList() {
        return statusList;
    }
    
    public boolean allowPost() {
        return true;
    }

}
