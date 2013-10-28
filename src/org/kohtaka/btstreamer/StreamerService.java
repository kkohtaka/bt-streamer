package org.kohtaka.btstreamer;

import android.app.IntentService;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.util.Log;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.UUID;

public class StreamerService extends IntentService {
  private static final String TAG = "StreamerService";

  private static final int BUFFER_LENGTH = 4096;

  private static final UUID SERVICE_UUID =
      UUID.fromString("00000000-0000-0000-0000-00000000abcd");

  private static final int SERVICE_PORT = 8888;

  public static final int STATE_NONE = 0;
  public static final int STATE_CONNECTING = 1;
  public static final int STATE_CONNECTED = 2;

  private BluetoothDevice mDevice;
  private final BluetoothAdapter mBtAdapter;
  private static int mState;
  private ServerThread mServerThread;
  private RfcommConnectThread mRfcommConnectThread;
  private TcpConnectThread mTcpConnectThread;
  private ProxyThread mProxyThread;
  private TcpProxyThread mTcpProxyThread;
  private BluetoothSocket mRfcommSocket;
  private Socket mTcpDownstreamSocket;
  private Socket mTcpUpstreamSocket;

  private class ServerThread extends Thread {
    private final ServerSocket mServerSocket;

    private ServerThread() {
      ServerSocket tmp = null;
      try {
        tmp = new ServerSocket(SERVICE_PORT);
      } catch (IOException exception) {
        Log.e(TAG, "ServerSocket(): " + exception);
      }
      mServerSocket = tmp;
    }

    public void run() {
      Socket socket = null;
      try {
        socket = mServerSocket.accept();
      } catch (IOException exception) {
        Log.e(TAG, "ServerSocket#accept(): " + exception);
        return;
      }

      synchronized(StreamerService.this) {
        mServerThread = null;
        mTcpDownstreamSocket = socket;
      }

      tcpAccepted();
    }

    public void cancel() {
      try {
        mServerSocket.close();
      } catch (IOException exception) {
        Log.e(TAG, "ServerSocket#close(): " + exception);
      }
    }
  }

  private class RfcommConnectThread extends Thread {
    private final BluetoothSocket mSocket;
    private final BluetoothDevice mDevice;

    public RfcommConnectThread(BluetoothDevice device) {
      BluetoothSocket tmp = null;
      try {
        tmp = device.createInsecureRfcommSocketToServiceRecord(SERVICE_UUID);
      } catch (IOException exception) {
        Log.e(TAG, "BluetoothDevice#createRfcommSocketToServiceRecord(): " + exception);
      }
      mSocket = tmp;
      mDevice = device;
    }

    public void run() {
      mBtAdapter.cancelDiscovery();
      try {
        mSocket.connect();
      } catch (IOException exception) {
        Log.e(TAG, "BluetoothSocket#connect(): " + exception);
        try {
          mSocket.close();
        } catch (IOException innerException) {
          Log.e(TAG, "BluetoothSocket#close(): " + innerException);
        }
        return;
      }

      synchronized(StreamerService.this) {
        mRfcommConnectThread = null;
        mRfcommSocket = mSocket;
      }

      rfcommConnected();
    }

    public void cancel() {
      try {
        mSocket.close();
      } catch (IOException exception) {
        Log.e(TAG, "BluetoothSocket#close(): " + exception);
      }
    }
  }

  private class TcpConnectThread extends Thread {
    private final Socket mSocket;

    public TcpConnectThread() {
      mSocket = new Socket();
    }

    public void run() {
      try {
        mSocket.connect(new InetSocketAddress("10.121.30.180", 8080));
      } catch (IOException exception) {
        Log.e(TAG, "BluetoothSocket#connect(): " + exception);
        try {
          mSocket.close();
        } catch (IOException innerException) {
          Log.e(TAG, "BluetoothSocket#close(): " + innerException);
        }
        return;
      }

      synchronized(StreamerService.this) {
        mTcpConnectThread = null;
        mTcpUpstreamSocket = mSocket;
      }

      tcpConnected();
    }

    public void cancel() {
      try {
        mSocket.close();
      } catch (IOException exception) {
        Log.e(TAG, "Socket#close(): " + exception);
      }
    }
  }

