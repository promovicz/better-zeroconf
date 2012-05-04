package prom.android.zeroconf.browser;

import java.util.List;

import prom.android.zeroconf.model.ZeroConfRecord;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

public class ServiceActivity extends Activity {

	public final static String TAG = MainActivity.class.toString();

	ZeroConfRecord record;
	
	List<String> propertyNames;
	ArrayAdapter<String> propertyAdapter;
	
	private TextView nameText;
	private TextView typeText;
	private TextView portText;
	private TextView protocolText;
	private TextView priorityText;
	private TextView weightText;
	private ListView propertyList;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		Log.d(TAG, "Creating service activity");
		
		super.onCreate(savedInstanceState);
		
		Log.d(TAG, "Initializing user interface");
		
		setContentView(R.layout.service);
		
		nameText = (TextView)findViewById(R.id.name);
		typeText = (TextView)findViewById(R.id.type);
		portText = (TextView)findViewById(R.id.port);
		protocolText = (TextView)findViewById(R.id.protocol);
		priorityText = (TextView)findViewById(R.id.priority);
		weightText = (TextView)findViewById(R.id.weight);
		propertyList = (ListView)findViewById(R.id.properties);
		
		Log.d(TAG, "Unpacking extras bundle");
		
		Intent intent = getIntent();
		Bundle extras = intent.getExtras();		

		record = (ZeroConfRecord)extras.getParcelable("record");
		
		Log.d(TAG, "Updating user interface");
		
		updateForRecord(record);
	}

	@Override
	protected void onDestroy() {
		Log.d(TAG, "Destroying service activity");
		
		super.onDestroy();
	}
	
	
	
	void updateForRecord(ZeroConfRecord record) {
		nameText.setText(record.name);
		typeText.setText(record.type);
		portText.setText(Integer.toString(record.port));
		protocolText.setText(record.protocol);		
		priorityText.setText(Integer.toString(record.priority));
		weightText.setText(Integer.toString(record.weight));
		
		propertyNames = record.getPropertyNames();
				
		propertyAdapter = new PropertyAdapter(
				getApplicationContext(),
				R.layout.service_properties,
				R.id.name);
		
		propertyList.setAdapter(propertyAdapter);
	}
	
	private class PropertyAdapter extends ArrayAdapter<String> {
		PropertyAdapter(Context context, int viewLayout, int nameId) {
			super(context, viewLayout, nameId, propertyNames);
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			View v = super.getView(position, convertView, parent);
			
			TextView valueText = (TextView)v.findViewById(R.id.value);
						
			valueText.setText(record.getPropertyString(propertyNames.get(position)));
			
			return v;
		}
		
	}

}
