package org.fourthline.cling.android.alternate;

import java.util.logging.Logger;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

public class NetworkUtils {
	
	private final static int CONNECTIVITY_TYPE_WIMAX = 6; // Since API 8
	private final static int CONNECTIVITY_TYPE_ETHERNET = 9; // Since API 13
	
	private static Logger log = Logger.getLogger(NetworkUtils.class.getName());
	
	// return null if no connected network
		static public NetworkInfo getConnectedNetworkInfo(Context context) {

			ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

			NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
			if(networkInfo != null && networkInfo.isAvailable() && networkInfo.isConnected()) {
				return networkInfo;
			}

			log.warning("Android did not return an active network");

			networkInfo = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
			if(networkInfo != null && networkInfo.isAvailable() && networkInfo.isConnected()) return networkInfo;

			networkInfo = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);
			if(networkInfo != null && networkInfo.isAvailable() && networkInfo.isConnected()) return networkInfo;

			// WiMax (4G), since API level 8
			networkInfo = connectivityManager.getNetworkInfo(6);
			if(networkInfo != null && networkInfo.isAvailable() && networkInfo.isConnected()) return networkInfo;

			log.warning("could not find a connected network");

			return null;

		}
		
		static public boolean isEthNetwork(NetworkInfo networkInfo) {
			return isNetworkType(networkInfo, CONNECTIVITY_TYPE_ETHERNET);
		}

		static public boolean isSSDPEnabledNetwork(NetworkInfo networkInfo) {
			return isWiFiNetwork(networkInfo) || isEthNetwork(networkInfo);
		}
		
		static public boolean isWiFiNetwork(NetworkInfo networkInfo) {
			return isNetworkType(networkInfo, ConnectivityManager.TYPE_WIFI);
		}
		
		static public boolean isMobileNetwork(NetworkInfo networkInfo) {
			return isNetworkType(networkInfo, ConnectivityManager.TYPE_MOBILE) || isNetworkType(networkInfo, CONNECTIVITY_TYPE_WIMAX);
		}

		static public boolean isNetworkType(NetworkInfo networkInfo, int type) {
			return networkInfo != null && networkInfo.getType() == type;
		}

}
