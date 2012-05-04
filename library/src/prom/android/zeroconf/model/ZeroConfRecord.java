package prom.android.zeroconf.model;

import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceInfo;

import android.os.Parcel;
import android.os.Parcelable;

public class ZeroConfRecord implements Parcelable {

	/** System-wide unique key. */
	public String key = "";

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

	public ZeroConfRecord() {
		this.urls = new String[0];
	}

	public void updateFromServiceEvent(ServiceEvent event) {
		ServiceInfo info = event.getInfo();

		this.key = info.getKey();

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
	}

	@Override
	public int describeContents() {
		return 0;
	}

	private ZeroConfRecord(Parcel in) {
		key = in.readString();

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
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeString(key);

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
