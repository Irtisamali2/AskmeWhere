//
//
//package org.blind.help.object.detection;
//
//import android.content.res.Resources;
//import android.graphics.Bitmap;
//import android.graphics.Bitmap.Config;
//import android.graphics.Canvas;
//import android.graphics.Color;
//import android.graphics.Matrix;
//import android.graphics.Paint;
//import android.graphics.Paint.Style;
//import android.graphics.RectF;
//import android.graphics.Typeface;
//import android.hardware.Sensor;
//import android.media.ImageReader.OnImageAvailableListener;
//import android.os.SystemClock;
//import android.speech.tts.TextToSpeech;
//import android.util.Log;
//import android.util.Size;
//import android.util.TypedValue;
//import android.widget.Button;
//import android.widget.Toast;
//
//import java.io.IOException;
//import java.util.LinkedList;
//import java.util.List;
//import java.util.Locale;
//
//import org.blind.help.object.detection.customview.OverlayView;
//import org.blind.help.object.detection.customview.OverlayView.DrawCallback;
//import org.blind.help.object.detection.env.BorderedText;
//import org.blind.help.object.detection.env.ImageUtils;
//import org.blind.help.object.detection.env.Logger;
//import org.blind.help.object.detection.tflite.Classifier;
//import org.blind.help.object.detection.tflite.YoloV4Classifier;
//import org.blind.help.object.detection.tracking.MultiBoxTracker;
//
///**
// * An activity that uses a TensorFlowMultiBoxDetector and ObjectTracker to detect and then track
// * objects.
// */
//public class DetectorActivity extends CameraActivity implements OnImageAvailableListener {
//    private static final Logger LOGGER = new Logger();
//    public static String speakThis="";
//    public String searchingClass="";
//
//    private static final int TF_OD_API_INPUT_SIZE = 416;
//    private static final boolean TF_OD_API_IS_QUANTIZED = false;
//    private static final String TF_OD_API_MODEL_FILE = "yolov4-416-fp16.tflite";
//    public  boolean IsDetectionFinish=false;
//    private static final String TF_OD_API_LABELS_FILE = "file:///android_asset/coco.txt";
//    public String cmd1Help="App can perfome following functionalities:\n" +
//            "It can detect following objects.\n1 Person.\n2 Apple\n3 Mug.\n4 Car.\n5 " +
//            "Cat.\n6 bowl.\n/7 watch.\n8 mobile phone.\n9 remote control.\n10 microwave oven.\n" +
//            "11 laptop.\n12 toilet.\n13 spoon.\n14 fork.\n15 chair.\n16 table.\n17 bottle.\n18 toaster.\n19 suitcase.\n20 platter.\n";
//    private static final DetectorMode MODE = DetectorMode.TF_OD_API;
//    private static final float MINIMUM_CONFIDENCE_TF_OD_API = 0.5f;
//    private static final boolean MAINTAIN_ASPECT = false;
//    private static final Size DESIRED_PREVIEW_SIZE = new Size(getScreenWidth(), getScreenHeight()*2);
//    private static final boolean SAVE_PREVIEW_BITMAP = false;
//    private static final float TEXT_SIZE_DIP = 10;
//    OverlayView trackingOverlay;
//    private Integer sensorOrientation;
//
//    private Classifier detector;
//
//    private long lastProcessingTimeMs;
//    private Bitmap rgbFrameBitmap = null;
//    private Bitmap croppedBitmap = null;
//    private Bitmap cropCopyBitmap = null;
//    private boolean computingDetection = false;
//
//    private long timestamp = 0;
//
//    private Matrix frameToCropTransform;
//    private Matrix cropToFrameTransform;
//
//    private MultiBoxTracker tracker;
//
//    private BorderedText borderedText;
//    String name;
//    float confi;
//
//    @Override
//    public void onPreviewSizeChosen(final Size size, final int rotation) {
//        final float textSizePx =
//                TypedValue.applyDimension(
//                        TypedValue.COMPLEX_UNIT_DIP, TEXT_SIZE_DIP, getResources().getDisplayMetrics());
//        borderedText = new BorderedText(textSizePx);
//        borderedText.setTypeface(Typeface.MONOSPACE);
//
//        tracker = new MultiBoxTracker(this);
//
//        int cropSize = TF_OD_API_INPUT_SIZE;
//
//        try {
//            Button btn = (Button) findViewById(R.id.button);
//            detector =
//                    YoloV4Classifier.create(
//                            btn.getText().toString(),
//                            this,
//                            getAssets(),
//                            TF_OD_API_MODEL_FILE,
//                            TF_OD_API_LABELS_FILE,
//                            TF_OD_API_IS_QUANTIZED);
//
//
//            cropSize = TF_OD_API_INPUT_SIZE;
//        } catch (final IOException e) {
//            e.printStackTrace();
//            LOGGER.e(e, "Exception initializing classifier!");
//            Toast toast =
//                    Toast.makeText(
//                            getApplicationContext(), "Classifier could not be initialized", Toast.LENGTH_SHORT);
//            toast.show();
//            finish();
//        }
//
//        previewWidth = size.getWidth();
//        previewHeight = size.getHeight();
//
//        sensorOrientation = rotation - getScreenOrientation();
//        LOGGER.i("Camera orientation relative to screen canvas: %d", sensorOrientation);
//
//        LOGGER.i("Initializing at size %dx%d", previewWidth, previewHeight);
//        rgbFrameBitmap = Bitmap.createBitmap(previewWidth, previewHeight, Config.ARGB_8888);
//        croppedBitmap = Bitmap.createBitmap(cropSize, cropSize, Config.ARGB_8888);
//
//        frameToCropTransform =
//                ImageUtils.getTransformationMatrix(
//                        previewWidth, previewHeight,
//                        cropSize, cropSize,
//                        sensorOrientation, MAINTAIN_ASPECT);
//
//        cropToFrameTransform = new Matrix();
//        frameToCropTransform.invert(cropToFrameTransform);
//
//        trackingOverlay = (OverlayView) findViewById(R.id.tracking_overlay);
//        trackingOverlay.addCallback(
//                new DrawCallback() {
//                    @Override
//                    public void drawCallback(final Canvas canvas) {
//                        tracker.draw(canvas);
//                        if (isDebug()) {
//                            tracker.drawDebug(canvas);
//                        }
//                    }
//                });
//
//        tracker.setFrameConfiguration(previewWidth, previewHeight, sensorOrientation);
//    }
//
//    @Override
//    protected void processImage() {
//
//        ++timestamp;
//        final long currTimestamp = timestamp;
//        trackingOverlay.postInvalidate();
//
//        // No mutex needed as this method is not reentrant.
//        if (computingDetection) {
//            readyForNextImage();
//            return;
//        }
//        computingDetection = true;
//        LOGGER.i("Preparing image " + currTimestamp + " for detection in bg thread.");
//
//        rgbFrameBitmap.setPixels(getRgbBytes(), 0, previewWidth, 0, 0, previewWidth, previewHeight);
//
//        readyForNextImage();
//
//        final Canvas canvas = new Canvas(croppedBitmap);
//        canvas.drawBitmap(rgbFrameBitmap, frameToCropTransform, null);
//        // For examining the actual TF input.
//        if (SAVE_PREVIEW_BITMAP) {
//            ImageUtils.saveBitmap(croppedBitmap);
//        }
//
//        /** background **/
//        runInBackground(
//                () -> {
//
//
//                    Log.i("focal",String.valueOf(DetectorActivity.focal));
//
//                    //todo: Navigation implementation
//                    LOGGER.i("Running detection on image " + currTimestamp);
//                    final long startTime = SystemClock.uptimeMillis();
//                    final List<Classifier.Recognition> results = detector.recognizeImage(croppedBitmap);
//                    lastProcessingTimeMs = SystemClock.uptimeMillis() - startTime;
//                    if(!results.isEmpty() && results.get(0).getTitle().toLowerCase(Locale.ROOT).matches(re))searchingClass=results.get(0).className;
//
//
//                    trackI=trackI+1;
//                    if(trackI>=25 && !isHelpMenu && !isSpeaking) {
//
//                        speakThis = searchingClass.isEmpty()? cmd0 : cmd0 + " or tap and say " + searchingClass +" for searching "+searchingClass;
//                        trackI=2;
//                    }
//
//
//
//                    if (mTTS.isSpeaking()){
//                        trackI=0;
//                        isSpeaking=true;
//                    }
//                    if (!mTTS.isSpeaking()){
//                        isSpeaking=false;
//
//                    }
//
//
//
//
//                    cropCopyBitmap = Bitmap.createBitmap(croppedBitmap);
//                    final Canvas canvas1 = new Canvas(cropCopyBitmap);
//                    final Paint paint = new Paint();
//                    paint.setColor(Color.RED);
//                    paint.setStyle(Style.STROKE);
//                    paint.setStrokeWidth(2.0f);
//
//                    float minimumConfidence = MINIMUM_CONFIDENCE_TF_OD_API;
//                    switch (MODE) {
//                        case TF_OD_API:
//                            minimumConfidence = MINIMUM_CONFIDENCE_TF_OD_API;
//                            break;
//                    }
//                    //todo: Add Help menu having list of commands
//                    if(isHelpMenu && !isSpeaking){
//
//                        if(!results.isEmpty())
//                            searchingClass= results.get(0).className;
//
//
//                        speakThis=cmd1Help;
//
//                        isHelpMenu=false;
//                    }
//
//
//                    final List<Classifier.Recognition> mappedRecognitions =
//                            new LinkedList<Classifier.Recognition>();
//                    int i=0;
//
//                    if(!isSpeaking && !isHelpMenu && voice_text.toLowerCase(Locale.ROOT).matches(re+"|.*stop.*|.*all.*"))
//                        for (final Classifier.Recognition result : results) {
//                            movementAfterDetection=0;
//                            final RectF location = result.getLocation();
//
//                            name=result.getTitle();
//                            confi=result.getConfidence();
//                            String className= result.className;
//                            Log.i("loop: Class Name: ",className);
//                            Log.i("loop: voice text: ",className);
//                            Log.i("loop: condition 1: ",String.valueOf(location != null && result.getConfidence() >= minimumConfidence  && name.toLowerCase(Locale.ROOT).matches(re)));
//
//                            if (location != null && result.getConfidence() >= minimumConfidence  && name.toLowerCase(Locale.ROOT).matches(re) ) {
//                                trackI=0;
//                                if (!isAllLooking && !isStopLooking && voice_text.toLowerCase(Locale.ROOT).contains(className) )
//                                {
//
//                                    i++;
//                                    speakThis = speakThis+ "\nobject"+i+"."+name+" with " + String.format("%.02f", confi * 100) + " confidence.\n";
//                                }else if (isAllLooking){
//                                    i++;
//                                    speakThis = speakThis+"\nobject"+i+"."+name+" with " + String.format("%.02f", confi * 100) + " confidence.\n";
//                                }else {
//                                    continue;
//                                }
//
//                                canvas1.drawRect(location, paint);
//                                confi=100*result.getConfidence();
//                                cropToFrameTransform.mapRect(location);
//
//                                result.setLocation(location);
//                                mappedRecognitions.add(result);
//
//
//                            }
//                            IsDetectionFinish=true;
//                        }
//
//                    tracker.trackResults(mappedRecognitions, currTimestamp);
//                    trackingOverlay.postInvalidate();
//
//                    computingDetection = false;
//
//                    runOnUiThread(
//                            () -> {
//
//                                if(!speakThis.isEmpty() ){
//                                    mTTS.speak(speakThis,TextToSpeech.QUEUE_FLUSH,null,null);
//                                    speakThis="";
//                                    if (mTTS.isSpeaking()){
//                                        isSpeaking=true;
//
//                                    }
//                                    if (!mTTS.isSpeaking()) {
//                                        isSpeaking = false;
//                                    }
//                                }
//
//                            });
//                });
//    }
//
//
//    public static int getScreenWidth() {
//        Log.i("wxh",""+Resources.getSystem().getDisplayMetrics().widthPixels+"x"+Resources.getSystem().getDisplayMetrics().heightPixels);
//        return Resources.getSystem().getDisplayMetrics().widthPixels;
//    }
//
//    public static int getScreenHeight() {
//        return Resources.getSystem().getDisplayMetrics().heightPixels;
//    }
//
//    @Override
//    public synchronized void onStart() {
//        super.onStart();
//    }
//
//    @Override
//    protected int getLayoutId() {
//        return R.layout.tfe_od_camera_connection_fragment_tracking;
//    }
//
//    @Override
//    protected Size getDesiredPreviewFrameSize() {
//        return DESIRED_PREVIEW_SIZE;
//    }
//
//    @Override
//    public void onAccuracyChanged(Sensor sensor, int accuracy) {
//
//    }
//
//    // Which detection model to use: by default uses Tensorflow Object Detection API frozen
//    // checkpoints.
//    private enum DetectorMode {
//        TF_OD_API
//    }
//
//    @Override
//    protected void setUseNNAPI(final boolean isChecked) {
//
//
//        runInBackground(() -> detector.setUseNNAPI(isChecked));
//    }
//
//    @Override
//    protected void setNumThreads(final int numThreads) {
//        runInBackground(() -> detector.setNumThreads(numThreads));
//    }
//
//
//}
