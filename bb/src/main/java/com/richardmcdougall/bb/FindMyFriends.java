package com.richardmcdougall.bb;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import net.sf.marineapi.nmea.util.Position;
import net.sf.marineapi.nmea.util.Time;
import net.sf.marineapi.provider.event.PositionEvent;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Created by rmc on 2/7/18.
 */

public class FindMyFriends {

    private static final String TAG = "BB.Gps";
    Context mContext;
    RF mRadio;
    Gps mGps;
    IoTClient mIotClient;
    findMyFriendsCallback mFindMyFriendsCallback = null;
    long mLastFix = 0;
    static final int kMaxFixAge = 5000;
    static final int kMagicNumberLen = 2;
    static final int kTTL = 1;
    static final int [] kTrackerMagicNumber = new int[] {0x02, 0xcb};
    static final int [] kGPSMagicNumber = new int[] {0xbb, 0x01};
    int mLat;
    int mLon;
    int mAlt;
    int mSpeed;
    int mHeading;
    int mAmIAccurate;
    int mTheirAddress;
    double mTheirLat;
    double mTheirLon;
    double mTheirAlt;
    double mTheirSpeed;
    double mTheirHeading;
    int mThereAccurate;
    long mLastSend = 0;
    long mLastRecv = 0;
    private int mBoardAddress;
    String mBoardId;
    byte[] mLastHeardLocation;

    public FindMyFriends(Context context, BBService service,
                         final RF radio, Gps gps, IoTClient iotclient) {
        mContext = context;
        mRadio = radio;
        mGps = gps;
        mIotClient = iotclient;
        l("Starting FindMyFriends");

        if (mRadio == null) {
            l("No Radio!");
            return;
        }

        mRadio.attach(new RF.radioEvents() {
            @Override
            public void receivePacket(byte[] bytes, int sigStrength) {
                l("FMF Packet: len(" + bytes.length + "), data: " + bytesToHex(bytes));
                if (processReceive(bytes)) {
                    l("theirLat = " + mTheirLat + ", theirLon = " + mTheirLon);
                    mIotClient.sendUpdate("bbevent", "[" +
                            "remote," + sigStrength + "," + mTheirLat + "," + mTheirLon + "]");
                }
            }

            @Override
            public void GPSevent(PositionEvent gps) {
                l("FMF Position: " + gps.toString());
                Position pos = gps.getPosition();
                mLat = (int)(pos.getLatitude() * 1000000);
                mLon = (int)(pos.getLongitude() * 1000000);
                mAlt = (int)(pos.getAltitude() * 1000000);
                long sinceLastFix = System.currentTimeMillis() - mLastFix;

                if (sinceLastFix > kMaxFixAge) {
                    l("FMF: sending GPS update");
                    mIotClient.sendUpdate("bbevent", "[" +
                                "local," + 0 + "," + mLat + "," + mLon + "]");
                    broadcastGPSpacket(mLat, mLon, mAlt, mAmIAccurate, 0, 0);
                    mLastFix = System.currentTimeMillis();

                }
            }

            @Override
            public void timeEvent(Time time) {
                l("FMF Time: " + time.toString());
            }
        });
        mBoardAddress = getBoardAddress(service.getBoardId());
    }

    // GPS Packet format =
    //         [0] kGPSMagicNumber byte one
    //         [1] kGPSMagicNumber byte two
    //         [2] board address bits 0-7
    //         [3] board address bits 8-15
    //         [4] ttl
    //         [5] lat bits 0-7
    //         [6] lat bits 8-15
    //         [7] lat bits 16-23
    //         [8] lat bits 24-31
    //         [9] lon bits 0-7
    //         [10] lon bits 8-15
    //         [11] lon bits 16-23
    //         [12] lon bits 24-31
    //         [13] elev bits 0-7
    //         [14] elev bits 8-15
    //         [15] elev bits 16-23
    //         [16] elev bits 24-31
    //         [17] i'm accurate flag

    //         [18] heading bits 0-7
    //         [19] heading bits 8-15
    //         [20] heading bits 16-23
    //         [21] heading bits 24-31
    //         [22] speed bits 0-7
    //         [23] speed bits 8-15
    //         [24] speed bits 16-23
    //         [25] speed bits 24-31


    public final static int BBGPSPACKETSIZE = 18;

    private void broadcastGPSpacket(int lat, int lon, int elev, int iMAccurate,
                                    int heading, int speed) {
        // Check GPS data is not stale
        int len = 2 * 4 + 1 + kMagicNumberLen + 1;
        ByteArrayOutputStream radioPacket = new ByteArrayOutputStream();

        for (int i = 0; i < kMagicNumberLen; i++) {
            radioPacket.write(kGPSMagicNumber[i]);
        }

        radioPacket.write(mBoardAddress & 0xFF);
        radioPacket.write((mBoardAddress >> 8) & 0xFF);
        radioPacket.write(kTTL);
        radioPacket.write(lat & 0xFF);
        radioPacket.write((lat >> 8) & 0xFF);
        radioPacket.write((lat >> 16) & 0xFF);
        radioPacket.write((lat >> 24) & 0xFF);
        radioPacket.write(lon & 0xFF);
        radioPacket.write((lon >> 8) & 0xFF);
        radioPacket.write((lon >> 16) & 0xFF);
        radioPacket.write((lon >> 24) & 0xFF);
        radioPacket.write(elev & 0xFF);
        radioPacket.write((elev >> 8) & 0xFF);
        radioPacket.write((elev >> 16) & 0xFF);
        radioPacket.write((elev >> 24) & 0xFF);

        radioPacket.write(iMAccurate);
        radioPacket.write(0);

        l("Sending packet...");
        mRadio.broadcast(radioPacket.toByteArray());
        mLastSend = System.currentTimeMillis();
        l("Sent packet...");

        radioPacket = new ByteArrayOutputStream();

        for (int i = 0; i < kMagicNumberLen; i++) {
            radioPacket.write(kTrackerMagicNumber[i]);
        }

        radioPacket.write(lat & 0xFF);
        radioPacket.write((lat >> 8) & 0xFF);
        radioPacket.write((lat >> 16) & 0xFF);
        radioPacket.write((lat >> 24) & 0xFF);
        radioPacket.write(lon & 0xFF);
        radioPacket.write((lon >> 8) & 0xFF);
        radioPacket.write((lon >> 16) & 0xFF);
        radioPacket.write((lon >> 24) & 0xFF);

        radioPacket.write(iMAccurate);
        radioPacket.write(0);

        l("Sending packet...");
        mRadio.broadcast(radioPacket.toByteArray());
        mLastSend = System.currentTimeMillis();
        l("Sent packet...");

    }

