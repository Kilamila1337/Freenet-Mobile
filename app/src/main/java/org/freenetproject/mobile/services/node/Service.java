package org.freenetproject.mobile.services.node;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.ServiceCompat;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

import org.freenetproject.mobile.proxy.Simple;
import org.freenetproject.mobile.receivers.BatteryLevelReceiver;
import org.freenetproject.mobile.receivers.PowerConnectionReceiver;
import org.freenetproject.mobile.ui.notification.Notification;

public class Service extends android.app.Service {
    PowerConnectionReceiver powerConnectionReceiver = new PowerConnectionReceiver();
    BatteryLevelReceiver batteryLevelReceiver = new BatteryLevelReceiver();
    ConnectivityManager connectivityManager = null;
    ConnectivityManager.NetworkCallback networkCallback = null;
    Thread proxyThread = null;
    boolean receiversRegistered = false;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i("Freenet", "Called service onStartCommand");

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        Boolean preserveBattery = sharedPreferences.getBoolean("preserve_battery", true);

        // Register power connection receiver
        if (preserveBattery) {
            // On Android 14+ (targetSdk 34+) receivers must be registered as
            // exported or not; these system broadcasts are not exported.
            IntentFilter powerConnectedFilter = new IntentFilter();
            powerConnectedFilter.addAction(Intent.ACTION_POWER_CONNECTED);
            powerConnectedFilter.addAction(Intent.ACTION_POWER_DISCONNECTED);
            ContextCompat.registerReceiver(
                    this, powerConnectionReceiver, powerConnectedFilter,
                    ContextCompat.RECEIVER_NOT_EXPORTED);

            ContextCompat.registerReceiver(
                    this, batteryLevelReceiver,
                    new IntentFilter(Intent.ACTION_BATTERY_LOW),
                    ContextCompat.RECEIVER_NOT_EXPORTED);
            receiversRegistered = true;
        }

        Boolean preserveData = sharedPreferences.getBoolean("preserve_data",true);
        // Register network callback
        if (preserveData && connectivityManager == null) {
            Context context = getApplicationContext();
            NetworkRequest networkRequest = new NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .build();

            connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            networkCallback = new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(Network network) {
                    Log.d("Freenet", "Resuming service from network change");
                    Manager.getInstance().resumeService(context, Manager.CONTEXT_NETWORK);
                }

                @Override
                public void onLost(Network network) {
                    Log.d("Freenet", "Pausing service from network change");
                    Manager.getInstance().pauseService(context, Manager.CONTEXT_NETWORK);
                }
            };

            connectivityManager.registerNetworkCallback(networkRequest, networkCallback);
        }

        Boolean webAccess = sharedPreferences.getBoolean("web_access",false);
        if (webAccess) {
            if (proxyThread != null) proxyThread.interrupt();
            proxyThread = new Thread(new Simple(), "simple-proxy-server");
            proxyThread.start();
        }

        ServiceCompat.startForeground(
                this,
                1,
                Notification.show(this),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        );

        return android.app.Service.START_STICKY;
    }


    @Override
    public void onDestroy() {
        Log.i("Freenet", "Stopping service");
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getBaseContext());

        Boolean preserveBattery = sharedPreferences.getBoolean("preserve_battery", true);
        if (preserveBattery && receiversRegistered) {
            try {
                unregisterReceiver(powerConnectionReceiver);
                unregisterReceiver(batteryLevelReceiver);
            } catch (IllegalArgumentException e) {
                Log.w("Freenet", "Receiver already unregistered: " + e);
            }
            receiversRegistered = false;
        }

        Boolean preserveData = sharedPreferences.getBoolean("preserve_data",true);
        if (preserveData && connectivityManager != null && networkCallback != null) {
            try {
                connectivityManager.unregisterNetworkCallback(networkCallback);
            } catch (IllegalArgumentException e) {
                Log.w("Freenet", "Network callback already unregistered: " + e);
            }
            connectivityManager = null;
            networkCallback = null;
        }

        Boolean webAccess = sharedPreferences.getBoolean("web_access",false);
        if (webAccess) {
            if (proxyThread != null) proxyThread.interrupt();
        }

        Notification.remove(getApplicationContext());
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE);
        stopSelf();
        super.onDestroy();
    }

    public IBinder onBind(Intent intent) {
        return null;
    }
}
