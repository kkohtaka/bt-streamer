package org.libsdl.app;

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
import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.egl.EGLSurface;

import android.app.*;
import android.content.*;
import android.view.*;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.view.WindowManager;
import android.widget.AbsoluteLayout;
import android.os.*;
import android.util.Log;
import android.graphics.*;
import android.media.*;
import android.hardware.*;

import org.kohtaka.btstreamer.DeviceListActivity;
import org.kohtaka.btstreamer.R;
import org.kohtaka.btstreamer.StreamerService;

/**
  SDL Activity
*/
public class SDLActivity extends Activity {
  private static final String TAG = "StreamerActivity";

  private static final int REQUEST_CONNECT_DEVICE = 2;
  private static final int REQUEST_ENABLE_BT = 3;

  private BluetoothAdapter mBtAdapter = null;
  private StreamerService mService = null;

  // Keep track of the paused state
  public static boolean mIsPaused = false;

  // Main components
  protected static SDLActivity mSingleton;
  protected static SDLSurface mSurface;
  protected static View mTextEdit;
  protected static ViewGroup mLayout;

  // This is what SDL runs in. It invokes SDL_main(), eventually
  protected static Thread mSDLThread;

  // Audio
  protected static Thread mAudioThread;
  protected static AudioTrack mAudioTrack;

  // EGL objects
  protected static EGLContext  mEGLContext;
  protected static EGLSurface  mEGLSurface;
  protected static EGLDisplay  mEGLDisplay;
  protected static EGLConfig   mEGLConfig;
  protected static int mGLMajor, mGLMinor;

  // Load the .so
  static {
    System.loadLibrary("SDL2");
    System.loadLibrary("main");
  }

  // Setup
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    Log.d("SDL", "onCreate()");
    super.onCreate(savedInstanceState);

    setContentView(R.layout.streamer);

    mBtAdapter = BluetoothAdapter.getDefaultAdapter();
    if (mBtAdapter == null) {
      Log.d(TAG, "Bluetooth is not available");
      finish();
    }

    // So we can call stuff from static callbacks
    mSingleton = this;

    // Set up the surface
    mSurface = new SDLSurface(getApplication());

    mLayout = new AbsoluteLayout(this);
    mLayout.addView(mSurface);

    setContentView(mLayout);
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

  // Events
  @Override
  protected void onPause() {
    Log.v("SDL", "onPause()");

    getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

    super.onPause();
    // Don't call SDLActivity.nativePause(); here, it will be called by SDLSurface::surfaceDestroyed
  }

  @Override
  protected void onResume() {
    Log.v("SDL", "onResume()");
    super.onResume();
    // Don't call SDLActivity.nativeResume(); here, it will be called via SDLSurface::surfaceChanged->SDLActivity::startApp

    if (mService != null) {
      if (mService.getState() == StreamerService.STATE_NONE) {
        mService.start();
      }
    }

    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
  }

