/*
 * Copyright (C) 2011 4th Line GmbH, Switzerland
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 2 of
 * the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.fourthline.cling.bridge.link;

import java.net.URL;

/**
 * @author Christian Bauer
 */
public class Endpoint {

    final protected String id;
    final protected URL callback;
    final protected Object userObject;

    public Endpoint(String id, URL callback) {
    	this(id, callback, null);
    }

    public Endpoint(String id, URL callback, Object userObject) {
        this.id = id;
        this.callback = callback;
    	this.userObject = userObject;
    }
    
    public Endpoint(String id) {
    	this(id, null);
    }

    
    public String getId() {
        return id;
    }

    public Object getUserObject() {
    	return userObject;
    	
    }
    
    public URL getCallback() {
        return callback;
    }

    public String getCallbackString() {
        String callbackURL = getCallback().toString();
        return (callbackURL.endsWith("/") ? callbackURL.substring(0, callbackURL.length()-1) : callbackURL);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Endpoint endpoint = (Endpoint) o;

        if (callback == null || !callback.equals(endpoint.callback)) return false;
        if (!id.equals(endpoint.id)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = id.hashCode();
        if(callback != null) {
        result = 31 * result + callback.hashCode();
        }
        return result;
    }

    @Override
    public String toString() {
        return "(" + getClass().getSimpleName() + ") ID: " + getId() + ", callback: " + (callback == null ? "none" : callback);
    }
}
