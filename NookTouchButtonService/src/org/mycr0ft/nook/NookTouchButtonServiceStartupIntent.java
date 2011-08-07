package org.mycr0ft.nook;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.preference.PreferenceManager;

public class NookTouchButtonServiceStartupIntent extends BroadcastReceiver {

	@Override
	public void onReceive(Context context, Intent intent) {	
		
    	String startAtBootKey = "startAtBoot"; 
		boolean startAtBoot;
		
		//check preferences for startAtBoot
		startAtBoot = PreferenceManager.getDefaultSharedPreferences(context).getBoolean(startAtBootKey, false);
		
		if (startAtBoot) {
			//start the service
			context.startService(new Intent(context, NookTouchButtonService.class));
		}
	}
}
