package net.opencurlybraces.android.projects.wifihandler.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import net.opencurlybraces.android.projects.wifihandler.service.ScanResultHandlerService;

/**
 * {@link BroadcastReceiver} filtered on {@link android.net.wifi
 * .WifiManager#SCAN_RESULTS_AVAILABLE_ACTION}
 * system action.
 *
 * @author Chris Carneiro
 */
public class WifiScanResultsReceiver extends BroadcastReceiver {
    private static final String TAG = WifiScanResultsReceiver.class.getSimpleName();

    @Override
    public void onReceive(Context context, Intent intent) {

        Log.d(TAG, "SCAN_RESULTS received");
        Intent handleResultsIntent = new Intent(context, ScanResultHandlerService.class);
        handleResultsIntent.setAction(ScanResultHandlerService.ACTION_HANDLE_WIFI_SCAN_RESULTS);
        context.startService(handleResultsIntent);
    }
}
