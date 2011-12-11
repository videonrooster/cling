package org.fourthline.cling.android.alternate;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.logging.Logger;

import org.fourthline.cling.transport.impl.NetworkAddressFactoryImpl;
import org.fourthline.cling.transport.spi.InitializationException;

public class AndroidNetworkAddressFactory extends NetworkAddressFactoryImpl {

	final private static Logger log = Logger.getLogger(AndroidUpnpServiceConfiguration.class.getName());

	final private static int VERSION_CODE_GINGERBREAD = 9;


	public AndroidNetworkAddressFactory(int streamListenPort) {
		super(streamListenPort);
	}

	@Override
	protected boolean isUsableNetworkInterface(NetworkInterface iface) throws Exception {

		if(android.os.Build.VERSION.SDK_INT >= VERSION_CODE_GINGERBREAD && !iface.isUp()) {
			log.finer("Skipping network interface (down): " + iface.getDisplayName());
			return false;
		}


		if (getInetAddresses(iface).size() == 0) {
			log.finer("Skipping network interface without bound IP addresses: " + iface.getDisplayName());
			return false;
		}

		if (android.os.Build.VERSION.SDK_INT >= VERSION_CODE_GINGERBREAD && iface.isLoopback()) {
			log.finer("Skipping network interface (ignoring loopback): " + iface.getDisplayName());
			return false;
		}

		return true;
	}

	@Override
	protected void displayInterfaceInformation(NetworkInterface netint) throws SocketException {
		if(android.os.Build.VERSION.SDK_INT >= VERSION_CODE_GINGERBREAD) {
			super.displayInterfaceInformation(netint);
		}
	}

	@Override
	protected void discoverNetworkInterfaces() throws InitializationException {
		super.discoverNetworkInterfaces();
		if(networkInterfaces.size() > 1) {
			NetworkInterface iface = networkInterfaces.get(0);
			networkInterfaces.clear();
			networkInterfaces.add(iface);
			log.warning("found several network interfaces, keeping only the first one");
		}
	}

	@Override
	public byte[] getHardwareAddress(InetAddress inetAddress) {
		if(android.os.Build.VERSION.SDK_INT >= VERSION_CODE_GINGERBREAD) {
			try {
				NetworkInterface iface = NetworkInterface.getByInetAddress(inetAddress);
				return iface != null ? iface.getHardwareAddress() : null;
			} catch (SocketException ex) {
				// on Android we get sometimes "java.net.SocketException: No such device or address" when switching networks
				log.warning("cannot get hardware address for inet address: " + ex);
			}
		}
		return null;
	}


	@Override
	public InetAddress getLocalAddress(NetworkInterface networkInterface, boolean isIPv6, InetAddress remoteAddress) {

		/*
		if(android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.GINGERBREAD ) {
			// First try to find a local IP that is in the same subnet as the remote IP
			InetAddress localIPInSubnet = getBindAddressInSubnetOf(remoteAddress);
			if (localIPInSubnet != null) return localIPInSubnet;
		}
		 */

		// TODO: This is totally random because we can't access low level InterfaceAddress on Android!
		for (InetAddress localAddress : getInetAddresses(networkInterface)) {
			if (isIPv6 && localAddress instanceof Inet6Address)
				return localAddress;
			if (!isIPv6 && localAddress instanceof Inet4Address)
				return localAddress;
		}
		throw new IllegalStateException("Can't find any IPv4 or IPv6 address on interface: " + networkInterface.getDisplayName());        	
	}

}
