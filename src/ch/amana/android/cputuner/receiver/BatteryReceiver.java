package ch.amana.android.cputuner.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.util.Log;
import ch.amana.android.cputuner.helper.Logger;
import ch.amana.android.cputuner.helper.Notifier;
import ch.amana.android.cputuner.helper.SettingsStorage;
import ch.amana.android.cputuner.model.PowerProfiles;
import ch.amana.android.cputuner.service.BatteryService;

public class BatteryReceiver extends BroadcastReceiver {

	private static Object lock = new Object();
	private static BatteryReceiver receiver = null;

	public static void registerBatteryReceiver(Context context) {
		synchronized (lock) {
			if (receiver == null) {
				IntentFilter batteryLevelFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
				receiver = new BatteryReceiver();
				context.registerReceiver(receiver, batteryLevelFilter);
				Log.w(Logger.TAG, "Registered BatteryReceiver");
			} else {
				Log.i(Logger.TAG, "BatteryReceiver allready registered, not registering again");
			}
		}
	}

	public static void unregisterBatteryReceiver(Context context) {
		synchronized (lock) {
			if (receiver != null) {
				try {
					context.unregisterReceiver(receiver);
				} catch (Throwable e) {
					Log.w(Logger.TAG, "Could not unregister BatteryReceiver", e);
				}
			}
		}
	}

	@Override
	public void onReceive(Context context, Intent intent) {

		Log.d(Logger.TAG, "BatteryReceiver got intent: " + intent.getAction());
		if (Intent.ACTION_BATTERY_CHANGED.equals(intent.getAction())) {
			int rawlevel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
			int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
			int plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
			if (plugged > -1) {
				PowerProfiles.setAcPower(plugged > 0);
			}
			int level = -1;
			if (rawlevel >= 0 && scale > 0) {
				level = (rawlevel * 100) / scale;
			}
			Log.d(Logger.TAG, "Battery Level Remaining: " + level + "%");
			if (level > -1) {
				// handle battery event
				PowerProfiles.setBatteryLevel(level);
			}
			if (SettingsStorage.getInstance().isEnableProfiles()) {
				context.startService(new Intent(context, BatteryService.class));
			}
		} else if (Intent.ACTION_POWER_CONNECTED.equals(intent.getAction())) {
			Notifier.notify(context, "CPU tuner: Power connected", 2);
			PowerProfiles.setAcPower(true);
		} else if (Intent.ACTION_POWER_DISCONNECTED.equals(intent.getAction())) {
			Notifier.notify(context, "CPU tuner: Power disconnected", 2);
			PowerProfiles.setAcPower(false);
		} else if (Intent.ACTION_BATTERY_LOW.equals(intent.getAction())) {
			Notifier.notify(context, "CPU tuner: Battery low", 2);
			PowerProfiles.setBatteryLow(true);
		} else if (Intent.ACTION_BATTERY_OKAY.equals(intent.getAction())) {
			Notifier.notify(context, "CPU tuner: Battery OK", 2);
			PowerProfiles.setBatteryLow(false);
		}
		// android.intent.action.SCREEN_OFF
		// android.intent.action.SCREEN_ON
	}
}