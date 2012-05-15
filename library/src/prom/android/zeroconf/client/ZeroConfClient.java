package prom.android.zeroconf.client;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Vector;

import prom.android.zeroconf.model.ZeroConfRecord;
import prom.android.zeroconf.service.IZeroConfService;
import prom.android.zeroconf.service.ZeroConfService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.util.Log;

public class ZeroConfClient {

	/** Log tag */
    public final static String TAG = ZeroConfClient.class.getSimpleName();

	/* Notification message types */
	private static final int NOTIFY_UPDATED = 1;
	private static final int NOTIFY_REMOVED = 2;
    private static final int NOTIFY_CONNECTED = 3;
    private static final int NOTIFY_DISCONNECTED = 4;
	
	/** Context of this client, used for service binding */
	private Context clientContext;

	/** Bound reference to service */
	private IZeroConfService service;
	
	/** Vector of all our listeners */
	private Vector<Listener> listeners = new Vector<Listener>();

    /** All records that have been registered using this client */
    private final HashMap<String, ZeroConfRecord> registeredRecords;

    /** All records that should be registered but aren't yet registered */
    private final HashMap<String, ZeroConfRecord> toBeRegistered;

    /** All records that should be unregistered but aren't yet unregistered */
    private final HashSet<String> toBeUnregistered;

	/**
	 * Public constructor
	 * 
	 * Creates a zeroconf client to be used for
	 * access to the shared zeroconf service.
	 * 
	 * @param context must be provided
	 */
	public ZeroConfClient(Context context) {

		this.clientContext = context;

        registeredRecords = new HashMap<String, ZeroConfRecord>();
        toBeRegistered = new HashMap<String, ZeroConfRecord>();
        toBeUnregistered = new HashSet<String>();
	}
	
	/**
	 * Debug function
	 * 
	 * @param message
	 */
	private final void debugClient(String message) {
		Log.d(TAG, message);
	}

	/**
	 * Connect to the zeroconf service
	 */
	public void connectToService() {
		debugClient("Connecting to service");

		// create intent using the service
		Intent serviceIntent = new Intent(clientContext, ZeroConfService.class);

		// perform the bind
		boolean result = clientContext.bindService(
				serviceIntent, serviceConnection,
				Context.BIND_AUTO_CREATE);

		// report on the results
		if(result) {
			debugClient("Bound to service, expecting connection");
		} else {
			debugClient("Bind failed");
		}
	}

	/**
	 * Disconnect from the zeroconf service
	 */
	public void disconnectFromService() {
		debugClient("Disconnecting from service");
		
		// unbind from service
		clientContext.unbindService(serviceConnection);
        service = null;
	}

    /**
     * Registers an mDNS service announcement.
     * 
     * @param the
     *            service record to announce
     * @return true if the service is connected and the service record was registered, false otherwise
     */
    public boolean registerService(ZeroConfRecord record) {
        
        Log.d(TAG, "registerService name='" + record.name + "', clientKey='" + record.clientKey + "', type='"
                + record.type + "'");

        if (registeredRecords.containsKey(record.clientKey)) {

            // it's already registered
            return true;
        }

        if (service == null) {

            Log.d(TAG, "ZeroConf service is not connected, registering service record later.");
            toBeRegistered.put(record.clientKey, record);
            toBeUnregistered.remove(record);
            return false;
        }

        try {

            service.registerService(record);
            registeredRecords.put(record.clientKey, record);
            return true;

        } catch (RemoteException e) {

            Log.e(TAG, "Exception while registering service: ", e);
            return false;
        }
    }

    /**
     * Unregisters an mDNS service announcement.
     * 
     * @return true if the service is connected and unregistering was successful, false otherwise
     */
    public boolean unregisterService(String clientKey) {

        Log.d(TAG, "unregisterService, clientKey='" + clientKey + "'");

        ZeroConfRecord record = registeredRecords.get(clientKey);
        if (record == null) {

            // can't unregister if it's not been registered
            Log.d(TAG, "Can't unregister service, the record is unknown.");
            return false;
        }

        if (service == null) {

            // not connected - unregister once the connection comes back up
            Log.d(TAG, "ZeroConf service is not connected, unregistering service record later.");
            toBeUnregistered.add(clientKey);
            toBeRegistered.remove(clientKey);
            return false;
        }

        try {

            service.unregisterService(record);
            registeredRecords.remove(clientKey);

            Log.d(TAG, "Service unregistered.");
            return true;

        } catch (RemoteException e) {

            Log.e(TAG, "Exception while unregistering service: ", e);
            return false;
        }
    }

