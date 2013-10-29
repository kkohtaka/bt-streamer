// Copyright (c) 2013 Kazumasa Kohtaka. All rights reserved.
// This file is available under the MIT license.

package org.kohtaka.btstreamer;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

public class DeviceListActivity extends Activity {

  private static final String TAG = "DeviceListActivity";

  public static String EXTRA_DEVICE_ADDRESS = "device_address";

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    Intent intent = new Intent();
    intent.putExtra(EXTRA_DEVICE_ADDRESS, "00:1B:DC:05:FF:1B");

    setResult(Activity.RESULT_OK, intent);
    finish();
  }
}