  @Override
  public void onLowMemory() {
    Log.v("SDL", "onLowMemory()");
    super.onLowMemory();
    SDLActivity.nativeLowMemory();
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    Log.v("SDL", "onDestroy()");
    // Send a quit message to the application
    SDLActivity.nativeQuit();

    // Now wait for the SDL thread to quit
    if (mSDLThread != null) {
      try {
        mSDLThread.join();
      } catch(Exception e) {
        Log.v("SDL", "Problem stopping thread: " + e);
      }
      mSDLThread = null;

      //Log.v("SDL", "Finished waiting for SDL thread");
    }

    if (mService != null) {
      mService.stop();
    }
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
    case R.id.disconnect_device:
      disconnectDevice();
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
        SDLActivity.startApp();
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

  // Messages from the SDLMain thread
  static final int COMMAND_CHANGE_TITLE = 1;
  static final int COMMAND_UNUSED = 2;
  static final int COMMAND_TEXTEDIT_HIDE = 3;

  protected static final int COMMAND_USER = 0x8000;

  /**
   * This method is called by SDL if SDL did not handle a message itself.
   * This happens if a received message contains an unsupported command.
   * Method can be overwritten to handle Messages in a different class.
   * @param command the command of the message.
   * @param param the parameter of the message. May be null.
   * @return if the message was handled in overridden method.
   */
  protected boolean onUnhandledMessage(int command, Object param) {
    return false;
  }

  /**
   * A Handler class for Messages from native SDL applications.
   * It uses current Activities as target (e.g. for the title).
   * static to prevent implicit references to enclosing object.
   */
  protected static class SDLCommandHandler extends Handler {
    @Override
    public void handleMessage(Message msg) {
      Context context = getContext();
      if (context == null) {
        Log.e(TAG, "error handling message, getContext() returned null");
        return;
      }
      switch (msg.arg1) {
      case COMMAND_CHANGE_TITLE:
        if (context instanceof Activity) {
          ((Activity) context).setTitle((String)msg.obj);
        } else {
          Log.e(TAG, "error handling message, getContext() returned no Activity");
        }
        break;
      case COMMAND_TEXTEDIT_HIDE:
        if (mTextEdit != null) {
          mTextEdit.setVisibility(View.GONE);

          InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
          imm.hideSoftInputFromWindow(mTextEdit.getWindowToken(), 0);
        }
        break;

      default:
        if ((context instanceof SDLActivity) && !((SDLActivity) context).onUnhandledMessage(msg.arg1, msg.obj)) {
          Log.e(TAG, "error handling message, command is " + msg.arg1);
        }
      }
    }
  }

  // Handler for the messages
  Handler commandHandler = new SDLCommandHandler();

  // Send a message from the SDLMain thread
  boolean sendCommand(int command, Object data) {
    Message msg = commandHandler.obtainMessage();
    msg.arg1 = command;
    msg.obj = data;
    return commandHandler.sendMessage(msg);
  }

  // C functions we call
  public static native void nativeInit();
  public static native void nativeLowMemory();
  public static native void nativeQuit();
  public static native void nativePause();
  public static native void nativeResume();
  public static native void onNativeResize(int x, int y, int format);
  public static native void onNativeKeyDown(int keycode);
  public static native void onNativeKeyUp(int keycode);
  public static native void onNativeTouch(int touchDevId, int pointerFingerId,
                      int action, float x, 
                      float y, float p);
  public static native void onNativeAccel(float x, float y, float z);
  public static native void nativeRunAudioThread();

  // Java functions called from C

  public static boolean createGLContext(int majorVersion, int minorVersion, int[] attribs) {
    return initEGL(majorVersion, minorVersion, attribs);
  }

  public static void flipBuffers() {
    flipEGL();
  }

  public static boolean setActivityTitle(String title) {
    // Called from SDLMain() thread and can't directly affect the view
    return mSingleton.sendCommand(COMMAND_CHANGE_TITLE, title);
  }

  public static boolean sendMessage(int command, int param) {
    return mSingleton.sendCommand(command, Integer.valueOf(param));
  }

  public static Context getContext() {
    return mSingleton;
  }

  public static void startApp() {
    // Start up the C app thread
    if (mSDLThread == null) {
      mSDLThread = new Thread(new SDLMain(), "SDLThread");
      mSDLThread.start();
    }
    else {
      /*
       * Some Android variants may send multiple surfaceChanged events, so we don't need to resume every time
       * every time we get one of those events, only if it comes after surfaceDestroyed
       */
      if (mIsPaused) {
        SDLActivity.nativeResume();
        SDLActivity.mIsPaused = false;
      }
    }
  }

  static class ShowTextInputTask implements Runnable {
    /*
     * This is used to regulate the pan&scan method to have some offset from
     * the bottom edge of the input region and the top edge of an input
     * method (soft keyboard)
     */
    static final int HEIGHT_PADDING = 15;

    public int x, y, w, h;

    public ShowTextInputTask(int x, int y, int w, int h) {
      this.x = x;
      this.y = y;
      this.w = w;
      this.h = h;
    }

    @Override
    public void run() {
      AbsoluteLayout.LayoutParams params = new AbsoluteLayout.LayoutParams(
          w, h + HEIGHT_PADDING, x, y);

      if (mTextEdit == null) {
        mTextEdit = new DummyEdit(getContext());

        mLayout.addView(mTextEdit, params);
      } else {
        mTextEdit.setLayoutParams(params);
      }

      mTextEdit.setVisibility(View.VISIBLE);
      mTextEdit.requestFocus();

      InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
      imm.showSoftInput(mTextEdit, 0);
    }
  }

  public static boolean showTextInput(int x, int y, int w, int h) {
    // Transfer the task to the main thread as a Runnable
    return mSingleton.commandHandler.post(new ShowTextInputTask(x, y, w, h));
  }

  // EGL functions
  public static boolean initEGL(int majorVersion, int minorVersion, int[] attribs) {
    try {
      if (SDLActivity.mEGLDisplay == null) {
        Log.v("SDL", "Starting up OpenGL ES " + majorVersion + "." + minorVersion);

        EGL10 egl = (EGL10)EGLContext.getEGL();

        EGLDisplay dpy = egl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY);

        int[] version = new int[2];
        egl.eglInitialize(dpy, version);

        EGLConfig[] configs = new EGLConfig[1];
        int[] num_config = new int[1];
        if (!egl.eglChooseConfig(dpy, attribs, configs, 1, num_config) || num_config[0] == 0) {
          Log.e("SDL", "No EGL config available");
          return false;
        }
        EGLConfig config = configs[0];

        SDLActivity.mEGLDisplay = dpy;
        SDLActivity.mEGLConfig = config;
        SDLActivity.mGLMajor = majorVersion;
        SDLActivity.mGLMinor = minorVersion;
      }
      return SDLActivity.createEGLSurface();

    } catch(Exception e) {
      Log.v("SDL", e + "");
      for (StackTraceElement s : e.getStackTrace()) {
        Log.v("SDL", s.toString());
      }
      return false;
    }
  }

