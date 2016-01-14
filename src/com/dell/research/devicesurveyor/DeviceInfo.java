package com.dell.research.devicesurveyor;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Build;
import android.provider.Settings;
import android.provider.Settings.Secure;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.Surface;

import java.io.*;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

public class DeviceInfo {
    private static final String WHICH_ROOT_COMMAND = "which su";
    private static final String LS_ROOT_COMMAND = "ls /system/xbin/su";
    private static final String[] GETEVENT_COMMAND = new String[] { "su", "-c", "getevent -lp" };
    private static final String RANDOM_ID_FILENAME = "RandomDeviceID.txt";

    // Device information
    private static String deviceID = null;
    private static String deviceManufacturer = null;
    private static String deviceModel = null;
    private static String randomID = null;

    // OS information
    private static int sdkNumber = -1;
    private static String sdkRelease = null;

    // Screen dimensions in pixels in the native orientation
    private static int displaySizeX = -1;
    private static int displaySizeY = -1;
    private static double xDensity = 0.0;
    private static double yDensity = 0.0;

    // Android-accessible sensors
    private static List<String> sensors = null;

    // Lock screen
    private static boolean patternLock = false;
    private static boolean passPinLock = false;

    // Locale
    private static String language = "";
    private static String keyboard = "";

    // getevent-enumerated sensors
    private static boolean isRooted = false;
    private static StringBuffer geteventBuffer = null;

    private static boolean isSet = false;

    public static void init(Activity act) {
        if (!isSet && act != null) {
            // Device information
            deviceID = Secure.getString(act.getContentResolver(), Secure.ANDROID_ID);
            deviceManufacturer = Build.MANUFACTURER.replaceAll("\\s+", "");
            deviceModel = Build.MODEL.replaceAll("\\s+", "");

            // If it exists, load a random ID. Otherwise, generate and save a new one
            File savedID = new File(act.getFilesDir(), RANDOM_ID_FILENAME);
            if(savedID.exists()) {
                loadRandomID(act);
            } else {
                generateRandomID(act);
            }

            // OS information
            sdkNumber = Build.VERSION.SDK_INT;
            sdkRelease = Build.VERSION.RELEASE;

            // Screen dimension in pixels in the native orientation
            DisplayMetrics dm = new DisplayMetrics();
            Display display = act.getWindowManager().getDefaultDisplay();
            display.getRealMetrics(dm);

            if (display.getRotation() == Surface.ROTATION_0 || display.getRotation() == Surface.ROTATION_180) {
                displaySizeX = dm.widthPixels;
                displaySizeY = dm.heightPixels;
            } else {
                displaySizeX = dm.heightPixels;
                displaySizeY = dm.widthPixels;
            }

            xDensity = dm.xdpi;
            yDensity = dm.ydpi;

            // Android-accessible sensors
            SensorManager sensorMan = (SensorManager) act.getSystemService(Context.SENSOR_SERVICE);
            List<Sensor> sensors = sensorMan.getSensorList(Sensor.TYPE_ALL);

            DeviceInfo.sensors = new LinkedList<String>();
            for (Sensor s : sensors) {
                DeviceInfo.sensors.add(s.getName());
            }

            // Check if any lock screen is enabled
            try {
                patternLock = Secure.getInt(act.getContentResolver(), Secure.LOCK_PATTERN_ENABLED) == 1;
            } catch (Settings.SettingNotFoundException e) {
                e.printStackTrace();
            }
            passPinLock = ((KeyguardManager)act.getSystemService(act.KEYGUARD_SERVICE)).isKeyguardSecure();

            // Get locale information
            language = Locale.getDefault().getDisplayLanguage();
            keyboard = Secure.getString(act.getContentResolver(), Secure.DEFAULT_INPUT_METHOD);

            // getevent-enumerated sensors if rooted
            try {
                isRooted = isRooted();
                if(isRooted) {
                    geteventBuffer = callGetevent();
                }
            } catch (InterruptedException | IOException e) {
                geteventBuffer = new StringBuffer();

                geteventBuffer.append(String.format("%s%n", e.getMessage()));

                for (StackTraceElement elt : e.getStackTrace()) {
                    geteventBuffer.append(String.format("%s%n", elt.toString()));
                }

                e.printStackTrace();
            } finally {
                isSet = true;
            }
        }
    }

    private static void loadRandomID(Context c) {
        File savedID = new File(c.getFilesDir(), RANDOM_ID_FILENAME);

        // Load the existing random ID
        try {
            FileReader fr = new FileReader(savedID);
            BufferedReader read = new BufferedReader(fr);

            String line = read.readLine();
            if(line != null && !line.isEmpty()) {
                randomID = line;
            } else {
                // Try generating a new random ID if the saved one is empty
                generateRandomID(c);
            }

            read.close();
            fr.close();
        } catch(IOException e) {
            // Try generating a new random ID if reading the previous one failed
            generateRandomID(c);
        }
    }

