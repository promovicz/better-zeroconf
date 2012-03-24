package prom.android.zeroconf;

interface IZeroConfService {

	String getName();
	void setName(String name);

	void subscribeAll();
	void unsubscribeAll();

	void subscribeType(String type);
	void unsubscribeType(String type);
	
}
