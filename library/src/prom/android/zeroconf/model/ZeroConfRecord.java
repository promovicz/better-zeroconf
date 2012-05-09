package prom.android.zeroconf.model;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Vector;

import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceInfo;

import android.os.Parcel;
import android.os.Parcelable;

public class ZeroConfRecord implements Parcelable {

	/** System-wide unique key. */
	public String serviceKey = "";

    public String clientKey = "";

	/** User-visible service name */
	public String name = "";
	/** User-visible type name */
	public String type = "";

	public String domain = "";
	public String protocol = "";
	public String application = "";
	public String instance = "";
	public String subtype = "";
	public String server = "";

	public int port = 0;

	public int priority = 0;
	public int weight = 0;

	public String[] urls;
	
	private HashMap<String, byte[]> properties;

	public ZeroConfRecord() {
		this.urls = new String[0];
		this.properties = new HashMap<String, byte[]>();
	}
	
	public List<String> getPropertyNames() {
		return new Vector<String>(this.properties.keySet());
	}
	
	public String getPropertyString(String propertyName) {
		String string = null;
		CharsetDecoder utf8Decoder =
			      Charset.forName("UTF8")
			      .newDecoder()
			      .onMalformedInput(CodingErrorAction.REPORT);
		if(this.properties.containsKey(propertyName)) {
			byte[] propertyValue = this.properties.get(propertyName);
			ByteBuffer bb = ByteBuffer.wrap(propertyValue);
			try {
				CharBuffer cb = utf8Decoder.decode(bb);
				string = cb.toString();
			} catch (CharacterCodingException e) {
				// ignore and return null
			}
		}
		return string;
	}
	
	public byte[] getPropertyBytes(String propertyName) {
		byte[] data = null;
		if(this.properties.containsKey(propertyName)) {
			data = this.properties.get(propertyName).clone();
		}
		return data;
	}

	public void updateFromServiceEvent(ServiceEvent event) {
		ServiceInfo info = event.getInfo();

		this.serviceKey = info.getKey();

		this.name = event.getName();
		this.type = event.getType();
		
		this.domain = info.getDomain();
		this.protocol = info.getProtocol();
		this.application = info.getApplication();
		this.instance = info.getName();
		this.subtype = info.getSubtype();
		this.server = info.getServer();

		this.port = info.getPort();

		this.priority = info.getPriority();
		this.weight = info.getWeight();

		this.urls = info.getURLs().clone();
		
		this.properties.clear();
		Enumeration<String> propertyNames = info.getPropertyNames();
		while(propertyNames.hasMoreElements()) {
			String propertyName = propertyNames.nextElement();
			this.properties.put(propertyName, info.getPropertyBytes(propertyName));
		}
	}

    public ServiceInfo toServiceInfo() {

        return ServiceInfo.create(this.type, this.name, this.subtype, this.port, this.weight, this.priority,
                this.properties);
    }

	@Override
	public int describeContents() {
		return 0;
	}

	private ZeroConfRecord(Parcel in) {
		serviceKey = in.readString();
        clientKey = in.readString();

		name = in.readString();
		type = in.readString();
		
		domain = in.readString();
		protocol = in.readString();
		application = in.readString();
		instance = in.readString();
		subtype = in.readString();
		server = in.readString();

		port = in.readInt();

		priority = in.readInt();
		weight = in.readInt();

		urls = in.createStringArray();
		
		Vector<String> propertyNames = new Vector<String>();
		in.readStringList(propertyNames);
		
		this.properties = new HashMap<String, byte[]>();
		Enumeration<String> props = propertyNames.elements();
		while(props.hasMoreElements()) {
			String propertyName = props.nextElement();
			this.properties.put(propertyName, in.createByteArray());
		}
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeString(serviceKey);
        dest.writeString(clientKey);

		dest.writeString(name);
		dest.writeString(type);
		
		dest.writeString(domain);
		dest.writeString(protocol);
		dest.writeString(application);
		dest.writeString(instance);
		dest.writeString(subtype);
		dest.writeString(server);

		dest.writeInt(port);

		dest.writeInt(priority);
		dest.writeInt(weight);

		dest.writeStringArray(urls);
		
		Vector<String> propertyNames = new Vector<String>(this.properties.keySet());
		dest.writeStringList(propertyNames);
		
		Enumeration<String> props = propertyNames.elements();
		while(props.hasMoreElements()) {
			String propertyName = props.nextElement();
			byte[] propertyValue = this.properties.get(propertyName);
			dest.writeByteArray(propertyValue);
		}
	}

	public static final Parcelable.Creator<ZeroConfRecord> CREATOR
	= new Parcelable.Creator<ZeroConfRecord>() {
		public ZeroConfRecord createFromParcel(Parcel in) {
			return new ZeroConfRecord(in);
		}

		public ZeroConfRecord[] newArray(int size) {
			return new ZeroConfRecord[size];
		}
	};

}
