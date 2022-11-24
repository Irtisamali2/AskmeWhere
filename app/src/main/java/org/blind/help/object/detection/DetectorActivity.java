/*
 * Copyright 2019 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.blind.help.object.detection;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.media.ImageReader.OnImageAvailableListener;
import android.media.MediaPlayer;
import android.os.SystemClock;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.util.Size;
import android.util.TypedValue;
import android.widget.Button;
import android.widget.Toast;

import java.io.Console;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

import org.blind.help.object.detection.R;
import org.blind.help.object.detection.customview.OverlayView;
import org.blind.help.object.detection.customview.OverlayView.DrawCallback;
import org.blind.help.object.detection.env.BorderedText;
import org.blind.help.object.detection.env.ImageUtils;
import org.blind.help.object.detection.env.Logger;
import org.blind.help.object.detection.tflite.Classifier;
import org.blind.help.object.detection.tflite.YoloV4Classifier;
import org.blind.help.object.detection.tracking.MultiBoxTracker;

/**
 * An activity that uses a TensorFlowMultiBoxDetector and ObjectTracker to detect and then track
 * objects.
 */
public class DetectorActivity extends CameraActivity implements OnImageAvailableListener {
    private static final Logger LOGGER = new Logger();
public static String speakThis="";
public boolean navigation=false;
    public String searchingClass="";

    private static final int TF_OD_API_INPUT_SIZE = 416;
    private static final boolean TF_OD_API_IS_QUANTIZED = false;
    private static final String TF_OD_API_MODEL_FILE = "yolov4-416-fp16.tflite";
    public  boolean IsDetectionFinish=false;
    private static final String TF_OD_API_LABELS_FILE = "file:///android_asset/coco.txt";
    public String cmd1Help="Application can perfome following functionalities: " +
            "\n1. Objects you can detect are person, apple, mug, car, cat, bowl, watch, mobile phone, remote control and microwave oven" +
            "\n2. You can detect object By Tapping on Screen and saying any sentence containing any of object from above objects" +
            "\n3. You can search all objects by saying \"all\"  objects after tapping on the screen";
    private static final DetectorMode MODE = DetectorMode.TF_OD_API;
    private static final float MINIMUM_CONFIDENCE_TF_OD_API = 0.5f;
    private static final boolean MAINTAIN_ASPECT = false;
    private static final Size DESIRED_PREVIEW_SIZE = new Size(getScreenWidth(), getScreenHeight()*2);
    private static final boolean SAVE_PREVIEW_BITMAP = false;
    private static final float TEXT_SIZE_DIP = 10;
    OverlayView trackingOverlay;
    private Integer sensorOrientation;
//    MediaPlayer mp ,mp1,mp2;

    private Classifier detector;

    private long lastProcessingTimeMs;
    private Bitmap rgbFrameBitmap = null;
    private Bitmap croppedBitmap = null;
    private Bitmap cropCopyBitmap = null;
    private long waitingTime = 0;
    private boolean computingDetection = false;

    private long timestamp = 0;

    private Matrix frameToCropTransform;
    private Matrix cropToFrameTransform;

    private MultiBoxTracker tracker;

    private BorderedText borderedText;
    String name;
    float confi;
    TextToSpeech texttospeach;

    @Override
    public void onPreviewSizeChosen(final Size size, final int rotation) {
        final float textSizePx =
                TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP, TEXT_SIZE_DIP, getResources().getDisplayMetrics());
        borderedText = new BorderedText(textSizePx);
        borderedText.setTypeface(Typeface.MONOSPACE);

        tracker = new MultiBoxTracker(this);

        int cropSize = TF_OD_API_INPUT_SIZE;

        try {
            Button btn = (Button) findViewById(R.id.button);
            detector =
                    YoloV4Classifier.create(
                            btn.getText().toString(),
                            this,
                            getAssets(),
                            TF_OD_API_MODEL_FILE,
                            TF_OD_API_LABELS_FILE,
                            TF_OD_API_IS_QUANTIZED);


            cropSize = TF_OD_API_INPUT_SIZE;
        } catch (final IOException e) {
            e.printStackTrace();
            LOGGER.e(e, "Exception initializing classifier!");
            Toast toast =
                    Toast.makeText(
                            getApplicationContext(), "Classifier could not be initialized", Toast.LENGTH_SHORT);
            toast.show();
            finish();
        }

