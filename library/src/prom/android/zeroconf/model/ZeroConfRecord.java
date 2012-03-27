package prom.android.zeroconf.model;

import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceInfo;

import android.os.Parcel;
import android.os.Parcelable;

public class ZeroConfRecord implements Parcelable {

	public String key = "";
	public String name = "";
	public String type = "";

	public String[] urls;

	public ZeroConfRecord() {
		this.urls = new String[0];
	}

	public void updateFromServiceEvent(ServiceEvent event) {
		ServiceInfo info = event.getInfo();

		this.type = event.getType();
		this.name = event.getName();

		this.key = info.getKey();

		this.urls = info.getURLs().clone();
	}

	@Override
	public int describeContents() {
		// TODO Auto-generated method stub
		return 0;
	}

	private ZeroConfRecord(Parcel in) {
		key = in.readString();
		name = in.readString();
		type = in.readString();
		urls = in.createStringArray();
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeString(key);
		dest.writeString(name);
		dest.writeString(type);
		dest.writeStringArray(urls);
	}

	public static final Parcelable.Creator<ZeroConfRecord> CREATOR = new Parcelable.Creator<ZeroConfRecord>() {
		public ZeroConfRecord createFromParcel(Parcel in) {
			return new ZeroConfRecord(in);
		}

		public ZeroConfRecord[] newArray(int size) {
			return new ZeroConfRecord[size];
		}
	};

}
