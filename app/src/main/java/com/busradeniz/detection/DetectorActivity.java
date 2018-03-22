package com.busradeniz.detection;

/**
 * Created by Shardool and Varun on 2/25/2018.
 */
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.media.ImageReader.OnImageAvailableListener;
import android.net.Uri;
import android.os.Environment;
import android.os.StrictMode;
import android.provider.MediaStore;
import android.speech.tts.TextToSpeech;
import android.util.Base64;
import android.util.Log;
import android.util.Size;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.SurfaceView;
import android.widget.ImageView;
import android.widget.Toast;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import com.busradeniz.detection.AzureHelper.CreatePersonForGroup;
import com.busradeniz.detection.AzureHelper.CreatePersonGroup;
import com.busradeniz.detection.env.BorderedText;
import com.busradeniz.detection.env.ImageUtils;
import com.busradeniz.detection.env.Logger;
import com.busradeniz.detection.tracking.ImageHelper;
import com.busradeniz.detection.tracking.MultiBoxTracker;
import com.google.android.gms.vision.Frame;
import com.google.android.gms.vision.face.FaceDetector;
import com.google.android.gms.vision.text.TextBlock;
import com.google.android.gms.vision.text.TextRecognizer;
import com.microsoft.projectoxford.face.*;
import com.microsoft.projectoxford.face.contract.*;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.microsoft.projectoxford.face.*;
import com.microsoft.projectoxford.face.contract.*;
import com.microsoft.projectoxford.face.contract.IdentifyResult;
import com.microsoft.projectoxford.face.contract.TrainingStatus;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static android.content.ContentValues.TAG;


public class DetectorActivity extends CameraActivity implements OnImageAvailableListener {
  private static final Logger LOGGER = new Logger();

  private static final int TF_OD_API_INPUT_SIZE = 300;
  private static final String TF_OD_API_MODEL_FILE =
      "file:///android_asset/ssd_mobilenet_v1_android_export.pb";
  private static final String TF_OD_API_LABELS_FILE = "file:///android_asset/coco_labels_list.txt";

  // Minimum detection confidence to track a detection.
  private static final float MINIMUM_CONFIDENCE_TF_OD_API = 0.6f;

  private static final Size DESIRED_PREVIEW_SIZE = new Size(640, 480);

  private static final float TEXT_SIZE_DIP = 10;

  private Integer sensorOrientation;
  private static int count = 0;
  private Classifier detector;

  private Bitmap rgbFrameBitmap = null;
  private Bitmap croppedBitmap = null;
  private Bitmap cropCopyBitmap = null;

  private boolean computingDetection = false;

  private long timestamp = 0;

  private Matrix frameToCropTransform;
  private Matrix cropToFrameTransform;

  private MultiBoxTracker tracker;

  private byte[] luminanceCopy;

  private BorderedText borderedText;

  public Context c;
  private List<Face> faces;
  private boolean detected;
  private Bitmap mBitmap;

