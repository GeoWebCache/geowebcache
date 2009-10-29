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
 *  @author David Winslow / The Open Planning Project 2008 
 */
package org.geowebcache.rest;

import org.restlet.data.MediaType;
import org.restlet.data.Status;
import org.restlet.resource.Representation;
import org.restlet.resource.StringRepresentation;

/**
 * An exception that specifies the Restlet representation and status code that
 * should be used to report it to the user.
 * 
 * @author David Winslow / OpenGeo
 */
public class RestletException extends RuntimeException {
    /**
     * 
     */
    private static final long serialVersionUID = 193659009533707359L;
    transient Status myStatus;
    Representation myRepresentation;

    /**
     * @param r The Representation to report this error to the user
     * @param stat The Status to report to the client
     * @param e The actual Exception that occurred
     */
    public RestletException(Representation r, Status stat, Exception e){
        super(e);
        init(r, stat);
    }
    /**
     * @param r The Representation to report this error to the user
     * @param stat The Status to report to the client
     */
    public RestletException(Representation r, Status stat){
        init(r, stat);
    }

    /**
     * @param s The message to report this error to the user (will report mimetype as text/plain)
     * @param stat The Status to report to the client
     * @param e The actual Exception that occurred
     */
    public RestletException(String s, Status stat, Exception e){
        super(e);
        init(new StringRepresentation(s + ":" + e.getMessage(), MediaType.TEXT_PLAIN), stat);
    }
    
    /**
     * @param s The message to report this error to the user (will report mimetype as text/plain)
     * @param stat The Status to report to the client
     */    
    public RestletException(String s, Status stat){
        init(new StringRepresentation(s, MediaType.TEXT_PLAIN), stat);
    }

    /**
     * Internal helper function so we can call the super constructor and still share initialization code within this class
     */
    private void init(Representation r, Status s){
        myRepresentation = r;
        myStatus = s;
    }

    /**
     * @return The Status associated with this exception
     */
    public Status getStatus(){
        return myStatus;
    }

    /**
     * @return the Representation associated with this exception
     */
    public Representation getRepresentation(){
        return myRepresentation;
    }
}