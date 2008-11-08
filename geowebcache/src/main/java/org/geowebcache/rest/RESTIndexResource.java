/**
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * 
 * @author Arne Kepp / The Open Planning Project 2008 
 */
package org.geowebcache.rest;

import org.restlet.Context;
import org.restlet.data.MediaType;
import org.restlet.data.Request;
import org.restlet.data.Response;
import org.restlet.resource.Representation;
import org.restlet.resource.Resource;
import org.restlet.resource.StringRepresentation;
import org.restlet.resource.Variant;

/** 
 * This class just provides links to the actual resources
 */
public class RESTIndexResource extends Resource {
    
    public RESTIndexResource(Context context, Request request,
            Response response) {
        
        super(context, request, response);
        
        // Here we add the representation variants exposed
        getVariants().add(new Variant(MediaType.TEXT_HTML));
    }
    
    @Override
    public Representation getRepresentation(Variant variant) {
        Representation result = null;
        if (variant.getMediaType().equals(MediaType.TEXT_HTML)) {
            result = new StringRepresentation(
                    "<html><body>\n"
                    +"<a id=\"logo\" href=\"http://geowebcache.org\">"
                    +"<img src=\"http://geowebcache.org/trac/chrome/site/geowebcache_logo.png\" alt=\"\" height=\"100\" width=\"353\" border=\"0\"/></a>\n"
                    +"<h3>Resources available from here:</h3>"
                    +"<ul>"
                    +"<li><h4><a href=\"layers/\">layers</a></h4>"
                    +"Lets you see the configured layers. You can also view a specific layer "
                    +" by appending the name of the layer to the URL, DELETE an existing layer "
                    +" or POST a new one. Note that the latter operations only make sense when GeoWebCache"
                    +" has been configured through geowebcache.xml. You can POST either XML or JSON."
                    +"</li>\n"
                    +"<li><h4>seed</h4>"
                    +""
                    +"</li>\n"
                    +"</ul>"
                    +"</body></html>",
                    
                    MediaType.TEXT_HTML
            );
        }
        return result;
    }

    

}