  private class ProxyThread extends Thread {
    private final BluetoothSocket mRfcommSocket;
    private final Socket mTcpDownstreamSocket;
    private final InputStream mInputUpstream;
    private final InputStream mInputDownstream;
    private final OutputStream mOutputUpstream;
    private final OutputStream mOutputDownstream;

    public ProxyThread(BluetoothSocket socket, Socket tcpSocket) {
      mRfcommSocket = socket;
      mTcpDownstreamSocket = tcpSocket;

      InputStream inTmp = null;
      try {
        inTmp = socket.getInputStream();
      } catch (IOException exception) {
        Log.e(TAG, "BluetoothSocket#getInputStream(): " + exception);
      }
      mInputUpstream = inTmp;

      inTmp = null;
      try {
        inTmp = tcpSocket.getInputStream();
      } catch (IOException exception) {
        Log.e(TAG, "Socket#getInputStream(): " + exception);
      }
      mInputDownstream = inTmp;

      OutputStream outTmp = null;
      try {
        outTmp = socket.getOutputStream();
      } catch (IOException exception) {
        Log.e(TAG, "BluetoothSocket#getOutputStream(): " + exception);
      }
      mOutputUpstream = outTmp;

      outTmp = null;
      try {
        outTmp = tcpSocket.getOutputStream();
      } catch (IOException exception) {
        Log.e(TAG, "Socket#getOutputStream(): " + exception);
      }
      mOutputDownstream = outTmp;
    }

    public void run() {
      Thread readThread = new Thread() {
        public void run() {
          int nread;
          byte[] buffer = new byte[BUFFER_LENGTH];
          try {
            while ((nread = mInputDownstream.read(buffer)) != 1) {
              mOutputUpstream.write(buffer, 0, nread);
              mOutputUpstream.flush();
            }
          } catch (IOException exception) {
            try {
              mOutputUpstream.close();
            } catch (IOException exceptionInner) {
            }
          }
        }
      };
      readThread.start();

      Thread writeThread = new Thread() {
        public void run() {
          int nread;
          byte[] buffer = new byte[BUFFER_LENGTH];
          try {
            while ((nread = mInputUpstream.read(buffer)) != 1) {
              mOutputDownstream.write(buffer, 0, nread);
              mOutputDownstream.flush();
            }
            cancel();
          } catch (IOException exception) {
            try {
              mOutputUpstream.close();
            } catch (IOException exceptionInner) {
            }
          }
        }
      };
      writeThread.start();
    }

    public void cancel() {
      try {
        mRfcommSocket.close();
      } catch (IOException exception) {
        Log.e(TAG, "BluetoothSocket#close(): " + exception);
      }
      try {
        mTcpDownstreamSocket.close();
      } catch (IOException exception) {
        Log.e(TAG, "Socket#close(): " + exception);
      }
    }
  }

  private class TcpProxyThread extends Thread {
    private final Socket mUpstreamSocket;
    private final Socket mDownstreamSocket;
    private final InputStream mInputUpstream;
    private final InputStream mInputDownstream;
    private final OutputStream mOutputUpstream;
    private final OutputStream mOutputDownstream;

    public TcpProxyThread(Socket upstreamSocket, Socket downstreamSocket) {
      mUpstreamSocket = upstreamSocket;
      mDownstreamSocket = downstreamSocket;

      InputStream inTmp = null;
      try {
        inTmp = upstreamSocket.getInputStream();
      } catch (IOException exception) {
        Log.e(TAG, "Socket#getInputStream(): " + exception);
      }
      mInputUpstream = inTmp;

      inTmp = null;
      try {
        inTmp = downstreamSocket.getInputStream();
      } catch (IOException exception) {
        Log.e(TAG, "Socket#getInputStream(): " + exception);
      }
      mInputDownstream = inTmp;

      OutputStream outTmp = null;
      try {
        outTmp = upstreamSocket.getOutputStream();
      } catch (IOException exception) {
        Log.e(TAG, "Socket#getOutputStream(): " + exception);
      }
      mOutputUpstream = outTmp;

      outTmp = null;
      try {
        outTmp = downstreamSocket.getOutputStream();
      } catch (IOException exception) {
        Log.e(TAG, "Socket#getOutputStream(): " + exception);
      }
      mOutputDownstream = outTmp;
    }

