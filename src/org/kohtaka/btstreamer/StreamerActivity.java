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
import android.widget.VideoView;
import android.webkit.WebView;

public class StreamerActivity extends Activity {

  private static final String TAG = "StreamerActivity";

  private static final int REQUEST_CONNECT_DEVICE_INSECURE = 2;
  private static final int REQUEST_ENABLE_BT = 3;

  private VideoView mVideoView = null;
  private WebView mWebView = null;

  private BluetoothAdapter mBtAdapter = null;
  private StreamerService mService = null;

  private final Handler mHandler = new Handler() {
    @Override
    public void handleMessage(Message msg) {
      Log.d(TAG, "message: " + msg.what);
    }
  };

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    setContentView(R.layout.streamer);

    mVideoView = (VideoView)findViewById(R.id.video_view);
    //mVideoView.setVideoPath("http://devimages.apple.com/iphone/samples/bipbop/gear1/prog_index.m3u8");
    //mVideoView.setVideoPath("http://10.0.1.2:8080/consume/first");
    //mVideoView.start();
    //mWebView = (WebView)findViewById(R.id.web_view);
    //mWebView.loadUrl("file:///android_asset/index.html");
    //mWebView.loadUrl("http://10.0.1.12/~kkohtaka/index.html");

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
      startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE_INSECURE);
      return true;
    }
    return false;
  }

  public void onActivityResult(int requestCode, int resultCode, Intent data) {
    switch (requestCode) {
    case REQUEST_CONNECT_DEVICE_INSECURE:
      if (resultCode == Activity.RESULT_OK) {
        connectDevice(data, true);
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

  private void connectDevice(Intent data, boolean secure) {
    String address = data.getExtras().getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
    BluetoothDevice device = mBtAdapter.getRemoteDevice(address);
    mService.connect(device);
  }
}

