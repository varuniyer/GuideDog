package com.busradeniz.detection.AzureHelper;

import android.os.*;

import com.microsoft.projectoxford.face.*;
import com.microsoft.projectoxford.face.contract.PersonGroup;



/**
 * Created by fabiosulser on 31.12.17.
 */

public class GetPersonGroups extends AsyncTask<String, String, PersonGroup[]>{

    public interface AsyncResponse {
        void processFinish(PersonGroup[] output);
    }
    private AsyncResponse delegate = null;

    public GetPersonGroups(AsyncResponse delegate){
        this.delegate = delegate;
    }

    @Override
    protected PersonGroup[] doInBackground(String... params) {
        // Get an instance of face service client.
        FaceServiceClient faceServiceClient =  new FaceServiceRestClient("https://westcentralus.api.cognitive.microsoft.com/face/v1.0", "86fab03db5424440ae3e31ba2011d96d");
        try{
            return faceServiceClient.getPersonGroups();
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    protected void onPostExecute(PersonGroup[] result) {
        delegate.processFinish(result);
    }


}
