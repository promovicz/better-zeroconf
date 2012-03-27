package prom.android.zeroconf.client;

import java.util.Enumeration;
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
	public final static String TAG = ZeroConfClient.class.toString();

	private static final int NOTIFY_UPDATED = 1;
	private static final int NOTIFY_REMOVED = 2;
	
	/** Context of this client, used for service binding */
	private Context clientContext;

	/** Bound reference to service */
	private IZeroConfService service;
	
	/** Vector of all our listeners */
	private Vector<Listener> listeners = new Vector<Listener>();

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
	}

	/**
	 * Convenient listener interface
	 * 
	 * To be implemented by clients.
	 */
	public interface Listener {
		void serviceUpdated(ZeroConfRecord record);
		void serviceRemoved(ZeroConfRecord record);
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
				debugClient("Dispatching serviceUpdated(" + r.key + ")");
				while(e.hasMoreElements()) {
					Listener l = e.nextElement();
					l.serviceUpdated(r);
				}
				break;
			case NOTIFY_REMOVED:
				debugClient("Dispatching serviceRemoved(" + r.key + ")");
				while(e.hasMoreElements()) {
					Listener l = e.nextElement();
					l.serviceRemoved(r);
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
		}
		@Override
		public void onServiceDisconnected(ComponentName name) {
			debugClient("Disconnected from service");
			service = null;
		}
	};
}
