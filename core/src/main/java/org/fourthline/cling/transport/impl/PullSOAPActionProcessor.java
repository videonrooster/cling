package org.fourthline.cling.transport.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.fourthline.cling.model.action.ActionArgumentValue;
import org.fourthline.cling.model.action.ActionException;
import org.fourthline.cling.model.action.ActionInvocation;
import org.fourthline.cling.model.message.control.ActionRequestMessage;
import org.fourthline.cling.model.message.control.ActionResponseMessage;
import org.fourthline.cling.model.meta.ActionArgument;
import org.fourthline.cling.model.types.ErrorCode;
import org.fourthline.cling.transport.spi.UnsupportedDataException;
import org.seamless.xml.XmlPullParserUtils;
import org.xmlpull.v1.XmlPullParser;

public class PullSOAPActionProcessor extends SOAPActionProcessorImpl  {

	private static Logger log = Logger.getLogger(PullSOAPActionProcessor.class.getName());

	public void readBody(ActionRequestMessage requestMessage, ActionInvocation actionInvocation) throws UnsupportedDataException {

		checkActionMessageBodyValidity(requestMessage);

		// Trim may not be needed, do it anyway
		String body = requestMessage.getBodyString().trim();

		try {
			
			XmlPullParser xpp = XmlPullParserUtils.createParser(body);
			//readBodyElement(xpp);
			// ignore the Body element, get directly to the request 
			readBodyRequest(xpp, requestMessage, actionInvocation);


		} catch (Exception ex) {
			throw new UnsupportedDataException("Can't transform message payload: " + ex, ex, body);
		}

	}

	public void readBody(ActionResponseMessage responseMsg, ActionInvocation actionInvocation) throws UnsupportedDataException {

		checkActionMessageBodyValidity(responseMsg);
		
		String body = responseMsg.getBodyString().trim();
		
		try {
			
			XmlPullParser xpp = XmlPullParserUtils.createParser(body);
			readBodyElement(xpp);
			readBodyResponse(xpp, actionInvocation);

		} catch (Exception ex) {
			ex.printStackTrace();
			throw new UnsupportedDataException("Can't transform message payload: " + ex, ex, body);
		}
		
	}
	
	private void readBodyElement(XmlPullParser xpp) throws Exception {
		XmlPullParserUtils.searchTag(xpp, "Body");
	}

	private void readBodyRequest(XmlPullParser xpp,	ActionRequestMessage requestMessage,ActionInvocation actionInvocation) throws Exception {
		XmlPullParserUtils.searchTag(xpp, actionInvocation.getAction().getName());
		readActionInputArguments(xpp, actionInvocation);
	}

	private void readBodyResponse(XmlPullParser xpp, ActionInvocation actionInvocation) 	throws Exception {

		// we're in the "Body" tag
		int	event;
		do {
			event = xpp.next();

			if(event == XmlPullParser.START_TAG) {
				if(xpp.getName().equals("Fault")) {
					ActionException e = readFaultElement(xpp);
					actionInvocation.setFailure(e);
					return ;
				} else if(xpp.getName().equals(actionInvocation.getAction().getName() + "Response")) {
					readActionOutputArguments(xpp, actionInvocation);
					return ;
				}
			}

		} while(event != XmlPullParser.END_DOCUMENT && (event != XmlPullParser.END_TAG || !xpp.getName().equals("Body")));
	}

	protected void readActionInputArguments(XmlPullParser xpp,	ActionInvocation actionInvocation) throws Exception {
		actionInvocation.setInput(readArgumentValues(xpp, actionInvocation.getAction().getInputArguments()));
	}
	
	protected void readActionOutputArguments(XmlPullParser xpp,	ActionInvocation actionInvocation) throws Exception {
		actionInvocation.setOutput(readArgumentValues(xpp, actionInvocation.getAction().getOutputArguments()));
	}

