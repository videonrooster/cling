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

package org.fourthline.cling.bridge.gateway;

import org.fourthline.cling.bridge.BridgeServerResource;
import org.fourthline.cling.bridge.BridgeWebApplicationException;
import org.fourthline.cling.bridge.Constants;
import org.fourthline.cling.model.meta.Action;
import org.fourthline.cling.model.meta.Device;
import org.fourthline.cling.model.meta.Icon;
import org.fourthline.cling.model.meta.LocalDevice;
import org.fourthline.cling.model.meta.RemoteDevice;
import org.fourthline.cling.model.meta.RemoteService;
import org.fourthline.cling.model.meta.Service;
import org.fourthline.cling.model.types.ServiceId;
import org.seamless.xhtml.XHTMLElement;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import java.net.URI;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Logger;

import static org.seamless.xhtml.XHTML.ATTR;
import static org.seamless.xhtml.XHTML.ELEMENT;

/**
 * @author Christian Bauer
 */
public class GatewayServerResource extends BridgeServerResource {

    final private static Logger log = Logger.getLogger(GatewayServerResource.class.getName());

    protected ServiceId getRequestedServiceId() {
        return new ServiceId(
                getFirstPathParamValue(Constants.PARAM_SERVICE_ID_NS),
                getFirstPathParamValue(Constants.PARAM_SERVICE_ID)
        );
    }

    protected Device getRequestedDevice() {
        Device d = getRegistry().getDevice(getRequestedUDN(), false);
        if (d == null) {
            throw new BridgeWebApplicationException(Response.Status.NOT_FOUND);
        }
        return d;
    }

    protected RemoteDevice getRequestedRemoteDevice() {
        Device device = getRequestedDevice();
        if (device == null) {
            throw new BridgeWebApplicationException(Response.Status.NOT_FOUND);
        } else if (!(device instanceof RemoteDevice)) {
            throw new BridgeWebApplicationException(Response.Status.FORBIDDEN);
        }
        return (RemoteDevice) device;
    }

    protected Service getRequestedService() {
        Device device = getRequestedDevice();
        ServiceId sid = getRequestedServiceId();
        for (Service service : device.getServices()) {
            if (service.getServiceId().equals(sid)) {
                return service;
            }
        }
        throw new BridgeWebApplicationException(Response.Status.NOT_FOUND);
    }

    protected RemoteService getRequestedRemoteService() {
        Service service = getRequestedService();
        if (service == null) {
            throw new BridgeWebApplicationException(Response.Status.NOT_FOUND);
        } else if (!(service instanceof RemoteService)) {
            throw new BridgeWebApplicationException(Response.Status.FORBIDDEN);
        }
        return (RemoteService) service;
    }

    protected Action getRequestedAction() {
        Service service = getRequestedService();
        Action action = service.getAction(getFirstPathParamValue(Constants.PARAM_ACTION_NAME));
        if (action == null) {
            throw new BridgeWebApplicationException(Response.Status.NOT_FOUND);
        }
        return action;
    }



}