    public boolean isServiceRegistered(String clientKey) {

        return registeredRecords.containsKey(clientKey);
    }

    @Override
    public void finalize() throws Throwable {

        Log.d(TAG, "finalize");

        for (ZeroConfRecord record : registeredRecords.values()) {

            service.unregisterService(record);
        }

        disconnectFromService();
    }

	/**
	 * Convenient listener interface
	 * 
	 * To be implemented by clients.
	 */
	public interface Listener {
		void serviceUpdated(ZeroConfRecord record);
		void serviceRemoved(ZeroConfRecord record);

        void connectedToService();
	}
	
	/**
	 * Register a client-level listener
	 * 
	 * @param listener
	 */
	public void registerListener(Listener listener) {
		listeners.add(listener);
	}
	
	/**
	 * Unregister a client-level listener
	 * 
	 * @param listener
	 */
	public void unregisterListener(Listener listener) {
		listeners.remove(listener);
	}
	
	/**
	 * Internal message handler
	 * 
	 * This is used to dispatch zeroconf events onto the UI thread.
	 * 
	 */
	private final Handler updateNotify = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			ZeroConfRecord r = (ZeroConfRecord)msg.obj;
			Enumeration<Listener> e = listeners.elements();
			switch(msg.what) {
			case NOTIFY_UPDATED:
                // debugClient("Dispatching serviceUpdated(" + r.serviceKey + ")");
				while(e.hasMoreElements()) {
					Listener l = e.nextElement();
					l.serviceUpdated(r);
				}
				break;
			case NOTIFY_REMOVED:
                // debugClient("Dispatching serviceRemoved(" + r.serviceKey + ")");
				while(e.hasMoreElements()) {
					Listener l = e.nextElement();
					l.serviceRemoved(r);
				}
				break;
			case NOTIFY_CONNECTED:
			    while (e.hasMoreElements()) {
			        Listener l = e.nextElement();
			        l.connectedToService();
			    }
			    break;
            }
		}
	};
	
	/**
	 * Internal callback structure
	 * 
	 * This is given to the server as a callback structure.
	 * 
	 * Events from the shared service reach the client here and
	 * get dispatched to the above Handler, to be dispatched on
	 * the UI thread.
	 * 
	 */
	private final IZeroConfClient.Stub callbacks = new IZeroConfClient.Stub() {
		@Override
		public void serviceUpdated(ZeroConfRecord r) throws RemoteException {
			Message msg = Message.obtain(updateNotify, NOTIFY_UPDATED, r);
			updateNotify.sendMessage(msg);
		}
		@Override
		public void serviceRemoved(ZeroConfRecord r) throws RemoteException {
			Message msg = Message.obtain(updateNotify, NOTIFY_REMOVED, r);
			updateNotify.sendMessage(msg);
		}
	};

	/**
	 * Internal service connection
	 * 
	 * Used to track service connection state.
	 * 
	 */
	private final ServiceConnection serviceConnection = new ServiceConnection() {
		@Override
		public void onServiceConnected(ComponentName name, IBinder binder) {
			debugClient("Connected to service");
			service = IZeroConfService.Stub.asInterface(binder);
			try {
				debugClient("Registering callbacks");
				service.registerCallbacks(callbacks);
				service.subscribeAll();
            } catch (RemoteException e) {
                Log.d(TAG, "Exception while subscribing: " + e.toString());
            }

            for (ZeroConfRecord record : toBeRegistered.values()) {
                try {
                    Log.d(TAG, "registering service " + record.clientKey);
                    service.registerService(record);
                    toBeRegistered.remove(record.clientKey);
                    registeredRecords.put(record.clientKey, record);
                } catch (RemoteException e) {
                    Log.e(TAG, "Exception while registering service: ", e);
                }
			}

            for (String clientKey : toBeUnregistered) {
                try {
                    Log.d(TAG, "unregistering service " + clientKey);
                    service.unregisterService(registeredRecords.get(clientKey));
                    toBeUnregistered.remove(clientKey);
                } catch (RemoteException e) {
                    Log.e(TAG, "Exception while registering service: ", e);
                }
            }

            Message msg = Message.obtain(updateNotify, NOTIFY_CONNECTED);
            updateNotify.sendMessage(msg);
        }
		@Override
		public void onServiceDisconnected(ComponentName name) {
			debugClient("Disconnected from service");
			service = null;
            Message msg = Message.obtain(updateNotify, NOTIFY_DISCONNECTED);
            updateNotify.sendMessage(msg);
		}
	};
}
