package net.opencurlybraces.android.projects.wifihandler.data.table;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.net.wifi.WifiConfiguration;
import android.provider.BaseColumns;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.util.Log;

import net.opencurlybraces.android.projects.wifihandler.data.provider.WifiHandlerContract;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

/**
 * ConfiguredWIfi Table, holds the table of the user's configured wifi along with their state.
 * <p/>
 * Created by chris on 08/06/15.
 */
public class ConfiguredWifi implements BaseColumns {

    private static final String TAG = "ConfiguredWifi";

    private ConfiguredWifi() {
    }

    public static final String TABLE = "configured_wifis";

    public static final String PATH_CONFIGURED_WIFIS = "configured_wifis";

    public static final Uri CONTENT_URI =
            WifiHandlerContract.BASE_CONTENT_URI.buildUpon().appendEncodedPath(PATH_CONFIGURED_WIFIS)
                    .build();

    public static final String CONTENT_TYPE = ContentResolver.CURSOR_DIR_BASE_TYPE
            + "/configured_wifi";
    public static final String CONTENT_ITEM_TYPE = ContentResolver.CURSOR_ITEM_BASE_TYPE +
            "/configured_wifi";


    public static final String SSID = "ssid";
    public static final String AUTO_TOGGLE = "is_auto_toggle";
    public static final String OPEN_WIFI = "is_open_wifi";
    public static final String PREFERRED = "is_preferred";
    public static final String BOOSTED = "is_boosted";
    public static final String IN_RANGE = "is_in_range";
    public static final String LOCKED = "is_locked";
    public static final String STATUS = "status";

    private static final String[] PROJECTION = new String[]{
            _ID,
            SSID,
            AUTO_TOGGLE,
            OPEN_WIFI,
            PREFERRED,
            BOOSTED,
            IN_RANGE,
            LOCKED,
            STATUS
    };


    // Database creation SQL statement
    private static final String CREATE_TABLE = "CREATE TABLE " + ConfiguredWifi.TABLE + " ("
            + _ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
            + SSID + " TEXT NOT NULL DEFAULT UNKNOWN,"
            + AUTO_TOGGLE + " INTEGER NOT NULL DEFAULT 0,"
            + OPEN_WIFI + " INTEGER NOT NULL DEFAULT 0,"
            + PREFERRED + " INTEGER NOT NULL DEFAULT 0,"
            + BOOSTED + " INTEGER NOT NULL DEFAULT 0,"
            + IN_RANGE + " INTEGER NOT NULL DEFAULT 0,"
            + LOCKED + " INTEGER NOT NULL DEFAULT 0,"
            + STATUS + " INTEGER NOT NULL DEFAULT 1"
            + ");";


    /**
     * Creates the table using {@link ConfiguredWifi} fields as columns
     *
     * @param database
     */
    public static void createTable(SQLiteDatabase database) {
        database.execSQL(CREATE_TABLE);
    }

    /**
     * Drops the table and recreates it with a new structure
     *
     * @param database
     * @param oldVersion
     * @param newVersion
     */
    public static void upgrade(SQLiteDatabase database, int oldVersion,
                               int newVersion) {
        Log.w(TAG, "Upgrading database from version "
                + oldVersion + " to " + newVersion
                + ", which will destroy all old data");
        database.execSQL("DROP TABLE IF EXISTS " + TABLE);
        createTable(database);
    }

    /**
     * Checks the {@code projection} passed in against the actual table projection to make sure the
     * query is done on columns that exist
     *
     * @param projection
     */
    public static void checkColumns(String[] projection) {
        if (projection != null) {
            HashSet<String> requestedColumns = new HashSet<>(Arrays.asList(projection));
            HashSet<String> availableColumns = new HashSet<>(Arrays.asList(PROJECTION));
            // check if all columns which are requested are available
            if (!availableColumns.containsAll(requestedColumns)) {
                throw new IllegalArgumentException("Unknown columns in projection");
            }
        }
    }


    /**
     * Build {@link Uri} for requested {@code ConfiguredWifi} {@link #_ID}.
     */
    public static Uri buildConfiguredWifiUri(String configuredWifiId) {
        if (TextUtils.isEmpty(configuredWifiId)) {
            throw new IllegalArgumentException("Unknown ID");
        }
        return CONTENT_URI.buildUpon().appendPath(configuredWifiId).build();
    }

    /**
     * Given the list of {@link WifiConfiguration}, prepares the data to be inserted
     *
     * @param configuredWifis
     * @return List of ContentValues data ready for insertion
     */
    @NonNull
    public static List<ContentValues> buildValuesUsingConfigurations(List<WifiConfiguration>
                                                                             configuredWifis) {
        if (configuredWifis == null || configuredWifis.isEmpty())
            throw new IllegalArgumentException("Nothing to build");

        List<ContentValues> contents = new ArrayList<>(configuredWifis.size());
        ContentValues values = new ContentValues();

        for (WifiConfiguration wifi : configuredWifis) {
            values.put(ConfiguredWifi.SSID, wifi.SSID);
            values.put(ConfiguredWifi.AUTO_TOGGLE, 0);
            values.put(ConfiguredWifi.OPEN_WIFI, 0);
            values.put(ConfiguredWifi.PREFERRED, 0);
            values.put(ConfiguredWifi.BOOSTED, 0);
            values.put(ConfiguredWifi.IN_RANGE, 0);
            values.put(ConfiguredWifi.LOCKED, 0);
            values.put(ConfiguredWifi.STATUS, wifi.status);
            contents.add(values);
        }
        return contents;
    }
}