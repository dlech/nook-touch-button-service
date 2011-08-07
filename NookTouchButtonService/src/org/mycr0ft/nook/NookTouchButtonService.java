package org.mycr0ft.nook;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.EventListener;
import java.util.EventObject;
import java.util.List;
import java.util.Vector;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.widget.Toast;

public class NookTouchButtonService extends android.app.Service {

	// constants

	// key scan codes
	/**
	 * Top left button on Nook Touch
	 */
	public static final int SCANCODE_TOP_LEFT = 412;
	/**
	 * Bottom left button on Nook Touch
	 */
	public static final int SCANCODE_BOTTOM_LEFT = 139;
	/**
	 * Top right button on Nook Touch
	 */
	public static final int SCANCODE_TOP_RIGHT = 407;
	/**
	 * Bottom right button on Nook Touch
	 */
	public static final int SCANCODE_BOTTOM_RIGHT = 158;
	/**
	 * Nook (home) button on Nook Touch
	 */
	public static final int SCANCODE_NOOK = 102;
	/**
	 * Power button on Nook Touch
	 */
	public static final int SCANCODE_POWER = 116;
	/**
	 * Touch screen on Nook Touch
	 */
	public static final int SCANCODE_TOUCH_SCREEN = 330;

	// key values
	/**
	 * Key pressed
	 */
	public static final long KEY_PRESSED = 1;
	/**
	 * Key released
	 */
	public static final long KEY_RELEASED = 0;

	// event types
	/**
	 * Synchronization Event
	 */
	public static final int EVENT_SYN = 0;
	/**
	 * Key Press Event
	 */
	public static final int EVENT_KEY = 1;
	/**
	 * Absolute Event (touch screen position)
	 */
	public static final int EVENT_ABS = 3;

	// global variables
	private Thread mGetKeysThread;
	private boolean mServiceRunning;
	private List<KeyEventListener> mKeyEventListenerList;
	private Handler mNotificationHandler;

	@Override
	public void onCreate() {
		super.onCreate();
		// TODO check that this is a Nook Touch and abort if it is not
		
		mKeyEventListenerList = new Vector<KeyEventListener>();

		mNotificationHandler = new ToastHandler();
		
		mServiceRunning = true;
		mGetKeysThread = new Thread(new GetKeys());
		mGetKeysThread.start();
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
	
		addKeyEventListener(new KeyEventListener() {
	
			@Override
			void onKey(KeyEvent evt) {
				
				if ((evt.getCode() == SCANCODE_TOP_LEFT) && (evt.getValue() == KEY_RELEASED)) {
					Intent i = new Intent(Intent.ACTION_MAIN);
					i.setComponent(new ComponentName("com.android.launcher", "com.android.launcher.Launcher"));
					i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
					startActivity(i);
				}
				if ((evt.getCode() == SCANCODE_TOP_RIGHT) && (evt.getValue() == KEY_RELEASED)) {
					ActivityManager am = (ActivityManager)getSystemService(ACTIVITY_SERVICE);
					Intent in = am.getRecentTasks(1, ActivityManager.RECENT_WITH_EXCLUDED).get(0).baseIntent;
					showToast("Action: " + in.getAction() + "\nCategory: " + in.getCategories() + "\nComponent: " 
					+ in.getComponent().flattenToShortString() + "\nFlags: " + in.getFlags());
				}
			}
	
		});
		showToast("Nook Button Service started.");
		return super.onStartCommand(intent, flags, startId);
	}

	@Override
	public void onDestroy() {
		mServiceRunning = false; // stop GetKeysThread

		super.onDestroy();
	}

	
	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		// TODO Auto-generated method stub
		super.onConfigurationChanged(newConfig);
	}

	@Override
	public IBinder onBind(Intent intent) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean onUnbind(Intent intent) {
		// TODO Auto-generated method stub
		return super.onUnbind(intent);
	}

	@Override
	public void onRebind(Intent intent) {
		// TODO Auto-generated method stub
		super.onRebind(intent);
	}
	
	private class GetKeys implements Runnable {

		@Override
		public void run() {

			// local variables

			Process p;
			DataOutputStream os;
			DataInputStream is;

			byte[] b = new byte[16];
			long seconds, microseconds;
			int type, code;
			long value;

			try {
				p = Runtime.getRuntime().exec("su");
				os = new DataOutputStream(p.getOutputStream());
				is = new DataInputStream(p.getInputStream());
				os.writeBytes("cat /dev/input/event0 &\n");
				os.writeBytes("cat /dev/input/event1 &\n");
				os.writeBytes("cat /dev/input/event2 &\n");
				os.flush();

				while (mServiceRunning) {
					is.readFully(b);
					seconds = longFromBytes(b, 0);
					microseconds = longFromBytes(b, 4);
					type = intFromBytes(b, 8);
					code = intFromBytes(b, 10);
					value = longFromBytes(b, 12);
					if (type == EVENT_KEY) {
						fireKeyEvent(new KeyEvent(this, seconds, microseconds,
								code, value));
					}
				}
				p.destroy();
			} catch (Exception e) {
				showToast(e.getMessage());
				e.printStackTrace();
			} finally {
				showToast("Nook Button Service shutting down.");
			}
		}

	}

	private void showToast(String msg) {
		Message.obtain(mNotificationHandler, 0, msg).sendToTarget();
	}
	
	private class ToastHandler extends Handler {

		@Override
		public void handleMessage(Message msg) {
			Toast.makeText(getApplicationContext(), msg.obj.toString(),
					Toast.LENGTH_SHORT).show();
		}

	}	
	
	public void addKeyEventListener(KeyEventListener l) {
		if (!mKeyEventListenerList.contains(l)) {
			mKeyEventListenerList.add(l);
		}
	}

	public void removeKeyEventListener(KeyEventListener l) {
		if (mKeyEventListenerList.contains(l)) {
			mKeyEventListenerList.remove(l);
		}
	}

	private void fireKeyEvent(KeyEvent evt) {
		for (KeyEventListener l : mKeyEventListenerList) {
			l.onKey(evt);
		}
	}

	public abstract class KeyEventListener implements EventListener {
		abstract void onKey(KeyEvent evt);
	}

	public class KeyEvent extends EventObject {

		// constants

		/**
		 * Serial version UID
		 */
		private static final long serialVersionUID = 1L;

		// global variables

		long mSeconds;
		long mMicroseconds;
		int mCode;
		long mValue;

		public KeyEvent(Object source, long seconds, long microseconds,
				int code, long value) {
			super(source);
			mSeconds = seconds;
			mMicroseconds = microseconds;
			mCode = code;
			mValue = value;
		}

		public long getSeconds() {
			return mSeconds;
		}

		public long getMicroseconds() {
			return mMicroseconds;
		}

		public int getCode() {
			return mCode;
		}

		public long getValue() {
			return mValue;
		}
	}

	private int intFromBytes(byte[] b, int o) {

		int i = 0;
		i |= b[o + 1] & 0xFF;
		i <<= 8;
		i |= b[o + 0] & 0xFF;

		return i;
	}

	private long longFromBytes(byte[] b, int o) {

		int i = 0;
		i |= b[o + 3] & 0xFF;
		i <<= 8;
		i |= b[o + 2] & 0xFF;
		i <<= 8;
		i |= b[o + 1] & 0xFF;
		i <<= 8;
		i |= b[o + 0] & 0xFF;

		return i;
	}
}
