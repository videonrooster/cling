package org.fourthline.cling.model.message.header;


// do not validate input. In 90% of cases it will be invalid anyway.
public class UserAgentHeader extends UpnpHeader<String> {
	
	public UserAgentHeader() {
    }
	
	public UserAgentHeader(String s) {
        setValue(s);
    }

	@Override
	public void setString(String s) throws InvalidHeaderException {
		setValue(s);
	}

	@Override
	public String getString() {
		return getValue().toString();
	}

}
