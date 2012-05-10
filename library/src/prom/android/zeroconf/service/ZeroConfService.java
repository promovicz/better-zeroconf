package prom.android.zeroconf.service;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Vector;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceInfo;
import javax.jmdns.ServiceListener;
import javax.jmdns.ServiceTypeListener;

import prom.android.zeroconf.client.IZeroConfClient;
import prom.android.zeroconf.model.ZeroConfRecord;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.MulticastLock;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.util.Log;

public class ZeroConfService extends Service implements Runnable {

    public final static String TAG = ZeroConfService.class.getSimpleName();

    public final static String MULTICAST_LOCK_TAG = ZeroConfService.class.toString();

	private final static int NOTIFY_TYPE_ADDED = 1;
	private final static int NOTIFY_SERVICE_ADDED = 2;
	private final static int NOTIFY_SERVICE_REMOVED = 3;
	private final static int NOTIFY_SERVICE_RESOLVED = 4;

    private final static int SERVICE_SLEEP_INTERVAL = 60000;

	WifiManager wifiManager;

	ConnectionStateListener connectionStateListener;

	MulticastLock multicastLock;

	JmDNS mDNS;

	Hashtable<String, SrvType> allTypesByName = new Hashtable<String, SrvType>();

	Vector<Srv> allServices = new Vector<Srv>();

	Vector<Connection> subscribeAllClients = new Vector<Connection>();

	int allTypeSubscriptions;

	Thread serviceThread;
	boolean serviceShutdownRequested = false;

	@Override
	public void onCreate() {
		super.onCreate();
        Log.i(TAG, "Creating service");

		Log.d(TAG, "Getting wifi manager");
		wifiManager = (WifiManager)getSystemService(WIFI_SERVICE);

		Log.d(TAG, "Registering connection state listener");
		connectionStateListener = new ConnectionStateListener();
		registerReceiver(connectionStateListener,
				new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));

