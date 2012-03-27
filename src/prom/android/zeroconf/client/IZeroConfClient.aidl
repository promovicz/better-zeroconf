package prom.android.zeroconf.client;

import prom.android.zeroconf.model.ZeroConfRecord;

interface IZeroConfClient {

	void serviceUpdated(in ZeroConfRecord record);
	void serviceRemoved(in ZeroConfRecord record);

}
