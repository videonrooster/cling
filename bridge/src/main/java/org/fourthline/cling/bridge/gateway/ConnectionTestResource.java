package org.fourthline.cling.bridge.gateway;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;


@Path("/res/connectiontest")
public class ConnectionTestResource extends GatewayServerResource {

    @GET
    public Response doTest() {
       	return Response.status(Response.Status.OK).build();
    }
	
}
