package org.fourthline.cling.model.meta;

public class DeviceXMLParseException extends DeviceException {
	
	String _xml;

	public DeviceXMLParseException(Device device, Exception e, String context, String xml) {
		super(device, e, context);
		_xml = xml;
	}
	
	@Override
	public String toString() {
		String s = super.toString();
		s += String.format("bad XML: %s", _xml);
		return s;
	}

}