    // TODO: should have a cache of board name/addresses that comes from the cloud.
    private int getBoardAddress(String boardId) {

        byte[] encoded = null;
        int myAddress;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            encoded = digest.digest(boardId.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            l("Could not calculate boardAddress");
            return -1;
        }
        // Natural board leaders get explicit addresses
        switch (boardId) {

            case "akula":
                myAddress = 1;
                break;

            case "candy":
                myAddress = 2;
                break;

            case "squeeze":
                myAddress = 3;
                break;

            case "biscuit":
                myAddress = 4;
                break;

            case "artemis":
                myAddress = 5;
                break;

            case "test_board":
                myAddress = 100;
                break;

            case "handheld1":
                myAddress = 200;
                break;

            default:
                // Otherwise, calculate 16 bit address for radio from name
                myAddress = (encoded[0] * 0xff) * 256 + (encoded[1] & 0xff);
        }
        l("Radio address for " + boardId + " = " + myAddress);
        return myAddress;
    }

    // TODO: should have a cache of boardnames that comes from the cloud.
    public String boardAddressToName(int address) {
        String [] boardNames = {
                "proto",
                "akula",
                "boadie",
                "artemis",
                "goofy",
                "joon",
                "biscuit",
                "squeeze",
                "ratchet",
                "pegasus",
                "vega",
                "monaco",
                "candy",
                "test_board",
                "handheld1"};
        for (String name :boardNames) {
            if (address == getBoardAddress(name)) {
                return (name);
            }
        }
        return "unknown";
    }

    public interface findMyFriendsCallback {

        public void audioTrack(int track);
        public void videoTrack(int track);
        public void globalAlert(int alert);

    }

    public void attach(FindMyFriends.findMyFriendsCallback newfunction) {

        mFindMyFriendsCallback = newfunction;
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

    final protected static char[] hexArray = "0123456789ABCDEF".toCharArray();

    public static String bytesToHex(byte[] bytes) {

        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    boolean processReceive(byte [] packet) {
        ByteArrayInputStream bytes = new ByteArrayInputStream(packet);

        int recvMagicNumber = magicNumberToInt(
                new int[] { bytes.read(), bytes.read()});

        if (recvMagicNumber == magicNumberToInt(kGPSMagicNumber)) {
            l("BB GPS Packet");
            mTheirAddress = (int) ((bytes.read() & 0xff) +
                    ((bytes.read() & 0xff) << 8));
            int ttl = bytes.read();
            mTheirLat = (double) ((bytes.read() & 0xff) +
                    ((bytes.read() & 0xff) << 8) +
                    ((bytes.read() & 0xff) << 16) +
                    ((bytes.read() & 0xff) << 24)) / 1000000.0;
            mTheirLon = (double)((bytes.read() & 0xff) +
                    ((bytes.read() & 0xff) << 8) +
                    ((bytes.read() & 0xff) << 16) +
                    ((bytes.read() & 0xff) << 24)) / 1000000.0;
            mTheirAlt = (double)((bytes.read() & 0xff) +
                    ((bytes.read() & 0xff) << 8) +
                    ((bytes.read() & 0xff) << 16) +
                    ((bytes.read() & 0xff) << 24)) / 1000000.0;
            mThereAccurate = bytes.read();
            mLastRecv = System.currentTimeMillis();
            mLastHeardLocation = packet.clone();
            return true;
        } else if (recvMagicNumber == magicNumberToInt(kTrackerMagicNumber)) {
            l("tracker packet");
            mTheirLat = (double) ((bytes.read() & 0xff) +
                    ((bytes.read() & 0xff) << 8) +
                    ((bytes.read() & 0xff) << 16) +
                    ((bytes.read() & 0xff) << 24)) / 1000000.0;
            mTheirLon = (double)((bytes.read() & 0xff) +
                    ((bytes.read() & 0xff) << 8) +
                    ((bytes.read() & 0xff) << 16) +
                    ((bytes.read() & 0xff) << 24)) / 1000000.0;
            mThereAccurate = bytes.read();
            mLastRecv = System.currentTimeMillis();
            return true;
        } else {
                l("rogue packet not for us!");
        }
        return false;
    }

    private static final int magicNumberToInt(int[] magic) {
        int magicNumber = 0;
        for (int i = 0; i < kMagicNumberLen; i++) {
            magicNumber = magicNumber + (magic[i] << ((kMagicNumberLen - 1 - i) * 8));
        }
        return (magicNumber);
    }


    // TODO: make this pull from a buffered list of recent locations
    // this just samples the last written location for now
    byte[] getRecentLocation() {

        if (mLastHeardLocation != null) {
            l("get recent location " + mLastHeardLocation.length);
            return mLastHeardLocation;
        } else {
            l("no recent locaton");
            return null;
        }

    }

}
