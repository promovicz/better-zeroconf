package prom.android.zeroconf;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Enumeration;
import java.util.Map;
import java.util.Vector;

import javax.jmdns.JmDNS;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.DhcpInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.MulticastLock;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

public class ZeroConfService extends Service {

	public final static String TAG = ZeroConfService.class.toString();

	public final static String MULTICAST_LOCK_TAG = ZeroConfService.class.toString();


	WifiManager wifiManager;

	WifiStateListener wifiStateListener;

	MulticastLock multicastLock;

	JmDNS mDNS;

	Map<String, Type> allTypes;

	int allTypeSubscriptions;


	void startDiscovery() {
		Log.d(TAG, "Attempting to start discovery");

		if(mDNS != null) {
			Log.d(TAG, "Already running");
			return;
		}

		if(wifiManager.getWifiState() != WifiManager.WIFI_STATE_ENABLED) {
			Log.d(TAG, "Wifi state is not enabled");
			return;
		}

		WifiInfo connInfo = wifiManager.getConnectionInfo();
		if(connInfo == null) {
			Log.d(TAG, "Failed to get connection info");
			return;
		}

		DhcpInfo dhcpInfo = wifiManager.getDhcpInfo();
		if(dhcpInfo == null) {
			Log.d(TAG, "Failed to get dhcp info");
			return;
		}

		// XXX ugly
		int myRawAddress = connInfo.getIpAddress();
		byte[] myAddressBytes = new byte[] {
				(byte) (myRawAddress & 0xff),
				(byte) (myRawAddress >> 8 & 0xff),
				(byte) (myRawAddress >> 16 & 0xff),
				(byte) (myRawAddress >> 24 & 0xff)
		};

		InetAddress myAddress;
		try {
			myAddress = InetAddress.getByAddress(myAddressBytes);
		} catch (UnknownHostException e) {
			Log.d(TAG, "Failed to get address: " + e.toString());
			return;
		}

		Log.d(TAG, "My address is " + myAddress.toString());

		Log.d(TAG, "Acquiring multicast lock");
		multicastLock.acquire();

		Log.d(TAG, "Starting discovery on address " + myAddress);
		try {
			mDNS = JmDNS.create(myAddress);
		} catch (IOException e) {
			Log.d(TAG, "Failed to start discovery: " + e.toString());
			mDNS = null;
			multicastLock.release();
		}
	}

	void stopDiscovery() {
		Log.d(TAG, "Attempting to stop discovery");

		if(mDNS == null) {
			Log.d(TAG, "Discovery not running");
			return;
		}

		try {
			mDNS.close();
		} catch (IOException e) {
			// XXX do we care?
		}
		mDNS = null;

		Log.d(TAG, "Releasing multicast lock");
		multicastLock.release();
	}

	Type ensureType(String type) {
		if(!allTypes.containsKey(type)) {
			Type t = new Type(type);
			allTypes.put(type, t);
			return t;
		}
		return allTypes.get(type);
	}

	@Override
	public void onCreate() {
		super.onCreate();
		Log.d(TAG, "Creating service");

		Log.d(TAG, "Getting wifi manager");
		wifiManager = (WifiManager)getSystemService(WIFI_SERVICE);

		wifiStateListener = new WifiStateListener();

		Log.d(TAG, "Registering wifi state listener");
		registerReceiver(wifiStateListener,
				new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));

		Log.d(TAG, "Creating multicast lock");
		multicastLock = wifiManager.createMulticastLock(MULTICAST_LOCK_TAG);
		multicastLock.setReferenceCounted(true);

		startDiscovery();
	}

	@Override
	public void onDestroy() {
		Log.d(TAG, "Destroying service");

		stopDiscovery();

		if(wifiStateListener != null) {
			unregisterReceiver(wifiStateListener);
		}

		super.onDestroy();
	}

	@Override
	public IBinder onBind(Intent intent) {
		Log.d(TAG, "Binding connection " + intent);
		return new Connection(intent);
	}

	class Type {
		int subscriptions;
		String typeName;

		Type(String typeName) {
			this.typeName = typeName;
		}

		void subscribe() {
			subscriptions++;
		}

		void unsubscribe() {
			subscriptions--;
		}
	}

	class WifiStateListener extends BroadcastReceiver {
		@Override
		public void onReceive(Context context, Intent intent) {
			Log.d(TAG, "Received broadcast " + intent.toString());
			if(wifiManager.getWifiState() == WifiManager.WIFI_STATE_ENABLED) {
				Log.d(TAG, "Wifi is now enabled");
				startDiscovery();
			} else {
				Log.d(TAG, "Wifi is now disabled");
				stopDiscovery();
			}
		}
	}

	class Connection extends IZeroConfService.Stub {
		Intent connectionIntent;
		boolean connectionAllTypes = false;
		Vector<Type> connectionTypes = new Vector<Type>();

		Connection(Intent intent) {
			connectionIntent = intent;
		}

		@Override
		public String getName() throws RemoteException {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public void setName(String name) throws RemoteException {
			// TODO Auto-generated method stub

		}

		@Override
		public void subscribeAll() throws RemoteException {
			if(!connectionAllTypes) {
				connectionAllTypes = true;
				allTypeSubscriptions++;
			}
		}

		@Override
		public void unsubscribeAll() throws RemoteException {
			if(connectionAllTypes) {
				allTypeSubscriptions--;
				connectionAllTypes = false;
			}
		}

		@Override
		public void subscribeType(String type) throws RemoteException {
			Type t = ensureType(type);
			if(!connectionTypes.contains(t)) {
				connectionTypes.add(t);
				t.subscribe();
			}
		}

		@Override
		public void unsubscribeType(String type) throws RemoteException {
			Type t = ensureType(type);
			if(connectionTypes.contains(t)) {
				connectionTypes.remove(t);
				t.unsubscribe();
			}
		}

		@Override
		protected void finalize() throws Throwable {
			Enumeration<Type> e = connectionTypes.elements();
			while(e.hasMoreElements()) {
				Type t = e.nextElement();
				if(connectionTypes.contains(t)) {
					connectionTypes.remove(t);
					t.unsubscribe();
				}
			}

			if(connectionAllTypes)  {
				allTypeSubscriptions--;
				connectionAllTypes = false;
			}

			super.finalize();
		}

	}

}
