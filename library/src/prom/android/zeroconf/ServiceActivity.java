package prom.android.zeroconf;

import java.net.URL;

import javax.jmdns.ServiceEvent;

import prom.android.zeroconf.model.ZeroConfRecord;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

public class ServiceActivity extends Activity {

	public final static String TAG = MainActivity.class.toString();

	ZeroConfRecord record;
	
	private TextView nameText;
	private TextView typeText;
	private TextView portText;
	private TextView protocolText;
	private TextView priorityText;
	private TextView weightText;
	
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
		
		Log.d(TAG, "Unpacking extras bundle");
		
		Intent intent = getIntent();
		Bundle extras = intent.getExtras();		

		record = (ZeroConfRecord)extras.getParcelable("record");
		
		Log.d(TAG, "Updating user interface");
		
		nameText.setText(record.name);
		typeText.setText(record.type);
		//portText.setText(new Integer(servicePort).toString());
		//protocolText.setText(serviceProtocol);		
		//priorityText.setText(new Integer(servicePriority).toString());
		//weightText.setText(new Integer(serviceWeight).toString());
	}

	@Override
	protected void onDestroy() {
		Log.d(TAG, "Destroying service activity");
		
		super.onDestroy();
	}
	
	
	
	void updateForServiceEvent(ServiceEvent event) {
		nameText.setText(event.getName());
		typeText.setText(event.getType());
	}

}
