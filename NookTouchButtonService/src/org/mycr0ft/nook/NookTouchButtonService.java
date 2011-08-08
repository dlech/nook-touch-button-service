package org.mycr0ft.nook;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.EventListener;
import java.util.EventObject;
import java.util.List;
import java.util.Vector;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningTaskInfo;
import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.WindowManager;
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
	public static final long VALUE_KEY_PRESSED = 1;
	/**
	 * Key released
	 */
	public static final long VALUE_KEY_RELEASED = 0;

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
	private List<LowLevelKeyEventListener> mLowLevelKeyEventListenerList;
	private Handler mNotificationHandler;
	private Instrumentation mInstrumentation;

	@Override
	public void onCreate() {
		super.onCreate();
		// TODO check that this is a Nook Touch and abort if it is not
		
		mLowLevelKeyEventListenerList = new Vector<LowLevelKeyEventListener>();

		mNotificationHandler = new ToastHandler();
		mInstrumentation = new Instrumentation();

		mServiceRunning = true;
		mGetKeysThread = new Thread(new GetKeys());
		mGetKeysThread.start();
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {

		addLowLevelKeyEventListener(new LowLevelKeyEventListener() {

			@Override
			void onKey(LowLevelKeyEvent evt) {
				try {
					ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
					ComponentName topActivity = am.getRunningTasks(1).get(0).topActivity;
					if ((evt.getScanCode() == SCANCODE_TOP_LEFT)
							&& (evt.getValue() == VALUE_KEY_RELEASED)) {
						Intent i = new Intent(Intent.ACTION_MAIN);
						i.setComponent(new ComponentName(
								"com.android.launcher",
								"com.android.launcher.Launcher"));
						i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
						startActivity(i);
					}
					if ((evt.getScanCode() == SCANCODE_TOP_RIGHT)
							&& (evt.getValue() == VALUE_KEY_RELEASED)) {
						RunningTaskInfo info = am.getRunningTasks(1).get(0);
						showToast("Description: " + info.description
								+ "\nBase Activity: "
								+ info.baseActivity.flattenToShortString()
								+ "\nTop Activity: "
								+ info.topActivity.flattenToShortString());
					}
					if (evt.getScanCode() == SCANCODE_BOTTOM_RIGHT) {
						int action = KeyEvent.ACTION_DOWN;						
						if (evt.getValue() == VALUE_KEY_RELEASED) {
							action = KeyEvent.ACTION_UP;
							sendKeys(new KeyEvent(action, KeyEvent.KEYCODE_MENU));
						}
						
					}
				} catch (Exception e) {
					// ignore errors, so hopefully service does not crash
					showToast(e.getMessage()); // show message for debugging
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

	/**
	 * This class is the loop that runs to capture key events from
	 * /dev/input/event* Events can be monitored using the
	 * {@link NookTouchButtonService.LowLevelKeyEventListener}
	 * 
	 */
	private class GetKeys implements Runnable {

		@Override
		public void run() {

			// local variables

			Process p = null;
			DataOutputStream os;
			DataInputStream is;

			byte[] b = new byte[16];
			long seconds, microseconds;
			int type, code;
			long value;

			try {
				// get super user privileges and listen to /dev/input/event*
				p = Runtime.getRuntime().exec("su");
				os = new DataOutputStream(p.getOutputStream());
				is = new DataInputStream(p.getInputStream());
				// event0 is the TWL4030 Keypad which consists of the 4 page
				// turn buttons
				os.writeBytes("cat /dev/input/event0 &\n");
				// event 1 is the gpio-keys which consists of the power and the
				// n buttons
				os.writeBytes("cat /dev/input/event1 &\n");
				// event 2 is the touch screen which also generates a key press
				// when touched
				os.writeBytes("cat /dev/input/event2 &\n");
				os.flush();

				// loop until we tell it to stop
				while (mServiceRunning) {
					// read input event and extract data
					is.readFully(b);
					seconds = longFromBytes(b, 0);
					microseconds = longFromBytes(b, 4);
					type = intFromBytes(b, 8);
					code = intFromBytes(b, 10);
					value = longFromBytes(b, 12);
					// filter for key events only
					if (type == EVENT_KEY) {
						fireLowLevelKeyEvent(new LowLevelKeyEvent(this,
								seconds, microseconds, code, value));
					}
				}
			} catch (Exception e) {
				// show error for debugging
				showToast(e.getMessage());
				e.printStackTrace();
			} finally {
				// make sure we kill process
				if (p != null) {
					p.destroy();
				}
				// and notify user that service was stopped/crashed
				showToast("Nook Button Service shutting down.");
			}
		}

	}

	/**
	 * @param scanCode
	 *            scan code of key press to generate
	 * @param value
	 *            one of KEY_PRESSED or KEY_RELEASED
	 */
	private void sendKeys(KeyEvent event) {

		try {
			//mInstrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_MENU);
			
			long seconds = SystemClock.elapsedRealtime() / 1000;
			long micoseconds = (SystemClock.elapsedRealtime() - (seconds * 1000)) * 1000;
			
			FileOutputStream fos = new FileOutputStream("/dev/input/event0");
			DataOutputStream dos = new DataOutputStream(fos);
			dos.writeLong(seconds);
			dos.writeLong(micoseconds);
			dos.writeInt(EVENT_KEY);
			dos.writeInt(229);
			dos.writeLong(VALUE_KEY_PRESSED);
			dos.flush();
			dos.writeLong(seconds);
			dos.writeLong(micoseconds);
			dos.writeInt(EVENT_KEY);
			dos.writeInt(229);
			dos.writeLong(VALUE_KEY_RELEASED);
			dos.flush();
			dos.close();

		} catch (Exception e) {
			// show error for debugging
			showToast(e.getMessage());
			e.printStackTrace();
		}
	}

	private void showToast(String msg) {
		Message.obtain(mNotificationHandler, 0, msg).sendToTarget();
	}

	private class ToastHandler extends Handler {

		@Override
		public void handleMessage(Message msg) {
			Toast.makeText(getApplicationContext(), msg.obj.toString(),
					Toast.LENGTH_LONG).show();
		}

	}

	public void addLowLevelKeyEventListener(LowLevelKeyEventListener l) {
		if (!mLowLevelKeyEventListenerList.contains(l)) {
			mLowLevelKeyEventListenerList.add(l);
		}
	}

	public void removeKeyEventListener(LowLevelKeyEventListener l) {
		if (mLowLevelKeyEventListenerList.contains(l)) {
			mLowLevelKeyEventListenerList.remove(l);
		}
	}

	private void fireLowLevelKeyEvent(LowLevelKeyEvent evt) {
		for (LowLevelKeyEventListener l : mLowLevelKeyEventListenerList) {
			l.onKey(evt);
		}
	}

	public abstract class LowLevelKeyEventListener implements EventListener {
		abstract void onKey(LowLevelKeyEvent evt);
	}

	/**
	 * This class is used to encapsulate low level key events from
	 * /dev/input/event*
	 * 
	 */
	public class LowLevelKeyEvent extends EventObject {

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

		/**
		 * @param source
		 *            the object that triggered the event
		 * @param seconds
		 *            seconds (since boot) when the event occurred
		 * @param microseconds
		 *            microseconds (since boot) when the event occurred
		 * @param scanCode
		 *            the scan code of the key that was pressed
		 * @param value
		 *            one of {@link VALUE_KEY_PRESSED} or
		 *            {@link VALUE_KEY_RELEASED}
		 * 
		 */
		public LowLevelKeyEvent(Object source, long seconds, long microseconds,
				int scanCode, long value) {
			super(source);
			mSeconds = seconds;
			mMicroseconds = microseconds;
			mCode = scanCode;
			mValue = value;
		}

		/**
		 * @return seconds (since boot) when the event occurred
		 */
		public long getSeconds() {
			return mSeconds;
		}

		/**
		 * @return microseconds (since boot) when the event occurred
		 */
		public long getMicroseconds() {
			return mMicroseconds;
		}

		/**
		 * @return scan code of key that was pressed
		 */
		public int getScanCode() {
			return mCode;
		}

		/**
		 * @return should be VALUE_KEY_PRESSED or VALUE_KEY_RELEASED
		 */
		public long getValue() {
			return mValue;
		}
	}

	/**
	 * @param b
	 *            byte array of length = offset + 4
	 * @param offset
	 *            offset from start of byte array
	 * @return integer from bytes
	 */
	private int intFromBytes(byte[] b, int offset) {

		int i = 0;
		i |= b[offset + 1] & 0xFF;
		i <<= 8;
		i |= b[offset + 0] & 0xFF;

		return i;
	}

	/**
	 * @param b
	 *            byte array of length = offset + 4
	 * @param offset
	 *            offset from start of byte array
	 * @return long integer from bytes
	 */
	private long longFromBytes(byte[] b, int offset) {

		long l = 0;
		l |= b[offset + 3] & 0xFF;
		l <<= 8;
		l |= b[offset + 2] & 0xFF;
		l <<= 8;
		l |= b[offset + 1] & 0xFF;
		l <<= 8;
		l |= b[offset + 0] & 0xFF;

		return l;
	}
}
