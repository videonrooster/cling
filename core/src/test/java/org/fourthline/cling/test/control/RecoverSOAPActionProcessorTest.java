package org.fourthline.cling.test.control;

import org.fourthline.cling.binding.xml.ServiceDescriptorBinder;
import org.fourthline.cling.binding.xml.UDA10ServiceDescriptorBinderImpl;
import org.fourthline.cling.model.action.ActionInvocation;
import org.fourthline.cling.model.message.StreamResponseMessage;
import org.fourthline.cling.model.message.control.IncomingActionResponseMessage;
import org.fourthline.cling.model.meta.Action;
import org.fourthline.cling.model.meta.RemoteService;
import org.fourthline.cling.test.data.SampleData;
import org.fourthline.cling.transport.impl.RecoverSOAPActionProcessor;
import org.fourthline.cling.transport.spi.SOAPActionProcessor;
import org.seamless.util.io.IO;
import org.testng.annotations.Test;

public class RecoverSOAPActionProcessorTest {


	@Test
	public void uppercaseOutputArguments() throws Exception {
		SOAPActionProcessor processor = new RecoverSOAPActionProcessor() {

			@Override
			protected void onInvalidSOAP(ActionInvocation actionInvocation,	String xml, Exception e) {
			}
		};

		ServiceDescriptorBinder binder = new UDA10ServiceDescriptorBinderImpl();

		RemoteService service = SampleData.createUndescribedRemoteService();
		service = binder.describe(service, IO.readLines(getClass().getResourceAsStream("/test-svc-uda10-connectionmanager.xml")));
		
		Action action = service.getAction("GetProtocolInfo");

		ActionInvocation actionInvocation = new ActionInvocation(action);
		StreamResponseMessage response = new StreamResponseMessage(IO.readLines(getClass().getResourceAsStream("/brokenxml/soap/uppercase_args.xml")));
		
		processor.readBody(new IncomingActionResponseMessage(response), actionInvocation);
	}
}
