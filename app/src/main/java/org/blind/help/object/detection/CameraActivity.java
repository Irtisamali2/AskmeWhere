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

import android.Manifest;
import android.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.TriggerEvent;
import android.hardware.TriggerEventListener;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.Image.Plane;
import android.media.ImageReader;
import android.media.ImageReader.OnImageAvailableListener;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Trace;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Locale;

import org.blind.help.object.detection.env.DoubleClickListener;
import org.blind.help.object.detection.env.ImageUtils;
import org.blind.help.object.detection.env.Logger;
import org.blind.help.object.detection.R;

public abstract class CameraActivity extends AppCompatActivity
    implements OnImageAvailableListener,
        Camera.PreviewCallback,
        CompoundButton.OnCheckedChangeListener,
        View.OnClickListener, SensorEventListener {
  public String voice_text = "";
  public TextToSpeech mTTS;
  private static final Logger LOGGER = new Logger();

  public boolean  isSpeaking=false;
  public boolean isHelpMenu = false;
  public int trackI=0;
  private SensorManager sensorManager;
  private Sensor sensor;

  private static final int PERMISSIONS_REQUEST = 1;

  private static final String PERMISSION_CAMERA = Manifest.permission.CAMERA;
  protected int previewWidth = 1080;
  protected int previewHeight = 2177;
  private boolean debug = false;
  private final float[] rotationMatrix = new float[9];

  private Handler handler;
  private HandlerThread handlerThread;
  private boolean useCamera2API;
  private boolean isProcessingFrame = false;
  private byte[][] yuvBytes = new byte[3][];
  private int[] rgbBytes = null;
  private int yRowStride;
  private Runnable postInferenceCallback;
  private Runnable imageConverter;
  public String re = ".*apple.*|.*car.*|.*cat.*|.*microwave.*|.*mobile.*|.*mug.*|.*person.*|.*platter.*|.*remote.*|.*control.*|.*watch.*";
 public float xChange =0;
 public float yChange = 0;
 public float [] history = new float[2];
 public String [] direction = {"NONE","NONE"};
  public boolean isStopLooking=false;
  public boolean isAllLooking=false;
  public String cmd0="Tap on screen search for object or double tap for the help?";
  public TriggerEventListener triggerEventListener;
  public float movementAfterDetection=0;
  public int  REQUEST_CODE_SPEECH_INPUT=1000;
  Button speechBtn;
  @Override
  protected void onCreate(final Bundle savedInstanceState) {
// todo:added sensor
    Log.i("triggerEvent","oncreate");

    LOGGER.d("onCreate " + this);
    super.onCreate(null);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    setContentView(R.layout.tfe_od_activity_camera);



    speechBtn= findViewById(R.id.button);
    mTTS = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
      @Override
      public void onInit(int status) {
        if(status==TextToSpeech.SUCCESS){
         int rs= mTTS.setLanguage(Locale.ENGLISH);
         if(rs==TextToSpeech.LANG_MISSING_DATA || rs==TextToSpeech.LANG_NOT_SUPPORTED){
           Toast.makeText(getApplicationContext(),"Not Supported Language",Toast.LENGTH_SHORT).show();
         }else{
           mTTS.speak("Tap on screen, then speak the object you are looking for or double tap for the help?",TextToSpeech.QUEUE_FLUSH,null);
         }
        }else {
          Toast.makeText(getApplicationContext(),"Initialization Failed",Toast.LENGTH_SHORT).show();

        }
      }
    }, "com.google.android.tts");


    speechBtn.setOnClickListener(new DoubleClickListener() {
      @Override
      public void onDoubleClick() {
        trackI=0;
        isHelpMenu=true;
      }
//TODO: Double CLick Implement
      @Override
      public void onSingleClick() {
         trackI=0;
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE,Locale.getDefault());
        try {
          startActivityForResult(intent,REQUEST_CODE_SPEECH_INPUT );//speech
        }catch (Exception e){
          LOGGER.e("Exception:",e.toString());
          Toast.makeText(getApplicationContext(), " "+e.getMessage().toString(),Toast.LENGTH_SHORT).show();
        }
      }
    });
    if (hasPermission()) {
      setFragment();
    } else {
      requestPermission();
    }




    sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
    sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);






  }

  protected int[] getRgbBytes() {
    imageConverter.run();
    return rgbBytes;
  }

  protected int getLuminanceStride() {
    return yRowStride;
  }

  protected byte[] getLuminance() {
    return yuvBytes[0];
  }

  /** Callback for android.hardware.Camera API */
  @Override
  public void onPreviewFrame(final byte[] bytes, final Camera camera) {
    if (isProcessingFrame) {
      LOGGER.w("Dropping frame!");
      return;
    }

    try {
      // Initialize the storage bitmaps once when the resolution is known.
      if (rgbBytes == null) {
        Camera.Size previewSize = camera.getParameters().getPreviewSize();
        previewHeight = previewSize.height;
        previewWidth = previewSize.width;
        rgbBytes = new int[previewWidth * previewHeight];
        onPreviewSizeChosen(new Size(previewSize.width, previewSize.height), 90);
      }
    } catch (final Exception e) {
      LOGGER.e(e, "Exception!");
      return;
    }

    isProcessingFrame = true;
    yuvBytes[0] = bytes;
    yRowStride = previewWidth;

    imageConverter =
        new Runnable() {
          @Override
          public void run() {
            ImageUtils.convertYUV420SPToARGB8888(bytes, previewWidth, previewHeight, rgbBytes);
          }
        };

    postInferenceCallback =
        new Runnable() {
          @Override
          public void run() {
            camera.addCallbackBuffer(bytes);
            isProcessingFrame = false;
          }
        };
    processImage();
  }

  /** Callback for Camera2 API */
  @Override
  public void onImageAvailable(final ImageReader reader) {
    // We need wait until we have some size from onPreviewSizeChosen
    if (previewWidth == 0 || previewHeight == 0) {
      return;
    }
    if (rgbBytes == null) {
      rgbBytes = new int[previewWidth * previewHeight];
    }
    try {
      final Image image = reader.acquireLatestImage();

      if (image == null) {
        return;
      }

      if (isProcessingFrame) {
        image.close();
        return;
      }
      isProcessingFrame = true;
      Trace.beginSection("imageAvailable");
      final Plane[] planes = image.getPlanes();
      fillBytes(planes, yuvBytes);
      yRowStride = planes[0].getRowStride();
      final int uvRowStride = planes[1].getRowStride();
      final int uvPixelStride = planes[1].getPixelStride();

      imageConverter =
          new Runnable() {
            @Override
            public void run() {
              ImageUtils.convertYUV420ToARGB8888(
                  yuvBytes[0],
                  yuvBytes[1],
                  yuvBytes[2],
                  previewWidth,
                  previewHeight,
                  yRowStride,
                  uvRowStride,
                  uvPixelStride,
                  rgbBytes);
            }
          };

      postInferenceCallback =
          new Runnable() {
            @Override
            public void run() {
              image.close();
              isProcessingFrame = false;
            }
          };

      processImage();
    } catch (final Exception e) {
      LOGGER.e(e, "Exception!");
      Trace.endSection();
      return;
    }
    Trace.endSection();
  }

  @Override
  public synchronized void onStart() {
    LOGGER.d("onStart " + this);
    super.onStart();
  }

  @Override
  public synchronized void onResume() {
    LOGGER.d("onResume " + this);
    super.onResume();
    sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL);

    handlerThread = new HandlerThread("inference");
    handlerThread.start();
    handler = new Handler(handlerThread.getLooper());
  }

  @Override
  public synchronized void onPause() {
    LOGGER.d("onPause " + this);

    handlerThread.quitSafely();
    try {
      handlerThread.join();
      handlerThread = null;
      handler = null;
    } catch (final InterruptedException e) {
      LOGGER.e(e, "Exception!");
    }

    super.onPause();
  }

  @Override
  public synchronized void onStop() {
    LOGGER.d("onStop " + this);
    super.onStop();
  }

  @Override
  public synchronized void onDestroy() {
    LOGGER.d("onDestroy " + this);
    if(mTTS!=null){
      mTTS.stop();
      mTTS.shutdown();
    }
    super.onDestroy();
  }

  protected synchronized void runInBackground(final Runnable r) {
    if (handler != null) {
      handler.post(r);
    }
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if(requestCode==0) return;
    switch (requestCode){
      case 1000:
        if (resultCode == RESULT_OK && null != data) {
          ArrayList<String> result = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
          String text = result.get(0);
          if(text.matches(re)) {
            voice_text = "Move your phone in the environment to find object";
            mTTS.speak(voice_text,TextToSpeech.QUEUE_FLUSH,null);
            isStopLooking=false;
            isAllLooking=false;
            Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
          }else  if(text.matches(".*stop.*")) {
            voice_text = "Object Detection Has Been Stopped";
            mTTS.speak(voice_text,TextToSpeech.QUEUE_FLUSH,null);
            voice_text="";

            isStopLooking=true;
            isAllLooking=false;
            speechBtn.setText(getString(R.string.please_speak_about_the_object_you_are_looking_for) + "\n" + text);
            Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
          }else  if(text.matches(".*all.*")) {
            voice_text = "Looking For All Objects, move your phone In the environment";
            mTTS.speak(voice_text,TextToSpeech.QUEUE_FLUSH,null);
            voice_text="";
            isStopLooking=false;
            isAllLooking=true;
            speechBtn.setText(getString(R.string.please_speak_about_the_object_you_are_looking_for) + "\n" + text);
            Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
          }else{
            isStopLooking=true;
            isAllLooking=false;
            mTTS.speak("I am not sure what are you looking for try different object",TextToSpeech.QUEUE_FLUSH,null);

          }
        }
        break;
      default:
        throw new IllegalStateException("Unexpected value: " + requestCode);
    }
  }

  @Override
  public void onRequestPermissionsResult(
      final int requestCode, final String[] permissions, final int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    if (requestCode == PERMISSIONS_REQUEST) {
      if (allPermissionsGranted(grantResults)) {
        setFragment();
      } else {
        requestPermission();
      }
    }
  }

  private static boolean allPermissionsGranted(final int[] grantResults) {
    for (int result : grantResults) {
      if (result != PackageManager.PERMISSION_GRANTED) {
        return false;
      }
    }
    return true;
  }

  private boolean hasPermission() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      return checkSelfPermission(PERMISSION_CAMERA) == PackageManager.PERMISSION_GRANTED;
    } else {
      return true;
    }
  }

  private void requestPermission() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      if (shouldShowRequestPermissionRationale(PERMISSION_CAMERA)) {
        Toast.makeText(
                CameraActivity.this,
                "Camera permission is required for this demo",
                Toast.LENGTH_LONG)
            .show();
      }
      requestPermissions(new String[] {PERMISSION_CAMERA}, PERMISSIONS_REQUEST);
    }
  }

  // Returns true if the device supports the required hardware level, or better.
  private boolean isHardwareLevelSupported(
      CameraCharacteristics characteristics, int requiredLevel) {
    int deviceLevel = characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
    if (deviceLevel == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY) {
      return requiredLevel == deviceLevel;
    }
    // deviceLevel is not LEGACY, can use numerical sort
    return requiredLevel <= deviceLevel;
  }

  private String chooseCamera() {
    final CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
    try {
      for (final String cameraId : manager.getCameraIdList()) {
        final CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);

        // We don't use a front facing camera in this sample.
        final Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
        if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
          continue;
        }

        final StreamConfigurationMap map =
            characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

        if (map == null) {
          continue;
        }

        // Fallback to camera1 API for internal cameras that don't have full support.
        // This should help with legacy situations where using the camera2 API causes
        // distorted or otherwise broken previews.
        useCamera2API =
            (facing == CameraCharacteristics.LENS_FACING_EXTERNAL)
                || isHardwareLevelSupported(
                    characteristics, CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL);
        LOGGER.i("Camera API lv2?: %s", useCamera2API);
        return cameraId;
      }
    } catch (CameraAccessException e) {
      LOGGER.e(e, "Not allowed to access camera");
    }

    return null;
  }

  protected void setFragment() {
    String cameraId = chooseCamera();

    Fragment fragment;
    if (useCamera2API) {
      CameraConnectionFragment camera2Fragment =
          CameraConnectionFragment.newInstance(
              new CameraConnectionFragment.ConnectionCallback() {
                @Override
                public void onPreviewSizeChosen(final Size size, final int rotation) {
                  previewHeight = size.getHeight();
                  previewWidth = size.getWidth();
                  CameraActivity.this.onPreviewSizeChosen(size, rotation);
                }
              },
              this,
              getLayoutId(),
              getDesiredPreviewFrameSize());

      camera2Fragment.setCamera(cameraId);
      fragment = camera2Fragment;
    } else {
      fragment =
          new LegacyCameraConnectionFragment(this, getLayoutId(), getDesiredPreviewFrameSize());
    }

    getFragmentManager().beginTransaction().replace(R.id.container, fragment).commit();
  }

  protected void fillBytes(final Plane[] planes, final byte[][] yuvBytes) {
    // Because of the variable row stride it's not possible to know in
    // advance the actual necessary dimensions of the yuv planes.
    for (int i = 0; i < planes.length; ++i) {
      final ByteBuffer buffer = planes[i].getBuffer();
      if (yuvBytes[i] == null) {
        LOGGER.d("Initializing buffer %d at size %d", i, buffer.capacity());
        yuvBytes[i] = new byte[buffer.capacity()];
      }
      buffer.get(yuvBytes[i]);
    }
  }

  public boolean isDebug() {
    return debug;
  }

  protected void readyForNextImage() {
    if (postInferenceCallback != null) {
      postInferenceCallback.run();
    }
  }

  protected int getScreenOrientation() {
    switch (getWindowManager().getDefaultDisplay().getRotation()) {
      case Surface.ROTATION_270:
        return 270;
      case Surface.ROTATION_180:
        return 180;
      case Surface.ROTATION_90:
        return 90;
      default:
        return 0;
    }
  }

  @Override
  public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
    setUseNNAPI(isChecked);
  }

  @Override
  public void onClick(View v) {

  }
  protected abstract void processImage();

  protected abstract void onPreviewSizeChosen(final Size size, final int rotation);

  protected abstract int getLayoutId();

  protected abstract Size getDesiredPreviewFrameSize();

  protected abstract void setNumThreads(int numThreads);

  protected abstract void setUseNNAPI(boolean isChecked);

  @Override
  public void onSensorChanged(SensorEvent event) {
    Log.i("onSensorChanged: ",String.valueOf(event.values.length));
    if(event.sensor.getType()==Sensor.TYPE_ACCELEROMETER){
      Log.i("onSensorChanged: ", String.valueOf(event.values[0]+","+event.values[1]+","+event.values[2]));
//      System.arraycopy(event.values, 0, rotationMatrix,
//              0, rotationMatrix.length);
      float tempx=history[0] - event.values[0];
      float tempy = history[1] - event.values[1];

      Log.i("onSensorChanged X: ", String.valueOf(xChange+" , error,"+ tempx/(tempx-xChange)*100));

//      if(tempx>0.0001 || tempx> -0.0001 )
       xChange =tempx;
//       else
//         xChange=0;

//      if(tempx>0.0001 || tempx> -0.0001 )
        yChange =tempy;
//      else
//        yChange=0;


      history[0] = event.values[0];
      history[1] = event.values[1];

      if (xChange > 2){
        direction[0] = "LEFT";
      }
      else if (xChange < -2){
        direction[0] = "RIGHT";
      }

    }
    LOGGER.i("sensor","sensing");
  }
}