  public static boolean createEGLContext() {
    EGL10 egl = (EGL10)EGLContext.getEGL();
    int EGL_CONTEXT_CLIENT_VERSION=0x3098;
    int contextAttrs[] = new int[] { EGL_CONTEXT_CLIENT_VERSION, SDLActivity.mGLMajor, EGL10.EGL_NONE };
    SDLActivity.mEGLContext = egl.eglCreateContext(SDLActivity.mEGLDisplay, SDLActivity.mEGLConfig, EGL10.EGL_NO_CONTEXT, contextAttrs);
    if (SDLActivity.mEGLContext == EGL10.EGL_NO_CONTEXT) {
      Log.e("SDL", "Couldn't create context");
      return false;
    }
    return true;
  }

  public static boolean createEGLSurface() {
    if (SDLActivity.mEGLDisplay != null && SDLActivity.mEGLConfig != null) {
      EGL10 egl = (EGL10)EGLContext.getEGL();
      if (SDLActivity.mEGLContext == null) createEGLContext();

      Log.v("SDL", "Creating new EGL Surface");
      EGLSurface surface = egl.eglCreateWindowSurface(SDLActivity.mEGLDisplay, SDLActivity.mEGLConfig, SDLActivity.mSurface, null);
      if (surface == EGL10.EGL_NO_SURFACE) {
        Log.e("SDL", "Couldn't create surface");
        return false;
      }

      if (egl.eglGetCurrentContext() != SDLActivity.mEGLContext) {
        if (!egl.eglMakeCurrent(SDLActivity.mEGLDisplay, surface, surface, SDLActivity.mEGLContext)) {
          Log.e("SDL", "Old EGL Context doesnt work, trying with a new one");
          // TODO: Notify the user via a message that the old context could not be restored, and that textures need to be manually restored.
          createEGLContext();
          if (!egl.eglMakeCurrent(SDLActivity.mEGLDisplay, surface, surface, SDLActivity.mEGLContext)) {
            Log.e("SDL", "Failed making EGL Context current");
            return false;
          }
        }
      }
      SDLActivity.mEGLSurface = surface;
      return true;
    } else {
      Log.e("SDL", "Surface creation failed, display = " + SDLActivity.mEGLDisplay + ", config = " + SDLActivity.mEGLConfig);
      return false;
    }
  }

  // EGL buffer flip
  public static void flipEGL() {
    try {
      EGL10 egl = (EGL10)EGLContext.getEGL();

      egl.eglWaitNative(EGL10.EGL_CORE_NATIVE_ENGINE, null);

      // drawing here

      egl.eglWaitGL();

      egl.eglSwapBuffers(SDLActivity.mEGLDisplay, SDLActivity.mEGLSurface);


    } catch(Exception e) {
      Log.v("SDL", "flipEGL(): " + e);
      for (StackTraceElement s : e.getStackTrace()) {
        Log.v("SDL", s.toString());
      }
    }
  }

