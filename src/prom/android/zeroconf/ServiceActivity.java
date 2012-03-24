package prom.android.zeroconf;

import java.net.URL;

import javax.jmdns.ServiceEvent;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

public class ServiceActivity extends Activity {

	public final static String TAG = MainActivity.class.toString();

	private TextView nameText;
	private TextView typeText;
	private TextView portText;
	private TextView protocolText;
	private TextView priorityText;
	private TextView weightText;
	
	String serviceName;
	String serviceType;
	String serviceServer;
	String serviceDomain;
	String serviceProtocol;
	String serviceApplication;
	String serviceSubtype;
	int servicePort;
	int servicePriority;
	int serviceWeight;
	URL[] serviceURLs;
	
	String serviceQualifiedName;
	String serviceNiceText;
	
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

		Log.d(TAG, "Bundle: " + extras);
		
		serviceName = extras.getString("serviceName");
		serviceType = extras.getString("serviceType");
		serviceServer = extras.getString("serviceServer");
		serviceDomain = extras.getString("serviceDomain");
		serviceProtocol = extras.getString("serviceProtocol");
		serviceApplication = extras.getString("serviceApplication");
		serviceSubtype = extras.getString("serviceSubtype");
		servicePriority = extras.getInt("servicePriority");
		serviceWeight = extras.getInt("serviceWeight");
		servicePort = extras.getInt("servicePort");
		serviceQualifiedName = extras.getString("serviceQualifiedName");
		serviceNiceText = extras.getString("serviceNiceText");
		
		Log.d(TAG, "Updating user interface");
		
		nameText.setText(serviceName);
		typeText.setText(serviceType);
		portText.setText(new Integer(servicePort).toString());
		protocolText.setText(serviceProtocol);		
		priorityText.setText(new Integer(servicePriority).toString());
		weightText.setText(new Integer(serviceWeight).toString());
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
