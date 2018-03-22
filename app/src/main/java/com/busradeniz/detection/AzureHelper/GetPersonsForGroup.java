package com.busradeniz.detection.AzureHelper;

import android.os.AsyncTask;

import com.microsoft.projectoxford.face.FaceServiceClient;
import com.microsoft.projectoxford.face.FaceServiceRestClient;
import com.microsoft.projectoxford.face.contract.Person;

import java.util.Objects;


/**
 * Created by fabiosulser on 31.12.17.
 */

public class GetPersonsForGroup extends AsyncTask<String, String, Person[]>{

    public interface AsyncResponse {
        void processFinish(Person[] output);
    }
    private GetPersonsForGroup.AsyncResponse delegate = null;

    public GetPersonsForGroup(GetPersonsForGroup.AsyncResponse delegate){
        this.delegate = delegate;
    }

    @Override
    protected Person[] doInBackground(String... params) {
        if(params[0] == null || Objects.equals(params[0], "")){
            return null;
        }
        // Get an instance of face service client.
        FaceServiceClient faceServiceClient = new FaceServiceRestClient("https://westcentralus.api.cognitive.microsoft.com/face/v1.0", "86fab03db5424440ae3e31ba2011d96d");
        try{
            return faceServiceClient.getPersons(params[0]);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    protected void onPostExecute(Person[] result) {
        delegate.processFinish(result);
    }
}
