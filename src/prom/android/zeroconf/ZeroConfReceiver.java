package prom.android.zeroconf;

import java.net.InetAddress;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Vector;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceListener;
import javax.jmdns.ServiceTypeListener;

import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.MulticastLock;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Adapter;
import android.widget.BaseAdapter;
import android.widget.ListAdapter;
import android.widget.TextView;

public class ZeroConfReceiver {

	public final static String TAG = ZeroConfReceiver.class.toString();
	public final static String MULTICAST_LOCK_NAME = ZeroConfReceiver.class.toString();

	private final static int NOTIFY_TYPE_ADDED = 1;
	private final static int NOTIFY_SERVICE_ADDED = 2;
	private final static int NOTIFY_SERVICE_REMOVED = 3;
	private final static int NOTIFY_SERVICE_RESOLVED = 4;

	MainActivity main = null;
	
	private JmDNS  mDNS = null;
	
	private MulticastLock mcastLock = null;

	private WifiManager wifiManager = null;

	Hashtable<String, SrvType> typesByName = new Hashtable<String, SrvType>();
	
	Vector<Srv> allServices = new Vector<Srv>();

	ServiceListAdapter servicesListAdapter = null;
	
	public ZeroConfReceiver(MainActivity main, WifiManager wifiManager) {
		this.main = main;
		this.wifiManager = wifiManager;
		
		this.servicesListAdapter = new ServiceListAdapter();
	}

	private void acquireMulticastLock() {
		if(mcastLock == null) {
			mcastLock = wifiManager.createMulticastLock(MULTICAST_LOCK_NAME);
			mcastLock.setReferenceCounted(true);
			mcastLock.acquire();
		}
	}

	private void releaseMulticastLock() {
		if(mcastLock != null) {
			mcastLock.release();
			mcastLock = null;
		}
	}

