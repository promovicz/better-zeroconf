package prom.android.zeroconf.service;

import prom.android.zeroconf.client.IZeroConfClient;

interface IZeroConfService {

	String getName();
	void setName(String name);

	void registerCallbacks(IZeroConfClient cb);

	void subscribeAll();
	void unsubscribeAll();

	void subscribeType(String type);
	void unsubscribeType(String type);
	
}
