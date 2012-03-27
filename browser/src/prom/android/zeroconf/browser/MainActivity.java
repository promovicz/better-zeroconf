package prom.android.zeroconf.browser;

import java.util.Hashtable;

import prom.android.zeroconf.client.ZeroConfClient;
import prom.android.zeroconf.model.ZeroConfRecord;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

public class MainActivity extends Activity {

	public final static String TAG = MainActivity.class.toString();

	TextView statusText = null;
	ListView servicesList = null;

	LayoutInflater layoutInflater;
	ArrayAdapter<ZeroConfRecord> listAdapter;

	ZeroConfClient client = null;

	Hashtable<String, ZeroConfRecord> recordsByKey
	= new Hashtable<String, ZeroConfRecord>();

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

		layoutInflater = getLayoutInflater();

		statusText = (TextView)findViewById(R.id.status);

		listAdapter = new ArrayAdapter<ZeroConfRecord>(this, R.layout.main_service, R.id.name) {
			@Override
			public View getView(int position, View previous, ViewGroup parent) {
				ZeroConfRecord r = this.getItem(position);
				LinearLayout v = (LinearLayout)previous;
				
				if(v == null) {
					v = (LinearLayout)layoutInflater.inflate(R.layout.main_service, null, false);
				}
				
				TextView name = (TextView)v.findViewById(R.id.name);
				TextView type = (TextView)v.findViewById(R.id.type);
				
				name.setText(r.name);
				type.setText(r.type);
				
				return v;
			}
		};
		
		servicesList = (ListView)findViewById(R.id.services);
		servicesList.setClickable(true);
		servicesList.setAdapter(listAdapter);
		servicesList.setOnItemClickListener(clickListener);

		client = new ZeroConfClient(this);
		client.registerListener(clientListener);
		client.connectToService();

		updateStatus();
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

		client.disconnectFromService();

		super.onDestroy();
	}

	void updateStatus() {
		String text = recordsByKey.size() + " services";
		statusText.setText(text);
	}

	OnItemClickListener clickListener = new OnItemClickListener() {
		@Override
		public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
			Intent intent = new Intent(view.getContext(), ServiceActivity.class);
			ZeroConfRecord record = (ZeroConfRecord)parent.getAdapter().getItem(position);
			intent.putExtra("record", record);
			startActivity(intent);
		}
	};

	ZeroConfClient.Listener clientListener = new ZeroConfClient.Listener() {

		@Override
		public void serviceUpdated(ZeroConfRecord record) {
			if(recordsByKey.containsKey(record.key)) {
				Log.d(TAG, "Updating service " + record.key);
				ZeroConfRecord old = recordsByKey.get(record.key);
				recordsByKey.put(old.key, record);
				int oldPosition = listAdapter.getPosition(old);
				listAdapter.insert(record, oldPosition);
				listAdapter.remove(old);
			} else {
				Log.d(TAG, "Adding service " + record.key);
				recordsByKey.put(record.key, record);
				listAdapter.add(record);
			}
			updateStatus();
		}

		@Override
		public void serviceRemoved(ZeroConfRecord record) {
			Log.d(TAG, "Removing service " + record.key);
			if(recordsByKey.containsKey(record.key)) {
				ZeroConfRecord old = recordsByKey.get(record.key);
				recordsByKey.remove(old.key);
				listAdapter.remove(old);
			}
			updateStatus();
		}
	};


}
