package org.fourthline.cling.transport.impl;

import java.util.logging.Logger;

import org.fourthline.cling.model.message.gena.IncomingEventRequestMessage;
import org.fourthline.cling.model.meta.StateVariable;
import org.fourthline.cling.model.state.StateVariableValue;
import org.fourthline.cling.transport.spi.UnsupportedDataException;
import org.seamless.xml.XmlPullParserUtils;
import org.xmlpull.v1.XmlPullParser;

public class PullGENAEventProcessor extends GENAEventProcessorImpl {

	private static Logger log = Logger.getLogger(PullGENAEventProcessor.class.getName());

	public void readBody(IncomingEventRequestMessage requestMessage) throws UnsupportedDataException {

		checkRequestBodyValidity(requestMessage);

		// TODO: UPNP VIOLATION: Netgear 834DG DSL Router sends trailing spaces/newlines after last XML element, need to trim()
		String body = requestMessage.getBodyString().trim();

		try {
			XmlPullParser xpp = XmlPullParserUtils.createParser(body);
			readProperties(xpp, requestMessage);
		} catch (Exception ex) {
			throw new UnsupportedDataException("Can't transform message payload: " + ex.getMessage(), ex, body);	
		}
	}


	private void readProperties(XmlPullParser xpp, IncomingEventRequestMessage message) throws Exception {

		// we're inside the propertyset tag
		StateVariable[] stateVariables = message.getService().getStateVariables();
		
		// ignore all but the property tags
		
		int event;
		while((event = xpp.next()) != XmlPullParser.END_DOCUMENT) {
			if(event != XmlPullParser.START_TAG) continue;
			
			if(xpp.getName().equals("property")) {
				readProperty(xpp, message, stateVariables);
			} 
		}

	}


	private void readProperty(XmlPullParser xpp, IncomingEventRequestMessage message, StateVariable[] stateVariables) throws Exception  {
		// we're inside the property tag
	
		int event ;
		do {
			event = xpp.next();
			if(event == XmlPullParser.START_TAG) {

				String stateVariableName = xpp.getName();
				for (StateVariable stateVariable : stateVariables) {
					if (stateVariable.getName().equals(stateVariableName)) {
						log.fine("Reading state variable value: " + stateVariableName);
						String value = xpp.nextText();
						message.getStateVariableValues().add(new StateVariableValue(stateVariable, value));
						//log.info(String.format("%s => %s", stateVariables, value));
						break;
					}
				} 
			}

		} while(event != XmlPullParser.END_DOCUMENT && (event != XmlPullParser.END_TAG || !xpp.getName().equals("property")));

		
	}


}
