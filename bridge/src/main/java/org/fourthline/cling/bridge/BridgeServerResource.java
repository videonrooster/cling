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

package org.fourthline.cling.bridge;

import org.fourthline.cling.controlpoint.ControlPoint;
import org.fourthline.cling.model.types.UDN;
import org.fourthline.cling.registry.Registry;
import org.seamless.xhtml.Body;
import org.seamless.xhtml.XHTML;
import org.seamless.xhtml.XHTMLElement;
import org.seamless.xhtml.XHTMLParser;

import javax.servlet.ServletContext;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;
import javax.xml.xpath.XPath;

/**
 * @author Christian Bauer
 */
public class BridgeServerResource {

    protected ServletContext servletContext;

    protected UriInfo uriInfo;

    public ServletContext getServletContext() {
        return servletContext;
    }

    @Context
    public void setServletContext(ServletContext servletContext) {
        this.servletContext = servletContext;
    }

    public UriInfo getUriInfo() {
        return uriInfo;
    }

    @Context
    public void setUriInfo(UriInfo uriInfo) {
        this.uriInfo = uriInfo;
    }

    protected BridgeUpnpService getUpnpService() {
        return (BridgeUpnpService)getServletContext().getAttribute(Constants.ATTR_UPNP_SERVICE);
    }

    protected Registry getRegistry() {
        return getUpnpService().getRegistry();
    }

    protected ControlPoint getControlPoint() {
        return getUpnpService().getControlPoint();
    }

    protected BridgeUpnpServiceConfiguration getConfiguration() {
        return getUpnpService().getConfiguration();
    }

    protected BridgeNamespace getNamespace() {
        return getUpnpService().getConfiguration().getNamespace();
    }

    protected String getFirstPathParamValue(String paramName) {
        MultivaluedMap<String, String> map = getUriInfo().getPathParameters();
        String value = map.getFirst(paramName);
        if (value == null) {
            throw new BridgeWebApplicationException(
                    Response.Status.INTERNAL_SERVER_ERROR,
                    "Desired path parameter value not found in request: " + paramName
            );
        }
        return value;
    }

    protected UDN getRequestedUDN() {
        return UDN.valueOf(getFirstPathParamValue(Constants.PARAM_UDN));
    }



}
