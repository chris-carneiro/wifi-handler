package net.opencurlybraces.android.projects.wifitoggler.ui;

import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.RelativeLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;

import net.opencurlybraces.android.projects.wifitoggler.R;
import net.opencurlybraces.android.projects.wifitoggler.WifiToggler;
import net.opencurlybraces.android.projects.wifitoggler.data.table.SavedWifi;
import net.opencurlybraces.android.projects.wifitoggler.service.WifiTogglerService;
import net.opencurlybraces.android.projects.wifitoggler.ui.fragments.DisabledWifiListFragment;
import net.opencurlybraces.android.projects.wifitoggler.ui.fragments.InfoMessageFragment;
import net.opencurlybraces.android.projects.wifitoggler.ui.fragments.SavedWifiListFragment;
import net.opencurlybraces.android.projects.wifitoggler.util.PrefUtils;
import net.opencurlybraces.android.projects.wifitoggler.util.SnackBarUndoActionDataHandler;
import net.opencurlybraces.android.projects.wifitoggler.util.StartupUtils;

import java.util.Observable;
import java.util.Observer;

public class SavedWifiListActivity extends SavedWifiListActivityAbstract implements
        CompoundButton.OnCheckedChangeListener,
        View.OnClickListener, Observer, FragmentManager.OnBackStackChangedListener {

    private static final String TAG = "SavedWifiList";
    public static final String AUTO_TOGGLE_IS_OFF = "0";
    public static final String AUTO_TOGGLE_IS_ON = "1";


    private TextView mWifiTogglerSwitchLabel = null;
    private Switch mWifiTogglerActivationSwitch = null;
    private RelativeLayout mBanner = null;

    private boolean mShowingDisabledWifi = false;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_saved_wifi_list);

        if (savedInstanceState == null) {

            FragmentManager fragmentManager = getFragmentManager();
            FragmentTransaction transaction = fragmentManager.beginTransaction();
            SavedWifiListFragment fragment = new SavedWifiListFragment();
            transaction.add(R.id.wifi_list_fragment_container, fragment);
            transaction.commit();
        }

        bindListView();
        bindViews();
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null)
            actionBar.setDisplayHomeAsUpEnabled(false);
    }

    @Override
    protected void onStart() {
        super.onStart();
        WifiToggler.registerSettingObserver(this);
    }

    @Override
    protected void onStop() {
        super.onStop();
        WifiToggler.unRegisterSettingObserver(this);
    }

    @Override
    protected void onResume() {
        Log.d(TAG, "onResume mShowingDisabledWifi=" + mShowingDisabledWifi);
        /** TODO restore value
         onResume, retain instance or use fragment.isVisible() instead or maybe move this field in
         the fragment itself **/
        super.onResume();

        startupCheck();
        handleNotification(mWifiTogglerActivationSwitch.isChecked());
        handleBannerDisplay();
        doSystemSettingsCheck();
        registerReceivers();
        switchActivation();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_disabled_wifi) {
            showDisabledWifis();

            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {

        MenuItem item = menu.findItem(R.id.action_disabled_wifi);
        boolean whenTogglerIsActive = PrefUtils.isWifiTogglerActive(this);
        item.setVisible(whenTogglerIsActive);
//        item.setEnabled(whenTogglerIsActive);
        item.setTitle(mShowingDisabledWifi ? R.string
                .action_enabled_wifis : R.string
                .action_disabled_wifis);

//        if (!whenTogglerIsActive) {
//            Toast.makeText(this, "To enable disabled Wi-Fis menu action,  activate Wi-Fi Toggler",
//                    Toast.LENGTH_LONG).show();
//        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceivers();
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        Log.d(TAG, "onCheckedChanged");
        switch (buttonView.getId()) {
            case R.id.wifi_toggler_activation_switch:
                handleSwitchLabelValue(isChecked);
                handleNotification(isChecked);

                if (isChecked) {
                    FragmentManager fragmentManager = getFragmentManager();
                    FragmentTransaction transaction = fragmentManager.beginTransaction();
                    Fragment fragment = SavedWifiListFragment.newInstance("");
                    transaction.setCustomAnimations(
                            R.animator.card_flip_right_in,
                            R.animator.card_flip_right_out,
                            R.animator.card_flip_left_in,
                            R.animator.card_flip_left_out);
                    transaction.replace(R.id.wifi_list_fragment_container, fragment)
                            .addToBackStack(null);
                    transaction.commit();
                } else {
                    FragmentManager fragmentManager = getFragmentManager();
                    FragmentTransaction transaction = fragmentManager
                            .beginTransaction();
                    Fragment fragment = InfoMessageFragment.newInstance(getString(R.string
                            .wifi_list_info_when_wifi_toggler_off));
                    transaction.setCustomAnimations(
                            R.animator.card_flip_right_in,
                            R.animator.card_flip_right_out,
                            R.animator.card_flip_left_in,
                            R.animator.card_flip_left_out);
                    transaction.replace(R.id.wifi_list_fragment_container, fragment).commit();


                }
                mShowingDisabledWifi = false;
                break;
            default:
                break;
        }

    }

    @Override
    public void onClick(View v) {
        Log.d(TAG, "View Id=" + v.getId());
        switch (v.getId()) {
            case R.id.wifi_toggler_message_banner:
                launchSystemSettingsCheckActivity();
                break;
            default:
                break;
        }
    }


    protected void showDisabledWifis() {

        if (mShowingDisabledWifi) {
            getFragmentManager().popBackStack();
            mShowingDisabledWifi = false;
            return;
        }


        Fragment fragment = DisabledWifiListFragment.newInstance("");
        FragmentManager fragmentManager = getFragmentManager();
        FragmentTransaction transaction = fragmentManager.beginTransaction();
        transaction.setCustomAnimations(
                R.animator.card_flip_right_in,
                R.animator.card_flip_right_out,
                R.animator.card_flip_left_in,
                R.animator.card_flip_left_out);
        transaction.replace(R.id.wifi_list_fragment_container, fragment)
                .addToBackStack(null);
        transaction.commit();

        mShowingDisabledWifi = true;
    }

    @Override
    public void onBackPressed() {
        if (mShowingDisabledWifi) {
            getFragmentManager().popBackStack();
            mShowingDisabledWifi = false;

        } else {
            super.onBackPressed();
        }
    }


    @Override
    protected void bindViews() {
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        mWifiTogglerSwitchLabel = (TextView) findViewById(R.id.wifi_toggler_switch_label);
        mWifiTogglerActivationSwitch = (Switch) findViewById(R.id.wifi_toggler_activation_switch);
        mWifiTogglerActivationSwitch.setOnCheckedChangeListener(this);
        TextView mBannerContent = (TextView) findViewById(R.id.wifi_toggler_message_banner_content);
        mBannerContent.setEllipsize(TextUtils.TruncateAt.MARQUEE);
        mBannerContent.setSingleLine(true);
        mBannerContent.setSelected(true);
        mBanner = (RelativeLayout) findViewById(R.id.wifi_toggler_message_banner);
        mBanner.setOnClickListener(this);

    }

    @Override
    protected void handleSnackBar(int reverseSortedPosition) {
        Cursor cursor = (Cursor) mSavedWifiCursorAdapter.getItem(reverseSortedPosition);
        String ssid = cursor.getString(cursor.getColumnIndexOrThrow(SavedWifi.SSID));

        String confirmationMessage = formatSnackBarMessage(ssid, R.string
                .wifi_disabled_confirmation_bottom_overlay_content);
        SnackBarUndoActionDataHandler.UndoData undoData = prepareSnackBarUndoDataObject
                (reverseSortedPosition, true);
        SnackBarUndoActionDataHandler snackBarUndoHelper = new SnackBarUndoActionDataHandler
                (this, undoData);

        //        showUndoSnackBar(confirmationMessage, snackBarUndoHelper);
    }

    private void startupCheck() {
        int startupMode = StartupUtils.appStartMode(this);

        switch (startupMode) {
            case StartupUtils.FIRST_TIME:
            case StartupUtils.FIRST_TIME_FOR_VERSION:
                handleFirstLaunch();
                break;
            case StartupUtils.NORMAL:
                handleNormalStartup();
                break;
            default:
                break;
        }
    }

    private void handleFirstLaunch() {
        Log.d(TAG, "handleFirstLaunch");
        if (WifiToggler.hasWrongSettingsForFirstLaunch()) {
            launchStartupCheckActivity();
        } else {
            PrefUtils.markSettingsCorrectAtFirstLaunch(this);
            if (!PrefUtils.isSavedWifiInsertComplete(this)) {
                loadSavedWifiIntoDatabase();
            }
        }
    }

    private void unregisterReceivers() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mNotificationActionsReceiver);
    }

    private void loadSavedWifiIntoDatabase() {
        Intent insertSavedWifi = new Intent(this, WifiTogglerService.class);
        insertSavedWifi.setAction(WifiTogglerService.ACTION_HANDLE_SAVED_WIFI_INSERT);
        startService(insertSavedWifi);
    }

    private void handleNormalStartup() {
        Log.d(TAG, "handleNormalStartup");
        if (!PrefUtils.wereSettingsCorrectAtFirstLaunch(this) && WifiToggler
                .hasWrongSettingsForFirstLaunch()) {
            launchStartupCheckActivity();
        } else {
            if (!PrefUtils.isSavedWifiInsertComplete(this)) {
                loadSavedWifiIntoDatabase();
            }
        }
    }

    private void launchStartupCheckActivity() {
        Log.d(TAG, "launchStartupCheckActivity");
        Intent startupCheck = new Intent(this, StartupSettingsCheckActivity.class);
        startActivity(startupCheck);
    }

    private void launchSystemSettingsCheckActivity() {
        Log.d(TAG, "launchSystemSettingsCheckActivity");
        Intent settingsCheck = new Intent(this, SystemSettingsCheckActivity.class);
        startActivity(settingsCheck);
    }

    private void handleNotification(boolean isChecked) {
        Log.d(TAG, "handleNotification=" + isChecked);
        if (isChecked) {
            buildWifiTogglerStateNotification(WifiTogglerService
                    .ACTION_HANDLE_ACTIVATE_WIFI_HANDLER);
        } else {
            buildWifiTogglerStateNotification(WifiTogglerService
                    .ACTION_HANDLE_PAUSE_WIFI_HANDLER);
        }
    }

    private void buildWifiTogglerStateNotification(String actionHandleActivateWifiHandler) {
        Log.d(TAG, "buildWifiTogglerStateNotification");
        Intent startForegroundNotificationIntent = new Intent(this, WifiTogglerService.class);
        startForegroundNotificationIntent.setAction(actionHandleActivateWifiHandler);
        startService(startForegroundNotificationIntent);
    }

    private void handleSwitchLabelValue(boolean isChecked) {
        Log.d(TAG, "handleSwitchLabelValue=" + isChecked);
        if (isChecked) {
            String on = getString(R.string.on_wifi_toggler_switch_label_value);
            mWifiTogglerSwitchLabel.setText(on);
        } else {
            String off = getString(R.string.off_wifi_toggler_switch_label_value);
            mWifiTogglerSwitchLabel.setText(off);
        }
    }


    private void switchActivation() {
        Log.d(TAG, "switchActivation");
        if (PrefUtils.isWifiTogglerActive(this)) {
            mWifiTogglerActivationSwitch.setChecked(true);
        } else {
            mWifiTogglerActivationSwitch.setChecked(false);
        }
    }

    private void registerReceivers() {
        Log.d(TAG, "registerReceivers");
        registerNotificationReceiver();
    }

    private void handleBannerDisplay() {
        Log.d(TAG, "handleBannerDisplay");
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (WifiToggler.hasWrongSettingsForAutoToggle()) {
                    mBanner.setVisibility(View.VISIBLE);
                } else {
                    mBanner.setVisibility(View.GONE);
                }
            }
        });
    }

    private void registerNotificationReceiver() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(WifiTogglerService.ACTION_HANDLE_NOTIFICATION_ACTION_ACTIVATE);
        intentFilter.addAction(WifiTogglerService.ACTION_HANDLE_NOTIFICATION_ACTION_PAUSE);
        LocalBroadcastManager.getInstance(this).registerReceiver(mNotificationActionsReceiver,
                intentFilter);
    }

    private BroadcastReceiver mNotificationActionsReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "mNotificationActionsReceiver Intent action=" + intent.getAction());
            if (WifiTogglerService.ACTION_HANDLE_NOTIFICATION_ACTION_ACTIVATE.equals(intent
                    .getAction())) {
                mWifiTogglerActivationSwitch.setChecked(true);
            } else if (WifiTogglerService.ACTION_HANDLE_NOTIFICATION_ACTION_PAUSE.equals(intent
                    .getAction())) {
                mWifiTogglerActivationSwitch.setChecked(false);
            }
        }
    };

    @Override
    public void update(Observable observable, Object data) {
        handleBannerDisplay();
    } // can be
    // called back from a worker thread

    // TODO move to util class
    private void doSystemSettingsCheck() {
        Intent checkSettings = new Intent(this, WifiTogglerService.class);
        checkSettings.setAction(WifiTogglerService.ACTION_STARTUP_SETTINGS_PRECHECK);
        startService(checkSettings);
    }

    @Override
    public void onConnected(Bundle bundle) {
    }

    @Override
    public void onConnectionSuspended(int i) {
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
    }

    @Override
    public void onBackStackChanged() {
        //TODO handle menu titles
    }
}