        previewWidth = size.getWidth();
        previewHeight = size.getHeight();

        sensorOrientation = rotation - getScreenOrientation();
        LOGGER.i("Camera orientation relative to screen canvas: %d", sensorOrientation);

        LOGGER.i("Initializing at size %dx%d", previewWidth, previewHeight);
        rgbFrameBitmap = Bitmap.createBitmap(previewWidth, previewHeight, Config.ARGB_8888);
        croppedBitmap = Bitmap.createBitmap(cropSize, cropSize, Config.ARGB_8888);

        frameToCropTransform =
                ImageUtils.getTransformationMatrix(
                        previewWidth, previewHeight,
                        cropSize, cropSize,
                        sensorOrientation, MAINTAIN_ASPECT);

        cropToFrameTransform = new Matrix();
        frameToCropTransform.invert(cropToFrameTransform);

        trackingOverlay = (OverlayView) findViewById(R.id.tracking_overlay);
        trackingOverlay.addCallback(
                new DrawCallback() {
                    @Override
                    public void drawCallback(final Canvas canvas) {
                        tracker.draw(canvas);
                        if (isDebug()) {
                            tracker.drawDebug(canvas);
                        }
                    }
                });

        tracker.setFrameConfiguration(previewWidth, previewHeight, sensorOrientation);
    }

    @Override
    protected void processImage() {

        ++timestamp;
        final long currTimestamp = timestamp;
        trackingOverlay.postInvalidate();

        // No mutex needed as this method is not reentrant.
        if (computingDetection) {
            readyForNextImage();
            return;
        }
        computingDetection = true;
        LOGGER.i("Preparing image " + currTimestamp + " for detection in bg thread.");

        rgbFrameBitmap.setPixels(getRgbBytes(), 0, previewWidth, 0, 0, previewWidth, previewHeight);

        readyForNextImage();

        final Canvas canvas = new Canvas(croppedBitmap);
        canvas.drawBitmap(rgbFrameBitmap, frameToCropTransform, null);
        // For examining the actual TF input.
        if (SAVE_PREVIEW_BITMAP) {
            ImageUtils.saveBitmap(croppedBitmap);
        }

        /** background **/
        runInBackground(
                new Runnable() {
                    @Override
                    public void run() {
                        //todo: Navigation implementation
                        LOGGER.i("Running detection on image " + currTimestamp);
                        final long startTime = SystemClock.uptimeMillis();
                        final List<Classifier.Recognition> results = detector.recognizeImage(croppedBitmap);
                        lastProcessingTimeMs = SystemClock.uptimeMillis() - startTime;
                        if(!results.isEmpty() && results.get(0).getTitle().toLowerCase(Locale.ROOT).matches(re))searchingClass=results.get(0).className;


                            trackI=trackI+1;
                            if(trackI%20==0 && !isHelpMenu && !isSpeaking) {

                                speakThis = searchingClass.isEmpty()? cmd0 : cmd0 + " or tap and say " + searchingClass +" for searching "+searchingClass;
                            }

                        Log.i("timeunit",String.valueOf(trackI));
                        if (mTTS.isSpeaking()){
                            isSpeaking=true;
                        }
                        if (!mTTS.isSpeaking()){
                            isSpeaking=false;

                    }



                        cropCopyBitmap = Bitmap.createBitmap(croppedBitmap);
                        final Canvas canvas = new Canvas(cropCopyBitmap);
                        final Paint paint = new Paint();
                        paint.setColor(Color.RED);
                        paint.setStyle(Style.STROKE);
                        paint.setStrokeWidth(2.0f);

                        float minimumConfidence = MINIMUM_CONFIDENCE_TF_OD_API;
                        switch (MODE) {
                            case TF_OD_API:
                                minimumConfidence = MINIMUM_CONFIDENCE_TF_OD_API;
                                break;
                        }
                        //todo: Add Help menu having list of commands
                        if(isHelpMenu && !isSpeaking){

                            if(!results.isEmpty())
                               searchingClass= results.get(0).className;


                                speakThis=cmd1Help;

                        isHelpMenu=false;
                        }
//                        movementAfterDetection=movementAfterDetection+xChange;
//                        if(movementAfterDetection>2){
//                            Log.i("Direction :","Left : "+movementAfterDetection);
//                        }else if(movementAfterDetection<2)
//                            Log.i("Direction :","Right : "+movementAfterDetection);


                        Log.i("Movement detected:",String.valueOf(movementAfterDetection));
                        final List<Classifier.Recognition> mappedRecognitions =
                                new LinkedList<Classifier.Recognition>();
                        int i=0;

                       Log.i("voice Text","voice: "+searchingClass);
                        if(!isSpeaking && !isHelpMenu && voice_text.toLowerCase(Locale.ROOT).matches(re+"|.*stop.*|.*all.*"))
                        for (final Classifier.Recognition result : results) {
                            movementAfterDetection=0;
                            final RectF location = result.getLocation();
                            name=result.getTitle();
                            confi=result.getConfidence();
                            if (location != null && result.getConfidence() >= minimumConfidence  && name.matches(re) ) {
                                trackI=0;
                                if (!isAllLooking && !isStopLooking && voice_text.toLowerCase(Locale.ROOT).matches(re) && name.toLowerCase(Locale.ROOT).matches(re))
                                {

                                    i++;
                              speakThis =  "object"+i+"."+name+" with " + String.format("%.02f", confi * 100) + " confidence, "+speakThis;
                               }else if (isAllLooking){
                                    i++;
                                    speakThis =  "object"+i+"."+name+" with " + String.format("%.02f", confi * 100) + " confidence, "+speakThis;
                             }else {
                                    continue;
                                }

                                canvas.drawRect(location, paint);
                                confi=100*result.getConfidence();
                                cropToFrameTransform.mapRect(location);

                                result.setLocation(location);
                                mappedRecognitions.add(result);


                            }
                            IsDetectionFinish=true;
                        }

                        tracker.trackResults(mappedRecognitions, currTimestamp);
                        trackingOverlay.postInvalidate();

                        computingDetection = false;

                        runOnUiThread(
                                new Runnable() {
                                    @Override
                                    public void run() {
                                       if(!speakThis.isEmpty() ){
                                          int rs= mTTS.speak(speakThis,TextToSpeech.QUEUE_FLUSH,null);
                                           speakThis="";
                                          if (mTTS.isSpeaking()){
                                              isSpeaking=true;

                                          }
                                          if (!mTTS.isSpeaking())
                                              isSpeaking=false;

                                       }

                                    }
                                });
                    }
                });
    }


    public static int getScreenWidth() {
        Log.i("wxh",""+Resources.getSystem().getDisplayMetrics().widthPixels+"x"+Resources.getSystem().getDisplayMetrics().heightPixels);
        return Resources.getSystem().getDisplayMetrics().widthPixels;
    }

    public static int getScreenHeight() {
        return Resources.getSystem().getDisplayMetrics().heightPixels;
    }

    @Override
    public synchronized void onStart() {
        super.onStart();
    }

    @Override
    protected int getLayoutId() {
        return R.layout.tfe_od_camera_connection_fragment_tracking;
    }

    @Override
    protected Size getDesiredPreviewFrameSize() {
        return DESIRED_PREVIEW_SIZE;
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    // Which detection model to use: by default uses Tensorflow Object Detection API frozen
    // checkpoints.
    private enum DetectorMode {
        TF_OD_API;
    }

    @Override
    protected void setUseNNAPI(final boolean isChecked) {


        runInBackground(() -> detector.setUseNNAPI(isChecked));
    }

    @Override
    protected void setNumThreads(final int numThreads) {
        runInBackground(() -> detector.setNumThreads(numThreads));
    }


}
