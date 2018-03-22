package com.busradeniz.detection.AzureHelper;

import android.os.AsyncTask;

import com.microsoft.projectoxford.face.FaceServiceClient;
import com.microsoft.projectoxford.face.FaceServiceRestClient;

import java.util.Objects;



/**
 * Created by fabiosulser on 31.12.17.
 */

public class TrainPersonGroup extends AsyncTask<String, String, String>{
    public interface AsyncResponse {
        void processFinish(String output);
    }
    private TrainPersonGroup.AsyncResponse delegate = null;

    public TrainPersonGroup(TrainPersonGroup.AsyncResponse delegate){
        this.delegate = delegate;
    }


    @Override
    protected String doInBackground(String... params) {
        if(Objects.equals(params[0], "") || params[0] == null){
            return null;
        }
        // Get an instance of face service client.
        FaceServiceClient faceServiceClient = new FaceServiceRestClient("https://westcentralus.api.cognitive.microsoft.com/face/v1.0", "86fab03db5424440ae3e31ba2011d96d");
        try{
            faceServiceClient.trainPersonGroup(params[0]);
            return params[0];
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    protected void onPostExecute(String result) {
        delegate.processFinish(result);
    }
}
