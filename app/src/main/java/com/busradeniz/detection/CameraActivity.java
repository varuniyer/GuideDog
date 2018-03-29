
/**
 * Created by Shardool and Varun on 2/25/2018.
 */
package com.busradeniz.detection;

import android.Manifest;
import android.app.ActionBar;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Camera;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.Image.Plane;
import android.media.ImageReader;
import android.media.ImageReader.OnImageAvailableListener;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.Trace;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.WindowManager;
import android.widget.Toast;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.VolleyLog;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.busradeniz.detection.env.ImageUtils;
import com.busradeniz.detection.env.Logger;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import com.google.android.gms.vision.CameraSource;
import com.google.android.gms.vision.Detector;
import com.google.android.gms.vision.Frame;
import com.google.android.gms.vision.text.TextBlock;
import com.google.android.gms.vision.text.TextRecognizer;

import android.content.pm.PackageManager;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.util.SparseArray;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

import edu.cmu.pocketsphinx.Assets;
import edu.cmu.pocketsphinx.Hypothesis;
import edu.cmu.pocketsphinx.RecognitionListener;
import edu.cmu.pocketsphinx.SpeechRecognizer;
import edu.cmu.pocketsphinx.SpeechRecognizerSetup;

import static android.widget.Toast.makeText;

import static edu.cmu.pocketsphinx.SpeechRecognizerSetup.defaultSetup;

