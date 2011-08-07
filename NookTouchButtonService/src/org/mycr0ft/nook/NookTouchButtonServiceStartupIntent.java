package org.mycr0ft.nook;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class NookTouchButtonServiceStartupIntent extends BroadcastReceiver {

	@Override
	public void onReceive(Context context, Intent intent) {	
		// TODO check for "start on boot" setting
		context.startService(new Intent("org.mycroft.nook.ButtonService"));		
	}

}
