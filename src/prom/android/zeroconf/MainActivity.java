package prom.android.zeroconf;

import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceInfo;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.DataSetObserver;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;

public class MainActivity extends Activity {

	public final static String TAG = MainActivity.class.toString();

	TextView statusText = null;
	ListView servicesList = null;

	ZeroConfReceiver zeroConf = null;


	IZeroConfService service;
	ServiceConnection serviceConnection;
	
	/**
	 * Activity instance activation
	 * 
	 * We initialize the GUI and start the mDNS stack here.
	 * 
	 * @param savedInstanceState
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Log.d(TAG, "Creating main activity");
		
		Log.d(TAG, "Initializing user interface");
		setContentView(R.layout.main);
		
		statusText = (TextView)findViewById(R.id.status);
		statusText.setText("Initializing");
		
		servicesList = (ListView)findViewById(R.id.services);
		servicesList.setClickable(true);
		

		
		Log.d(TAG, "Instantiating mDNS listener");
		WifiManager wifiManager =
				(WifiManager)this.getSystemService(WIFI_SERVICE);
		zeroConf = new ZeroConfReceiver(this, wifiManager);

		Log.d(TAG, "Starting mDNS listener");
		
		boolean initialized = false;
		
		try {
			zeroConf.startListening();
			initialized = true;
		} catch (Exception e) {
		}
		
		if(initialized) {
			statusText.setText("Listening");
		} else {
			statusText.setText("Failed to start mDNS listener");
			statusText.setTextColor(0xFF0000);
		}
		
		Log.d(TAG, "Attaching ListView");
		ListAdapter adapter = zeroConf.getServiceListAdapter();
		
		DataSetObserver observer = new DataSetObserver() {
			@Override
			public void onChanged() {
				updateCounts();
			}
			@Override
			public void onInvalidated() {
			}
		};
		adapter.registerDataSetObserver(observer);
		
		servicesList.setAdapter(adapter);
		
		Log.d(TAG, "Attaching click listener");
		OnItemClickListener l = new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view,
									int position, long id) {
				Intent intent = new Intent(view.getContext(), ServiceActivity.class);
				ZeroConfReceiver.Srv service = (ZeroConfReceiver.Srv)(parent.getAdapter().getItem(position));
				ServiceEvent lastEvent = service.getLastEvent(); 
				ServiceInfo serviceInfo = lastEvent.getInfo();
				intent.putExtra("serviceName", serviceInfo.getName());
				intent.putExtra("serviceType", serviceInfo.getType());
				intent.putExtra("servicePort", serviceInfo.getPort());
				intent.putExtra("serviceServer", serviceInfo.getServer());
				intent.putExtra("serviceDomain", serviceInfo.getDomain());
				intent.putExtra("serviceProtocol", serviceInfo.getDomain());
				intent.putExtra("serviceApplication", serviceInfo.getApplication());
				intent.putExtra("serviceSubtype", serviceInfo.getSubtype());
				intent.putExtra("servicePriority", serviceInfo.getPriority());
				intent.putExtra("serviceWeight", serviceInfo.getWeight());
				intent.putExtra("serviceURLs", serviceInfo.getURLs());
				intent.putExtra("serviceQualifiedName", serviceInfo.getQualifiedName());
				intent.putExtra("serviceNiceText", serviceInfo.getNiceTextString());
                startActivity(intent);
			}
		};
		servicesList.setOnItemClickListener(l);
		
		Log.d(TAG, "Starting service");
		Intent serviceIntent = new Intent(this, ZeroConfService.class);
		serviceConnection = new ServiceConnection() {
			
			@Override
			public void onServiceDisconnected(ComponentName name) {
				Log.d(TAG, "Got disconnect for " + name.toString());
				if(name.toString() == "prom.android.zeroconf.ZeroConfService") {
					serviceConnection = null;
					service = null;
				}
			}
			
			@Override
			public void onServiceConnected(ComponentName name, IBinder binder) {
				Log.d(TAG, "Got service binder for " + name.toString());
				if(name.toString() == "prom.android.zeroconf.ZeroConfService") {
					service = (IZeroConfService)binder;
					try {
						service.subscribeAll();
					} catch (RemoteException e) {
						Log.d(TAG, "Exception while subscribing: " + e.toString());
					}
				}
			}
		};
		
		Log.d(TAG, "Binding!");
		boolean result = bindService(serviceIntent, serviceConnection, BIND_AUTO_CREATE);
		Log.d(TAG, "Done binding: " + result);
	}

	/**
	 * Activity instance shutdown
	 * 
	 * Shut down the mDNS stack and clear the GUI.
	 * 
	 */
	@Override
	protected void onDestroy() {
		Log.d(TAG, "Destroying activity");

		unbindService(serviceConnection);
		serviceConnection = null;
		service = null;
		
		Log.d(TAG, "Stopping mDNS listener");
		zeroConf.stopListening();

		super.onDestroy();
	}
	

	/**
	 * Update status line with number of services;
	 */
	void updateCounts() {
		int serviceCount = zeroConf.getServiceCount();
		int typeCount = zeroConf.getTypeCount();
		String text =
			+ serviceCount + " services, "
			+ typeCount + " types";
		statusText.setText(text);
	}
	
}
