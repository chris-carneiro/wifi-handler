package net.opencurlybraces.android.projects.wifihandler.ui;

import android.content.Context;
import android.database.Cursor;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import android.widget.RelativeLayout;
import android.widget.TextView;

import net.opencurlybraces.android.projects.wifihandler.R;
import net.opencurlybraces.android.projects.wifihandler.data.table.SavedWifi;
import net.opencurlybraces.android.projects.wifihandler.util.NetworkUtils;

/**
 * Created by chris on 13/06/15.
 */
public class SavedWifiListAdapter extends CursorAdapter {
    private static final String TAG = "SavedWifiListAdapter";

    final LayoutInflater mLayoutInflater;

    public SavedWifiListAdapter(Context context, Cursor c, int flag) {
        super(context, c, flag);
        mLayoutInflater = (LayoutInflater) context.getSystemService(Context
                .LAYOUT_INFLATER_SERVICE);
    }

    static class ViewHolder {
        TextView ssid;
        TextView connected;
    }


    //    @Override
    //    public View getView(int position, View convertView, ViewGroup parent) {
    //        ViewHolder viewHolder = null;
    //        if (convertView == null) {
    //            convertView = ((LayoutInflater) parent.getContext()
    //                    .getSystemService(Context.LAYOUT_INFLATER_SERVICE))
    //                    .inflate(R.layout.configured_wifi_list_row, parent, false);
    //            viewHolder = new ViewHolder();
    //
    //            /**
    //             * Keep a reference of the widgets so that the adapter doesn't call
    //             * findViewById() each time getView() is called
    //             **/
    //            viewHolder.ssid = (TextView) convertView
    //                    .findViewById(R.id.configured_wifi_ssid);
    //            viewHolder.connected = (TextView) convertView
    //                    .findViewById(R.id.configured_wifi_state);
    //
    //            /** Set the reference of the viewHolder on the Item **/
    //            convertView.setTag(viewHolder);
    //        } else {
    //            /** Get the object's reference we set earlier on the convertView **/
    //            viewHolder = (ViewHolder) convertView.getTag();
    //        }
    //
    //        Resources res = convertView.getContext().getResources();
    //
    //        UserWifi userWifi = getItem(position);
    //        viewHolder.ssid.setText(userWifi.mSSID);
    //
    //        String isConnected = (userWifi.mConnected ? "Connected" : "");
    //        if (userWifi.mConnected) {
    //            viewHolder.connected.setTextColor(res.getColor(android.R.color.holo_green_dark));
    //            viewHolder.connected.setVisibility(View.VISIBLE);
    //        } else {
    //            viewHolder.connected.setVisibility(View.GONE);
    //        }
    //        viewHolder.connected.setText(isConnected);
    //
    //        return convertView;
    //    }

    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
        return mLayoutInflater.inflate(R.layout.configured_wifi_list_row, parent, false);
    }

    //TODO optimize using viewHolder
    @Override
    public void bindView(View view, Context context, Cursor cursor) {
        Log.d(TAG, "bindView");
        //        Resources res = context.getResources();

        int ssidColumnIndex = cursor.getColumnIndexOrThrow(SavedWifi.SSID);
        int statusColumnIndex = cursor.getColumnIndexOrThrow(SavedWifi.STATUS);

        String ssidValue = cursor.getString(ssidColumnIndex);
        int status = cursor.getInt(statusColumnIndex);

        TextView ssid = (TextView) view.findViewById(R.id.configured_wifi_ssid);
        ssid.setText(ssidValue);
        ssid.setPadding(15, 0, 0, 0);

        TextView wifiStatus = (TextView) view.findViewById(R.id.configured_wifi_state);
        String statusValue = toStringStatus(context, status);
        wifiStatus.setText(statusValue);
        wifiStatus.setPadding(15, 0, 0, 0);

        RelativeLayout.LayoutParams lp = createLayoutParams(status);
        ssid.setLayoutParams(lp);

    }

    private RelativeLayout.LayoutParams createLayoutParams(int status) {
        RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(RelativeLayout
                .LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
        if (status != NetworkUtils.WifiAdapterStatus.CONNECTED) {
            lp.addRule(RelativeLayout.CENTER_VERTICAL);

        }
        return lp;
    }

    private String toStringStatus(final Context context, int status) {
        String wifiStatus = "";
        switch (status) {
            case NetworkUtils.WifiAdapterStatus.CONNECTED:
                wifiStatus = context.getString(R.string.connected_saved_wifi_status);
                break;
        }
        return wifiStatus;
    }

}