  // Audio
  public static void audioInit(int sampleRate, boolean is16Bit, boolean isStereo, int desiredFrames) {
    int channelConfig = isStereo ? AudioFormat.CHANNEL_CONFIGURATION_STEREO : AudioFormat.CHANNEL_CONFIGURATION_MONO;
    int audioFormat = is16Bit ? AudioFormat.ENCODING_PCM_16BIT : AudioFormat.ENCODING_PCM_8BIT;
    int frameSize = (isStereo ? 2 : 1) * (is16Bit ? 2 : 1);

    Log.v("SDL", "SDL audio: wanted " + (isStereo ? "stereo" : "mono") + " " + (is16Bit ? "16-bit" : "8-bit") + " " + (sampleRate / 1000f) + "kHz, " + desiredFrames + " frames buffer");

    // Let the user pick a larger buffer if they really want -- but ye
    // gods they probably shouldn't, the minimums are horrifyingly high
    // latency already
    desiredFrames = Math.max(desiredFrames, (AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat) + frameSize - 1) / frameSize);

    mAudioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, sampleRate,
        channelConfig, audioFormat, desiredFrames * frameSize, AudioTrack.MODE_STREAM);

    audioStartThread();

    Log.v("SDL", "SDL audio: got " + ((mAudioTrack.getChannelCount() >= 2) ? "stereo" : "mono") + " " + ((mAudioTrack.getAudioFormat() == AudioFormat.ENCODING_PCM_16BIT) ? "16-bit" : "8-bit") + " " + (mAudioTrack.getSampleRate() / 1000f) + "kHz, " + desiredFrames + " frames buffer");
  }

  public static void audioStartThread() {
    mAudioThread = new Thread(new Runnable() {
      @Override
      public void run() {
        mAudioTrack.play();
        nativeRunAudioThread();
      }
    });

    // I'd take REALTIME if I could get it!
    mAudioThread.setPriority(Thread.MAX_PRIORITY);
    mAudioThread.start();
  }

  public static void audioWriteShortBuffer(short[] buffer) {
    for (int i = 0; i < buffer.length; ) {
      int result = mAudioTrack.write(buffer, i, buffer.length - i);
      if (result > 0) {
        i += result;
      } else if (result == 0) {
        try {
          Thread.sleep(1);
        } catch(InterruptedException e) {
          // Nom nom
        }
      } else {
        Log.w("SDL", "SDL audio: error return from write(short)");
        return;
      }
    }
  }

  public static void audioWriteByteBuffer(byte[] buffer) {
    for (int i = 0; i < buffer.length; ) {
      int result = mAudioTrack.write(buffer, i, buffer.length - i);
      if (result > 0) {
        i += result;
      } else if (result == 0) {
        try {
          Thread.sleep(1);
        } catch(InterruptedException e) {
          // Nom nom
        }
      } else {
        Log.w("SDL", "SDL audio: error return from write(byte)");
        return;
      }
    }
  }

  public static void audioQuit() {
    if (mAudioThread != null) {
      try {
        mAudioThread.join();
      } catch(Exception e) {
        Log.v("SDL", "Problem stopping audio thread: " + e);
      }
      mAudioThread = null;

      //Log.v("SDL", "Finished waiting for audio thread");
    }

    if (mAudioTrack != null) {
      mAudioTrack.stop();
      mAudioTrack = null;
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

  private void disconnectDevice() {
    mService.disconnect();
  }
}

/**
  Simple nativeInit() runnable
*/
class SDLMain implements Runnable {
  @Override
  public void run() {
    // Runs SDL_main()
    SDLActivity.nativeInit();

    //Log.v("SDL", "SDL thread terminated");
  }
}

/**
  SDLSurface. This is what we draw on, so we need to know when it's created
  in order to do anything useful.

  Because of this, that's where we set up the SDL thread
*/
class SDLSurface extends SurfaceView implements SurfaceHolder.Callback,
  View.OnKeyListener, View.OnTouchListener, SensorEventListener  {

  // Sensors
  protected static SensorManager mSensorManager;

  // Keep track of the surface size to normalize touch events
  protected static float mWidth, mHeight;

  // Startup  
  public SDLSurface(Context context) {
    super(context);
    getHolder().addCallback(this);

    setFocusable(true);
    setFocusableInTouchMode(true);
    requestFocus();
    setOnKeyListener(this);
    setOnTouchListener(this);

    mSensorManager = (SensorManager)context.getSystemService(Context.SENSOR_SERVICE);

    // Some arbitrary defaults to avoid a potential division by zero
    mWidth = 1.0f;
    mHeight = 1.0f;
  }

  // Called when we have a valid drawing surface
  @Override
  public void surfaceCreated(SurfaceHolder holder) {
    Log.v("SDL", "surfaceCreated()");
    holder.setType(SurfaceHolder.SURFACE_TYPE_GPU);
    enableSensor(Sensor.TYPE_ACCELEROMETER, true);
  }

  // Called when we lose the surface
  @Override
  public void surfaceDestroyed(SurfaceHolder holder) {
    Log.v("SDL", "surfaceDestroyed()");
    if (!SDLActivity.mIsPaused) {
      SDLActivity.mIsPaused = true;
      SDLActivity.nativePause();
    }
    enableSensor(Sensor.TYPE_ACCELEROMETER, false);
  }

  // Called when the surface is resized
  @Override
  public void surfaceChanged(SurfaceHolder holder,
                 int format, int width, int height) {
    Log.v("SDL", "surfaceChanged()");

    int sdlFormat = 0x15151002; // SDL_PIXELFORMAT_RGB565 by default
    switch (format) {
    case PixelFormat.A_8:
      Log.v("SDL", "pixel format A_8");
      break;
    case PixelFormat.LA_88:
      Log.v("SDL", "pixel format LA_88");
      break;
    case PixelFormat.L_8:
      Log.v("SDL", "pixel format L_8");
      break;
    case PixelFormat.RGBA_4444:
      Log.v("SDL", "pixel format RGBA_4444");
      sdlFormat = 0x15421002; // SDL_PIXELFORMAT_RGBA4444
      break;
    case PixelFormat.RGBA_5551:
      Log.v("SDL", "pixel format RGBA_5551");
      sdlFormat = 0x15441002; // SDL_PIXELFORMAT_RGBA5551
      break;
    case PixelFormat.RGBA_8888:
      Log.v("SDL", "pixel format RGBA_8888");
      sdlFormat = 0x16462004; // SDL_PIXELFORMAT_RGBA8888
      break;
    case PixelFormat.RGBX_8888:
      Log.v("SDL", "pixel format RGBX_8888");
      sdlFormat = 0x16261804; // SDL_PIXELFORMAT_RGBX8888
      break;
    case PixelFormat.RGB_332:
      Log.v("SDL", "pixel format RGB_332");
      sdlFormat = 0x14110801; // SDL_PIXELFORMAT_RGB332
      break;
    case PixelFormat.RGB_565:
      Log.v("SDL", "pixel format RGB_565");
      sdlFormat = 0x15151002; // SDL_PIXELFORMAT_RGB565
      break;
    case PixelFormat.RGB_888:
      Log.v("SDL", "pixel format RGB_888");
      // Not sure this is right, maybe SDL_PIXELFORMAT_RGB24 instead?
      sdlFormat = 0x16161804; // SDL_PIXELFORMAT_RGB888
      break;
    default:
      Log.v("SDL", "pixel format unknown " + format);
      break;
    }

    mWidth = width;
    mHeight = height;
    SDLActivity.onNativeResize(width, height, sdlFormat);
    Log.v("SDL", "Window size:" + width + "x"+height);

    //SDLActivity.startApp();
  }

  // unused
  @Override
  public void onDraw(Canvas canvas) {}

  // Key events
  @Override
  public boolean onKey(View  v, int keyCode, KeyEvent event) {

    if (event.getAction() == KeyEvent.ACTION_DOWN) {
      //Log.v("SDL", "key down: " + keyCode);
      SDLActivity.onNativeKeyDown(keyCode);
      return true;
    }
    else if (event.getAction() == KeyEvent.ACTION_UP) {
      //Log.v("SDL", "key up: " + keyCode);
      SDLActivity.onNativeKeyUp(keyCode);
      return true;
    }

    return false;
  }

  // Touch events
  @Override
  public boolean onTouch(View v, MotionEvent event) {
    final int touchDevId = event.getDeviceId();
    final int pointerCount = event.getPointerCount();
    // touchId, pointerId, action, x, y, pressure
    int actionPointerIndex = (event.getAction() & MotionEvent.ACTION_POINTER_ID_MASK) >> MotionEvent.ACTION_POINTER_ID_SHIFT; /* API 8: event.getActionIndex(); */
    int pointerFingerId = event.getPointerId(actionPointerIndex);
    int action = (event.getAction() & MotionEvent.ACTION_MASK); /* API 8: event.getActionMasked(); */

    float x = event.getX(actionPointerIndex) / mWidth;
    float y = event.getY(actionPointerIndex) / mHeight;
    float p = event.getPressure(actionPointerIndex);

    if (action == MotionEvent.ACTION_MOVE && pointerCount > 1) {
      // TODO send motion to every pointer if its position has
      // changed since prev event.
      for (int i = 0; i < pointerCount; i++) {
        pointerFingerId = event.getPointerId(i);
        x = event.getX(i) / mWidth;
        y = event.getY(i) / mHeight;
        p = event.getPressure(i);
        SDLActivity.onNativeTouch(touchDevId, pointerFingerId, action, x, y, p);
      }
    } else {
      SDLActivity.onNativeTouch(touchDevId, pointerFingerId, action, x, y, p);
    }
    return true;
  }

  // Sensor events
  public void enableSensor(int sensortype, boolean enabled) {
    // TODO: This uses getDefaultSensor - what if we have >1 accels?
    if (enabled) {
      mSensorManager.registerListener(this, 
              mSensorManager.getDefaultSensor(sensortype), 
              SensorManager.SENSOR_DELAY_GAME, null);
    } else {
      mSensorManager.unregisterListener(this, 
              mSensorManager.getDefaultSensor(sensortype));
    }
  }

  @Override
  public void onAccuracyChanged(Sensor sensor, int accuracy) {
    // TODO
  }

  @Override
  public void onSensorChanged(SensorEvent event) {
    if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
      SDLActivity.onNativeAccel(event.values[0] / SensorManager.GRAVITY_EARTH,
                    event.values[1] / SensorManager.GRAVITY_EARTH,
                    event.values[2] / SensorManager.GRAVITY_EARTH);
    }
  }
}

