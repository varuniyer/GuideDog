package com.busradeniz.detection.AzureHelper;

import android.graphics.Bitmap;
import android.os.AsyncTask;

import com.microsoft.projectoxford.face.FaceServiceClient;
import com.microsoft.projectoxford.face.FaceServiceRestClient;
import com.microsoft.projectoxford.face.contract.Face;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.UUID;



/**
 * Created by fabiosulser on 31.12.17.
 */

public class DetectPersonFace extends AsyncTask<String, String, UUID> {
    private final InputStream imageInputStream;

    public interface AsyncResponse {
        void processFinish(UUID output);
    }
    private DetectPersonFace.AsyncResponse delegate = null;

    public DetectPersonFace(Bitmap bitmap, DetectPersonFace.AsyncResponse delegate){
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream);
        imageInputStream = new ByteArrayInputStream(stream.toByteArray());

        this.delegate = delegate;
    }

    @Override
    protected UUID doInBackground(String... strings) {

        // Get an instance of face service client.
        FaceServiceClient faceServiceClient =  new FaceServiceRestClient("https://westcentralus.api.cognitive.microsoft.com/face/v1.0", "86fab03db5424440ae3e31ba2011d96d");
        try{
            Face[] faces = faceServiceClient.detect(imageInputStream,true,false, null);
            if(faces.length == 0){
                return null;
            }
            return faces[0].faceId;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    protected void onPostExecute(UUID result) {
        delegate.processFinish(result);
    }
}