	Handler updateNotify = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			Log.d(TAG, "Received notification of type " + msg.what);
			if(msg.what == NOTIFY_TYPE_ADDED) {
				String name = (String)msg.obj;
				SrvType t = new SrvType(name);
				typesByName.put(t.name, t);
			} else {
				ServiceEvent e = (ServiceEvent)msg.obj;
				SrvType t;
				Srv s;
				switch(msg.what) {
				case NOTIFY_SERVICE_ADDED:
					t = typesByName.get(e.getType());
					s = new Srv(e);
					t.addSrv(s);
					allServices.insertElementAt(s, 0);
					break;
				case NOTIFY_SERVICE_REMOVED:
					t = typesByName.get(e.getType());
					s = t.getSrvByName(e.getName());
					t.removeSrv(s);
					allServices.remove(s);
					break;
				case NOTIFY_SERVICE_RESOLVED:
					break;
				}
				servicesListAdapter.notifyDataSetChanged();
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
	
	private void startJmDNS() throws Exception {
		WifiInfo connInfo = wifiManager.getConnectionInfo();

		// XXX ugly
		int myRawAddress = connInfo.getIpAddress();
		byte[] myAddressBytes = new byte[] {
				(byte) (myRawAddress & 0xff),
				(byte) (myRawAddress >> 8 & 0xff),
				(byte) (myRawAddress >> 16 & 0xff),
				(byte) (myRawAddress >> 24 & 0xff)
		};

		InetAddress myAddress = InetAddress.getByAddress(myAddressBytes);
		mDNS = JmDNS.create(myAddress);
		mDNS.addServiceTypeListener(new SrvTypeListener());
	}

	private void stopJmDNS() {
		if(mDNS != null) {
			try {
				mDNS.close();
			} catch (Exception e) {
				Log.d(TAG, "Exception while stopping mDNS", e);
			}
			mDNS = null;
		}
	}

	public void startListening() throws Exception {
		//acquireMulticastLock();
		//startJmDNS();
	}

	public void stopListening() {
		//stopJmDNS();
		//releaseMulticastLock();
	}

	JmDNS getMDNS() {
		return mDNS;
	}
	
	public int getTypeCount() {
		return typesByName.size();
	}
	
	public int getServiceCount() {
		return allServices.size();
	}
	
	public ListAdapter getServiceListAdapter() {
		return servicesListAdapter;
	}

	/**
	 * Internal representation of services
	 * 
	 * @author prom
	 */
	public class Srv {
		final String type;
		final String name;
		boolean resolved;
		ServiceEvent lastEvent;

		Srv(ServiceEvent event) {
			this.type = event.getType();
			this.name = event.getName();
			this.resolved = false;
			updateFromEvent(event);
		}

		void resolved(ServiceEvent event) {
			updateFromEvent(event);
			resolved = true;
		}

		void updateFromEvent(ServiceEvent event) {
			this.lastEvent = event;
		}
		
		ServiceEvent getLastEvent() {
			return this.lastEvent;
		}
	}

	/**
	 * Internal representation of service types
	 * 
	 * @author prom
	 */
	public class SrvType {
		String name;
		private Hashtable<String, Srv> servicesByName
		= new Hashtable<String, ZeroConfReceiver.Srv>();

		SrvType(String name) {
			this.name = name;
		}

		void addSrv(Srv srv) {
			servicesByName.put(srv.name, srv);
		}

		void removeSrv(Srv srv) {
			servicesByName.remove(srv.name);
		}

		Srv getSrvByName(String name) {
			return servicesByName.get(name);
		}
	}

	/**
	 * Internal listener for service type events.
	 * 
	 * This gets called by JmDNS when services are removed,
	 * added or resolved. This class is responsible for forwarding
	 * these events to the main thread in a synchronized fashion.
	 * 
	 * @author prom
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
		public void serviceAdded(ServiceEvent event) {
			//Log.d(TAG, "serviceAdded(" + serviceType + " | " + event.getName() + ")");

			// notify the main thread
			sendServiceMessage(NOTIFY_SERVICE_ADDED, event);

			// request resolution of service details
			mDNS.requestServiceInfo(event.getType(), event.getName(), true);
		}

		/** Callback method for removing services */
		@Override
		public void serviceRemoved(ServiceEvent event) {
			//Log.d(TAG, "serviceRemoved(" + serviceType + " | " + event.getName() + ")");

			// notify the main thread
			sendServiceMessage(NOTIFY_SERVICE_REMOVED, event);
		}

		/** Callback method for resolution results */
		@Override
		public void serviceResolved(ServiceEvent event) {
			//Log.d(TAG, "serviceResolved(" + serviceType + " | "+ event.getName() + ")");

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
	 * 
	 * @author prom
	 */
	private class SrvTypeListener implements ServiceTypeListener {

		/** Set of all types seen during listener lifetime */
		private final HashSet<String> seenTypes = new HashSet<String>();

		/** Callback method for adding service types */
		@Override
		public void serviceTypeAdded(ServiceEvent event) {
			//Log.d(TAG, "serviceTypeAdded(" + event.getType() + ")");

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

		/** Callback method for adding service subtypes */
		@Override
		public void subTypeForServiceTypeAdded(ServiceEvent event) {
		}

	}

	/**
	 * Simple list adapter for all our services
	 * 
	 * @author prom
	 */
	private class ServiceListAdapter extends BaseAdapter implements ListAdapter {

		@Override
		public int getCount() {
			return allServices.size();
		}

		@Override
		public Object getItem(int pos) {
			return allServices.get(pos);
		}

		@Override
		public long getItemId(int pos) {
			return pos;
		}

		@Override
		public int getItemViewType(int pos) {
			return Adapter.IGNORE_ITEM_VIEW_TYPE;
		}

		@Override
		public View getView(int pos, View previous, ViewGroup parent) {
			TextView v = (TextView)previous;
			if(previous == null) {
				v = new TextView(main);
			}
			Srv s = allServices.get(pos);
			String text = s.name + "\n" + s.type + "\n";
			v.setText(text);
			return v;
		}

		@Override
		public int getViewTypeCount() {
			return 1;
		}

		@Override
		public boolean hasStableIds() {
			return false;
		}

		@Override
		public boolean isEmpty() {
			return allServices.size() == 0;
		}

		@Override
		public boolean areAllItemsEnabled() {
			return true;
		}

		@Override
		public boolean isEnabled(int position) {
			return true;
		}

	}
}