	protected Map<String, String> getMatchingNodes(XmlPullParser xpp, ActionArgument[] args) throws Exception {

		List<String> names = new ArrayList<String>();
		for (ActionArgument argument : args) {
			names.add(argument.getName());
			names.addAll(Arrays.asList(argument.getAliases()));
		}

		Map<String, String>  matches = new HashMap<String, String>();

		String enclosingTag = xpp.getName();

		int event;
		do {
			event = xpp.next();
			if(event == XmlPullParser.START_TAG && names.contains(xpp.getName())) {
				if (names.contains(xpp.getName())) {
					matches.put(xpp.getName(), xpp.nextText());	
				}
			} 

		} while(event != XmlPullParser.END_DOCUMENT && (event != XmlPullParser.END_TAG || !xpp.getName().equals(enclosingTag)));

		if (matches.size() < args.length) {
			throw new ActionException(
					ErrorCode.ARGUMENT_VALUE_INVALID,
					"Invalid number of input or output arguments in XML message, expected " + args.length + " but found " + matches.size()
					);
		}
		return matches;
	}



	private  ActionArgumentValue[] readArgumentValues(XmlPullParser xpp, ActionArgument[] args)	throws Exception {

		// we're in the <ActionName>Response tag

		Map<String, String> matches = getMatchingNodes(xpp, args);

		ActionArgumentValue[] values = new ActionArgumentValue[args.length];

		for (int i = 0; i < args.length; i++) {

			ActionArgument arg = args[i];
			String value = findActionArgumentValue(matches, arg);
			if(value == null) {
				throw new ActionException(
						ErrorCode.ARGUMENT_VALUE_INVALID,
						"Could not find argument '" + arg.getName() + "' node");
			}

			log.fine("Reading action argument: " + arg.getName());
			values[i] = createValue(arg, value);
		}
		return values;

	}

	private String findActionArgumentValue(Map<String, String> entries,	ActionArgument arg) {
		for(Map.Entry<String, String> entry : entries.entrySet()) {
			if(arg.isNameOrAlias(entry.getKey())) return entry.getValue();
		}
		
		return null;
	}

/*
	 public static final String RESPONSE_FAILURE = "<?xml version=\"1.0\"?>\n" +
	            " <s:Envelope\n" +
	            "     xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\"\n" +
	            "     s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">\n" +
	            "   <s:Body>\n" +
	            "     <s:Fault>\n" +
	            "       <faultcode>s:Client</faultcode>\n" +
	            "       <faultstring>UPnPError</faultstring>\n" +
	            "       <detail>\n" +
	            "         <UPnPError xmlns=\"urn:schemas-upnp-org:control-1-0\">\n" +
	            "           <errorCode>611</errorCode>\n" +
	            "           <errorDescription>A test string</errorDescription>\n" +
	            "         </UPnPError>\n" +
	            "       </detail>\n" +
	            "     </s:Fault>\n" +
	            "   </s:Body>\n" +
	            " </s:Envelope>";*/


	private ActionException readFaultElement(XmlPullParser xpp) throws Exception {

		String errorCode = null;
		String errorDescription = null;

		// we're in the "Fault" tag

		XmlPullParserUtils.searchTag(xpp, "UPnPError");
		
		int event;
		do {
			event = xpp.next();
			if(event == XmlPullParser.START_TAG) {
				String tag = xpp.getName();
				if(tag.equals("errorCode")) {
					errorCode = xpp.nextText();
				} else if(tag.equals("errorDescription")) {
					errorDescription = xpp.nextText();
				}
			}
		} while(event != XmlPullParser.END_DOCUMENT && (event != XmlPullParser.END_TAG || !xpp.getName().equals("UPnPError")));
		
		if (errorCode != null) {
			try {
				int numericCode = Integer.valueOf(errorCode);
				ErrorCode standardErrorCode = ErrorCode.getByCode(numericCode);
				if (standardErrorCode != null) {
					log.fine("Reading fault element: " + standardErrorCode.getCode() + " - " + errorDescription);
					return new ActionException(standardErrorCode, errorDescription, false);
				} else {
					log.fine("Reading fault element: " + numericCode + " - " + errorDescription);
					return new ActionException(numericCode, errorDescription);
				}
			} catch (NumberFormatException ex) {
				throw new RuntimeException("Error code was not a number");
			}
		}
		
		throw new RuntimeException("Received fault element but no error code");

	}


}
