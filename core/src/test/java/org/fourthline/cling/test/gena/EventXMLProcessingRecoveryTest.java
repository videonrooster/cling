package org.fourthline.cling.test.gena;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import org.fourthline.cling.binding.xml.ServiceDescriptorBinder;
import org.fourthline.cling.binding.xml.UDA10ServiceDescriptorBinderImpl;
import org.fourthline.cling.mock.MockUpnpService;
import org.fourthline.cling.model.gena.CancelReason;
import org.fourthline.cling.model.gena.RemoteGENASubscription;
import org.fourthline.cling.model.message.StreamRequestMessage;
import org.fourthline.cling.model.message.UpnpMessage.BodyType;
import org.fourthline.cling.model.message.UpnpResponse;
import org.fourthline.cling.model.message.gena.IncomingEventRequestMessage;
import org.fourthline.cling.model.message.gena.OutgoingEventRequestMessage;
import org.fourthline.cling.model.meta.RemoteService;
import org.fourthline.cling.model.state.StateVariableValue;
import org.fourthline.cling.model.types.UnsignedIntegerFourBytes;
import org.fourthline.cling.test.data.SampleData;
import org.fourthline.cling.transport.impl.RecoverGENAEventProcessor;
import org.fourthline.cling.transport.spi.GENAEventProcessor;
import org.seamless.util.io.IO;
import org.seamless.util.logging.LoggingUtil;
import org.seamless.util.logging.SystemOutLoggingHandler;
import org.seamless.xml.XmlPullParserUtils;
import org.testng.annotations.Test;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

public class EventXMLProcessingRecoveryTest {

	final private static Logger log = Logger.getLogger(EventXMLProcessingRecoveryTest.class.getName());

	Map<String, Object> values = new HashMap<String, Object>();
	
	static {
		LoggingUtil.resetRootHandler(new SystemOutLoggingHandler());
	}
	
	@Test
	public void brokenNonXMLEscapedLastChange() throws Exception {
		doTest("/brokenxml/propertyset/orange_liveradio.xml");
	}
	

	@Test
	public void brokenTruncatedXml() throws Exception {
		doTest("/brokenxml/propertyset/truncated.xml");
	}
	
	
	@Test
	public void brokenTerratecNoxon2() throws Exception {
		doTest("/brokenxml/propertyset/terratec_noxon2.xml");
	}
	
	@Test
	public void brokenDenon() throws Exception {
		doTest("/brokenxml/propertyset/denon_avr4306.xml");
	}
	
	
	@Test
	public void brokenPhilips() throws Exception {
		doTest("/brokenxml/propertyset/philips_np2900.xml");
	}
	

	@Test
	public void brokenMarantz() throws Exception {
		doTest("/brokenxml/propertyset/marantz_mcr603.xml");
	}


	private void doTest(String filename) throws Exception {
		
		GENAEventProcessor processor = new RecoverGENAEventProcessor();

		ServiceDescriptorBinder binder = new UDA10ServiceDescriptorBinderImpl();

		RemoteService service = SampleData.createUndescribedRemoteService();
		service = binder.describe(service, IO.readLines(getClass().getResourceAsStream("/test-svc-uda10-avtransport.xml")));
		
		RemoteGENASubscription subscription = new RemoteGENASubscription(service, 1800) {
			public void invalidXMLException(String xml, Exception e) {	}
			public void failed(UpnpResponse responseStatus) { }
			public void ended(CancelReason reason, UpnpResponse responseStatus) { }
			public void eventsMissed(int numberOfMissedEvents) { }
			public void established() {	}
			public void eventReceived() { }
			
		};
		subscription.receive(new UnsignedIntegerFourBytes(0), new ArrayList<StateVariableValue>());
		
		OutgoingEventRequestMessage outgoingCall =
				new OutgoingEventRequestMessage(subscription, SampleData.getLocalBaseURL());

		MockUpnpService upnpService = new MockUpnpService();
		upnpService.getConfiguration().getGenaEventProcessor().writeBody(outgoingCall);

		StreamRequestMessage incomingStream = new StreamRequestMessage(outgoingCall);

		IncomingEventRequestMessage message = new IncomingEventRequestMessage(incomingStream, service);
		message.setBody(BodyType.STRING, IO.readLines(getClass().getResourceAsStream(filename)));
		

		processor.readBody(message);
		
		boolean found = false;
		for(StateVariableValue varValue : message.getStateVariableValues()) {
			if(varValue.getStateVariable().getName().equals("LastChange") && varValue.getValue() != null) {
				found = true;
				String lastChange = (String)varValue.getValue();
				try {
					 parseLastChangeWithRecovery(lastChange);
				} catch(Exception e) {
					log.warning("could not parse LastChange fully: " + e);
				}

				assert !values.isEmpty();

				break;
			}
		}
		
		assert found;

	}
	
	private void parseLastChangeWithRecovery(String lastChange) throws Exception {
		try {
			parseLastChange(lastChange);
		} catch(XmlPullParserException e) {
			log.severe("error parsing LastChange: " + e);
			parseLastChange(XmlPullParserUtils.fixXMLEntities(lastChange));
		}
	}

	private void parseLastChange(String lastChange) throws Exception {
		
		// this parser is super lenient to handle borken LastChange
		
		XmlPullParser xpp = XmlPullParserUtils.createParser(lastChange);

		values.clear();
		
		xpp.nextTag();
		
		int event;
		while((event = xpp.next()) != XmlPullParser.END_DOCUMENT) {
			if(event != XmlPullParser.START_TAG) continue;
			
			String tag = xpp.getName();
			String value = xpp.getAttributeValue(null, "val");
			if(value == null) {
				log.warning(String.format("skipping Event name=%s for which there is no value", tag));
				continue;
			}
			values.put(tag, value);
			//log.info(tag + ": " + value);
		}

	}

}
