/*
 * Copyright (C) 2011 4th Line GmbH, Switzerland
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 2 of
 * the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.fourthline.cling.android.alternate;

import java.lang.reflect.Field;
import java.util.logging.Logger;

import org.fourthline.cling.UpnpServiceConfiguration;
import org.fourthline.cling.protocol.ProtocolFactory;
import org.fourthline.cling.transport.Router;
import org.fourthline.cling.transport.SwitchableRouterImpl;
import org.fourthline.cling.transport.impl.NetworkAddressFactoryImpl;
import org.fourthline.cling.transport.spi.InitializationException;
import org.fourthline.cling.transport.spi.NetworkAddressFactory;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;

/**
 * Monitor all network connectivity changes, switching the router accordingly and passing network change info
 * to user of this class for further work. It store the current network state with convenience functions for the caller
 *
 * User of this class should call start() once after the Router has been initialized by UpnpService to get initial
 * state of the network
 *  
 * @author Michael Pujos
 */
public class AndroidSwitchableRouter extends SwitchableRouterImpl {

	private static Logger log = Logger.getLogger(Router.class.getName());
	
	public static class NoNetworkInitializationException extends InitializationException {
		public NoNetworkInitializationException() {
			super("no connected network found");
		}
	}
	
	final private ConnectivityManager connectivityManager;
	final private Context context;
	
	// WiFi related
	final private WifiManager wifiManager;
	private WifiManager.MulticastLock multicastLock;
	private WifiManager.WifiLock wifiLock;

	private NetworkInfo networkInfo;
	
	final BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {

			if (!intent.getAction().equals(ConnectivityManager.CONNECTIVITY_ACTION)) return;

			displayIntentInfo(intent);

			NetworkInfo newNetworkInfo = NetworkUtils.getConnectedNetworkInfo(context);
			if(newNetworkInfo != null && (networkInfo.getType() != newNetworkInfo.getType() && newNetworkInfo.isConnected())) {
				// maybe we should also notify no network connectivity changes (info == null)
				// but there is a short interval when this happens and is normal: going from WiFi <-> mobile
				onNetworkTypeChange(networkInfo, newNetworkInfo);
				return ;
			}
		}

