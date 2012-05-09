package prom.android.zeroconf.service;

import prom.android.zeroconf.client.IZeroConfClient;
import prom.android.zeroconf.model.ZeroConfRecord;

interface IZeroConfService {

	String getName();
	void setName(String name);

	void registerCallbacks(IZeroConfClient cb);

	void subscribeAll();
	void unsubscribeAll();

	void subscribeType(String type);
	void unsubscribeType(String type);
	
    void registerService(in ZeroConfRecord service);
    void unregisterService(in ZeroConfRecord service);
}
