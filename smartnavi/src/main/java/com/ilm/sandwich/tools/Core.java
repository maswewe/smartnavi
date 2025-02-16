package com.ilm.sandwich.tools;

import android.content.Context;
import android.content.Intent;
import android.hardware.GeomagneticField;
import android.hardware.SensorManager;
import android.net.Uri;
import android.opengl.Matrix;
import android.os.Environment;
import android.util.Log;

import com.ilm.sandwich.Config;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * This is the heart/core of SmartNavi
 * Here all important calculation takes place.
 * The MapActivities just give the Core all sensordata.
 * Core recognizes steps and computes direction, location, etc.
 *
 * @author Christian Henke
 *         www.smartnavi-app.com
 */
public class Core {

    public static float[] gravity = new float[3];
    public static float[] linear = new float[4];
    public static float[] linearRemapped = new float[4];
    public static float[] origMagn = new float[3];
    public static float[] magn = new float[3];
    public static float[] origAcl = new float[3]; //only needed for logging/debug purposes
    public static double startLat;
    public static double startLon;
    public static int stepCounter = 0;
    public static double azimuth;
    public static double schrittFrequenz = 0;
    public static int altitude = 150;
    public static double distanceLongitude;
    public static float stepLength;
    public static boolean export;
    public static double korr;
    public static int version;
    public static float lastErrorGPS;
    static File posFile;
    static File sensorFile;
    private static float frequency;
    private static boolean stepBegin = false;
    private static float[] iMatrix = new float[9];
    private static float[] RMatrix = new float[16];
    private static float[] RMatrixRemapped = new float[16];
    private static float[] RMatrixTranspose = new float[16];
    private static float[] orientation = new float[3];
    private static double deltaLat;
    private static double deltaLon;
    private static float iStep = 1;
    private static float ugainA;
    private static float ugainM;
    private static double[] xa0 = new double[4];
    private static double[] ya0 = new double[4];
    private static double[] xa1 = new double[4];
    private static double[] ya1 = new double[4];
    private static double[] xa2 = new double[4];
    private static double[] ya2 = new double[4];
    private static float[] tpA = new float[3];
    private static float[] tpM = new float[3];
    private static double[] xm0 = new double[4];
    private static double[] ym0 = new double[4];
    private static double[] xm1 = new double[4];
    private static double[] ym1 = new double[4];
    private static double[] xm2 = new double[4];
    private static double[] ym2 = new double[4];
    private static float stepThreshold = 2.0f;
    private static boolean sensorFileNotExisting = true;
    private static boolean positionsFileNotExisting = true;
    private static float decl = 0;
    private static boolean initialStep;
    private static boolean newStepDetected = false;
    private static boolean startedToExport = false;
    private static double azimuthUnfilteredUncorrected;


    // -----------------------------------------------------------------------------------------------------------------
    // ------ONCREATE-------------------------------------------------------------------------------------------------
    // -----------------------------------------------------------------------------------------------------------------

    public Core() {

        positionsFileNotExisting = true;
        sensorFileNotExisting = true;

        stepCounter = 0;
        initialStep = true;

        magn[0] = magn[1] = magn[2] = gravity[0] = gravity[1] = 0;
        gravity[2] = 9.81f;
        ugainM = ugainA = 154994.3249f;
        tpA[0] = tpM[0] = 0.9273699683f;
        tpA[1] = tpM[1] = -2.8520278186f;
        tpA[2] = tpM[2] = 2.9246062355f;
    }

    // -----------------------------------------------------------------------------------------------------------------
    // ------END ONCREATE----------------------------------------------------------------------------------------
    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Initializing
     *
     * @param startLat
     * @param startLon
     * @param distanceLongitude
     */
    public static void initialize(double startLat, double startLon, double distanceLongitude, double altitude, float lastError) {
        Core.startLat = startLat;
        Core.startLon = startLon;
        Core.distanceLongitude = distanceLongitude;
        Core.altitude = (int) altitude;
        Core.lastErrorGPS = lastError;
        trueNorth();

    }

    /**
     * Set actual position
     *
     * @param lat
     * @param lon
     */
    public static void setLocation(double lat, double lon) {
        startLat = lat;
        startLon = lon;
    }

