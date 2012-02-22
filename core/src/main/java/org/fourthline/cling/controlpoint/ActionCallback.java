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

package org.fourthline.cling.controlpoint;

import org.fourthline.cling.model.action.ActionException;
import org.fourthline.cling.model.action.ActionInvocation;
import org.fourthline.cling.model.message.UpnpResponse;
import org.fourthline.cling.model.message.control.IncomingActionResponseMessage;
import org.fourthline.cling.model.meta.LocalService;
import org.fourthline.cling.model.meta.RemoteService;
import org.fourthline.cling.model.meta.Service;
import org.fourthline.cling.protocol.sync.SendingAction;

import java.net.URL;

/**
 * Execute actions on any service.
 * <p>
 * Usage example for asynchronous execution in a background thread:
 * </p>
 * <pre>
 * Service service = device.findService(new UDAServiceId("SwitchPower"));
 * Action getStatusAction = service.getAction("GetStatus");
 * ActionInvocation getStatusInvocation = new ActionInvocation(getStatusAction);
 *
 * ActionCallback getStatusCallback = new ActionCallback(getStatusInvocation) {
 *
 *      public void success(ActionInvocation invocation) {
 *          ActionArgumentValue status  = invocation.getOutput("ResultStatus");
 *          assertEquals((Boolean) status.getValue(), Boolean.valueOf(false));
 *      }
 *
 *      public void failure(ActionInvocation invocation, UpnpResponse res) {
 *          System.err.println(
 *              createDefaultFailureMessage(invocation, res)
 *          );
 *      }
 * };
 *
 * upnpService.getControlPoint().execute(getStatusCallback)
 * </pre>
 * <p>
 * You can also execute the action synchronously in the same thread using the
 * {@link org.fourthline.cling.controlpoint.ActionCallback.Default} implementation:
 * </p>
 * <pre>
 * myActionInvocation.setInput("foo", bar);
 * new ActionCallback.Default(myActionInvocation, upnpService.getControlPoint()).run();
 * myActionInvocation.getOutput("baz");
 * </pre>
 *
 * @author Christian Bauer
 */
public abstract class ActionCallback implements Runnable {

    /**
     * Empty implementation of callback methods, simplifies synchronous
     * execution of an {@link org.fourthline.cling.model.action.ActionInvocation}.
     */
    public static final class Default extends ActionCallback {

        public Default(ActionInvocation actionInvocation, ControlPoint controlPoint) {
            super(actionInvocation, controlPoint);
        }

        @Override
        public void success(ActionInvocation invocation) {
        }

        @Override
        public void failure(ActionInvocation invocation, UpnpResponse operation, String defaultMsg) {

        }
    }

    public static interface ActionCallbackInterceptor {
    	public void preExecute(ActionInvocation actionInvocation);
    	public void postExecute(ActionInvocation actionInvocation);
    }

    protected final ActionInvocation actionInvocation;

    protected ControlPoint controlPoint;

    protected ActionCallback(ActionInvocation actionInvocation, ControlPoint controlPoint) {
        this.actionInvocation = actionInvocation;
        this.controlPoint = controlPoint;
    }

    protected ActionCallback(ActionInvocation actionInvocation) {
        this.actionInvocation = actionInvocation;
    }

    public ActionInvocation getActionInvocation() {
        return actionInvocation;
    }

    synchronized public ControlPoint getControlPoint() {
        return controlPoint;
    }

    synchronized public ActionCallback setControlPoint(ControlPoint controlPoint) {
        this.controlPoint = controlPoint;
        return this;
    }

    public void run() {
        Service service = actionInvocation.getAction().getService();
        ActionCallbackInterceptor actionCallbackInterceptor = actionInvocation.getAction().getActionCallbackInterceptor();

        // Local execution
        if (service instanceof LocalService) {
            LocalService localService = (LocalService)service;

            if(actionCallbackInterceptor != null) {
            	actionCallbackInterceptor.preExecute(actionInvocation);
            }

            // Executor validates input inside the execute() call immediately
            localService.getExecutor(actionInvocation.getAction()).execute(actionInvocation);

            if(actionCallbackInterceptor != null) {
            	actionCallbackInterceptor.postExecute(actionInvocation);
            }

            if (actionInvocation.getFailure() != null) {
                failure(actionInvocation, null);
            } else {
                success(actionInvocation);
            }

        // Remote execution
        } else if (service instanceof RemoteService){

            if (getControlPoint()  == null) {
                throw new IllegalStateException("Callback must be executed through ControlPoint");
            }

            RemoteService remoteService = (RemoteService)service;

            // Figure out the remote URL where we'd like to send the action request to
            URL controLURL = remoteService.getDevice().normalizeURI(remoteService.getControlURI());

            // Do it
            SendingAction prot = getControlPoint().getProtocolFactory().createSendingAction(actionInvocation, controLURL);
            
            if(actionCallbackInterceptor != null) {
            	actionCallbackInterceptor.preExecute(actionInvocation);
            }
            
            prot.run();
            
            if(actionCallbackInterceptor != null) {
            	actionCallbackInterceptor.postExecute(actionInvocation);
            }

            IncomingActionResponseMessage response = prot.getOutputMessage();

            if (response == null) {
                failure(actionInvocation, null);
            } else if (response.getOperation().isFailed()) {
                failure(actionInvocation, response.getOperation());
            } else {
                success(actionInvocation);
            }
        }
    }

    protected String createDefaultFailureMessage(ActionInvocation invocation, UpnpResponse operation) {
        String message = "Error: ";
        final ActionException exception = invocation.getFailure();
        if (exception != null) {
            message = message + exception.getMessage();
        }
        if (operation != null) {
            message = message + " (HTTP response was: " + operation.getResponseDetails() + ")";
        }
        return message;
    }

    protected void failure(ActionInvocation invocation, UpnpResponse operation) {
        failure(invocation, operation, createDefaultFailureMessage(invocation, operation));
    }

    /**
     * Called when the action invocation succeeded.
     *
     * @param invocation The successful invocation, call its <code>getOutput()</code> method for results.
     */
    public abstract void success(ActionInvocation invocation);

    /**
     * Called when the action invocation failed.
     *
     * @param invocation The failed invocation, call its <code>getFailure()</code> method for more details.
     * @param operation If the invocation was on a remote service, the response message, otherwise null.
     * @param defaultMsg A user-friendly error message generated from the invocation exception and response.
     * @see #createDefaultFailureMessage
     */
    public abstract void failure(ActionInvocation invocation, UpnpResponse operation, String defaultMsg);

    @Override
    public String toString() {
        return "(ActionCallback) " + actionInvocation;
    }
}