/* This is a fake invisible editor view that receives the input and defines the
 * pan&scan region
 */
class DummyEdit extends View implements View.OnKeyListener {
  InputConnection ic;

  public DummyEdit(Context context) {
    super(context);
    setFocusableInTouchMode(true);
    setFocusable(true);
    setOnKeyListener(this);
  }

  @Override
  public boolean onCheckIsTextEditor() {
    return true;
  }

  @Override
  public boolean onKey(View v, int keyCode, KeyEvent event) {

    // This handles the hardware keyboard input
    if (event.isPrintingKey()) {
      if (event.getAction() == KeyEvent.ACTION_DOWN) {
        ic.commitText(String.valueOf((char) event.getUnicodeChar()), 1);
      }
      return true;
    }

    if (event.getAction() == KeyEvent.ACTION_DOWN) {
      SDLActivity.onNativeKeyDown(keyCode);
      return true;
    } else if (event.getAction() == KeyEvent.ACTION_UP) {
      SDLActivity.onNativeKeyUp(keyCode);
      return true;
    }

    return false;
  }

  @Override
  public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
    ic = new SDLInputConnection(this, true);

    outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI
        | 33554432 /* API 11: EditorInfo.IME_FLAG_NO_FULLSCREEN */;

    return ic;
  }
}