    private static void generateRandomID(Context c) {
        File savedID = new File(c.getFilesDir(), RANDOM_ID_FILENAME);

        // Generate and save a new random ID
        try {
            String digestInput = deviceID + Math.random();
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(digestInput.getBytes("UTF-8"));
            byte[] result = digest.digest();

            randomID = String.format("%064x", new BigInteger(1, result));
        } catch(UnsupportedEncodingException | NoSuchAlgorithmException e) {
            // Just generate a random number if hash(deviceID + random) fails
            randomID = "" + Math.random();
        }

        // Truncate the random ID to the last 15 characters
        final int MAX_LENGTH = 15;
        int idLength = randomID.length();
        if(idLength > MAX_LENGTH) {
            randomID = randomID.substring(idLength - MAX_LENGTH, idLength);
        }

        try {
            FileWriter fw = new FileWriter(savedID);
            BufferedWriter out = new BufferedWriter(fw);

            out.write(randomID);

            out.close();
            fw.close();
        } catch(IOException e) {
            // Really can't think of a good way to handle the ID not being written out...
            e.printStackTrace();
        }
    }

    /**
     * 
     * @return The output of a getevent -lp call
     * @throws IOException
     * @throws InterruptedException
     */
    private static StringBuffer callGetevent() throws IOException, InterruptedException {
        Process proc = Runtime.getRuntime().exec(GETEVENT_COMMAND);
        proc.waitFor();

        BufferedReader procReader = new BufferedReader(new InputStreamReader(proc.getInputStream()));
        StringBuffer outBuffer = new StringBuffer();
        String line = "";
        while ((line = procReader.readLine()) != null) {
            String formattedLine = String.format("%s%n", line);

            outBuffer.append(formattedLine);
        }

        return outBuffer;
    }

    /**
     *
     * @return True if the device is rooted
     * @throws IOException
     * @throws InterruptedException
     */
    private static boolean isRooted() throws InterruptedException {
        boolean rooted = false;
        try {
            // Try the "which" command to look for the "su" program
            Process proc = Runtime.getRuntime().exec(WHICH_ROOT_COMMAND);
            int returnValue = proc.waitFor();
            rooted = returnValue == 0;
        } catch(IOException e) {
            try {
                // Try looking under /system/xbin for the "su" program
                Process proc2 = Runtime.getRuntime().exec(LS_ROOT_COMMAND);
                int returnValue2 = proc2.waitFor();
                rooted = returnValue2 == 0;
            } catch(IOException e2) {
                // Do nothing if it fails
            }
        }

        return rooted;
    }

    public static String getDeviceID() {
        return deviceID;
    }

    public static String getDeviceManufacturer() {
        return deviceManufacturer;
    }

    public static String getDeviceModel() {
        return deviceModel;
    }

    public static String getRandomID() {
        return randomID;
    }

    public static int getSdkNumber() {
        return sdkNumber;
    }

    public static String getSdkRelease() {
        return sdkRelease;
    }

    public static int getDisplaySizeX() {
        return displaySizeX;
    }

    public static int getDisplaySizeY() {
        return displaySizeY;
    }

    public static double getDensityX() {
        return xDensity;
    }

    public static double getDensityY() {
        return yDensity;
    }

    public static List<String> getSensors() {
        return sensors;
    }

    public static StringBuffer getGeteventBuffer() {
        return geteventBuffer;
    }

    public static boolean getRooted() {
        return isRooted;
    }

    public static boolean isSet() {
        return isSet;
    }

    public static Map<String, String> getData() {
        Map<String, String> dataFields = new LinkedHashMap<>();

        // Device information
        dataFields.put("deviceRandomID", randomID);
        dataFields.put("deviceManufacturer", deviceManufacturer);
        dataFields.put("deviceModel", deviceModel);
        dataFields.put("deviceRooted", "" + isRooted);

        // OS information
        dataFields.put("androidSDKInt", "" + sdkNumber);
        dataFields.put("androidSDKRelease", sdkRelease);

        // Lock available
        dataFields.put("pinLockEnabled", "" + passPinLock);
        dataFields.put("patternLockEnabled", "" + patternLock);

        // Locale information
        dataFields.put("language", language);
        dataFields.put("keyboard", keyboard);

        // Screen dimensions in pixels in the native orientation
        dataFields.put("screenWidth", "" + displaySizeX);
        dataFields.put("screenHeight", "" + displaySizeY);
        dataFields.put("screenDensityX", "" + xDensity);
        dataFields.put("screenDensityY", "" + yDensity);
        dataFields.put("screenPhysicalXInch", "" + (displaySizeX / xDensity));
        dataFields.put("screenPhysicalYInch", "" + (displaySizeY / yDensity));

        // Android-accessible sensors
        int counter = 0;
        for (String s : sensors) {
            String fieldName = String.format(Locale.getDefault(), "sensor_%02d", counter++);
            dataFields.put(fieldName, s);
        }

        return dataFields;
    }

    public static String getSummary() {
        if (isSet) {
            Map<String, String> dataFields = getData();

            // Generate string
            StringBuffer buffer = new StringBuffer();
            for (String name : dataFields.keySet()) {
                String value = dataFields.get(name);
                String formattedLine = String.format("%s : %s%n", name, value);

                buffer.append(formattedLine);
            }

            if(isRooted) {
                buffer.append("\n");
                buffer.append("getevent fields (root only)\n");
                buffer.append(geteventBuffer);
            }

            return buffer.toString();
        } else {
            return "DeviceInfo not set";
        }
    }
}
