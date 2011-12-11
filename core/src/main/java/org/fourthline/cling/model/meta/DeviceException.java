package org.fourthline.cling.model.meta;


public class DeviceException extends Exception {

	
	private String _context;
	private Device _device;
	
	public DeviceException(Device device, Exception e, String context) {
		super(e);
		_context = context;
		_device = device;
	}
	
	private String toDeviceDetailsString() {
		if(_device == null) {
			return "No device details";
		}
		DeviceDetails details = _device.getDetails();
		String ret = "Manufacturer: " + details.getManufacturerDetails().getManufacturer() + "\n";
		
		ModelDetails model = details.getModelDetails();
		
		ret += "Model Description: " + model.getModelDescription() + "\n";
		ret += "Model Name: " + model.getModelName() + "\n";
		ret += "Model Number: " + model.getModelNumber() + "\n";
		
		return ret;
	}
	
	@Override
	public String toString() {
		String s = String.format("device exception: %s\ncontext: %s\n",
		                     (getCause() == null ? "none" : getCause().toString()),
		                     _context);
		
		s += toDeviceDetailsString();
		return s;

	}

	public Device getDevice() {
		return _device;
	}
}
