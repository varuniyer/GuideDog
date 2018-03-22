package com.busradeniz.detection.AzureHelper;

import android.os.AsyncTask;

import com.microsoft.projectoxford.face.FaceServiceClient;
import com.microsoft.projectoxford.face.FaceServiceRestClient;
import com.microsoft.projectoxford.face.contract.IdentifyResult;

import java.util.UUID;


/**
 * Created by fabiosulser on 31.12.17.
 */

public class IdentifyPerson {
    /*private final UUID[] faceIDs;
    private final String personGroup;

    private IdentifyPerson.AsyncResponse delegate = null;

    public IdentifyPerson(UUID[] faceIDs, String personGroup, IdentifyPerson.AsyncResponse delegate){
        this.personGroup = personGroup;
        this.faceIDs = faceIDs;
        this.delegate = delegate;
    }

    @Override
    protected UUID doInBackground(String... strings) {
        // Get an instance of face service client.
        FaceServiceClient faceServiceClient = new FaceServiceRestClient("https://westcentralus.api.cognitive.microsoft.com/face/v1.0", "86fab03db5424440ae3e31ba2011d96d");
        try{
            IdentifyResult[] faces = faceServiceClient.identity(personGroup, faceIDs, 1);
            return faces[0].candidates.get(0).personId;
        } catch (Exception e) {
            return null;
        }
    }


    @Override
    protected void onPostExecute(UUID result) {
        delegate.processFinish(result);
    } */
}
