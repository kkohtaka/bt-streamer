// Copyright (c) 2013 Kazumasa Kohtaka. All rights reserved.
// This file is available under the MIT license.

package org.kohtaka.btstreamer;

import android.app.ActionBar;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
//import android.view.WindowManager;
import android.widget.VideoView;
import android.webkit.WebView;

public class StreamerActivity extends Activity {

  private static final String TAG = "StreamerActivity";

  private static final int REQUEST_CONNECT_DEVICE = 2;
  private static final int REQUEST_ENABLE_BT = 3;

  private BluetoothAdapter mBtAdapter = null;
  private StreamerService mService = null;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    setContentView(R.layout.streamer);

    mBtAdapter = BluetoothAdapter.getDefaultAdapter();
    if (mBtAdapter == null) {
      Log.d(TAG, "Bluetooth is not available");
      finish();
    }
  }

  @Override
  public void onStart() {
    super.onStart();

    if (!mBtAdapter.isEnabled()) {
      Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
      startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
    } else {
      if (mService == null) {
        setupService();
      }
    }
  }

  @Override
  public synchronized void onResume() {
    super.onResume();

    //getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

    if (mService != null) {
      if (mService.getState() == StreamerService.STATE_NONE) {
        mService.start();
      }
    }
  }

  @Override
  public synchronized void onPause() {
    super.onPause();
    Log.e(TAG, "- ON PAUSE -");

    //getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
  }

  @Override
  public void onStop() {
    super.onStop();
    Log.e(TAG, "-- ON STOP --");
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    if (mService != null) {
      mService.stop();
    }
    Log.e(TAG, "--- ON DESTROY ---");
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    MenuInflater inflater = getMenuInflater();
    inflater.inflate(R.menu.option_menu, menu);
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    Intent serverIntent = null;
    switch (item.getItemId()) {
    case R.id.insecure_connect_scan:
      serverIntent = new Intent(this, DeviceListActivity.class);
      startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE);
      return true;
    }
    return false;
  }

  public void onActivityResult(int requestCode, int resultCode, Intent data) {
    switch (requestCode) {
    case REQUEST_CONNECT_DEVICE:
      if (resultCode == Activity.RESULT_OK) {
        String address = data.getExtras().getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
        connectDevice(address);
      }
      break;
    case REQUEST_ENABLE_BT:
      if (resultCode == Activity.RESULT_OK) {
      } else {
        Log.d(TAG, "BT not enabled");
        finish();
      }
      break;
    }
  }

  private void setupService() {
    Log.d(TAG, "setupService()");
    mService = new StreamerService();
  }

  private void connectDevice(String address) {
    BluetoothDevice device = mBtAdapter.getRemoteDevice(address);
    mService.connect(device);
  }
}

