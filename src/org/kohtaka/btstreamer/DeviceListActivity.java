// Copyright (c) 2013 Kazumasa Kohtaka. All rights reserved.
// This file is available under the MIT license.

package org.kohtaka.btstreamer;

import java.util.Set;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

public class DeviceListActivity extends Activity {

  private static final String TAG = "DeviceListActivity";

  public static String EXTRA_DEVICE_ADDRESS = "device_address";

  private BluetoothAdapter mBtAdapter;
  private ArrayAdapter<String> mPairedDeviceAdapter;
  private ArrayAdapter<String> mNewDeviceAdapter;

  private final OnItemClickListener mDeviceClickListener = new OnItemClickListener() {
    public void onItemClick(AdapterView<?> av, View v, int arg2, long arg3) {
      mBtAdapter.cancelDiscovery();

      String info = ((TextView)v).getText().toString();
      String address = info.substring(info.length() - 17);

      Intent intent = new Intent();
      intent.putExtra(EXTRA_DEVICE_ADDRESS, address);

      setResult(Activity.RESULT_OK, intent);
      finish();
    }
  };

  private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
    @Override
    public void onReceive(Context context, Intent intent) {
      String action = intent.getAction();

      if (BluetoothDevice.ACTION_FOUND.equals(action)) {
        BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

        if (device.getBondState() != BluetoothDevice.BOND_BONDED) {
          mNewDeviceAdapter.add(device.getName() + "\n" + device.getAddress());
        }
      } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
        setProgressBarIndeterminateVisibility(false);
        setTitle(R.string.msg_select_device);

        findViewById(R.id.scan_button).setClickable(true);

        if (mNewDeviceAdapter.getCount() == 0) {
          mNewDeviceAdapter.add(
              getResources().getText(R.string.msg_no_devices).toString());
        }
      }
    }
  };

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
    setContentView(R.layout.device_list);

    //Intent intent = new Intent(this, BTStreamer.class);
    //startService(intent);

    setResult(Activity.RESULT_CANCELED);

    Button scanButton = (Button)findViewById(R.id.scan_button);
    scanButton.setOnClickListener(new OnClickListener() {
      public void onClick(View view) {
        doDiscovery();
        view.setClickable(false);
      }
    });

    mPairedDeviceAdapter = new ArrayAdapter<String>(this, R.layout.device_name);
    ListView pairedDeviceListView = (ListView)findViewById(R.id.paired_devices);
    pairedDeviceListView.setAdapter(mPairedDeviceAdapter);
    pairedDeviceListView.setOnItemClickListener(mDeviceClickListener);

    mNewDeviceAdapter = new ArrayAdapter<String>(this, R.layout.device_name);
    ListView newDeviceListView = (ListView)findViewById(R.id.new_devices);
    newDeviceListView.setAdapter(mNewDeviceAdapter);
    newDeviceListView.setOnItemClickListener(mDeviceClickListener);

    this.registerReceiver(
        mReceiver,
        new IntentFilter(BluetoothDevice.ACTION_FOUND));

    this.registerReceiver(
        mReceiver,
        new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED));

    mBtAdapter = BluetoothAdapter.getDefaultAdapter();

    Set<BluetoothDevice> pairedDevices = mBtAdapter.getBondedDevices();

    if (pairedDevices.size() > 0) {
      for (BluetoothDevice device : pairedDevices) {
        mPairedDeviceAdapter.add(device.getName() + "\n" + device.getAddress());
      }
    } else {
      mPairedDeviceAdapter.add(
          getResources().getText(R.string.msg_no_paired_devices).toString());
    }
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();

    if (mBtAdapter != null) {
      mBtAdapter.cancelDiscovery();
    }

    this.unregisterReceiver(mReceiver);
  }

  private void doDiscovery() {
    setProgressBarIndeterminateVisibility(true);
    setTitle(R.string.msg_scanning);

    if (mBtAdapter.isDiscovering()) {
      mBtAdapter.cancelDiscovery();
    }

    mBtAdapter.startDiscovery();
  }
}

