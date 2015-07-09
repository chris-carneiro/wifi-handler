package net.opencurlybraces.android.projects.wifihandler.service;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import net.opencurlybraces.android.projects.wifihandler.R;
import net.opencurlybraces.android.projects.wifihandler.SavedWifiListActivity;
import net.opencurlybraces.android.projects.wifihandler.data.DataAsyncQueryHandler;
import net.opencurlybraces.android.projects.wifihandler.data.provider.WifiHandlerContract;
import net.opencurlybraces.android.projects.wifihandler.data.table.SavedWifi;
import net.opencurlybraces.android.projects.wifihandler.receiver.WifiAdapterStateReceiver;
import net.opencurlybraces.android.projects.wifihandler.receiver.WifiConnectionStateReceiver;
import net.opencurlybraces.android.projects.wifihandler.receiver.WifiScanResultsReceiver;
import net.opencurlybraces.android.projects.wifihandler.util.NetworkUtils;
import net.opencurlybraces.android.projects.wifihandler.util.PrefUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Service that handles wifi events (enabling/disabling ...) and then sends data results when needed
 * to a view controller. <BR/> Created by chris on 01/06/15.
 */
public class WifiHandlerService extends Service implements DataAsyncQueryHandler
        .AsyncQueryListener, NetworkUtils.SavedWifiConfigurationListener {

    private static final String TAG = "WifiHandlerService";

    private static final String SERVICE_ACTION_PREFIX = "net.opencurlybraces.android" +
            ".projects.wifihandler.service.action.";

    /**
     * Intent actions reserved to switch button
     */
    public static final String ACTION_HANDLE_PAUSE_WIFI_HANDLER = SERVICE_ACTION_PREFIX +
            "action.ACTION_HANDLE_PAUSE_WIFI_HANDLER";

    public static final String ACTION_HANDLE_ACTIVATE_WIFI_HANDLER =
            SERVICE_ACTION_PREFIX + "ACTION_HANDLE_ACTIVATE_WIFI_HANDLER";

    /**
     * Intent actions reserved to notification actions
     */
    public static final String ACTION_HANDLE_NOTIFICATION_ACTION_ACTIVATE = SERVICE_ACTION_PREFIX
            + "ACTION_HANDLE_NOTIFICATION_ACTION_ACTIVATE";

    public static final String ACTION_HANDLE_NOTIFICATION_ACTION_PAUSE = SERVICE_ACTION_PREFIX +
            "ACTION_HANDLE_NOTIFICATION_ACTION_PAUSE";

    public static final String ACTION_HANDLE_SAVED_WIFI_INSERT = SERVICE_ACTION_PREFIX +
            "ACTION_HANDLE_SAVED_WIFI_INSERT";

    public static final String ACTION_HANDLE_SAVED_WIFI_UPDATE_CONNECT = SERVICE_ACTION_PREFIX +
            "ACTION_HANDLE_SAVED_WIFI_UPDATE_CONNECT";

    public static final String ACTION_HANDLE_SAVED_WIFI_UPDATE_DISCONNECT = SERVICE_ACTION_PREFIX +
            "ACTION_HANDLE_SAVED_WIFI_UPDATE_DISCONNECT";

    private WifiManager mWifiManager;
    private WifiScanResultsReceiver mWifiScanResultsReceiver = null;
    private WifiAdapterStateReceiver mWifiAdapterStateReceiver = null;
    private WifiConnectionStateReceiver mWifiConnectionStateReceiver = null;
    private static final int NOTIFICATION_ID = 100;
    private static final String[] PROJECTION = new String[]{SavedWifi._ID, SavedWifi
            .SSID, SavedWifi.STATUS};

    private DataAsyncQueryHandler mDataAsyncQueryHandler = null;

    private static final int TOKEN_QUERY = 1;
    private static final int TOKEN_UPDATE = 3;
    private static final int TOKEN_INSERT_BATCH = 5;

    @Override
    public void onCreate() {
        Log.d(TAG, "OnCreate");
        super.onCreate();

        lazyInit();

        registerScanResultReceiver();
        registerWifiStateReceiver();
        registerWifiSupplicantStateReceiver();

    }

    private void lazyInit() {
        if (mWifiManager == null) {
            mWifiManager = (WifiManager) getSystemService(Context
                    .WIFI_SERVICE);
        }

        if (mDataAsyncQueryHandler == null) {
            mDataAsyncQueryHandler = new DataAsyncQueryHandler(getContentResolver(), this);
        }

        if (mWifiAdapterStateReceiver == null) {
            mWifiAdapterStateReceiver = new WifiAdapterStateReceiver();
        }

        if (mWifiConnectionStateReceiver == null) {
            mWifiConnectionStateReceiver = new WifiConnectionStateReceiver();
        }
        if (mWifiScanResultsReceiver == null) {
            mWifiScanResultsReceiver = new WifiScanResultsReceiver();
        }
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "OnDestroy");
        unregisterReceiver(mWifiScanResultsReceiver);
        unregisterReceiver(mWifiAdapterStateReceiver);
        unregisterReceiver(mWifiConnectionStateReceiver);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Intent=" + (intent != null ? intent.getAction() : null));
        if (intent == null) return START_REDELIVER_INTENT;

        switch (intent.getAction()) {
            case ACTION_HANDLE_NOTIFICATION_ACTION_ACTIVATE:
                activateWifiHandler();
                buildForegroundNotification();
                sendLocalBroadcastAction
                        (ACTION_HANDLE_NOTIFICATION_ACTION_ACTIVATE);
                break;
            case ACTION_HANDLE_NOTIFICATION_ACTION_PAUSE:
                pauseWifiHandler();
                buildDismissableNotification();
                sendLocalBroadcastAction(ACTION_HANDLE_NOTIFICATION_ACTION_PAUSE);
                stopSelf();
                break;
            case ACTION_HANDLE_PAUSE_WIFI_HANDLER:
                pauseWifiHandler();
                buildDismissableNotification();
                stopSelf();
                break;
            case ACTION_HANDLE_ACTIVATE_WIFI_HANDLER:
                activateWifiHandler();
                buildForegroundNotification();
                break;
            case ACTION_HANDLE_SAVED_WIFI_INSERT:
                handleSavedWifiInsert();
                break;
            case ACTION_HANDLE_SAVED_WIFI_UPDATE_CONNECT:
                insertWifiConnected(intent);
                break;
            case ACTION_HANDLE_SAVED_WIFI_UPDATE_DISCONNECT:
                insertWifiDisconnected();

                break;

        }

        return START_REDELIVER_INTENT;

    }

    private void insertWifiConnected(Intent intent) {
        String ssid = intent.getStringExtra(WifiConnectionStateReceiver
                .EXTRA_CURRENT_SSID);
        ContentValues cv = new ContentValues();
        cv.put(SavedWifi.STATUS, NetworkUtils.WifiAdapterStatus
                .CONNECTED);
        mDataAsyncQueryHandler.startUpdate(TOKEN_UPDATE, null, SavedWifi
                        .CONTENT_URI,
                cv,
                SavedWifi.SSID + "=?", new String[]{ssid});
    }

    private void insertWifiDisconnected() {
        ContentValues values = new ContentValues();
        values.put(SavedWifi.STATUS, NetworkUtils.WifiAdapterStatus.DISCONNECTED);
        mDataAsyncQueryHandler.startUpdate(TOKEN_UPDATE, null, SavedWifi.CONTENT_URI,
                values,
                SavedWifi.STATUS + "=?", new String[]{String.valueOf(NetworkUtils
                        .WifiAdapterStatus.CONNECTED)});
    }

    private void handleSavedWifiInsert() {
        Log.d(TAG, "handleSavedWifiInsert");
        NetworkUtils.getConfiguredWifis(mWifiManager, this);

    }

    private void insertBatchAsync(ArrayList<ContentProviderOperation> batch) {
        if (batch == null) return;
        mDataAsyncQueryHandler.startInsertBatch(TOKEN_INSERT_BATCH, null, WifiHandlerContract
                .AUTHORITY, batch);

    }


    /**
     * Issue an async Query given the selection params. Note that wifiState here is used to pass
     * wifi state value to the #onQueryComplete callback
     *
     * @param wifiState
     * @param where
     * @param whereArgs
     */
    private void startQuery(int wifiState, String where, String[] whereArgs) {
        mDataAsyncQueryHandler.startQuery(TOKEN_QUERY, wifiState, SavedWifi.CONTENT_URI, PROJECTION,
                where,
                whereArgs, null);
    }

    private String getRowIdFromCursor(final Cursor cursor) {
        String rowId = null;
        try {
            if (cursor != null) {
                if (cursor.getCount() > 1) {
                    return null;
                }
                if (cursor.moveToFirst()) {
                    rowId = cursor.getString(0);
                    cursor.close();
                }
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return rowId;
    }

    private void registerScanResultReceiver() {
        Log.d(TAG, "registerScanResultReceiver");
        IntentFilter wifiScanFilter = new IntentFilter();
        wifiScanFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);

        registerReceiver(mWifiScanResultsReceiver, wifiScanFilter);
    }


    private void registerWifiStateReceiver() {
        Log.d(TAG, "registerWifiStateReceiver");
        IntentFilter wifiStateFilter = new IntentFilter();
        wifiStateFilter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);

        registerReceiver(mWifiAdapterStateReceiver, wifiStateFilter);
    }

    private void registerWifiSupplicantStateReceiver() {
        Log.d(TAG, "registerWifiSupplicantStateReceiver");
        IntentFilter wifiSupplicantFilter = new IntentFilter();
        wifiSupplicantFilter.addAction(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION);
        registerReceiver(mWifiConnectionStateReceiver, wifiSupplicantFilter);
    }

    //    private void registerManifestReceiver(Class<? extends BroadcastReceiver> receiverClass) {
    //        PackageManager pm = getPackageManager();
    //        ComponentName compName =
    //                new ComponentName(getApplicationContext(),
    //                        receiverClass);
    //        pm.setComponentEnabledSetting(
    //                compName,
    //                PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
    //                PackageManager.DONT_KILL_APP);
    //    }
    //
    //    private void unregisterManifestReceiver(Class<? extends BroadcastReceiver>
    // receiverClass) {
    //        PackageManager pm = getPackageManager();
    //        ComponentName compName =
    //                new ComponentName(getApplicationContext(),
    //                        receiverClass);
    //        pm.setComponentEnabledSetting(
    //                compName,
    //                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
    //                PackageManager.DONT_KILL_APP);
    //    }


    private void sendLocalBroadcastAction(String action) {
        Intent switchIntent = new Intent();
        switchIntent.setAction(action);
        LocalBroadcastManager.getInstance(this).sendBroadcast(switchIntent);
    }

    private void pauseWifiHandler() {
        PrefUtils.setWifiHandlerActive(this, false);
    }


    private void activateWifiHandler() {
        PrefUtils.setWifiHandlerActive(this, true);
    }


    private void buildDismissableNotification() {
        NotificationManager notifManager = (NotificationManager) getSystemService(Context
                .NOTIFICATION_SERVICE);
        Resources res = getResources();
        Intent notificationIntent = new Intent(this, SavedWifiListActivity.class);
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        PendingIntent intent = PendingIntent.getActivity(this, 0,
                notificationIntent, 0);

        NotificationCompat.Builder notifBuilder = new NotificationCompat.Builder(this)
                .setContentTitle(res.getString(R.string.app_name))
                .setContentText(res.getString(R.string
                        .paused_wifi_handler_notification_context_title))
                .setTicker(res.getString(R.string.disable_notification_ticker_content))
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(intent);

        notifBuilder.addAction(0, res.getString(R.string.enable_action_title)
                , createActivateWifiHandlerIntent());
        Notification notification = notifBuilder.build();
        notifManager.notify(NOTIFICATION_ID, notification);

        stopForeground(false);

    }

    private void buildForegroundNotification() {
        Resources res = getResources();

        Intent notificationIntent = new Intent(this, SavedWifiListActivity.class);
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        PendingIntent intent = PendingIntent.getActivity(this, 0,
                notificationIntent, 0);

        NotificationCompat.Builder notifBuilder = new NotificationCompat.Builder(this)
                .setContentTitle(res.getString(R.string.app_name))
                .setContentText(res.getString(R.string
                        .active_wifi_handler_notification_context_title))
                .setTicker(res.getString(R.string.enable_notification_ticker_content))
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(intent);


        notifBuilder.addAction(0, res.getString(R.string.disable_action_title)
                , createPauseWifiHandlerIntent());

        Notification notification = notifBuilder.build();
        startForeground(NOTIFICATION_ID, notification);

    }

    private PendingIntent createPauseWifiHandlerIntent() {
        Intent pauseIntent = new Intent(ACTION_HANDLE_NOTIFICATION_ACTION_PAUSE,
                null, this, WifiHandlerService.class);

        return PendingIntent.getService(this, 0, pauseIntent,
                PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private PendingIntent createActivateWifiHandlerIntent() {
        Intent activateIntent = new Intent(ACTION_HANDLE_NOTIFICATION_ACTION_ACTIVATE,
                null, this, WifiHandlerService.class);

        return PendingIntent.getService(this, 0, activateIntent,
                PendingIntent.FLAG_UPDATE_CURRENT);
    }

    @Override
    public void onInsertBatchComplete(int token, Object cookie, ContentProviderResult[] results) {
        Log.d(TAG, "onInsertBatchComplete: Async Batch Insert complete, stopping service");
        if (!PrefUtils.isWifiHandlerActive(this)) {
            stopSelf();
        }
    }

    @Override
    public void onQueryComplete(int token, Object cookie, Cursor cursor) {
        Log.d(TAG, "onQueryComplete: Query Complete token=" + token + " cookie=" + cookie);
        try {
            if (cursor == null || cursor.getCount() <= 0) return;

            String rowId = getRowIdFromCursor(cursor);

            Uri uri = SavedWifi.buildConfiguredWifiUri(rowId);

            ContentValues value = new ContentValues(1);
            value.put(SavedWifi.STATUS, (int) cookie);

            startUpdate(cookie, rowId, uri, value);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    /**
     * Issue an async update given the rowid params. Note that wifiState here is used to pass wifi
     * state value to the {@link #onQueryComplete(int, Object, Cursor)} callback
     *
     * @param cookie an object that gets passed to {@link #onUpdateComplete(int, Object, int)}, here
     *               the wifi state is passed
     * @param rowId  the row to update
     * @param uri
     * @param value
     */
    private void startUpdate(Object cookie, String rowId, Uri uri, ContentValues value) {
        mDataAsyncQueryHandler.startUpdate(TOKEN_UPDATE, cookie, uri, value, SavedWifi
                ._ID + "=?", new String[]{rowId});
    }

    @Override
    public void onUpdateComplete(int token, Object cookie, int result) {
        Log.d(TAG, "onUpdateComplete: Async Update complete" );
    }

    @Override
    public void onSavedWifiLoaded(List<WifiConfiguration> savedWifis) {
        Log.d(TAG, "onSavedWifiLoaded savedWifis count" + (savedWifis != null ? savedWifis.size()
                : null));
        try {
            List<ContentProviderOperation> batch = SavedWifi.buildBatch(savedWifis);

            insertBatchAsync((ArrayList<ContentProviderOperation>) batch);
        } catch (IllegalArgumentException e) {
            Log.d(TAG, "Nothing to build");
            //TODO handle
        }
    }
}