class SDLInputConnection extends BaseInputConnection {

  public SDLInputConnection(View targetView, boolean fullEditor) {
    super(targetView, fullEditor);

  }

  @Override
  public boolean sendKeyEvent(KeyEvent event) {

    /*
     * This handles the keycodes from soft keyboard (and IME-translated
     * input from hardkeyboard)
     */
    int keyCode = event.getKeyCode();
    if (event.getAction() == KeyEvent.ACTION_DOWN) {
      if (event.isPrintingKey()) {
        commitText(String.valueOf((char) event.getUnicodeChar()), 1);
      }
      SDLActivity.onNativeKeyDown(keyCode);
      return true;
    } else if (event.getAction() == KeyEvent.ACTION_UP) {

      SDLActivity.onNativeKeyUp(keyCode);
      return true;
    }
    return super.sendKeyEvent(event);
  }

  @Override
  public boolean commitText(CharSequence text, int newCursorPosition) {

    nativeCommitText(text.toString(), newCursorPosition);

    return super.commitText(text, newCursorPosition);
  }

  @Override
  public boolean setComposingText(CharSequence text, int newCursorPosition) {

    nativeSetComposingText(text.toString(), newCursorPosition);

    return super.setComposingText(text, newCursorPosition);
  }

  public native void nativeCommitText(String text, int newCursorPosition);

  public native void nativeSetComposingText(String text, int newCursorPosition);

}