  public static String imageText = "";
  public static String personName ="";
  private String person;
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
      detector = TensorFlowObjectDetectionAPIModel.create(
          getAssets(), TF_OD_API_MODEL_FILE, TF_OD_API_LABELS_FILE, TF_OD_API_INPUT_SIZE);
      cropSize = TF_OD_API_INPUT_SIZE;
    } catch (final IOException e) {
      LOGGER.e("Exception initializing classifier!", e);
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
            sensorOrientation, false);

    cropToFrameTransform = new Matrix();
    frameToCropTransform.invert(cropToFrameTransform);

    trackingOverlay = findViewById(R.id.tracking_overlay);
    trackingOverlay.addCallback(
        new OverlayView.DrawCallback() {
          @Override
          public void drawCallback(final Canvas canvas) {
            tracker.draw(canvas);
          }
        });
  }

  OverlayView trackingOverlay;

  @Override
  protected void processImage() {
    ++timestamp;
    final long currTimestamp = timestamp;
    byte[] originalLuminance = getLuminance();
    tracker.onFrame(
        previewWidth,
        previewHeight,
        sensorOrientation);
    trackingOverlay.postInvalidate();

    // No mutex needed as this method is not reentrant.
    if (computingDetection) {
      readyForNextImage();
      return;
    }
    computingDetection = true;
    LOGGER.i("Preparing image " + currTimestamp + " for detection in bg thread.");

    rgbFrameBitmap.setPixels(getRgbBytes(), 0, previewWidth, 0, 0, previewWidth, previewHeight);

    if (luminanceCopy == null) {
      luminanceCopy = new byte[originalLuminance.length];
    }
    System.arraycopy(originalLuminance, 0, luminanceCopy, 0, originalLuminance.length);
    readyForNextImage();

    final Canvas canvas = new Canvas(croppedBitmap);
    canvas.drawBitmap(rgbFrameBitmap, frameToCropTransform, null);
    c = getApplicationContext();
    runInBackground(
        new Runnable() {
          @Override
          public void run() {
            LOGGER.i("Running detection on image " + currTimestamp);
            imageText = "";
            //TODO: ADD BITMAP -> SPEECH HERE
            TextRecognizer textRecognizer = new TextRecognizer.Builder(getApplicationContext()).build();

            Frame imageFrame = new Frame.Builder()

                    .setBitmap(croppedBitmap)                 // your image bitmap
                    .build();

            SparseArray<TextBlock> textBlocks = textRecognizer.detect(imageFrame);

            for (int i = 0; i < textBlocks.size(); i++) {
              TextBlock textBlock = textBlocks.get(textBlocks.keyAt(i));
              imageText = textBlock.getValue();
            }
            imageText = imageText.toLowerCase();
            Log.d("text",imageText);

            //FaceServiceClient faceServiceClient = new FaceServiceRestClient("https://westcentralus.api.cognitive.microsoft.com/face/v1.0", "86fab03db5424440ae3e31ba2011d96d");
            final List<Classifier.Recognition> results = detector.recognizeImage(croppedBitmap);

            cropCopyBitmap = Bitmap.createBitmap(croppedBitmap);
            final Canvas canvas = new Canvas(cropCopyBitmap);
            final Paint paint = new Paint();
            paint.setColor(Color.RED);
            paint.setStyle(Style.STROKE);
            paint.setStrokeWidth(2.0f);

              final List<Classifier.Recognition> mappedRecognitions =
                    new LinkedList<>();

            for (final Classifier.Recognition result : results) {
              final RectF location = result.getLocation();
              if (location != null && result.getConfidence() >= MINIMUM_CONFIDENCE_TF_OD_API) {
                canvas.drawRect(location, paint);

                /*if(result.getTitle().equals("person")) {
                  File myDir = new File("/sdcard/temp");
                  myDir.mkdirs();
                  File file = new File(myDir, "screen.jpg");
                  Log.i(TAG, "" + file);
                  if (file.exists())
                    file.delete();
                  try {
                    FileOutputStream out = new FileOutputStream(file);
                    croppedBitmap.compress(Bitmap.CompressFormat.JPEG, 100, out);
                    out.flush();
                    out.close();
                  } catch (Exception e) {
                    e.printStackTrace();
                  }

                  String path = "/sdcard/temp/screen.jpg";
                  File f = new File(path);  //
                  Uri imageUri = Uri.fromFile(f);

                  mBitmap = ImageHelper.loadSizeLimitedBitmapFromUri(
                          imageUri, getContentResolver());

                  ByteArrayOutputStream output = new ByteArrayOutputStream();
                  mBitmap.compress(Bitmap.CompressFormat.JPEG, 100, output);

                  new DetectionTask().execute(output.toByteArray());

                  if(faces.size() == 0 ) Log.d("here", "here6");

                  ArrayList<UUID> faceIds = new ArrayList<>();
                  for (Face face: faces) {
                    faceIds.add(face.faceId);
                  }
                  UUID[] uuids = new UUID[faceIds.size()];
                  for(int i = 0; i < faceIds.size(); i++) {
                    uuids[i] = faceIds.get(i);
                  }

                  new IdentificationTask("people").execute(uuids);
                  Log.d("test", "person: " + person);
                }*/

                cropToFrameTransform.mapRect(location);
                result.setLocation(location);
                mappedRecognitions.add(result);
              }
            }

            tracker.trackResults(mappedRecognitions);
            toSpeech(mappedRecognitions);
            trackingOverlay.postInvalidate();

            computingDetection = false;
          }
        });
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);

    if (requestCode == 0 && resultCode == RESULT_OK) {
      Log.d("CameraDemo", "Pic saved");
    }
  }

  @Override
  protected int getLayoutId() {
    return R.layout.camera_connection_fragment_tracking;
  }

  @Override
  protected Size getDesiredPreviewFrameSize() {
    return DESIRED_PREVIEW_SIZE;
  }

  private class IdentificationTask extends AsyncTask<UUID, String, IdentifyResult[]> {
    private boolean mSucceed = true;
    String mPersonGroupId;
    IdentificationTask(String personGroupId) {
      this.mPersonGroupId = personGroupId;
    }

    @Override
    protected IdentifyResult[] doInBackground(UUID... params) {
      String logString = "Request: Identifying faces ";
      for (UUID faceId: params) {
        logString += faceId.toString() + ", ";
      }
      logString += " in group " + mPersonGroupId;

      // Get an instance of face service client to detect faces in image.
      FaceServiceClient faceServiceClient = new FaceServiceRestClient("https://westcentralus.api.cognitive.microsoft.com/face/v1.0", "86fab03db5424440ae3e31ba2011d96d");
      try{
        publishProgress("Getting person group status...");

        TrainingStatus trainingStatus = faceServiceClient.getLargePersonGroupTrainingStatus(
                this.mPersonGroupId);     /* personGroupId */
        if (trainingStatus.status != TrainingStatus.Status.Succeeded) {
          publishProgress("Person group training status is " + trainingStatus.status);
          mSucceed = false;
          return null;
        }

        publishProgress("Identifying...");

        // Start identification.
        return faceServiceClient.identityInLargePersonGroup(
                this.mPersonGroupId,   /* personGroupId */
                params,                  /* faceIds */
                1);  /* maxNumOfCandidatesReturned */
      }  catch (Exception e) {
        mSucceed = false;
        publishProgress(e.getMessage());
        return null;
      }
    }

    @Override
    protected void onPreExecute() {
    }

    @Override
    protected void onProgressUpdate(String... values) {
      // Show the status of background detection task on screen.a
    }

    @Override
    protected void onPostExecute(IdentifyResult[] result) {
      person = result[0].candidates.get(0).personId.toString();
    }
  }

  private class DetectionTask extends AsyncTask<InputStream, String, Face[]> {
    @Override
    protected Face[] doInBackground(InputStream... params) {
      // Get an instance of face service client to detect faces in image.
      FaceServiceClient faceServiceClient = new FaceServiceRestClient("https://westcentralus.api.cognitive.microsoft.com/face/v1.0", "86fab03db5424440ae3e31ba2011d96d");
      try{
        // Start detection.
        Log.d("background", "here");
        Log.d("background", params[0].read() + "" + params[0]);

        return faceServiceClient.detect(
                params[0],  /* Input stream of image to detect */
                true,       /* Whether to return face ID */
                false,       /* Whether to return face landmarks */
                        /* Which face attributes to analyze, currently we support:
                           age,gender,headPose,smile,facialHair */
                null);
      }  catch (Exception e) {
        Log.d("aftertry", "here");
        e.printStackTrace();
        return null;
      }
    }

    @Override
    protected void onPreExecute() {
      Log.d("execute", "pre");
    }

    @Override
    protected void onProgressUpdate(String... values) {
      Log.d("execute", "progress");
    }

    @Override
    protected void onPostExecute(Face[] result) {
      //progressDialog.dismiss();

      Log.d("execute", "inside");
      if (result == null) Log.d("PostExecute", "It's null");
      if (result != null) {
        // Set the adapter of the ListView which contains the details of detected faces.
        Log.d("PostExecute", "inside2");
        faces = new ArrayList<>();
        if (result != null) {
          faces = Arrays.asList(result);
        }
        if (result.length == 0) {
          detected = false;
          Log.d("test", "No faces detected!");
        } else {
          detected = true;
          Log.d("test", "Click on the \"Identify\" button to identify the faces in image.");
        }
      } else {
        detected = false;
      }
    }
  }


}
