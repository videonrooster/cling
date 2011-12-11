package org.fourthline.cling.transport.impl;

import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.fourthline.cling.model.message.UpnpMessage;
import org.fourthline.cling.model.message.gena.IncomingEventRequestMessage;
import org.fourthline.cling.transport.spi.UnsupportedDataException;
import org.seamless.xml.XmlPullParserUtils;

public class RecoverGENAEventProcessor extends PullGENAEventProcessor {
	
	private static Logger log = Logger.getLogger(RecoverGENAEventProcessor.class.getName());

	public void readBody(IncomingEventRequestMessage requestMessage) throws UnsupportedDataException {

		checkRequestBodyValidity(requestMessage);
		
		try {
			super.readBody(requestMessage);
		} catch(UnsupportedDataException ex) {
			
			log.severe("bad GENA Event XML found: " + ex);
			
			// some properties may have been read by the pull parser at this point, so reset the list
			requestMessage.getStateVariableValues().clear();

			String fixedBody = fixXMLEncodedLastChange(XmlPullParserUtils.fixXMLEntities(requestMessage.getBodyString()));
			requestMessage.setBody(UpnpMessage.BodyType.STRING, fixedBody);

			try {
				super.readBody(requestMessage);
				log.info("sucessfully fixed bad GENA XML");
			} catch(UnsupportedDataException ex2) {
				
				// check if some properties were read
				if(requestMessage.getStateVariableValues().isEmpty()) {
					// throw the initial exception containing unmodified xml
					throw ex;
				}

				log.warning("partial read of GENA event properties (probably due to truncated XML)");
				
			}
		}
	}
	
	private String fixXMLEncodedLastChange(String xml) {
		Pattern pattern = Pattern.compile("<LastChange>(.*)</LastChange>", Pattern.DOTALL);
		Matcher matcher = pattern.matcher(xml);

		if (matcher.find() && matcher.groupCount() == 1) {

			String xmlEncodedLastChange = matcher.group(1);

			// delete funky characters (at least found in the Philips NP2900 that inserts garbage HTML)
			String fixedXmlEncodedLastChange = StringUtils.replaceChars(xmlEncodedLastChange, "<>", null);
			if(fixedXmlEncodedLastChange.equals(xmlEncodedLastChange)) {
				return xml;
			}
			
			log.warning("deleted invalid characters in LastChange");

			String fixedXml =
					"<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
							"<e:propertyset xmlns:e=\"urn:schemas-upnp-org:event-1-0\">" +
							"<e:property>" +
							"<LastChange>" +
							fixedXmlEncodedLastChange +
							"</LastChange>" +
							"</e:property>" +
							"</e:propertyset>";

			return fixedXml;

		}

		return xml;
	}

}
