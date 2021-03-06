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

package org.fourthline.cling.workbench.plugins.contentdirectory;

import org.fourthline.cling.model.meta.Service;
import org.fourthline.cling.model.types.ServiceType;
import org.fourthline.cling.model.types.UDAServiceType;
import org.fourthline.cling.workbench.spi.AbstractControlPointAdapter;

import javax.enterprise.inject.Instance;
import javax.inject.Inject;

/**
 * @author Christian Bauer
 */
public class ContentDirectoryControlPointAdapter extends AbstractControlPointAdapter {

    @Inject
    protected Instance<ContentDirectoryPresenter> contentDirectoryPresenterInstance;

    @Override
    protected ServiceType[] getSupportedServiceTypes() {
        return new ServiceType[]{new UDAServiceType("ContentDirectory", 1)};
    }

    @Override
    protected void onUseServiceRequest(Service service) {
        contentDirectoryPresenterInstance.get().init(service);
    }
}