		private void displayIntentInfo(Intent intent) {

			boolean noConnectivity = intent.getBooleanExtra(ConnectivityManager.EXTRA_NO_CONNECTIVITY, false);
			String reason = intent.getStringExtra(ConnectivityManager.EXTRA_REASON);
			boolean isFailover = intent.getBooleanExtra(ConnectivityManager.EXTRA_IS_FAILOVER, false);

			NetworkInfo currentNetworkInfo = (NetworkInfo) intent.getParcelableExtra(ConnectivityManager.EXTRA_NETWORK_INFO);
			NetworkInfo otherNetworkInfo = (NetworkInfo) intent.getParcelableExtra(ConnectivityManager.EXTRA_OTHER_NETWORK_INFO);

			log.info("CONNECTIVITY_ACTION");
			log.info("EXTRA_NO_CONNECTIVITY: " + noConnectivity);
			log.info("EXTRA_REASON: " + reason);
			log.info("EXTRA_IS_FAILOVER: " + isFailover);
			log.info("EXTRA_NETWORK_INFO: " + (currentNetworkInfo == null ? "none" : currentNetworkInfo));
			log.info("EXTRA_OTHER_NETWORK_INFO: " + (otherNetworkInfo == null ? "none" : otherNetworkInfo));
			log.info("EXTRA_EXTRA_INFO: " + intent.getStringExtra(ConnectivityManager.EXTRA_EXTRA_INFO));
		}

	};
	
	public AndroidSwitchableRouter(UpnpServiceConfiguration configuration, ProtocolFactory protocolFactory, Context context) throws InitializationException {
		super(configuration, protocolFactory);
		
		this.context = context;
		
		networkInfo = NetworkUtils.getConnectedNetworkInfo(context);
		if(networkInfo == null) {
			throw new NoNetworkInitializationException();
		}

		wifiManager = ((WifiManager) context.getSystemService(Context.WIFI_SERVICE));
		connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
		
		this.context.registerReceiver(broadcastReceiver, new IntentFilter("android.net.conn.CONNECTIVITY_CHANGE"));
	}
	

	public NetworkInfo getNetworkInfo() {
		return networkInfo;
	}
	
	
	public boolean isMobileNetwork() {
		return NetworkUtils.isMobileNetwork(networkInfo);
	}

	public boolean isWiFiNetwork() {
		return NetworkUtils.isWiFiNetwork(networkInfo);
	}

	public boolean isEthNetwork() {
		return NetworkUtils.isEthNetwork(networkInfo);
	}
	
	public boolean isSSDPEnabledNetwork() {
		return NetworkUtils.isSSDPEnabledNetwork(networkInfo);
	}
	
	public void displayInterfacesInformation() {
		NetworkAddressFactory factory = getNetworkAddressFactory();
		
		if(factory instanceof NetworkAddressFactoryImpl) {
			((NetworkAddressFactoryImpl)factory).displayInterfacesInformation();
		} else {
			log.warning("cannot display network interfaces: router not enabled");
		}
		
	}
	
	@Override
	protected int getLockTimeoutMillis() {
		return 15000;
	}
	
	private WifiManager.WifiLock createWiFiLock() {

		int wifiMode = WifiManager.WIFI_MODE_FULL;

		try {
			Field f = WifiManager.class.getField("WIFI_MODE_FULL_HIGH_PERF");
			wifiMode = f.getInt(null);
		} catch (Exception e) {
		}

		WifiManager.WifiLock wifiLock = wifiManager.createWifiLock(wifiMode, getClass().getSimpleName()); 
		log.info("created wifi lock, mode: " + wifiMode);

		return wifiLock;
	}

	private void setWiFiMulticastLock(boolean enable) {
		if(multicastLock == null) {
			multicastLock = wifiManager.createMulticastLock(getClass().getSimpleName());
		}

		if(enable) {
			if(multicastLock.isHeld()) {
				log.warning("WiFi multicast lock already acquired");
			} else {
				log.info("WiFi multicast lock acquired");
				multicastLock.acquire();	
			}
		} else {
			if(multicastLock.isHeld()) {
				log.info("WiFi multicast lock released");
				multicastLock.release();	
			} else {
				log.warning("WiFi multicast lock already released");
			}
		}
	}

	private void setWifiLock(boolean enable) {

		if(wifiLock == null) {
			wifiLock = createWiFiLock();
		}

		if (enable) {
			if (wifiLock.isHeld()) {
				log.warning("WiFi lock already acquired");
			} else {
				log.info("WiFi lock acquired");
				wifiLock.acquire();
			}
		} else {
			if (wifiLock.isHeld()) {
				log.info("WiFi lock released");
				wifiLock.release();
			} else {
				log.warning("WiFi lock already released");
			}
		}
	}

	// on first call, oldNetwork == null
	// can be overriden by subclasses to do additional work
	protected void onNetworkTypeChange(NetworkInfo oldNetwork, NetworkInfo newNetwork) {
		
		log.info(String.format("network type changed %s => %s", 
				oldNetwork == null ? ""     : oldNetwork.getTypeName(), 
				newNetwork == null ? "NONE" : newNetwork.getTypeName()));

		if(oldNetwork != null) {
			if(disable()) {
				log.info(String.format("disabled router on network type change (old network: %s)", oldNetwork.getTypeName()));	
			}
		}

		if(newNetwork != null) {
			networkInfo = newNetwork;
			if(enable()) {
				log.info(String.format("enabled router on network type change (new network: %s)", newNetwork.getTypeName()));
			}
		}
	}

	
	public void shutdown()  {
		super.shutdown();
		context.unregisterReceiver(broadcastReceiver);
	}
	
	// should be called by user of this class after Router is fully initialized by UpnpService to get the
	// first notification of network info state
	public void start() {
		onNetworkTypeChange(null, networkInfo);
	}
	
	public void enableWiFi() {
		log.info("enabling WiFi...");
		wifiManager.setWifiEnabled(true);
		
	}

	@Override
	public boolean enable() throws RouterLockAcquisitionException {
		lock(writeLock);
		try {
			boolean enabled;
			if ((enabled = super.enable())) {
				// Enable multicast on the WiFi network interface, requires android.permission.CHANGE_WIFI_MULTICAST_STATE

				if(networkInfo.getType() == ConnectivityManager.TYPE_WIFI) {
					setWiFiMulticastLock(true);
					setWifiLock(true);
				}
			}
			return enabled;
		} finally {
			unlock(writeLock);
		}
	}

	@Override
	public boolean disable() throws RouterLockAcquisitionException {
		lock(writeLock);
		try {
			if(networkInfo.getType() == ConnectivityManager.TYPE_WIFI) {
				setWiFiMulticastLock(false);
				setWifiLock(false);
			}
			return super.disable();
		} finally {
			unlock(writeLock);
		}
	}



}
