/*
 * Copyright (C) 2010 Teleal GmbH, Switzerland
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of
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
import org.fourthline.cling.transport.SwitchableRouterImpl.RouterLockAcquisitionException;
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
 * Switches the network transport layer on/off by monitoring WiFi connectivity.
 * <p>
 * This implementation listens to connectivity changes in an Android environment. Register the
 * {@link #getBroadcastReceiver()} instance with intent <code>android.net.conn.CONNECTIVITY_CHANGE</code>.
 * </p>
 *
 * @author Christian Bauer
 */
public class AndroidSwitchableRouter extends SwitchableRouterImpl {

	private static Logger log = Logger.getLogger(Router.class.getName());

	final BroadcastReceiver _broadcastReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {

			if (!intent.getAction().equals(ConnectivityManager.CONNECTIVITY_ACTION)) return;
			
			
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

			NetworkInfo networkInfo = NetworkUtils.getConnectedNetworkInfo(context);
			if(networkInfo != null && (_networkInfo.getType() != networkInfo.getType() && networkInfo.isConnected())) {
				onNetworkTypeChange(_networkInfo, networkInfo);
				return ;
			}
		}
	};
	
	final private ConnectivityManager _connectivityManager;
	final private Context _context;
	
	// WiFi related
	final private WifiManager _wifiManager;
	private WifiManager.MulticastLock _multicastLock;
	private WifiManager.WifiLock _wifiLock;



	private NetworkInfo _networkInfo; 


	public NetworkInfo getNetworkInfo() {
		return _networkInfo;
	}

	private WifiManager.WifiLock createWiFiLock() {

		int wifiMode = WifiManager.WIFI_MODE_FULL;

		try {
			Field f = WifiManager.class.getField("WIFI_MODE_FULL_HIGH_PERF");
			wifiMode = f.getInt(null);
		} catch (Exception e) {
		}

		WifiManager.WifiLock wifiLock = _wifiManager.createWifiLock(wifiMode, getClass().getName()); // FIXME
		log.info("created wifi lock, mode: " + wifiMode);

		return wifiLock;
	}

	private void setWiFiMulticastLock(boolean enable) {
		if(_multicastLock == null) {
			_multicastLock = getWifiManager().createMulticastLock(getClass().getSimpleName());
		}

		if(enable) {
			if(_multicastLock.isHeld()) {
				log.warning("WiFi multicast lock already acquired");
			} else {
				log.info("WiFi multicast lock acquired");
				_multicastLock.acquire();	
			}
		} else {
			if(_multicastLock.isHeld()) {
				log.info("WiFi multicast lock released");
				_multicastLock.release();	
			} else {
				log.warning("WiFi multicast lock already released");
			}
		}
	}

	public void setWifiLock(boolean enable) {

		if(_wifiLock == null) {
			_wifiLock = createWiFiLock();
		}

		if (enable) {
			if (_wifiLock.isHeld()) {
				log.warning("WiFi lock already acquired");
			} else {
				log.info("WiFi lock acquired");
				_wifiLock.acquire();
			}
		} else {
			if (_wifiLock.isHeld()) {
				log.info("WiFi lock released");
				_wifiLock.release();
			} else {
				log.warning("WiFi lock already released");
			}
		}
	}

	// on first call, oldNetwork == null
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
			
			_networkInfo = newNetwork;
			if(enable()) {
				log.info(String.format("enabled router on network type change (new network: %s)", newNetwork.getTypeName()));
			}
		}
	}

	public AndroidSwitchableRouter(UpnpServiceConfiguration configuration, ProtocolFactory protocolFactory, Context context) throws InitializationException {
		super(configuration, protocolFactory);
		
		_context = context;
		
		_networkInfo = NetworkUtils.getConnectedNetworkInfo(context);
		if(_networkInfo == null) {
			throw new NoNetworkInitializationException();
		}

		_wifiManager = ((WifiManager) context.getSystemService(Context.WIFI_SERVICE));
		_connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
		
		_context.registerReceiver(_broadcastReceiver, new IntentFilter("android.net.conn.CONNECTIVITY_CHANGE"));
	}
	
	public void shutdown()  {
		super.shutdown();
		_context.unregisterReceiver(_broadcastReceiver);
	}
	
	public void start() {
		onNetworkTypeChange(null, _networkInfo);
		displayInterfacesInformation();
	}

	protected WifiManager getWifiManager() {
		return _wifiManager;
	}

	protected ConnectivityManager getConnectivityManager() {
		return _connectivityManager;
	}

	@Override
	public boolean enable() throws RouterLockAcquisitionException {
		lock(writeLock);
		try {
			boolean enabled;
			if ((enabled = super.enable())) {
				// Enable multicast on the WiFi network interface, requires android.permission.CHANGE_WIFI_MULTICAST_STATE

				if(_networkInfo.getType() == ConnectivityManager.TYPE_WIFI) {
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
	public void handleStartFailure(InitializationException ex) {
		/*
		if (_multicastLock != null && _multicastLock.isHeld()) {
			_multicastLock.release();
			_multicastLock = null;
		}
		 */


		super.handleStartFailure(ex);
	}

	@Override
	public boolean disable() throws RouterLockAcquisitionException {
		lock(writeLock);
		try {
			/*
			if (_multicastLock != null && _multicastLock.isHeld()) {
				_multicastLock.release();
				_multicastLock = null;
			}
			 */
			if(_networkInfo.getType() == ConnectivityManager.TYPE_WIFI) {
				setWiFiMulticastLock(false);
				setWifiLock(false);
			}
			return super.disable();
		} finally {
			unlock(writeLock);
		}
	}

	@Override
	protected int getLockTimeoutMillis() {
		return 15000;
	}

	public void enableWiFi() {
		log.info("enabling WiFi...");
		_wifiManager.setWifiEnabled(true);
		
	}
	
	//////////////////
	
	public boolean isMobileNetwork() {
		return NetworkUtils.isMobileNetwork(_networkInfo);
	}

	public boolean isWiFiNetwork() {
		return NetworkUtils.isWiFiNetwork(_networkInfo);
	}

	public boolean isEthNetwork() {
		return NetworkUtils.isEthNetwork(_networkInfo);
	}
	
	public boolean isSSDPEnabledNetwork() {
		return NetworkUtils.isSSDPEnabledNetwork(_networkInfo);
	}
	
	public static class NoNetworkInitializationException extends InitializationException {
		public NoNetworkInitializationException() {
			super("no connected network found");
		}
	}

	public void displayInterfacesInformation() {
		NetworkAddressFactory factory = getNetworkAddressFactory();
		
		if(factory instanceof NetworkAddressFactoryImpl) {
			((NetworkAddressFactoryImpl)factory).displayInterfacesInformation();
		} else {
			log.warning("cannot display network interfaces: router not enabled");
		}
		
	}

}
