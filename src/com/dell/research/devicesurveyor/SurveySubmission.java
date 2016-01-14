package com.dell.research.devicesurveyor;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.protocol.HTTP;

/**
 * Created by irwin_reyes on 10/5/15.
 */
public class SurveySubmission extends AsyncTask<String, Void, Boolean> {
    private static final String USER_AGENT = "Dell Research Device Surveyor";
    private static final String RESPONSE_HEADER = "X-SurveySuccess";

    private Activity sourceActivity = null;
    private Uri submitAddress = null;
    private Uri surveyAddress = null;

    private String lastResponse = "";

    /**
     *
     * @param act The Activity that called this SurveySubmission. Results will be displayed on a pop-up on that Activity.
     * @param submitAddress Full address to the submission server
     * @param surveyAddress Full address to the user survey website
     */
    public SurveySubmission(Activity act, String submitAddress, String surveyAddress) {
        this.sourceActivity = act;
        this.submitAddress = Uri.parse(submitAddress);
        this.surveyAddress = Uri.parse(surveyAddress);
    }

    @Override
    protected Boolean doInBackground(String... params) {
        if(params.length == 2) {
            String address = params[0];
            String json = params[1];

            try {
                DefaultHttpClient client = new DefaultHttpClient();
                HttpPost post = new HttpPost(address);

                post.setHeader("User-Agent", USER_AGENT);

                StringEntity se = new StringEntity(json, HTTP.UTF_8);
                se.setContentType("application/json");
                post.setEntity(se);

                HttpResponse response = client.execute(post);
                Header surveySuccess = response.getFirstHeader(RESPONSE_HEADER);

                if(surveySuccess != null) {
                    lastResponse = surveySuccess.getValue();
                    return lastResponse.startsWith("success");
                } else {
                    lastResponse = "error: invalid response header";
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return false;
    }

    @Override
    protected void onPostExecute(Boolean result) {
        // Display a pop-up alert if this submission was called from an Activity
        if(sourceActivity != null) {
            // Configure "Submit" dialog
            AlertDialog.Builder dialog = new AlertDialog.Builder(sourceActivity);
            dialog.setCancelable(false);

            if(!result.booleanValue()) {
                // Error
                dialog.setTitle(R.string.result_title_error);
                dialog.setMessage(lastResponse);
                dialog.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // Do nothing
                    }
                });
            } else {
                // Success
                dialog.setTitle(R.string.result_title_success);
                dialog.setMessage(R.string.result_message_success);
                dialog.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // Open survey in browser
                        Intent browserIntent = new Intent(Intent.ACTION_VIEW, surveyAddress);
                        sourceActivity.startActivity(browserIntent);
                    }
                });
            }

            dialog.show();
        }
    }
}