    private static void trueNorth() {
        long time = System.currentTimeMillis();
        GeomagneticField geo = new GeomagneticField((float) startLat, (float) startLon, altitude, time);
        decl = geo.getDeclination();
    }

    private static void positionOutput() {
        try {
            File folder = new File(Environment.getExternalStorageDirectory() + "/smartnavi/");
            folder.mkdir();
            if (folder.canWrite()) {
                if (positionsFileNotExisting) {
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.GERMAN);
                    String curentDateandTime = sdf.format(new Date());
                    String textname = "track_" + curentDateandTime + ".gpx";
                    posFile = new File(folder, textname);
                    FileWriter posWriter = new FileWriter(posFile);
                    BufferedWriter out = new BufferedWriter(posWriter);

                    TimeZone tz = TimeZone.getTimeZone("UTC");
                    SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mmZ", Locale.GERMAN);
                    df.setTimeZone(tz);
                    String nowAsISO = df.format(new Date());

                    out.write("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\" ?><gpx> <trk><name>SmartNavi " + nowAsISO
                            + "</name><number>1</number><trkseg>");
                    out.close();
                    positionsFileNotExisting = false;
                } else {
                    FileWriter posWriter = new FileWriter(posFile, true);
                    BufferedWriter out = new BufferedWriter(posWriter);

                    if (newStepDetected) {
                        out.newLine();

                        TimeZone tz = TimeZone.getTimeZone("UTC");
                        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mmZ", Locale.GERMAN);
                        df.setTimeZone(tz);
                        String nowAsISO = df.format(new Date());

                        out.write("<trkpt lat=\"" + startLat + "\" lon=\"" + startLon + "\"><time>" + nowAsISO + "</time></trkpt>");

                        newStepDetected = false;
                    }

                    out.close();
                }
            }
        } catch (IOException e) {
            // e.printStackTrace();
        }
    }

    public static void closeLogFile() {
        if (export && positionsFileNotExisting == false) {
            try {
                FileWriter posWriter;
                posWriter = new FileWriter(posFile, true);
                BufferedWriter out = new BufferedWriter(posWriter);
                out.newLine();
                out.write("</trkseg></trk></gpx>");
                out.close();
            } catch (Exception e) {
                // e.printStackTrace();
            }
        }
        export = false;
        positionsFileNotExisting = true;
        sensorFileNotExisting = true;
    }

    private static void dataOutput() {
        try {
            File folder = new File(Environment.getExternalStorageDirectory() + "/smartnavi/");
            folder.mkdir();
            if (folder.canWrite()) {
                if (sensorFileNotExisting) {
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.GERMAN);
                    String curentDateandTime = sdf.format(new Date());
                    String textname = "sensoren_" + curentDateandTime + ".csv";
                    sensorFile = new File(folder, textname);
                    FileWriter sensorWriter = new FileWriter(sensorFile);
                    BufferedWriter outs = new BufferedWriter(sensorWriter);
                    outs.write(startLat + "; " + startLon + "; " + stepLength + ";" + version + "; ");
                    outs.newLine();
                    outs.write("origmagn0; origmagn1; origmagn2; origaccel0; origaccel1; origaccel2; "
                            + "imbamagn0; imbamagn1; imbamagn2; gravity0; gravity1; gravity2; "
                            + "azimuthUnfilteredUncorrected; korr; azimuth; schrittFrequenz;");
                    outs.close();
                    sensorFileNotExisting = false;
                } else {
                    FileWriter sensorWriter = new FileWriter(sensorFile, true);
                    BufferedWriter outs = new BufferedWriter(sensorWriter);

                    outs.newLine();

                    outs.write(origMagn[0] + ";" + origMagn[1] + ";" + origMagn[2] + ";" + origAcl[0] + ";" + origAcl[1] + ";" + origAcl[2] + ";"
                            + magn[0] + ";" + magn[1] + ";" + magn[2] + ";" + gravity[0] + ";" + gravity[1] + ";" + gravity[2] + ";" + azimuthUnfilteredUncorrected
                            + ";" + korr + ";" + azimuth + ";" + schrittFrequenz + ";");
                    outs.close();
                }
            }
        } catch (IOException e) {
            // e.printStackTrace();
        }
    }

    public void writeLog(boolean sollich) {
        if (sollich) {
            export = true;
            startedToExport = true;
        } else if (startedToExport == true && sollich == false) {
            closeLogFile();
        }
    }

    public void imbaMagnetic(float[] magnetic) {
        // LowPass 0.5Hz for alpha0
        xm0[0] = xm0[1];
        xm0[1] = xm0[2];
        xm0[2] = xm0[3];
        xm0[3] = magnetic[0] / ugainM;
        ym0[0] = ym0[1];
        ym0[1] = ym0[2];
        ym0[2] = ym0[3];
        ym0[3] = (xm0[0] + xm0[3]) + 3 * (xm0[1] + xm0[2]) + (tpM[0] * ym0[0]) + (tpM[1] * ym0[1]) + (tpM[2] * ym0[2]);
        magn[0] = (float) ym0[3];

        // LowPass 0.5Hz for alpha1
        xm1[0] = xm1[1];
        xm1[1] = xm1[2];
        xm1[2] = xm1[3];
        xm1[3] = magnetic[1] / ugainM;
        ym1[0] = ym1[1];
        ym1[1] = ym1[2];
        ym1[2] = ym1[3];
        ym1[3] = (xm1[0] + xm1[3]) + 3 * (xm1[1] + xm1[2]) + (tpM[0] * ym1[0]) + (tpM[1] * ym1[1]) + (tpM[2] * ym1[2]);
        magn[1] = (float) ym1[3];

        // LowPass 0.5Hz for alpha2
        xm2[0] = xm2[1];
        xm2[1] = xm2[2];
        xm2[2] = xm2[3];
        xm2[3] = magnetic[2] / ugainM;
        ym2[0] = ym2[1];
        ym2[1] = ym2[2];
        ym2[2] = ym2[3];
        ym2[3] = (xm2[0] + xm2[3]) + 3 * (xm2[1] + xm2[2]) + (tpM[0] * ym2[0]) + (tpM[1] * ym2[1]) + (tpM[2] * ym2[2]);
        magn[2] = (float) ym2[3];
    }

    // private static void berechneSchrittfrequenz(long now) {
    //
    // schrittx[0] = schrittx[1];
    // schrittx[1] = schrittx[2];
    // schrittx[2] = schrittx[3];
    // schrittx[3] = schrittx[4];
    // schrittx[4] = now;
    //
    // if (schrittx[0] != 0) {
    // schrittFrequenzCounter++;
    // double dauerFuenfSchritte = (now - schrittx[0]) / 1000.00; // in
    // // sekunden
    // if (schrittFrequenz != 0 && schrittFrequenzCounter == 5) {
    // schrittFrequenzGesamt = (schrittFrequenzGesamt + schrittFrequenz)
    // / schrittFrequenzTeiler;
    // schrittFrequenzGesamtString = df.format(schrittFrequenzGesamt);
    // schrittFrequenzTeiler = 2;
    // schrittFrequenzCounter = 0;
    // } else {
    // schrittFrequenz = (5 / dauerFuenfSchritte);
    // }
    // schrittFreq = df.format(schrittFrequenz);
    // }
    // }

    // private double korrekturAz(double x) { // auf alle hab ich +0.6 nach Test
    // 11 und nochmal +0.5 auf die positive Werte
    // // 3. Juni: wieder 0.3 weniger auf die +werte und posSinX auch 0.1
    // weniger
    // // made by Christian Henke
    // if (azimuthUngefiltert < 180 ){
    // posSinX = 0.5f;
    // } else {
    // posSinX = 0;
    // }
    //
    // if (deltaSollGravity > 0.15 && deltaSollGravity <= 0.3){
    // korr = ((posSinX+3.8)*Math.sin((double)(3.141592653589793*x/180)));
    // }
    // else if (deltaSollGravity > 0.3 && deltaSollGravity <= 0.45){
    // korr = ((posSinX+7.0)*Math.sin((double)(3.141592653589793*x/180)));
    // } // made by Christian Henke
    // else if (deltaSollGravity > 0.45 && deltaSollGravity <= 0.55){
    // korr = ((posSinX+8.6)*Math.sin((double)(3.141592653589793*x/180)));
    // }
    // else if (deltaSollGravity > 0.55 && deltaSollGravity <= 0.75){
    // korr = ((posSinX+10.3)*Math.sin((double)(3.141592653589793*x/180)));
    // }
    // else if (deltaSollGravity > 0.75 && deltaSollGravity <= 0.95){
    // korr = ((posSinX+13.5)*Math.sin((double)(3.141592653589793*x/180)));
    // }
    // else if (deltaSollGravity > 0.95 && deltaSollGravity <= 1.25){
    // korr = ((posSinX+16.5)*Math.sin((double)(3.141592653589793*x/180)));
    // }// made by Christian Henke
    // else if (deltaSollGravity > 1.25){
    // korr = ((posSinX+20.8)*Math.sin((double)(3.141592653589793*x/180)));
    // }else{
    // korr = 0;
    // }
    // double azKorrigiert = x + korr;
    // return azKorrigiert;
    // }

    // private void rechneSollYBeschl() {
    // //Tiefpass 0.5Hz
    // xv[0] = xv[1]; xv[1] = xv[2]; xv[2] = xv[3];
    // xv[3] = orientation[1] / ugainA;
    // yv[0] = yv[1]; yv[1] = yv[2]; yv[2] = yv[3];
    // yv[3] = (xv[0] + xv[3]) + 3 * (xv[1] + xv[2]) + ( tpA[0] * yv[0]) + (
    // tpA[1] * yv[1]) + ( tpA[2] * yv[2]);
    // // made by Christian Henke
    // double bubu = (yv[3]*(-1));
    // sollYBeschl = (float)(Math.sin(bubu)*9.08665);
    // deltaSollGravity = Math.abs((sollYBeschl-gravity[1]));
    // }

    public void imbaGravity(float[] accel) {
        // LowPass 0.5Hz for alpha0
        xa0[0] = xa0[1];
        xa0[1] = xa0[2];
        xa0[2] = xa0[3];
        xa0[3] = accel[0] / ugainA;
        ya0[0] = ya0[1];
        ya0[1] = ya0[2];
        ya0[2] = ya0[3];
        ya0[3] = (xa0[0] + xa0[3]) + 3 * (xa0[1] + xa0[2]) + (tpA[0] * ya0[0]) + (tpA[1] * ya0[1]) + (tpA[2] * ya0[2]);
        gravity[0] = (float) ya0[3];

        // LowPass 0.5Hz for alpha1
        xa1[0] = xa1[1];
        xa1[1] = xa1[2];
        xa1[2] = xa1[3];
        xa1[3] = accel[1] / ugainA;
        ya1[0] = ya1[1];
        ya1[1] = ya1[2];
        ya1[2] = ya1[3];
        ya1[3] = (xa1[0] + xa1[3]) + 3 * (xa1[1] + xa1[2]) + (tpA[0] * ya1[0]) + (tpA[1] * ya1[1]) + (tpA[2] * ya1[2]);
        gravity[1] = (float) ya1[3];

        // LowPass 0.5Hz for alpha2
        xa2[0] = xa2[1];
        xa2[1] = xa2[2];
        xa2[2] = xa2[3];
        xa2[3] = accel[2] / ugainA;
        ya2[0] = ya2[1];
        ya2[1] = ya2[2];
        ya2[2] = ya2[3];
        ya2[3] = (xa2[0] + xa2[3]) + 3 * (xa2[1] + xa2[2]) + (tpA[0] * ya2[0]) + (tpA[1] * ya2[1]) + (tpA[2] * ya2[2]);
        gravity[2] = (float) ya2[3];
    }

    public void imbaLinear(float[] accel) {
        linear[0] = accel[0] - gravity[0];
        linear[1] = accel[1] - gravity[1];
        linear[2] = accel[2] - gravity[2];
    }

    public void calculate() {

        SensorManager.getRotationMatrix(RMatrix, iMatrix, gravity, magn);
        SensorManager.remapCoordinateSystem(RMatrix, SensorManager.AXIS_X, SensorManager.AXIS_Y, RMatrixRemapped);
        SensorManager.getOrientation(RMatrixRemapped, orientation);
        Matrix.transposeM(RMatrixTranspose, 0, RMatrix, 0);
        Matrix.multiplyMV(linearRemapped, 0, RMatrixTranspose, 0, linear, 0);

        // rechneSollYBeschl(); //Old correction of azimuth

        if (orientation[0] >= 0) {
            // Azimuth-Calculation (rad in degree)
            azimuthUnfilteredUncorrected = (orientation[0] * 57.29577951f + decl);
        } else {
            // Azimuth-Calculation (rad in degree) +360
            azimuthUnfilteredUncorrected = (orientation[0] * 57.29577951f + 360 + decl);
        }

        if (azimuthUnfilteredUncorrected >= 360) {
            azimuthUnfilteredUncorrected -= 360;
        }

        // if (android4 == false && (Karte.gravityExistiert == true ||
        // Smartgeo.gravityExistiert == true )){ //Geraete mit 2.3.3 bekommen
        // korrekturAz
        // azimuthUngefiltert =
        // korrekturAz(azimuthUnfilteredUncorrected)/57.29577951;
        // }
        // else {
        // azimuthUngefiltert = azimuthUnfilteredUncorrected /57.29577951; //war nur
        // for Az Filterung, ist also nicht mehr noetig, hat sich als ineffizient
        // herausgestellt
        // }

        azimuth = azimuthUnfilteredUncorrected;

        if (export && Config.debugMode) {
            dataOutput();
        }
    }

    public void stepDetection() {
        float value = linearRemapped[2]; // Beware: linear or 2imbaLinear
        if (initialStep && value >= stepThreshold) {
            // Introduction of a step
            initialStep = false;
            stepBegin = true;
        }
        if (stepBegin && iStep / frequency >= 0.24f && iStep / frequency <= 0.8f) {
            // Timeframe for step between minTime and maxTime
            // Messung bis negativer Peak
            if (value < -stepThreshold) {
                // TimeFrame correct AND Threshold of reverse side reached
                stepCounter++;
                stepBegin = false;
                iStep = 1;
                initialStep = true;
                newStep(azimuth);
                newStepDetected = true;
                if (export) {
                    positionOutput();
                }
            } else {
                // TimeFrame correct but negative Threshold is too low
                iStep++;
            }
        } else if (stepBegin && iStep / frequency < 0.24f) {
            // TimeFrame for step too small, so wait and iStep++
            iStep++;
        } else if (stepBegin && iStep / frequency > 0.8f) {
            // TimeFrame for step too long
            stepBegin = false;
            initialStep = true;
            iStep = 1;
        }
    }

    private void newStep(double winkel) {
        double winkel2 = winkel * 0.01745329252;
        if (Config.debugMode) {
            Log.d("Location-Status", "Step: " + Core.startLon);
        }
        deltaLat = Math.cos(winkel2) * 0.000008984725966 * stepLength;
        // 100cm for a step will be calculated according to angle on lat
        deltaLon = Math.sin(winkel2) / (distanceLongitude * 1000) * stepLength;
        // 100cm for a step will be calculated according to angle on lon

        deltaLat = Math.abs(deltaLat);
        deltaLon = Math.abs(deltaLon);
        // made by Christian Henke
        if (startLat > 0) {
            // User is on northern hemisphere, Latitude bigger than 0
            if (winkel > 270 || winkel < 90) { // Movement towards north
                startLat += deltaLat;
            } else {
                // Movement towards south
                startLat -= deltaLat;
            }
        } else if (startLat < 0) {
            // User is on southern hemisphere, Latitude smaller than 0
            if (winkel > 270 || winkel < 90) {
                // Movement towards north
                startLat += deltaLat;
            } else {
                // Movement towards south
                startLat -= deltaLat;
            }
        }
        if (winkel < 180) {
            // Movement towards east
            startLon += deltaLon;
        } else {
            // Movement towards west
            startLon -= deltaLon;
        }
    }

    public void changeDelay(int freq, int sensor) {
        // LowPassFilter 3. Order - Corner frequency all at 0.3 Hz

        //Initializing on 50Hz
        float ugain = 154994.3249f;
        float tp0 = 0.9273699683f;
        float tp1 = -2.8520278186f;
        float tp2 = 2.9246062355f;

        // Values according to actual frequency
        if (freq >= 125) {    //130
            ugain = 2662508.633f;
            tp0 = 0.9714168814f;
            tp1 = -2.9424208232f;
            tp2 = 2.9710009372f;
        } else if (freq <= 124 && freq >= 115) { //120
            ugain = 2096647.970f;
            tp0 = 0.9690721133f;
            tp1 = -2.9376603253f;
            tp2 = 2.9685843964f;
        } else if (freq <= 114 && freq >= 105) { //110
            ugain = 1617241.715f;
            tp0 = 0.9663083052f;
            tp1 = -2.9320417512f;
            tp2 = 2.9657284993f;
        } else if (freq <= 104 && freq >= 95) { //100
            ugain = 1217122.860f;
            tp0 = 0.9630021159f;
            tp1 = -2.9253101348f;
            tp2 = 2.9623014461f;
        } else if (freq <= 94 && freq >= 85) { //90
            ugain = 889124.3983f;
            tp0 = 0.9589765397f;
            tp1 = -2.9170984005f;
            tp2 = 2.9581128632f;
        } else if (freq <= 84 && freq >= 75) { //80
            ugain = 626079.3215f;
            tp0 = 0.9539681632f;
            tp1 = -2.9068581408f;
            tp2 = 2.9528771997f;
        } else if (freq <= 74 && freq >= 65) { //70
            ugain = 420820.6222f;
            tp0 = 0.9475671238f;
            tp1 = -2.8937318862f;
            tp2 = 2.9461457520f;
        } else if (freq <= 64 && freq >= 55) { //60
            ugain = 266181.2926f;
            tp0 = 0.9390989403f;
            tp1 = -2.8762997235f;
            tp2 = 2.9371707284f;
        } else if (freq <= 54 && freq >= 45) {  //50
            ugain = 154994.3249f;
            tp0 = 0.9273699683f;
            tp1 = -2.8520278186f;
            tp2 = 2.9246062355f;
        } else if (freq <= 44 && freq >= 35) { //40
            ugain = 80092.71123f;
            tp0 = 0.9100493001f;
            tp1 = -2.8159101079f;
            tp2 = 2.9057609235f;
        } else if (freq <= 34 && freq >= 28) { //30
            ugain = 34309.44333f;
            tp0 = 0.8818931306f;
            tp1 = -2.7564831952f;
            tp2 = 2.8743568927f;
        } else if (freq <= 27 && freq >= 23) { //25
            ugain = 20097.49869f;
            tp0 = 0.8599919781f;
            tp1 = -2.7096291328f;
            tp2 = 2.8492390952f;
        } else if (freq <= 22 && freq >= 15) { //20
            ugain = 10477.51171f;
            tp0 = 0.8281462754f;
            tp1 = -2.6404834928f;
            tp2 = 2.8115736773f;
        } else if (freq <= 14) { //10
            ugain = 1429.899908f;
            tp0 = 0.6855359773f;
            tp1 = -2.3146825811f;
            tp2 = 2.6235518066f;
        }

        // Set values for specific sensor
        if (sensor == 0) {
            //  Accelerometer
            frequency = freq;
            ugainA = ugain;
            tpA[0] = tp0;
            tpA[1] = tp1;
            tpA[2] = tp2;
        } else if (sensor == 1) {
            // Magnetic Field
            // here not: frequency = freq; otherwise value is wrong for step detection
            //that value has to be specified by accelerometer
            ugainM = ugain;
            tpM[0] = tp0;
            tpM[1] = tp1;
            tpM[2] = tp2;
        }
    }

    public void shutdown(Context mContext) {
        try {
            //Show new files with MTP for Windows immediatly
            mContext.sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(sensorFile)));
        } catch (Exception e) {
            // is always the case
        }
        try {
            //Show new files with MTP for Windows immediatly
            mContext.sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(posFile)));
        } catch (Exception e) {
            // is always the case
        }
        closeLogFile();
        // finish();
    }

}