public abstract class CameraActivity extends Activity
        implements OnImageAvailableListener, RecognitionListener {

    /* Named searches allow to quickly reconfigure the decoder */

    private static final String KWS_SEARCH = "hey guide dog";
    private static final String FORECAST_SEARCH = "to my left";
    private static final String DIGITS_SEARCH = "front of me";
    private static final String PHONE_SEARCH = "to my right";
    private static final String MENU_SEARCH = "near me";
    /* Keyword we are looking for to activate menu */
    private static final String KEYPHRASE = "hey guide dog";
    private static String arr[] = {KWS_SEARCH,FORECAST_SEARCH,DIGITS_SEARCH,PHONE_SEARCH,MENU_SEARCH};
    private static int index = 0;
    /* Used to handle permission request */
    private static final int PERMISSIONS_REQUEST_RECORD_AUDIO = 1;

    private SpeechRecognizer recognizer;
    private HashMap<String, Integer> captions;

    private static final Logger LOGGER = new Logger();

    private static final int PERMISSIONS_REQUEST = 1;

    private static final String PERMISSION_CAMERA = Manifest.permission.CAMERA;
    private static final String PERMISSION_STORAGE = Manifest.permission.WRITE_EXTERNAL_STORAGE;
    private static final int REQUEST_CODE = 1234;

    private Handler handler;
    private HandlerThread handlerThread;
    private boolean isProcessingFrame = false;
    private byte[][] yuvBytes = new byte[3][];
    private int[] rgbBytes = null;
    private int yRowStride;
    private boolean left = false;
    private boolean right = false;
    protected int previewWidth = 0;
    protected int previewHeight = 0;
    private cBluetooth bl = null;
    private boolean BT_is_Connect = false;
    protected static String distance = "0";
    private Runnable postInferenceCallback;
    private Runnable imageConverter;

    private TextToSpeech textToSpeech;
    private String question;
    private String mainText = "There are no relevant objects in your view";
    private static CameraActivity a;


    SurfaceView cameraView;
    TextView textView;
    CameraSource cameraSource;
    final int RequestCameraPermissionID = 1001;

    String API_KEY = "4d4b29d2140946a39b3c9f797995db98";


    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        LOGGER.d("onCreate " + this);
        super.onCreate(null);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_camera);

        bl = new cBluetooth(this, mHandler);
        bl.checkBTState();
        BT_is_Connect = bl.BT_Connect("98:D3:61:FD:34:F1", true);
        Log.d("Connected?", BT_is_Connect + "");

        if (hasPermission()) {
            setFragment();
        } else {
            requestPermission();
        }

        this.textToSpeech = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if (status == TextToSpeech.SUCCESS) {
                    LOGGER.i("onCreate", "TextToSpeech is initialised");
                } else {
                    LOGGER.e("onCreate", "Cannot initialise text to speech!");
                }
            }
        });

        captions = new HashMap<>();
        captions.put(KWS_SEARCH, R.string.kws_caption);
        captions.put(MENU_SEARCH, R.string.menu_caption);
        captions.put(DIGITS_SEARCH, R.string.digits_caption);
        captions.put(PHONE_SEARCH, R.string.phone_caption);
        captions.put(FORECAST_SEARCH, R.string.forecast_caption);

        // Check if user has given permission to record audio
        int permissionCheck = ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.RECORD_AUDIO);
        if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, PERMISSIONS_REQUEST_RECORD_AUDIO);
            return;
        }
        // Recognizer initialization is a time-consuming and it involves IO,
        // so we execute it in async task
        a = this;
        try {
            Assets assets = new Assets(CameraActivity.this);
            File assetDir = assets.syncAssets();
            setupRecognizer(assetDir);
        } catch(IOException io){}

        recognizer.stop();
        recognizer.startListening(DIGITS_SEARCH);
    }

    private static class SetupTask extends AsyncTask<Void, Void, Exception> {
        WeakReference<CameraActivity> activityReference;
        SetupTask(CameraActivity activity) {
            this.activityReference = new WeakReference<>(activity);
        }
        @Override
        protected Exception doInBackground(Void... params) {
            try {
                Assets assets = new Assets(activityReference.get());
                File assetDir = assets.syncAssets();
                activityReference.get().setupRecognizer(assetDir);
            } catch (IOException e) {
                return e;
            }
            return null;
        }
        @Override
        protected void onPostExecute(Exception result) {
            //new SetupTask(a).execute();
            if (result != null) {
               // ((TextView) activityReference.get().findViewById(R.id.caption_text))
                 //       .setText("Failed to init recognizer " + result);
                Log.i("Failed to init recognizer",result.toString());
            } else {
               activityReference.get().switchSearch(DIGITS_SEARCH);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions, @NonNull  int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSIONS_REQUEST_RECORD_AUDIO) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Recognizer initialization is a time-consuming and it involves IO,
                // so we execute it in async task
                //new SetupTask(this).execute();
            } else {
                finish();
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (recognizer != null) {
            recognizer.cancel();
            recognizer.shutdown();
        }
    }

    /**
     * In partial result we get quick updates about current hypothesis. In
     * keyword spotting mode we can react here, in other modes we need to wait
     * for final result in onResult.
     */
    @Override
    public void onPartialResult(Hypothesis hypothesis) {
        if (hypothesis == null)
            return;

        String text = hypothesis.getHypstr();
        Log.i("converted text", text);
        //index++;
        //if(index == arr.length) index = 0;
        Log.i("Array Val",arr[index]);
      ////  switchSearch(arr[index]);
        /*if (text.equals(KEYPHRASE))
            switchSearch(MENU_SEARCH);
        else if (text.equals(_SEARCH))
            switchSearch(DIGITS_SEARCH);
        else if (text.equals(PHONE_SEARCH))
            switchSearch(PHONE_SEARCH);
        else if (text.equals(FORECAST_SEARCH))
            switchSearch(FORECAST_SEARCH);
        else Log.i("partial text", text);*/
        //switchSearch(DIGITS_SEARCH);
            //((TextView) findViewById(R.id.result_text)).setText(text);
    }

    /**
     * This callback is called when we stop the recognizer.
     */
    @Override
    public void onResult(Hypothesis hypothesis) {
        //((TextView) findViewById(R.id.result_text)).setText("");
        if (hypothesis != null) {
            String text = hypothesis.getHypstr();
            Log.i("result text", ":"+text+":");

            boolean stuff = false;
            for(String s : arr) if(s.equals(text.trim())) stuff = true;
            if(text.trim().equals("assistant")) {
                textToSpeech.speak("Guide Dog is listening", TextToSpeech.QUEUE_FLUSH, null);
            }
            if(text.trim().equals("news")) {
                Log.i("news","happened");
                RequestQueue queue = Volley.newRequestQueue(this);
                String url = "https://newsapi.org/v2/top-headlines?country=us&sortBy=publishedAt&apiKey="
                        + API_KEY;

                final JsonObjectRequest jsonObjectRequest = new JsonObjectRequest
                        (url, null, new Response.Listener<JSONObject>() {

                            @Override
                            public void onResponse(JSONObject response) {
                                Log.i("JSON Response", response.toString());
                                try {
                                    JSONArray articles = response.getJSONArray("articles");
                                    Log.i("Information", articles.get(0).toString());
                                    String intro = "Here is an article from " + ((JSONObject)((JSONObject)articles.get(0)).get("source")).get("name");
                                    String title = ((JSONObject)articles.get(0)).get("title").toString();
                                    textToSpeech.speak(intro + ". " + title + ".", TextToSpeech.QUEUE_FLUSH, null);
                                } catch(JSONException j) {
                                    Log.i("JSON Array Error", j.toString());
                                    return;
                                }
                            }
                        }, new Response.ErrorListener() {

                            @Override
                            public void onErrorResponse(VolleyError error) {
                                Log.e("JSON Error", error.toString());

                            }
                        });

                queue.add(jsonObjectRequest);
                return;
            }
            else question = text;
            makeText(getApplicationContext(), text, Toast.LENGTH_SHORT).show();
            //new SetupTask(a).execute();
        }
    }

    @Override
    public void onBeginningOfSpeech() {
    }

    /**
     * We stop recognizer here to get a final result
     */
    @Override
    public void onEndOfSpeech() {
        index++;
        if(arr.length == index) index = 0;
        Log.i("intended word",arr[index]);
        recognizer.stop();
        recognizer.startListening(DIGITS_SEARCH);
    }

    private void switchSearch(String searchName) {
        //recognizer.stop();

        // If we are not spotting, start listening with timeout (10000 ms or 10 seconds).
        //if (searchName.equals(KWS_SEARCH))
         //   recognizer.startListening(searchName);
       // else
           //for(String s : arr) recognizer.startListening(s);
        //recognizer.startListening(DIGITS_SEARCH);
        String caption = getResources().getString(captions.get(searchName));
        Log.i("caption", caption);
        //((TextView) findViewById(R.id.caption_text)).setText(caption);
    }

    private void setupRecognizer(File assetsDir) throws IOException {
        // The recognizer can be configured to perform multiple searches
        // of different kind and switch between them

        recognizer = SpeechRecognizerSetup.defaultSetup()
                .setAcousticModel(new File(assetsDir, "en-us-ptm"))
                .setDictionary(new File(assetsDir, "cmudict-en-us.dict"))

                .setRawLogDir(assetsDir) // To disable logging of raw audio comment out this call (takes a lot of space on the device)

                .getRecognizer();
        recognizer.addListener(this);

        /* In your application you might not need to add all those searches.
          They are added here for demonstration. You can leave just one.
         */

        // Create keyword-activation search.
        File digitsGrammar = new File(assetsDir,"menu.gram");
        recognizer.addKeywordSearch(DIGITS_SEARCH, digitsGrammar);


       /* // Create grammar-based search for selection between demos
        File menuGrammar = new File(assetsDir, "menu.gram");
        recognizer.addGrammarSearch(MENU_SEARCH, menuGrammar);

        // Create grammar-based search for digit recognition
        File digitsGrammar = new File(assetsDir, "digits.gram");
        recognizer.addGrammarSearch(DIGITS_SEARCH, digitsGrammar);

        // Create language model search
        File languageModel = new File(assetsDir, "weather.dmp");
        recognizer.addNgramSearch(FORECAST_SEARCH, languageModel);

        // Phonetic search
        File phoneticModel = new File(assetsDir, "en-phone.dmp");
        recognizer.addAllphoneSearch(PHONE_SEARCH, phoneticModel);*/
    }

    @Override
    public void onError(Exception error) {
        Log.i("error",error.getMessage());
        //((TextView) findViewById(R.id.caption_text)).setText(error.getMessage());
    }

    @Override
    public void onTimeout() {
        //index++;
        //if(index == arr.length) index= 0;
        //switchSearch(arr[index]);
    }


    protected int[] getRgbBytes() {
        imageConverter.run();
        return rgbBytes;
    }

    protected byte[] getLuminance() {
        return yuvBytes[0];
    }

    /**
     * Callback for Camera2 API
     */
    @Override
    public void onImageAvailable(final ImageReader reader) {
        //We need wait until we have some size from onPreviewSizeChosen
        if (previewWidth == 0 || previewHeight == 0) {
            return;
        }
        if (rgbBytes == null) {
            rgbBytes = new int[previewWidth * previewHeight];
        }
        try {
            final android.media.Image image = reader.acquireLatestImage();

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

        handlerThread = new HandlerThread("inference");
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper());
    }

    @Override
    public synchronized void onPause() {
        LOGGER.d("onPause " + this);

        if (!isFinishing()) {
            LOGGER.d("Requesting finish");
            finish();
        }

        handlerThread.quitSafely();
        try {
            handlerThread.join();
            handlerThread = null;
            handler = null;
        } catch (final InterruptedException e) {
            LOGGER.e(e, "Exception!");
        }

        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }

        super.onPause();
    }

    @Override
    public synchronized void onStop() {
        LOGGER.d("onStop " + this);
        super.onStop();
    }

    protected synchronized void runInBackground(final Runnable r) {
        if (handler != null) {
            handler.post(r);
        }
    }

    private boolean hasPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return checkSelfPermission(PERMISSION_CAMERA) == PackageManager.PERMISSION_GRANTED &&
                    checkSelfPermission(PERMISSION_STORAGE) == PackageManager.PERMISSION_GRANTED;
        } else {
            return true;
        }
    }

    private void requestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (shouldShowRequestPermissionRationale(PERMISSION_CAMERA) ||
                    shouldShowRequestPermissionRationale(PERMISSION_STORAGE)) {
                Toast.makeText(CameraActivity.this,
                        "Camera AND storage permission are required for this demo", Toast.LENGTH_LONG).show();
            }
            requestPermissions(new String[]{PERMISSION_CAMERA, PERMISSION_STORAGE}, PERMISSIONS_REQUEST);
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

                boolean useCamera2API = isHardwareLevelSupported(characteristics,
                        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL);
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

        getFragmentManager()
                .beginTransaction()
                .replace(R.id.container, camera2Fragment)
                .commit();
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


    private List<Classifier.Recognition> currentRecognitions;

    protected void toSpeech(List<Classifier.Recognition> recognitions) {
        if (recognitions.isEmpty() || textToSpeech.isSpeaking()) {
            currentRecognitions = Collections.emptyList();
            return;
        }

        if (currentRecognitions != null) {

            // Ignore if current and new are same.
            if (currentRecognitions.equals(recognitions)) {
                return;
            }
            final Set<Classifier.Recognition> intersection = new HashSet<>(recognitions);
            intersection.retainAll(currentRecognitions);

            // Ignore if new is sub set of the current
            if (intersection.equals(recognitions)) {
                return;
            }
        }

        currentRecognitions = recognitions;

        speak();
    }

    private void speak() {

        final double rightStart = previewWidth / 2 - 0.10 * previewWidth;
        final double rightFinish = previewWidth;
        final double letStart = 0;
        final double leftFinish = previewWidth / 2 + 0.10 * previewWidth;
        final double previewArea = previewWidth * previewHeight;

        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("There is a ");
        String title = "";
        for (int i = 0; i < currentRecognitions.size(); i++) {
            Classifier.Recognition recognition = currentRecognitions.get(i);
            title = recognition.getTitle();
            if(recognition.getTitle().equals("tv")) {
                stringBuilder.append("monitor");
            }
            if(recognition.getTitle().equals("surfboard")){
                stringBuilder.append("table");
            }else{
                stringBuilder.append(recognition.getTitle());
            }

            float start = recognition.getLocation().top;
            float end = recognition.getLocation().bottom;
            double objArea = recognition.getLocation().width() * recognition.getLocation().height();

            if (objArea > previewArea / 2) {
                /*if(meters <=1){
                        stringBuilder.append("1 meter ");
                    }else{
                        stringBuilder.append(meters + "meters ");
                    }*/
                stringBuilder.append(distance + " centimeters in front of you");
            } else {
                left = false;
                right = false;
                if (start > letStart && end < leftFinish) {
                    if(distance.length() > 0){
                        stringBuilder.append(" " + distance + " centimeters on the right ");
                    }
                    right = true;
                } else if (start > rightStart && end < rightFinish) {
                    if(distance.length() > 0){
                        stringBuilder.append(" " + distance + " centimeters on the left ");
                    }
                    left = true;
                }
            }

            if (i + 1 < currentRecognitions.size()) {
                stringBuilder.append(" and ");
            }
        }
        double steps = 0;
        stringBuilder.append(".");
        if(distance.length() > 0){
            Log.d("Distance:",distance);
            String str = distance.split("\r\n")[0].split(" ")[0];

            int cm = Integer.parseInt(str);
            Log.d("meter",cm + "");
            double meters = (cm + 0.0)/100.0;
            /*if(left){
                steps = meters * 1.31;
                String slight = "";
                if(meters <= 1){
                    slight = "slight";
                }
                stringBuilder.append(" Move " + (int)steps + " steps to the " + slight + " right to move past the " + title + " and continue walking");
                left = false;
            }

            if(right){
                steps = meters * 1.31;
                String slight = "";
                if(meters > 1) {
                    slight = "slight";
                }
                stringBuilder.append(" Move " + (int) steps + " steps to the " + slight + "  left to move past the " + title + " and continue walking");
                right = false;
            }*/
        }


        if(DetectorActivity.imageText.length() > 0){
            stringBuilder.append("  The text in front of you reads " + DetectorActivity.imageText + ".");
        }





        if(DetectorActivity.imageText.length() > 32) stringBuilder.append(DetectorActivity.imageText);


        Log.i("string", stringBuilder.toString());
        if(question != null) Log.i("question", question);
        if(question != null) {
            String[] occurrences = question.split(" ");
            boolean news = question.contains("news");

            boolean near = question.contains("around");
         //   boolean objects = question.contains("objects");
            boolean front = question.contains("front");
            boolean left = question.contains("left");
            boolean right = question.contains("right");
            boolean what = question.contains("what");
            Log.i("right",Boolean.toString(right));
            if(near || front) {
                left = false;
                right = false;
            }
            String newStr = "";
            String nearstr = stringBuilder.toString();
            mainText = stringBuilder.toString();
            String[] sentences = stringBuilder.toString().split("centimeters");

            /*if(question.contains("navigate")){
                newStr += "To navigate to the exit, move ";
                for(String s: sentences){
                    if(s.contains("right")){
                        newStr += "to the right passed the"
                    }
                }

            }else{

            }*/

            if(question.contains("time")){
                Date currentTime = Calendar.getInstance().getTime();
                String str = currentTime.toString();
                Log.d("TIME", str);
                String[] arr = str.split(" ",6);
                String ampm = "am";
                String hour =  arr[3].split(":")[0];
                if(Integer.parseInt(arr[3].split(":")[0]) > 12) {
                    ampm = "pm";
                    hour = "" + (Integer.parseInt(arr[3].split(":")[0]) - 12);
                }
                String minute = arr[3].split(":")[1];
               newStr = " The time is " + hour + " " + minute +" "+ampm+".";
            }else if(question.contains("assistant")){
                    newStr = "";
                }else if(near ||  what) {
                    newStr = nearstr;
                }else{
                    for(String s : sentences) Log.i("sentence", s);
                    for(int i = 1; i < sentences.length; i+=2) {
                        for (int j = 0; j < occurrences.length; j++) {
                            if ((sentences[i - 1].contains(occurrences[j]) || sentences[i].contains(occurrences[j]))
                                    && (!left || sentences[i - 1].contains("left") || sentences[i].contains("left")) && (!right || sentences[i - 1].contains("right") || sentences[i].contains("right"))) {
                                newStr += sentences[i - 1] + "centimeters" + sentences[i];
                                break;
                            }
                        }
                    }
                }


           int len = newStr.length()-1;
            if(len > 5){
                if(!question.contains("time") && Character.isDigit(newStr.charAt(len - 1))){
                    newStr += " centimeters away";
                }
            }



            if(newStr.equals("") && !question.contains("assistant") && !question.contains("right")) newStr = "There are no relevant objects in your view.";
            textToSpeech.speak(newStr, TextToSpeech.QUEUE_FLUSH, null);
            question = null;
        }
    }



    protected abstract void processImage();

    protected abstract void onPreviewSizeChosen(final Size size, final int rotation);

    protected abstract int getLayoutId();

    protected abstract Size getDesiredPreviewFrameSize();

    private static class MyHandler extends Handler {
        private final WeakReference<CameraActivity> mActivity;

        public MyHandler(CameraActivity activity) {
            mActivity = new WeakReference<CameraActivity>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            CameraActivity activity = mActivity.get();
            if (activity != null) {
                switch (msg.what) {
                    case cBluetooth.BL_NOT_AVAILABLE:
                        Log.d(cBluetooth.TAG, "Bluetooth is not available. Exit");
                        Toast.makeText(activity.getBaseContext(), "Bluetooth is not available", Toast.LENGTH_SHORT).show();
                        activity.finish();
                        break;
                    case cBluetooth.BL_INCORRECT_ADDRESS:
                        Log.d(cBluetooth.TAG, "Incorrect MAC address");
                        Toast.makeText(activity.getBaseContext(), "Incorrect Bluetooth address", Toast.LENGTH_SHORT).show();
                        break;
                    case cBluetooth.BL_REQUEST_ENABLE:
                        Log.d(cBluetooth.TAG, "Request Bluetooth Enable");
                        BluetoothAdapter.getDefaultAdapter();
                        Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                        activity.startActivityForResult(enableBtIntent, 1);
                        break;
                    case cBluetooth.BL_SOCKET_FAILED:
                        Toast.makeText(activity.getBaseContext(), "Socket failed", Toast.LENGTH_SHORT).show();
                        //activity.finish();
                        break;
                    case cBluetooth.RECIEVE_MESSAGE:
                        distance = new String((byte[]) msg.obj, 0, msg.arg1);
                        //double m = Integer.parseInt(distance) + 0.0;
                        //meters = m/100.0;

                        Log.d("distance", distance);
                        break;
                }
            }
        }
    }

    private final MyHandler mHandler = new MyHandler(this);

    private final static Runnable sRunnable = new Runnable() {
        public void run() { }
    };
}
