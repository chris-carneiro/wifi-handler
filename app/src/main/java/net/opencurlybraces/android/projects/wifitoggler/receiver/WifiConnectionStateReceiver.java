package net.opencurlybraces.android.projects.wifitoggler.receiver;

import android.content.BroadcastReceiver;
import android.content.ContentProviderResult;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;

import net.opencurlybraces.android.projects.wifitoggler.Config;
import net.opencurlybraces.android.projects.wifitoggler.data.DataAsyncQueryHandler;
import net.opencurlybraces.android.projects.wifitoggler.data.table.SavedWifi;
import net.opencurlybraces.android.projects.wifitoggler.service.WifiTogglerService;
import net.opencurlybraces.android.projects.wifitoggler.util.DeletedSavedWifiHandlerTask;
import net.opencurlybraces.android.projects.wifitoggler.util.NetworkUtils;
import net.opencurlybraces.android.projects.wifitoggler.util.PrefUtils;

import java.lang.ref.WeakReference;

/**
 * Created by chris on 30/06/15.
 */
public class WifiConnectionStateReceiver extends BroadcastReceiver implements
        DataAsyncQueryHandler.AsyncQueryListener {
    private static final String TAG = "WifiConnectionReceiver";


    public static final String EXTRA_CURRENT_SSID = "net.opencurlybraces.android" +
            ".projects.wifitoggler.receiver.current_ssid";


    private DataAsyncQueryHandler mDataAsyncQueryHandler = null;
    private ScheduleDisableWifi mScheduleDisableWifi = null;
    private static final String[] PROJECTION_SSID = new String[]{SavedWifi._ID, SavedWifi
            .SSID};

    private Context mContext = null;

    @Override
    public void onReceive(Context context, Intent intent) {

        if (PrefUtils.isSavedWifiInsertComplete(context)) {
            if (PrefUtils.isWifiTogglerActive(context)) {
                lazyInit(context);
                SupplicantState state = intent.getParcelableExtra(WifiManager
                        .EXTRA_NEW_STATE);
                updateCurrentWifiState(context, state);
            } else {
                Log.d(TAG, "WifiToggler inactive ignoring SupplicantStateChanged events");
            }
        }
    }

    private void lazyInit(Context context) {
        if (mDataAsyncQueryHandler == null) {
            mDataAsyncQueryHandler = new DataAsyncQueryHandler(context.getContentResolver(),
                    this);
        }

        mScheduleDisableWifi = new ScheduleDisableWifi(context);
        if (mContext == null) {
            mContext = context;
        }
    }

    private void updateCurrentWifiState(final Context context,
                                        SupplicantState
                                                state) {
        switch (state) {
            case COMPLETED:
                Log.d(TAG, "COMPLETED supplicant state received=");
                WifiManager wifiManager = (WifiManager) context.getSystemService(Context
                        .WIFI_SERVICE);
                WifiInfo wifiInfo = wifiManager.getConnectionInfo();

                String strippedSSID = wifiInfo.getSSID().replace("\"", "");

                mDataAsyncQueryHandler.startQuery(1, strippedSSID, SavedWifi.CONTENT_URI,
                        PROJECTION_SSID,
                        SavedWifi.SSID + "=?", new String[]{strippedSSID}, null);
                break;
            case DISCONNECTED:
                Log.d(TAG, "DISCONNECTED supplicant state received=");
                Intent disconnectSavedWifi = new Intent(context,
                        WifiTogglerService.class);
                disconnectSavedWifi.setAction(WifiTogglerService
                        .ACTION_HANDLE_SAVED_WIFI_UPDATE_DISCONNECT);

                //TODO add check on wifi that were removed manually by the user from the system DB
                context.startService(disconnectSavedWifi);

                new DeletedSavedWifiHandlerTask(context).execute();
                // Might be better to use a handler here...
                scheduleDisableWifi();
                break;
            default:
                // Ignore other wifi states
                Log.d(TAG, "Supplicant state received and ignored=" + state);
                break;
        }
    }


    private void scheduleDisableWifi() {
        mScheduleDisableWifi.sendMessageDelayed(Message.obtain(mScheduleDisableWifi,
                        4),
                Config.INTERVAL_FIVE_SECOND);
    }

    @Override
    public void onBatchInsertComplete(int token, Object cookie, ContentProviderResult[] results) {

    }

    @Override
    public void onQueryComplete(int token, Object availableSsid, Cursor cursor) {
        if (Config.UNKNOWN_SSID.equals(availableSsid)) return;
        String ssidFromDB = connectedWifiExistInDB(cursor);

        Intent insertSavedWifiState = buildUpdateIntentAccordingSsidValue((String) availableSsid,
                ssidFromDB);

        mContext.startService(insertSavedWifiState);
    }

    /**
     * <p>Build an Intent according to whether the ssid the user just connected to, exists in DB
     * .</p> <p>If it exists, an intent to update the existing ssid to the connected state is
     * returned.</p> Otherwise an intent to insert in db the new ssid with the connected state is
     * returned
     *
     * @param currentSsid
     * @param ssidFromDB
     * @return Intent
     */
    private Intent buildUpdateIntentAccordingSsidValue(String currentSsid,
                                                       String ssidFromDB) {
        Log.d(TAG, "buildUpdateIntentAccordingSsidValue currentSsid=" + currentSsid +
                " ssidFromDB=" + ssidFromDB);
        Intent insertSavedWifiState = new Intent(mContext,
                WifiTogglerService.class);
        if (TextUtils.isEmpty(ssidFromDB)) {

            insertSavedWifiState.putExtra(EXTRA_CURRENT_SSID,
                    currentSsid);

            insertSavedWifiState.setAction(WifiTogglerService
                    .ACTION_HANDLE_INSERT_NEW_CONNECTED_WIFI);
        } else {

            insertSavedWifiState.putExtra(EXTRA_CURRENT_SSID,
                    ssidFromDB);

            insertSavedWifiState.setAction(WifiTogglerService
                    .ACTION_HANDLE_SAVED_WIFI_UPDATE_CONNECT);
        }
        return insertSavedWifiState;
    }

    /**
     * Checks whether the ssid the user just connected to already exists in DB.
     *
     * @param cursor
     * @return SSID to update or null to insert
     */
    @Nullable
    private String connectedWifiExistInDB(final Cursor cursor) {
        Log.d(TAG, "connectedWifiExistInDB");
        try {
            if (cursor.moveToFirst()) {
                String ssidFromDb = cursor.getString(cursor.getColumnIndexOrThrow(SavedWifi.SSID));
                cursor.close();
                Log.d(TAG, "Ssid Exists=" + ssidFromDb);
                return ssidFromDb;
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        Log.d(TAG, "New SSID insert needed");
        return null;
    }

    @Override
    public void onUpdateComplete(int token, Object cookie, int result) {

    }

    @Override
    public void onInsertComplete(int token, Object cookie, Uri uri) {

    }

    @Override
    public void onBatchUpdateComplete(int token, Object cookie, ContentProviderResult[] results) {

    }


    public final class ScheduleDisableWifi extends Handler {
        private final WeakReference<Context> mHost;


        public ScheduleDisableWifi(Context host) {
            mHost = new WeakReference<>(host);
        }

        @Override
        public void handleMessage(Message msg) {
            Context host = mHost.get();
            if (host != null) {
                NetworkUtils.disableWifiAdapter(host);
            }
        }
    }

}
