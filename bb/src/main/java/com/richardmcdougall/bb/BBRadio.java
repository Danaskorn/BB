package com.richardmcdougall.bb;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.hoho.android.usbserial.util.SerialInputOutputManager;


import net.sf.marineapi.nmea.io.SentenceReader;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by rmc on 2/5/18.
 */


/*
BN: Adafruit Feather M0
VID: 239A
PID: 800B
SN: 0000000A0026
 */

public class BBRadio {

    public Context mContext = null;
    public BBRadio.radioEvents radioCallback = null;
    public CmdMessenger mListener = null;
    private SerialInputOutputManager mSerialIoManager;
    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    protected final Object mSerialConn = new Object();
    private static UsbSerialPort sPort = null;
    private static UsbSerialDriver mDriver = null;
    private UsbDevice mUsbDevice = null;
    private UsbManager mUsbManager = null;
    protected static final String GET_USB_PERMISSION = "GetUsbPermission";
    private static final String TAG = "BB.BBRadio";
    public BBService mBBService = null;
    public BBGps mGps = null;
    public BBRadio.radioEvents mRadioCallback = null;


    public BBRadio(BBService service, Context context) {
        mBBService = service;
        mContext = context;
        // Register to receive attach/detached messages that are proxied from MainActivity
        IntentFilter filter = new IntentFilter(UsbManager.ACTION_USB_ACCESSORY_DETACHED);
        mBBService.registerReceiver(mUsbReceiver, filter);
        filter = new IntentFilter(UsbManager.ACTION_USB_ACCESSORY_ATTACHED);
        mBBService.registerReceiver(mUsbReceiver, filter);
        initUsb();
        mGps = new BBGps(mContext);
        mGps.attach( new BBGps.GpsEvents() {
            public void timeEvent(net.sf.marineapi.nmea.util.Time time) {
                l("Radio Time: " + time.toString());
                if (mRadioCallback != null) {
                    mRadioCallback.timeEvent(time);
                }
            };
            public void positionEvent(net.sf.marineapi.provider.event.PositionEvent gps) {
                l("Radio Position: " + gps.toString());
                if (mRadioCallback != null) {
                    mRadioCallback.GPSevent(gps);
                }
            };
        });
    }

    public interface radioEvents {
        void receivePacket(byte [] bytes, int sigStrength);
        void GPSevent(net.sf.marineapi.provider.event.PositionEvent gps);
        void timeEvent(net.sf.marineapi.nmea.util.Time time);
    }

    public void attach(radioEvents newfunction) {
        mRadioCallback = newfunction;
    }

    public BBGps getGps() {
        return mGps;
    }

    public void broadcast(byte[] packet) {
        if (mListener != null) {
            mListener.sendCmdStart(5);
            mListener.sendCmdArg(packet.length);
            for (int i = 0; i < packet.length; i++) {
                mListener.sendCmdArg((int)packet[i]);
            }
            mListener.sendCmdEnd();
        }
    }

    private void sendLogMsg(String msg) {
        Intent in = new Intent(BBService.ACTION_STATS);
        in.putExtra("resultCode", Activity.RESULT_OK);
        in.putExtra("msgType", 4);
        // Put extras into the intent as usual
        in.putExtra("logMsg", msg);
        LocalBroadcastManager.getInstance(mContext).sendBroadcast(in);
    }

    public void l(String s) {
        Log.v(TAG, s);
        sendLogMsg(s);
    }


    private void onDeviceStateChange() {
        l("BBRadio: onDeviceStateChange()");
        stopIoManager();
        if (sPort != null) {
            startIoManager();
        }
    }

    private boolean checkUsbDevice(UsbDevice device) {
        int vid = device.getVendorId();
        int pid = device.getProductId();
        l("checking device pid:" + pid + ", vid: " + vid);
        if ((pid == 0x800B) && (vid == 0x239A)) {
            return true;
        } else {
            return false;
        }
    }

    public void initUsb() {
        l("BBRadio: initUsb()");

        if (mUsbDevice != null) {
            l("initUsb: already have a device");
            return;
        }

        mUsbManager = (UsbManager) mBBService.getSystemService(Context.USB_SERVICE);

        // Find all available drivers from attached devices.
        List<UsbSerialDriver> availableDrivers =
                UsbSerialProber.getDefaultProber().findAllDrivers(mUsbManager);
        if (availableDrivers.isEmpty()) {
            l("USB: No USB Devices");
            return;
        }

        // Find the Radio device by pid/vid
        mUsbDevice = null;
        l("There are " + availableDrivers.size() + " drivers");
        for (int i = 0; i < availableDrivers.size(); i++) {
            mDriver = availableDrivers.get(i);

            // See if we can find the adafruit M0 which is the Radio
            mUsbDevice = mDriver.getDevice();

            if (checkUsbDevice(mUsbDevice)) {
                l("found BBRadio");
                break;
            } else {
                mUsbDevice = null;
            }
        }

        if (mUsbDevice == null) {
            l("No Radio device found");
            return;
        }

        if (!mUsbManager.hasPermission(mUsbDevice)) {
            //ask for permission
            PendingIntent pi = PendingIntent.getBroadcast(mContext, 0, new Intent(GET_USB_PERMISSION), 0);
            mContext.registerReceiver(new BBRadio.PermissionReceiver(), new IntentFilter(GET_USB_PERMISSION));
            mUsbManager.requestPermission(mUsbDevice, pi);
            l("USB: No Permission");
            return;
        } else {
            usbConnect(mUsbDevice);
        }
    }