    public void run() {
      byte[] buffer = new byte[BUFFER_LENGTH];
      try {
        int nread;
        do {
          nread = mInputDownstream.read(buffer);
          Log.d(TAG, "input. (" + nread + "): " + bytesToHex(buffer, nread));
          mOutputUpstream.write(buffer, 0, nread);
          Log.d(TAG, "output.");
          // [FIXME]
          if (nread > 4 &&
              buffer[nread - 4] == 0x0D && buffer[nread - 3] == 0x0A &&
              buffer[nread - 2] == 0x0D && buffer[nread - 1] == 0x0A) {
            break;
          }
        } while (nread != -1);

        do {
          nread = mInputUpstream.read(buffer);
          Log.d(TAG, "input. (" + nread + "): " + bytesToHex(buffer, nread));
          mOutputDownstream.write(buffer, 0, nread);
          Log.d(TAG, "output.");
        } while (nread != -1);
      } catch (IOException exception) {
        Log.e(TAG, "InputStream#read(): " + exception);
      }
      cancel();
    }

    public void cancel() {
      try {
        mUpstreamSocket.close();
      } catch (IOException exception) {
        Log.e(TAG, "Socket#close(): " + exception);
      }
      try {
        mDownstreamSocket.close();
      } catch (IOException exception) {
        Log.e(TAG, "Socket#close(): " + exception);
      }
    }
  }

  public StreamerService() {
    super("StreamerService");

    mBtAdapter = BluetoothAdapter.getDefaultAdapter();
    setState(STATE_NONE);
  }

  @Override
  public void onHandleIntent(Intent workIntent) {
    String dataString = workIntent.getDataString();
  }

  public void connect(BluetoothDevice device) {
    Log.d(TAG, "StreamerService#connect: " + device);

    startProxy(device);
  }

  public void startProxy(BluetoothDevice device) {

    mDevice = device;

    mServerThread = new ServerThread();
    mServerThread.start();
  }

  private void tcpAccepted() {
    if (getState() == STATE_CONNECTING) {
      if (mRfcommConnectThread != null) {
        mRfcommConnectThread.cancel();
      }
      if (mTcpConnectThread != null) {
        mTcpConnectThread.cancel();
      }
    }

    if (mProxyThread != null) {
      mProxyThread.cancel();
    }
    if (mTcpProxyThread != null) {
      mTcpProxyThread.cancel();
    }

    if (true) {
      mRfcommConnectThread = new RfcommConnectThread(mDevice);
      mRfcommConnectThread.start();
    } else {
      mTcpConnectThread = new TcpConnectThread();
      mTcpConnectThread.start();
    }

    setState(STATE_CONNECTING);
  }

  private void rfcommConnected() {
    if (mRfcommConnectThread != null) {
      mRfcommConnectThread.cancel();
      mRfcommConnectThread = null;
    }

    if (mProxyThread != null) {
      mProxyThread.cancel();
      mProxyThread = null;
    }

    mProxyThread = new ProxyThread(mRfcommSocket, mTcpDownstreamSocket);
    mProxyThread.start();
    setState(STATE_CONNECTED);
  }

  private void tcpConnected() {
    if (mTcpConnectThread != null) {
      mTcpConnectThread.cancel();
      mTcpConnectThread = null;
    }

    if (mTcpProxyThread != null) {
      mTcpProxyThread.cancel();
      mTcpProxyThread = null;
    }

    mTcpProxyThread = new TcpProxyThread(mTcpUpstreamSocket, mTcpDownstreamSocket);
    mTcpProxyThread.start();
    setState(STATE_CONNECTED);
  }

  public void start() {
    Log.d(TAG, "StreamerService#start()");
  }

  public void stop() {
    Log.d(TAG, "StreamerService#stop()");
  }

  public int getState() {
    return mState;
  }

  private void setState(int state) {
    mState = state;
  }

  final protected static char[] hexArray = "0123456789ABCDEF".toCharArray();

  public static String bytesToHex(byte[] bytes, int length) {
    if (length > bytes.length) {
      length = bytes.length;
    }
    char[] hexChars = new char[length * 2];
    int v;
    for (int j = 0; j < length; j++) {
      v = bytes[j] & 0xFF;
      hexChars[j * 2] = hexArray[v >>> 4];
      hexChars[j * 2 + 1] = hexArray[v & 0x0F];
    }
    return new String(hexChars);
  }
}

