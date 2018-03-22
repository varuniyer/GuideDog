package com.busradeniz.detection.AzureHelper;
import android.graphics.Bitmap;
import android.os.AsyncTask;

import com.microsoft.projectoxford.face.FaceServiceClient;
import com.microsoft.projectoxford.face.FaceServiceRestClient;
import com.microsoft.projectoxford.face.contract.AddPersistedFaceResult;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Objects;
import java.util.UUID;

/**
 * Created by fabiosulser on 31.12.17.
 */

public class UploadPersonFace extends AsyncTask<String, String, AddPersistedFaceResult> {
    private final InputStream imageInputStream;

    public interface AsyncResponse {
        void processFinish(@SuppressWarnings("unused") AddPersistedFaceResult output);
    }
    private UploadPersonFace.AsyncResponse delegate = null;

    public UploadPersonFace(Bitmap imageBitmap, UploadPersonFace.AsyncResponse delegate){
        this.delegate = delegate;

        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        imageBitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream);
        imageInputStream = new ByteArrayInputStream(stream.toByteArray());
    }


    @Override
    protected AddPersistedFaceResult doInBackground(String... strings) {
        try {
            if(Objects.equals(strings[0], "") || strings[0] == null){
                return null;
            }
            if(Objects.equals(strings[1], "") || strings[1] == null){
                return  null;
            }

            String personGroupId = strings[0];
            UUID personId = UUID.fromString(strings[1]);

            // Get an instance of face service client.
            FaceServiceClient faceServiceClient = new FaceServiceRestClient("https://westcentralus.api.cognitive.microsoft.com/face/v1.0", "86fab03db5424440ae3e31ba2011d96d");

            AddPersistedFaceResult res = faceServiceClient.addPersonFace(personGroupId, personId, imageInputStream, null, null);
            return res;
        } catch (Exception e) {

            return null;
        }
    }

    @Override
    protected void onPostExecute(AddPersistedFaceResult result) {
        delegate.processFinish(result);
    }

}
