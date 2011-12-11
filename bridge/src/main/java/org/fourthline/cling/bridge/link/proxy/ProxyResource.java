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

package org.fourthline.cling.bridge.link.proxy;

import java.util.logging.Logger;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;

import org.fourthline.cling.bridge.BridgeWebApplicationException;
import org.fourthline.cling.bridge.link.EndpointResource;
import org.fourthline.cling.bridge.link.LinkResource;
import org.fourthline.cling.model.meta.LocalDevice;
import org.fourthline.cling.model.types.UDN;
import org.seamless.util.Exceptions;

/**
 * @author Christian Bauer
 */
@Path("/res/link/{EndpointId}/proxy")
public class ProxyResource extends LinkResource {

    final private static Logger log = Logger.getLogger(ProxyResource.class.getName());

    @GET
    @Path("/{UDN}")
    @Produces({MediaType.TEXT_XML, MediaType.APPLICATION_XML})
    public Response getProxyDeviceDescriptor(@PathParam("UDN") String udn) {
        EndpointResource resource = getRequestedEndpointResource();
        log.fine("Received get proxy descriptor for: " + resource.getModel() + ", udn: " + udn);
        
        String body = getUpnpService().getLinkManager().getDeviceDiscovery().getProxyDeviceDescriptor(udn);
        ResponseBuilder response;
        if(body == null) {
        	response = Response.status(Response.Status.NOT_FOUND);
        } else {
        	response = Response.status(Response.Status.OK);
        	response.entity(body);
        	response.header("EndpointId", resource.getModel());
        }
        return response.build();
    }


    @PUT
    @Path("/{UDN}")
    @Consumes({MediaType.TEXT_XML, MediaType.APPLICATION_XML})
    public Response storeProxy(String xml) {
        EndpointResource resource = getRequestedEndpointResource();
        log.fine("Received proxy combined descriptor for: " + resource.getModel());
        try {
            ProxyLocalDevice proxy =
                    getConfiguration().getCombinedDescriptorBinder().read(xml, resource.getModel());
            log.info("Received device proxy: " + proxy);
            getRegistry().addDevice(proxy);
        } catch (BridgeWebApplicationException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BridgeWebApplicationException(
                    Response.Status.INTERNAL_SERVER_ERROR,
                    "Proxy registration failed: " + Exceptions.unwrap(ex)
            );
        }
        return Response.status(Response.Status.OK).build();
    }

    @DELETE
    @Path("/{UDN}")
    public Response removeProxy() {
        EndpointResource resource = getRequestedEndpointResource();
        log.fine("Deleting proxy for : " + resource.getModel());
        try {
            UDN udn = getRequestedUDN();
            LocalDevice proxy = getRegistry().getLocalDevice(udn, true);
            if (proxy == null || !(proxy instanceof ProxyLocalDevice)) {
                throw new BridgeWebApplicationException(
                        Response.Status.NOT_FOUND,
                        "Proxy not found with UDN: " + udn
                );
            }
            log.fine("Deleting device proxy: " + proxy);
            getUpnpService().getRegistry().removeDevice(proxy);
        } catch (BridgeWebApplicationException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BridgeWebApplicationException(
                    Response.Status.INTERNAL_SERVER_ERROR,
                    "Proxy removal failed: " + Exceptions.unwrap(ex)
            );
        }
        return Response.status(Response.Status.OK).build();
    }

}
