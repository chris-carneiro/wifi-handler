package net.opencurlybraces.android.projects.wifihandler.ui;

import android.app.LoaderManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.CursorLoader;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.Loader;
import android.content.res.Configuration;
import android.database.Cursor;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.CursorAdapter;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.Switch;
import android.widget.TextView;

import net.opencurlybraces.android.projects.wifihandler.Config;
import net.opencurlybraces.android.projects.wifihandler.R;
import net.opencurlybraces.android.projects.wifihandler.data.table.SavedWifi;
import net.opencurlybraces.android.projects.wifihandler.receiver.AirplaneModeStateReceiver;
import net.opencurlybraces.android.projects.wifihandler.service.WifiHandlerService;
import net.opencurlybraces.android.projects.wifihandler.util.NetworkUtils;
import net.opencurlybraces.android.projects.wifihandler.util.PrefUtils;
import net.opencurlybraces.android.projects.wifihandler.util.StartupUtils;


public class SavedWifiListActivity extends AppCompatActivity implements
        CompoundButton.OnCheckedChangeListener,
        LoaderManager.LoaderCallbacks<Cursor>, DialogInterface.OnClickListener {

    private static final String TAG = "SavedWifiList";

    private TextView mWifiHandlerSwitchLabel = null;
    private Switch mWifiHandlerActivationSwitch = null;
    private ListView mWifiHandlerWifiList = null;
    private TextView mEmptyView = null;
    private RelativeLayout mBanner = null;
    private CursorAdapter mSavedWifiCursorAdapter = null;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_configured_wifi_list);

        mWifiHandlerSwitchLabel = (TextView) findViewById(R.id.wifi_handler_switch_label);
        mWifiHandlerActivationSwitch = (Switch) findViewById(R.id.wifi_handler_activation_switch);
        mWifiHandlerWifiList = (ListView) findViewById(android.R.id.list);
        mEmptyView = (TextView) findViewById(android.R.id.empty);
        mWifiHandlerActivationSwitch.setOnCheckedChangeListener(this);
        mBanner = (RelativeLayout) findViewById(R.id.wifi_handler_message_banner);

        initCursorLoader();
        mSavedWifiCursorAdapter = initAdapter();
    }


    private BroadcastReceiver mNotificationActionsReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "Intent=" + intent.getAction());
            if (WifiHandlerService.ACTION_HANDLE_NOTIFICATION_ACTION_ACTIVATE.equals(intent
                    .getAction())) {
                mWifiHandlerActivationSwitch.setChecked(true);
            } else if (WifiHandlerService.ACTION_HANDLE_NOTIFICATION_ACTION_PAUSE.equals(intent
                    .getAction())) {
                mWifiHandlerActivationSwitch.setChecked(false);
            }
        }
    };


    private BroadcastReceiver mAirplaneModeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            boolean isAirplaneModeOn = intent.getBooleanExtra(AirplaneModeStateReceiver
                    .EXTRAS_AIRPLANE_MODE_STATE, false);

            if (isAirplaneModeOn) {
                if (PrefUtils.areWarningNotificationsEnabled(context)) {
                    NetworkUtils.buildAirplaneNotification(context);
                }
                mBanner.setVisibility(View.VISIBLE);
            } else {
                mWifiHandlerActivationSwitch.setEnabled(true);
                mBanner.setVisibility(View.GONE);
                NetworkUtils.dismissNotification(context, Config
                        .NOTIFICATION_ID_AIRPLANE_MODE);
            }
        }
    };

    @Override
    protected void onResume() {

        super.onResume();
        Log.d(TAG, "onResume");

        startupCheck();
        setListAdapterAccordingToSwitchState();
        registerReceivers();
        showBannerAccordingAirplaneMode();


    }

    private void setListAdapterAccordingToSwitchState() {
        if (PrefUtils.isWifiHandlerActive(this)) {
            mWifiHandlerActivationSwitch.setChecked(true);
            mWifiHandlerWifiList.setAdapter(mSavedWifiCursorAdapter);
        } else {
            mWifiHandlerActivationSwitch.setChecked(false);
        }
    }

    private void registerReceivers() {
        registerNotificationReceiver();
        registerAirplaneModeReceiver();
    }

    private void showBannerAccordingAirplaneMode() {
        if (NetworkUtils.isAirplaneModeEnabled(this)) {
            mBanner.setVisibility(View.VISIBLE);
        } else {
            mBanner.setVisibility(View.GONE);
        }
    }

    private void registerAirplaneModeReceiver() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        registerReceiver(mAirplaneModeReceiver,
                intentFilter);
    }


    private void registerNotificationReceiver() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(WifiHandlerService.ACTION_HANDLE_NOTIFICATION_ACTION_ACTIVATE);
        intentFilter.addAction(WifiHandlerService.ACTION_HANDLE_NOTIFICATION_ACTION_PAUSE);
        LocalBroadcastManager.getInstance(this).registerReceiver(mNotificationActionsReceiver,
                intentFilter);
    }

    @Override
    protected void onPause() {
        super.onPause();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mNotificationActionsReceiver);
        unregisterReceiver(mAirplaneModeReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_configured_wifi_list, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            handleDisplaySettings();
            return true;
        }

        return false;
    }

    private void handleDisplaySettings() {
        Intent preferencesIntent = new Intent(this, SettingsActivity.class);
        startActivity(preferencesIntent);
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        switch (buttonView.getId()) {
            case R.id.wifi_handler_activation_switch:
                handleSwitchLabelValue(isChecked);
                handleSavedWifiListLoading(isChecked);
                handleNotification(isChecked);
                break;
        }

    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        Log.d(TAG, "onConfigurationChanged");
        boolean isChecked = PrefUtils.isWifiHandlerActive(this);
        handleSavedWifiListLoading(isChecked);
    }


    private void handleNotification(boolean isChecked) {
        if (isChecked) {
            Intent startForegroundNotificationIntent = new Intent(this, WifiHandlerService.class);
            startForegroundNotificationIntent.setAction(WifiHandlerService
                    .ACTION_HANDLE_ACTIVATE_WIFI_HANDLER);
            startService(startForegroundNotificationIntent);
        } else {
            Intent dismissableNotificationIntent = new Intent(this, WifiHandlerService.class);
            dismissableNotificationIntent.setAction(WifiHandlerService
                    .ACTION_HANDLE_PAUSE_WIFI_HANDLER);
            startService(dismissableNotificationIntent);
        }

    }

    private void handleSwitchLabelValue(boolean isChecked) {
        if (isChecked) {
            String on = getString(R.string.on_wifi_handler_switch_label_value);
            mWifiHandlerSwitchLabel.setText(on);
        } else {
            String off = getString(R.string.off_wifi_handler_switch_label_value);
            mWifiHandlerSwitchLabel.setText(off);
        }
    }


    private void handleSavedWifiListLoading(boolean isChecked) {
        if (isChecked) {
            mWifiHandlerWifiList.setAdapter(mSavedWifiCursorAdapter);
        } else {
            mWifiHandlerWifiList.setAdapter(null);
        }
    }


    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        String[] projection = {SavedWifi._ID, SavedWifi.SSID, SavedWifi.STATUS};
        CursorLoader cursorLoader = new CursorLoader(this,
                SavedWifi.CONTENT_URI, projection, null, null, null);
        return cursorLoader;
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        mSavedWifiCursorAdapter.swapCursor(data);
        mWifiHandlerWifiList.setEmptyView(mEmptyView);
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        mSavedWifiCursorAdapter.swapCursor(null);
    }

    private void initCursorLoader() {
        getLoaderManager().initLoader(0, null, this);
    }

    private CursorAdapter initAdapter() {
        Log.d(TAG, "initAdapter");

        if (mSavedWifiCursorAdapter == null) {
            mSavedWifiCursorAdapter = new SavedWifiListAdapter(this, null, 0);
        }
        return mSavedWifiCursorAdapter;
    }


    private void startupCheck() {
        int startupMode = StartupUtils.appStartMode(this);

        switch (startupMode) {
            case StartupUtils.FIRST_TIME:
                Log.d(TAG, "Startup mode: FIRST_TIME");
            case StartupUtils.FIRST_TIME_FOR_VERSION:
                Log.d(TAG, "Startup mode: FIRST_TIME_FOR_VERSION");
                launchStartupCheckActivity();
                break;
            case StartupUtils.NORMAL:

                //TODO handle this case better...
                handleNormalStartup();
                //TODO make hashmapSettingsStatesHolder observable
                Log.d(TAG, "Startup mode: NORMAL");
                break;
        }
    }

    private void handleNormalStartup() {
        if (!PrefUtils.wereSettingsCorrectAtFirstLaunch(this)) {
            mWifiHandlerActivationSwitch.setChecked(false);

            launchStartupCheckActivity();
        }
    }

    private void launchStartupCheckActivity() {
        Intent startupCheck = new Intent(this, StartupCheckActivity.class);
        startActivity(startupCheck);
    }

    //    private void askUserEnableWifi() {
    //        StringBuilder sb = new StringBuilder();
    //        sb.append("The wifi needs to be activated for the first launch, please go to " +
    //                "settings and enable wifi. We won't bother you with this again :)");
    //        AlertDialog.Builder askUserChangeSettings = new AlertDialog.Builder(this);
    //
    //
    //        askUserChangeSettings.setMessage(sb.toString());
    //        askUserChangeSettings.setPositiveButton("Go to settings", new DialogInterface
    //                .OnClickListener() {
    //
    //
    //            @Override
    //            public void onClick(DialogInterface dialog, int which) {
    //                Intent openWifiSettings = new Intent(Settings.ACTION_WIFI_SETTINGS);
    //                SavedWifiListActivity.this.startActivityForResult(openWifiSettings, 0);
    //            }
    //        });
    //        askUserChangeSettings.setNegativeButton("Not now", this);
    //        askUserChangeSettings.show();
    //    }
    //
    //    private void askUserEnableScanAlwaysAvailable() {
    //        StringBuilder sb = new StringBuilder();
    //        sb.append("Passive scan mode, which is the core feature of WifiHandler, is not
    // active, to" +
    //                " enjoy auto-toggle please activate it." +
    //                " ");
    //        AlertDialog.Builder askUserChangeSettings = new AlertDialog.Builder(this);
    //
    //
    //        askUserChangeSettings.setMessage(sb.toString());
    //        askUserChangeSettings.setPositiveButton("Go to settings", new DialogInterface
    //                .OnClickListener() {
    //
    //
    //            @Override
    //            public void onClick(DialogInterface dialog, int which) {
    //                Intent openWifiSettings = new Intent(WifiManager
    //                        .ACTION_REQUEST_SCAN_ALWAYS_AVAILABLE);
    //                SavedWifiListActivity.this.startActivityForResult(openWifiSettings, 0);
    //            }
    //        });
    //        askUserChangeSettings.setNegativeButton("Not now", this);
    //        askUserChangeSettings.show();
    //    }


    @Override
    public void onClick(DialogInterface dialog, int which) {
        dialog.cancel();
    }
}