    private void usbConnect(UsbDevice device) {

        if (checkUsbDevice(mUsbDevice)) {
            l("found BBRadio");
        } else {
            l("not BBRadio");
            return;
        }

        UsbDeviceConnection connection = mUsbManager.openDevice(mDriver.getDevice());
        if (connection == null) {
            l("open device failed");
            return;
        }

        try {
            sPort = (UsbSerialPort) mDriver.getPorts().get(0);//Most have just one port (port 0)
            sPort.open(connection);
            sPort.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
            sPort.setDTR(true);
        } catch (IOException e) {
            l("USB: Error setting up device: " + e.getMessage());
            try {
                sPort.close();
            } catch (IOException e2) {/*ignore*/}
            sPort = null;
            l(("USB Device Error"));
            return;
        }

        sendLogMsg("USB: Connected");
        startIoManager();
    }


    public void stopIoManager() {
        synchronized (mSerialConn) {
            //status.setText("Disconnected");
            if (mSerialIoManager != null) {
                l("Stopping io manager ..");
                mSerialIoManager.stop();
                mSerialIoManager = null;
                mListener = null;
            }
            if (sPort != null) {
                try {
                    sPort.close();
                } catch (IOException e) {
                    // Ignore.
                }
                sPort = null;
            }
            sendLogMsg("USB Disconnected");

        }
    }

    public void startIoManager() {

        synchronized (mSerialConn) {
            if (sPort != null) {
                l("Starting io manager ..");
                //mListener = new BBListenerAdapter();
                mListener = new CmdMessenger(sPort, ',', ';', '\\');
                mSerialIoManager = new SerialInputOutputManager(sPort, mListener);
                mExecutor.submit(mSerialIoManager);

                // attach default cmdMessenger callback
                BBRadio.BBRadioCallbackDefault defaultCallback =
                        new BBRadio.BBRadioCallbackDefault();
                mListener.attach(defaultCallback);

                // attach Radio Receive cmdMessenger callback
                BBRadio.BBRadioCallbackReceive radioReceiveCallback =
                        new BBRadio.BBRadioCallbackReceive();
                mListener.attach(4, radioReceiveCallback);

                // attach Mode cmdMessenger callback
                BBRadio.BBRadioCallbackGPS gpsCallback =
                        new BBRadio.BBRadioCallbackGPS();
                mListener.attach(2, gpsCallback);

                sendLogMsg("USB Connected to radio");
            }
        }
    }

    public class BBRadioCallbackDefault implements CmdMessenger.CmdEvents {
        public void CmdAction(String str) {

            Log.d(TAG, "ardunio default callback:" + str);
        }
    }

    public class BBRadioCallbackReceive implements CmdMessenger.CmdEvents {
        public void CmdAction(String str) {

            int sigStrength = mListener.readIntArg();
            int len = mListener.readIntArg();
            l("radio receive callback: sigstrength" + sigStrength + ", " + len + " bytes");
            ByteArrayOutputStream recvBytes =  new ByteArrayOutputStream();
            for (int i = 0; i < len; i++) {
                recvBytes.write(Math.min(mListener.readIntArg(), 255));
            }
            if (mRadioCallback != null) {
                mRadioCallback.receivePacket(recvBytes.toByteArray(), sigStrength);
            }
        }
    }

    public class BBRadioCallbackGPS implements CmdMessenger.CmdEvents {
        public void CmdAction(String str) {

            //l("GPS callback:" + str);
            String gpsStr = mListener.readStringArg().replaceAll("_", ",");
            //l("GPS: " + gpsStr);
            // TODO: strip string
            try {
                mGps.addStr(gpsStr);
            } catch (Exception e) {

            }
        }
    }

    // We use this to catch the USB accessory detached message
    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {

            final String TAG = "mUsbReceiver";
            l("onReceive entered");
            String action = intent.getAction();
            if (UsbManager.ACTION_USB_ACCESSORY_DETACHED.equals(action)) {
                UsbDevice device = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);

                l("A USB Accessory was detached (" + device + ")");
                if (device != null) {
                    if (mUsbDevice == device) {
                        l("It's this device, shutting down");
                        mUsbDevice = null;
                        stopIoManager();
                    }
                }
            }
            if (UsbManager.ACTION_USB_ACCESSORY_ATTACHED.equals(action)) {
                UsbDevice device = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                l("USB Accessory attached (" + device + ")");
                if (mUsbDevice == null) {
                    l("Calling initUsb to check if we should add this device");
                    usbConnect(device);;
                } else {
                    l("USB already attached");
                }
            }
            l("onReceive exited");
        }
    };

    // Receive permission if it's being asked for (typically for the first time)
    private class PermissionReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            mContext.unregisterReceiver(this);
            if (intent.getAction().equals(GET_USB_PERMISSION)) {
                UsbDevice device = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                    l("USB we got permission");
                    if (device != null) {
                        usbConnect(device);;
                    } else {
                        l("USB perm receive device==null");
                    }

                } else {
                    l("USB no permission");
                }
            }
        }
    }

}
