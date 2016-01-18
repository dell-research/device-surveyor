package com.dell.research.devicesurveyor;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Map;

public class DeviceSurveyor extends Activity {
    private String submitAddress = "";
    private String surveyAddress = "";

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        DeviceInfo.init(this);
        String report = DeviceInfo.getSummary();

        submitAddress = getResources().getString(R.string.default_collector_address);
        surveyAddress = getResources().getString(R.string.default_survey_address) + "&randid=" + DeviceInfo.getRandomID();

        final TextView textDisplay = (TextView)findViewById(R.id.textDisplay);
        textDisplay.setText(report);
    }

    /**
     * Method attached to when the user taps on the big "Submit" button
     * @param view
     */
    public void submitOnClick(View view) {
        // Configure "Submit" dialog
        AlertDialog.Builder dialog = new AlertDialog.Builder(this);
        dialog.setTitle(R.string.confirm_title);
        dialog.setMessage(R.string.confirm_message);
        dialog.setCancelable(false);

        // Field pointing to the collection address
//        final EditText input = new EditText(this);
//        input.setSingleLine();
//        input.setText(submitAddress);
//        dialog.setView(input);

        // Submit/cancel actions
        dialog.setPositiveButton(R.string.confirm_yes, new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
                submitToServer(submitAddress);
            }
        });
        dialog.setNegativeButton(R.string.confirm_no, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // Do nothing, just dismiss the dialog
            }
        });

        dialog.show();
    }

    /**
     * Submit device information to the server
     * @param address Address to the collection server
     */
    private void submitToServer(String address) {
        JSONObject report = new JSONObject();

        Map<String, String> data = DeviceInfo.getData();
        for(String label : data.keySet()) {
            String value = data.get(label);

            try {
                report.put(label, value);
            } catch (JSONException e) {
                // Skip if there's a problem building the JSON message
                e.printStackTrace();
                continue;
            }
        }
        if(DeviceInfo.getRooted()) {
            try {
                report.put("getevent", DeviceInfo.getGeteventBuffer());
            } catch (JSONException e) {
                // Ignore if there's a problem building the JSON message
                e.printStackTrace();
            }
        }

        new SurveySubmission(this, submitAddress, surveyAddress).execute(address, report.toString());
    }
}