		Log.d(TAG, "Starting service thread");
		serviceThread = new Thread(this);
		serviceThread.start();
	}

	@Override
	public void onDestroy() {
        Log.i(TAG, "Destroying service");

		Log.d(TAG, "Shutting down service thread");
		serviceShutdownRequested = true;
		boolean joined = false;
		while(!joined) {
			try {
				serviceThread.interrupt();
				serviceThread.join();
				joined = true;
			} catch (InterruptedException e) {
			}
		}
		
		Log.d(TAG, "Unregistering connection state listener");
		unregisterReceiver(connectionStateListener);

		super.onDestroy();
	}

	@Override
	public IBinder onBind(Intent intent) {
        Log.i(TAG, "Binding connection " + intent);
		return new Connection(intent);
	}

	private void onWifiChange() {
		serviceThread.interrupt();
	}

	public void run() {
		int lastWifiState = WifiManager.WIFI_STATE_UNKNOWN;
		// loop until shutdown
		while(!serviceShutdownRequested) {
			// depending on wifi connection
			int currentWifiState = wifiManager.getWifiState();
			if(currentWifiState != lastWifiState) {
				if(currentWifiState == WifiManager.WIFI_STATE_ENABLED) {
					startDiscovery();
				} else {
					stopDiscovery();
				}
			}
			lastWifiState = currentWifiState;
			// idle wait
			try {
                Thread.sleep(SERVICE_SLEEP_INTERVAL);
			} catch (InterruptedException e) {
				// ignored
			}
		}

        stopDiscovery();
	}

    private void startDiscovery() {
		Log.d(TAG, "Attempting to start discovery");

		JmDNS cur = mDNS;
		if(cur != null) {
			Log.d(TAG, "Discovery already running");
			return;
		}
		
		Log.d(TAG, "Creating multicast lock");
		multicastLock = wifiManager.createMulticastLock(MULTICAST_LOCK_TAG);
		multicastLock.setReferenceCounted(true);

		Log.d(TAG, "Getting wifi state");
		if(wifiManager.getWifiState() != WifiManager.WIFI_STATE_ENABLED) {
			Log.d(TAG, "Wifi state is not enabled");
			return;
		}

		Log.d(TAG, "Getting connection info");
		WifiInfo connInfo = wifiManager.getConnectionInfo();
		if(connInfo == null) {
			Log.d(TAG, "Failed to get connection info");
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
			mDNS.addServiceTypeListener(new SrvTypeListener());

            Log.d(TAG, "Discovery was successfully started, mDNS instance is " + mDNS);

		} catch (IOException e) {
			Log.d(TAG, "Failed to start discovery: " + e.toString());
			mDNS = null;
			multicastLock.release();
		}
	}

	private void stopDiscovery() {
		Log.d(TAG, "Attempting to stop discovery");

		JmDNS cur = mDNS;
		if(cur == null) {
			Log.d(TAG, "Discovery not running");
			return;
		}
		mDNS = null;

        Log.d(TAG, "Removing all services");
		Collection<SrvType> types = allTypesByName.values();
		Iterator<SrvType> i = types.iterator();
		while(i.hasNext()) {
			SrvType t = i.next();
			t.removeAllSrv();
		}

        Log.d(TAG, "Shutting down listener");
		try {

            // this takes about 5 seconds
			cur.close();
		} catch (IOException e) {
			// XXX do we care?
		}

        Log.d(TAG, "Releasing multicast lock");
		multicastLock.release();
	}

	/**
	 * Message handler for updates from JmDNS
	 * 
	 * This gets fed from the listeners we install
	 * in JmDNS, collecting discovered things into
	 * our service-global database.
	 * 
	 */
	private Handler updateNotify = new Handler() {
		@Override
		public void handleMessage(Message msg) {

            SrvType t;
			if(msg.what == NOTIFY_TYPE_ADDED) {
				String name = (String)msg.obj;
				t = ensureType(name);
				allTypesByName.put(t.typeName, t);
			} else {
				ServiceEvent e = (ServiceEvent)msg.obj;
				Srv s;
				switch(msg.what) {
				case NOTIFY_SERVICE_ADDED:
					t = ensureType(e.getType());
					s = new Srv(e);
					Log.d(TAG, "Adding svc " + e.getName());
					t.addSrv(s);
					allServices.insertElementAt(s, 0);
					break;
				case NOTIFY_SERVICE_REMOVED:
					t = ensureType(e.getType());
					s = t.getSrvByKey(e.getInfo().getKey());
					Log.d(TAG, "Removing svc " + e.getName());
					t.removeSrv(s);
					allServices.remove(s);
					break;
				case NOTIFY_SERVICE_RESOLVED:
					break;
				}
			}
        }
	};

	private void sendTypeMessage(int what, String type) {
		Message m = Message.obtain(updateNotify, what, type);
		updateNotify.sendMessage(m);
	}

	private void sendServiceMessage(int what, ServiceEvent event) {
		Message m = Message.obtain(updateNotify, what, event);
		updateNotify.sendMessage(m);
	}

	/**
	 * Internal representation of services
	 * 
	 * @author prom
	 */
	public class Srv {
		String type;
		String name;

		String key;

		boolean resolved;
		ServiceEvent lastEvent;

		ZeroConfRecord record = new ZeroConfRecord();

		Srv(ServiceEvent event) {
			this.resolved = false;
			updateFromEvent(event);
		}

		void resolved(ServiceEvent event) {
			updateFromEvent(event);
			resolved = true;
		}

		void updateFromEvent(ServiceEvent event) {
			this.lastEvent = event;

			this.type = event.getType();
			this.name = event.getName();
			this.key  = event.getInfo().getKey();

			record.updateFromServiceEvent(event);
		}

		ServiceEvent getLastEvent() {
			return this.lastEvent;
		}

		ZeroConfRecord getRecord() {
			return this.record;
		}
	}

	/**
	 * Internal representation of service types
	 * 
	 * @author prom
	 */
	class SrvType {
		int subscriptions;

		Vector<Connection> subscribedClients
		= new Vector<Connection>();

		public String typeName;

		private Vector<Srv> typeServices
		= new Vector<Srv>();

		private Hashtable<String, Srv> servicesByKey
		= new Hashtable<String, Srv>();

		SrvType(String typeName) {
			this.typeName = typeName;
		}

		void subscribe(Connection client) {
			if(!subscribedClients.contains(client)) {
				subscribedClients.add(client);

				Enumeration<Srv> e = typeServices.elements();
				while(e.hasMoreElements()) {
					Srv s = e.nextElement();
					notifyUpdate(s.record, client);
				}
			}
		}

		void unsubscribe(Connection client) {
			if(subscribedClients.contains(client)) {
				subscribedClients.remove(client);
			}
		}

		void addSrv(Srv srv) {
			typeServices.add(srv);
			servicesByKey.put(srv.key, srv);

			updateSrv(srv);
		}

		void updateSrv(Srv srv) {
			ZeroConfRecord r = srv.getRecord();

			notifyUpdate(r, subscribedClients.elements());
			notifyUpdate(r, subscribeAllClients.elements());
		}

		void removeSrv(Srv srv) {
			ZeroConfRecord r = srv.getRecord();

			notifyRemove(r, subscribedClients.elements());
			notifyRemove(r, subscribeAllClients.elements());

			typeServices.remove(srv);
			servicesByKey.remove(srv.key);
		}

		void removeAllSrv() {
			Enumeration<Srv> e = allServices.elements();
			while(e.hasMoreElements()) {
				Srv s = e.nextElement();
				removeSrv(s);
			}
		}

		Srv getSrvByKey(String key) {
			return servicesByKey.get(key);
		}

		private void notifyUpdate(ZeroConfRecord r, Connection c) {
			try {
				if(c.callbacks != null) {
					c.callbacks.serviceUpdated(r);
				}
			} catch (RemoteException ex) {
				Log.d(TAG, "Update callback exception: " + ex.toString());
			}
		}

		private void notifyUpdate(ZeroConfRecord r, Enumeration<Connection> e) {
			while(e.hasMoreElements()) {
				notifyUpdate(r, e.nextElement());
			}
		}

		private void notifyRemove(ZeroConfRecord r, Connection c) {
			try {
				if(c.callbacks != null) {
					c.callbacks.serviceRemoved(r);
				}
			} catch (RemoteException ex) {
				Log.d(TAG, "Removed callback exception: " + ex.toString());
			}
		}

		private void notifyRemove(ZeroConfRecord r, Enumeration<Connection> e) {
			while(e.hasMoreElements()) {
				notifyRemove(r, e.nextElement());
			}
		}
	}

	SrvType ensureType(String type) {
		if(!allTypesByName.containsKey(type)) {
			SrvType t = new SrvType(type);
			allTypesByName.put(type, t);
			return t;
		}
		return allTypesByName.get(type);
	}

	/**
	 * Broadcast receiver watching connection state
	 */
	class ConnectionStateListener extends BroadcastReceiver {
		@Override
		public void onReceive(Context context, Intent intent) {
			boolean status = (wifiManager.getWifiState() == WifiManager.WIFI_STATE_ENABLED);
			Log.d(TAG, "Connection state is now " + (status ? "enabled" : "disabled"));
			onWifiChange();
		}
	}

	int connectionSerialCounter = 0;

	/**
	 * Representation of client connection and RPC stub
	 */
	class Connection extends IZeroConfService.Stub {
		int connectionSerial = connectionSerialCounter++;
		Intent connectionIntent;
		boolean connectionAllTypes = false;
		Vector<SrvType> connectionTypes = new Vector<SrvType>();
		IZeroConfClient callbacks;

		Connection(Intent intent) {
			this.connectionIntent = intent;
		}

		private void debugConnection(String message) {
			Log.d(TAG, "connection " + connectionSerial + ": "+ message);
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
		public void registerCallbacks(IZeroConfClient cb)
				throws RemoteException {
			debugConnection("registerCallbacks()");
			callbacks = cb;
		}

		private void prenotifySubscribeAll() {
			Enumeration<Srv> e = allServices.elements();
			while(e.hasMoreElements()) {
				Srv s = e.nextElement();
				try {
					callbacks.serviceUpdated(s.getRecord());
				} catch (RemoteException ex) {
					Log.d(TAG, "remote exception while prenotifying: " + ex.toString());
				}
			}
		}

		@Override
		public void subscribeAll() throws RemoteException {
			debugConnection("subscribeAll()");
			if(!connectionAllTypes) {
				connectionAllTypes = true;
				allTypeSubscriptions++;
				subscribeAllClients.add(this);
				prenotifySubscribeAll();
			}
		}

		@Override
		public void unsubscribeAll() throws RemoteException {
			debugConnection("unsubscribeAll()");
			if(connectionAllTypes) {
				allTypeSubscriptions--;
				connectionAllTypes = false;
				subscribeAllClients.remove(this);
			}
		}

		@Override
		public void subscribeType(String type) throws RemoteException {
			debugConnection("subscribeType(" + type + ")");
			SrvType t = ensureType(type);
			if(!connectionTypes.contains(t)) {
				connectionTypes.add(t);
				t.subscribe(this);
			}
		}

		@Override
		public void unsubscribeType(String type) throws RemoteException {
			debugConnection("unsubscribeType(" + type + ")");
			SrvType t = ensureType(type);
			if(connectionTypes.contains(t)) {
				connectionTypes.remove(t);
				t.unsubscribe(this);
			}
		}

		@Override
		protected void finalize() throws Throwable {
			debugConnection("finalize()");

			Enumeration<SrvType> e = connectionTypes.elements();
			while(e.hasMoreElements()) {
				SrvType t = e.nextElement();
				if(connectionTypes.contains(t)) {
					connectionTypes.remove(t);
					t.unsubscribe(this);
				}
			}

			unsubscribeAll();

			super.finalize();
		}

        @Override
        public void registerService(final ZeroConfRecord pService) throws RemoteException {

            Log.d(TAG, "registerService");

            // WORKSFORNOW this is a workaround to get this off the main thread
            new Thread(new Runnable() {

                @Override
                public void run() {

                    ServiceInfo serviceInfo = pService.toServiceInfo();
                    Log.d(TAG, "service info text: " + new String(serviceInfo.getTextBytes()));

                    try {

                        debugConnection("registeringService " + pService.name);

                        // this takes up to 8 seconds
                        mDNS.registerService(serviceInfo);

                    } catch (IOException e) {
                        Log.e(TAG, "registering service " + pService.name + " failed: ", e);
                    }
                }
            }).start();
        }

        @Override
        public void unregisterService(ZeroConfRecord pService) throws RemoteException {

            ServiceInfo serviceInfo = pService.toServiceInfo();
            debugConnection("unregisteringService " + pService.name);
            mDNS.unregisterService(serviceInfo);
        }
	}

	/**
	 * Debug wrapper
	 * 
	 * @param message
	 */
	private final void debugListener(String message) {
		//Log.d(TAG, "listener " + message);
	}

	/**
	 * Internal listener for service type events.
	 * 
	 * This gets called by JmDNS when services are removed,
	 * added or resolved. This class is responsible for forwarding
	 * these events to the main thread in a synchronized fashion.
	 */
	private class SrvListener implements ServiceListener {

		/** Service type for this listener */
		private final String serviceType;

		/** Simple constructor */
		public SrvListener(String serviceType) {
			this.serviceType = serviceType;
		}

		/** Callback method for adding services */
		@Override
        public void serviceAdded(final ServiceEvent event) {
			debugListener("serviceAdded(" + serviceType + " | " + event.getName() + ")");

            // WORKSFORNOW this is a workaround to get this off the main thread
            new Thread(new Runnable() {

                @Override
                public void run() {

                    // notify the main thread
                    sendServiceMessage(NOTIFY_SERVICE_ADDED, event);

                    // request resolution of service details
                    mDNS.requestServiceInfo(event.getType(), event.getName(), false);
                }
            }).start();

		}

		/** Callback method for removing services */
		@Override
		public void serviceRemoved(ServiceEvent event) {
			debugListener("serviceRemoved(" + serviceType + " | " + event.getName() + ")");

			// notify the main thread
			sendServiceMessage(NOTIFY_SERVICE_REMOVED, event);
		}

		/** Callback method for resolution results */
		@Override
		public void serviceResolved(ServiceEvent event) {
			debugListener("serviceResolved(" + serviceType + " | "+ event.getName() + ")");

			// notify the main thread
			sendServiceMessage(NOTIFY_SERVICE_RESOLVED, event);
		}
	}

	/**
	 * Internal listener for service type events.
	 * 
	 * This gets called by JmDNS when new service types
	 * get discovered. This class is responsible for forwarding
	 * these events to the main thread in a synchronized fashion.
	 */
	private class SrvTypeListener implements ServiceTypeListener {

		/** Set of all types seen during listener lifetime */
		private final HashSet<String> seenTypes = new HashSet<String>();

		/** Callback method for adding service types */
		@Override
		public void serviceTypeAdded(ServiceEvent event) {
			debugListener("serviceTypeAdded(" + event.getType() + ")");
			if(mDNS != null) {
				String type = event.getType();

				// if we have never seen this type
				if(!seenTypes.contains(type)) {
					// notify the main thread
					sendTypeMessage(NOTIFY_TYPE_ADDED, type);
					// add a service listener for the type
					mDNS.addServiceListener(type, new SrvListener(type));
					// remember the type
					seenTypes.add(type);
				}
			}
		}

		/** Callback method for adding service subtypes */
		@Override
		public void subTypeForServiceTypeAdded(ServiceEvent event) {
		}

	}

}
