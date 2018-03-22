
/**
 * Created by Shardool and Varun on 2/25/2018.
 */
package com.busradeniz.detection;

import android.Manifest;
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

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;

import com.busradeniz.detection.AzureHelper.CreatePersonForGroup;
import com.busradeniz.detection.AzureHelper.CreatePersonGroup;
import com.busradeniz.detection.AzureHelper.TrainPersonGroup;
import com.busradeniz.detection.AzureHelper.UploadPersonFace;
import com.busradeniz.detection.env.ImageUtils;
import com.busradeniz.detection.env.Logger;
import java.nio.ByteBuffer;
import java.util.Collections;
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
import com.microsoft.projectoxford.face.contract.AddPersistedFaceResult;

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
import java.io.IOException;

public abstract class CameraActivity extends Activity
        implements OnImageAvailableListener {
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


    SurfaceView cameraView;
    TextView textView;
    CameraSource cameraSource;
    final int RequestCameraPermissionID = 1001;



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

        /*new CreatePersonGroup(new CreatePersonGroup.AsyncResponse() {
            @Override
            public void processFinish(String output) {
            }
        }).execute("people");
        createPersonForGroup("people", "Dhruv");
        createPersonForGroup("people", "Shardool");
        createPersonForGroup("people", "Achintya");

        uploadPersonFace(drawableToBitmap(ContextCompat.getDrawable(getApplicationContext(), R.drawable.Achintya1)), "people", "Achintya");
        uploadPersonFace(drawableToBitmap(ContextCompat.getDrawable(getApplicationContext(), R.drawable.Achintya2)), "people", "Achintya");
        uploadPersonFace(drawableToBitmap(ContextCompat.getDrawable(getApplicationContext(), R.drawable.Achintya3)), "people", "Achintya");
        uploadPersonFace(drawableToBitmap(ContextCompat.getDrawable(getApplicationContext(), R.drawable.Dhruv1)), "people", "Dhruv");
        uploadPersonFace(drawableToBitmap(ContextCompat.getDrawable(getApplicationContext(), R.drawable.Dhruv2)), "people", "Dhruv");
        uploadPersonFace(drawableToBitmap(ContextCompat.getDrawable(getApplicationContext(), R.drawable.Dhruv3)), "people", "Dhruv");
        uploadPersonFace(drawableToBitmap(ContextCompat.getDrawable(getApplicationContext(), R.drawable.Shardool1)), "people", "Shardool");
        uploadPersonFace(drawableToBitmap(ContextCompat.getDrawable(getApplicationContext(), R.drawable.Shardool2)), "people", "Shardool");
        uploadPersonFace(drawableToBitmap(ContextCompat.getDrawable(getApplicationContext(), R.drawable.Shardool3)), "people", "Shardool");

        trainPersonGroup("people"); */

    }

    public void trainPersonGroup(String group) {
        new TrainPersonGroup(new TrainPersonGroup.AsyncResponse() {
            @Override
            public void processFinish(String output) {

            }
        }).execute(group);
    }

    public void uploadPersonFace(Bitmap bitmap, String group, String name) {
        new UploadPersonFace(bitmap, new UploadPersonFace.AsyncResponse() {
            @Override
            public void processFinish(AddPersistedFaceResult output) {

            }
        }).execute(group, name);
    }

    public void createPersonForGroup(String group, String person) {
        new CreatePersonForGroup(new CreatePersonForGroup.AsyncResponse() {
            @Override
            public void processFinish(String output) {
            }
        }).execute(group, person);
    }

    public static Bitmap drawableToBitmap (Drawable drawable) {
        Bitmap bitmap = null;

        if (drawable instanceof BitmapDrawable) {
            BitmapDrawable bitmapDrawable = (BitmapDrawable) drawable;
            if(bitmapDrawable.getBitmap() != null) {
                return bitmapDrawable.getBitmap();
            }
        }

        if(drawable.getIntrinsicWidth() <= 0 || drawable.getIntrinsicHeight() <= 0) {
            bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888); // Single color bitmap will be created of 1x1 pixel
        } else {
            bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
        }

        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);
        return bitmap;
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

    @Override
    public synchronized void onDestroy() {
        LOGGER.d("onDestroy " + this);
        super.onDestroy();
    }

    protected synchronized void runInBackground(final Runnable r) {
        if (handler != null) {
            handler.post(r);
        }
    }

    @Override
    public void onRequestPermissionsResult(
            final int requestCode, final String[] permissions, final int[] grantResults) {
        if (requestCode == PERMISSIONS_REQUEST) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED
                    && grantResults[1] == PackageManager.PERMISSION_GRANTED) {
                setFragment();
            } else {
                requestPermission();
            }
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

        String title = "";
        for (int i = 0; i < currentRecognitions.size(); i++) {
            Classifier.Recognition recognition = currentRecognitions.get(i);
            title = recognition.getTitle();
            if(recognition.getTitle().equals("person")) {

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
                stringBuilder.append(distance + "centimeters in front of you");
            } else {
                left = false;
                right = false;
                if (start > letStart && end < leftFinish) {
                    if(distance.length() > 0){
                        stringBuilder.append(" " + distance + "centimeters on the right ");
                    }
                    right = true;
                } else if (start > rightStart && end < rightFinish) {
                    if(distance.length() > 0){
                        stringBuilder.append(" " + distance + "centimeters on the left ");
                    }
                    left = true;
                }
            }

            if (i + 1 < currentRecognitions.size()) {
                stringBuilder.append(" and ");
            }
        }
        double steps = 0;
        stringBuilder.append(" detected.");
        if(distance.length() > 0){
            Log.d("Distance:",distance);
            String str = distance.split("\r\n")[0].split(" ")[0];

            int cm = Integer.parseInt(str);
            Log.d("meter",cm + "");
            double meters = (cm + 0.0)/100.0;
            if(left){
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
                if(meters > 1){
                    slight = "slight";
                }
                stringBuilder.append(" Move " + (int) steps + " steps to the " + slight + "  left to move past the " + title + " and continue walking");
                right = false;
            }
        }


        if(DetectorActivity.imageText.length() > 0){
            stringBuilder.append("  The text in front of you reads " + DetectorActivity.imageText + ".");
        }

        boolean time = true;
        Date currentTime = Calendar.getInstance().getTime();
        String str = currentTime.toString();
        Log.d("TIME", str);
        Scanner scan = new Scanner(str);
        String[] arr = str.split(" ",6);

        if(stringBuilder.toString().contains("clock")){
            stringBuilder.append(" The time is " + arr[3]  +".");
        }

        if(DetectorActivity.imageText.length() > 32) stringBuilder.append(DetectorActivity.imageText);

        textToSpeech.speak(stringBuilder.toString(), TextToSpeech.QUEUE_FLUSH, null);


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
