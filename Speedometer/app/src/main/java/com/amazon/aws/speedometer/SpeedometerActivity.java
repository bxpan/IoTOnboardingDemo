package com.amazon.aws.speedometer;

import android.os.AsyncTask;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.NumberPicker;
import android.widget.TextView;
import android.widget.ToggleButton;

import com.amazonaws.auth.CognitoCachingCredentialsProvider;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.iotdata.AWSIotDataClient;
import com.amazonaws.services.iotdata.model.GetThingShadowRequest;
import com.amazonaws.services.iotdata.model.GetThingShadowResult;
import com.amazonaws.services.iotdata.model.UpdateThingShadowRequest;
import com.amazonaws.services.iotdata.model.UpdateThingShadowResult;
import com.github.anastr.speedviewlib.*;
import com.google.gson.Gson;

import java.nio.ByteBuffer;

import static android.widget.TextView.BufferType.EDITABLE;

public class SpeedometerActivity extends AppCompatActivity {

    private static final String LOG_TAG = SpeedometerActivity.class.getCanonicalName();

    // --- Constants to modify per your configuration ---

    // Customer specific IoT endpoint
    // AWS Iot CLI describe-endpoint call returns: XXXXXXXXXX.iot.<region>.amazonaws.com
    private static final String CUSTOMER_SPECIFIC_ENDPOINT = "CHANGE_ME.iot.us-west-2.amazonaws.com";
    // Cognito pool ID. For this app, pool needs to be unauthenticated pool with
    // AWS IoT permissions.
    private static final String COGNITO_POOL_ID = "us-west-2:-CHANGE_ME";
    // Region of AWS IoT
    private static final Regions MY_REGION = Regions.US_WEST_2;

    CognitoCachingCredentialsProvider credentialsProvider;

    AWSIotDataClient iotDataClient;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_speedometer);

        // Initialize the Amazon Cognito credentials provider
        credentialsProvider = new CognitoCachingCredentialsProvider(
                getApplicationContext(),
                COGNITO_POOL_ID, // Identity Pool ID
                MY_REGION // Region
        );

        iotDataClient = new AWSIotDataClient(credentialsProvider);
        String iotDataEndpoint = CUSTOMER_SPECIFIC_ENDPOINT;
        iotDataClient.setEndpoint(iotDataEndpoint);

        EditText sp = (EditText) findViewById(R.id.speed);
        sp.setText("0", EDITABLE);
        AwesomeSpeedometer speedometer = (AwesomeSpeedometer) findViewById(R.id.awesomeSpeedometer);
        speedometer.setWithTremble(false);
        getShadows();

    }

    public void speedUpdated(String cruiseControlState) {
        Gson gson = new Gson();
        CruiseControl tc = gson.fromJson(cruiseControlState, CruiseControl.class);

        Log.i(LOG_TAG, String.format("speed: %d", tc.state.desired.speed));
        Log.i(LOG_TAG, String.format("isActivated: %b", tc.state.desired.isActivated));

        ToggleButton tb = (ToggleButton) findViewById(R.id.enableButton);
        tb.setChecked(tc.state.desired.isActivated);

        EditText speed = (EditText) findViewById(R.id.speed);
        speed.setText(tc.state.desired.speed.toString());
        AwesomeSpeedometer speedometer = (AwesomeSpeedometer) findViewById(R.id.awesomeSpeedometer);
        speedometer.speedTo(tc.state.desired.speed);
    }

    public void getShadow(View view) {
        getShadows();
    }

    public void getShadows() {

        GetShadowTask getControlShadowTask = new GetShadowTask("CruiseControl");
        getControlShadowTask.execute();
    }

    public void turnOnOffCruise(View view) {
        ToggleButton tb = (ToggleButton) findViewById(R.id.enableButton);

        Log.i(LOG_TAG, String.format("System %s", tb.isChecked() ? "Cruise ON" : "Cruise OFF"));
        int newSpeed = 0;
        AwesomeSpeedometer speedometer = (AwesomeSpeedometer) findViewById(R.id.awesomeSpeedometer);


            EditText curSpeed  = (EditText) findViewById(R.id.speed);
            newSpeed = Integer.parseInt(curSpeed.getText().toString());


        speedometer.speedTo(newSpeed);
        UpdateShadowTask updateShadowTask = new UpdateShadowTask();
        updateShadowTask.setThingName("CruiseControl");

        String newState = String.format("{\"state\":{\"desired\":{\"speed\":%d,\"isActivated\":%s}}}",
                newSpeed, tb.isChecked() ? "true" : "false");
        Log.i(LOG_TAG, newState);
        updateShadowTask.setState(newState);
        updateShadowTask.execute();
    }

    public void increaseSpeed(View view) {
        EditText np = (EditText) findViewById(R.id.speed);
        Integer newSpeed = Integer.parseInt(np.getText().toString()) + 1;
        if(newSpeed > 100) newSpeed = 100;
        Log.i(LOG_TAG, "New speed:" + newSpeed);
        np.setText(newSpeed.toString());
        AwesomeSpeedometer speedometer = (AwesomeSpeedometer) findViewById(R.id.awesomeSpeedometer);
        speedometer.speedTo(newSpeed);
        UpdateShadowTask updateShadowTask = new UpdateShadowTask();
        updateShadowTask.setThingName("CruiseControl");
        String newState = String.format("{\"state\":{\"desired\":{\"speed\":%d}}}", newSpeed);
        Log.i(LOG_TAG, newState);
        updateShadowTask.setState(newState);
        updateShadowTask.execute();
    }

    public void decreaseSpeed(View view) {
        EditText np = (EditText) findViewById(R.id.speed);
        Integer newSpeed = Integer.parseInt(np.getText().toString()) - 1;
        if(newSpeed< 0) newSpeed = 0;
        Log.i(LOG_TAG, "New speed:" + newSpeed);
        np.setText(newSpeed.toString());
        AwesomeSpeedometer speedometer = (AwesomeSpeedometer) findViewById(R.id.awesomeSpeedometer);
        speedometer.speedTo(newSpeed);
        UpdateShadowTask updateShadowTask = new UpdateShadowTask();
        updateShadowTask.setThingName("CruiseControl");
        String newState = String.format("{\"state\":{\"desired\":{\"speed\":%d}}}", newSpeed);
        Log.i(LOG_TAG, newState);
        updateShadowTask.setState(newState);
        updateShadowTask.execute();
    }

    private class GetShadowTask extends AsyncTask<Void, Void, AsyncTaskResult<String>> {

        private final String thingName;

        public GetShadowTask(String name) {
            thingName = name;
        }

        @Override
        protected AsyncTaskResult<String> doInBackground(Void... voids) {
            try {
                GetThingShadowRequest getThingShadowRequest = new GetThingShadowRequest()
                        .withThingName(thingName);
                GetThingShadowResult result = iotDataClient.getThingShadow(getThingShadowRequest);
                byte[] bytes = new byte[result.getPayload().remaining()];
                result.getPayload().get(bytes);
                String resultString = new String(bytes);
                return new AsyncTaskResult<String>(resultString);
            } catch (Exception e) {
                Log.e("E", "getShadowTask", e);
                return new AsyncTaskResult<String>(e);
            }
        }

        @Override
        protected void onPostExecute(AsyncTaskResult<String> result) {
            if (result.getError() == null) {
                Log.i(GetShadowTask.class.getCanonicalName(), "result:"+result.getResult());
                if ("CruiseControl".equals(thingName)) {
                    speedUpdated(result.getResult());
                }
            } else {
                Log.e(GetShadowTask.class.getCanonicalName(), "getShadowTask", result.getError());
            }
        }
    }

    private class UpdateShadowTask extends AsyncTask<Void, Void, AsyncTaskResult<String>> {

        private String thingName;
        private String updateState;

        public void setThingName(String name) {
            thingName = name;
        }

        public void setState(String state) {
            updateState = state;
        }

        @Override
        protected AsyncTaskResult<String> doInBackground(Void... voids) {
            try {
                UpdateThingShadowRequest request = new UpdateThingShadowRequest();
                request.setThingName(thingName);

                ByteBuffer payloadBuffer = ByteBuffer.wrap(updateState.getBytes());
                request.setPayload(payloadBuffer);

                UpdateThingShadowResult result = iotDataClient.updateThingShadow(request);

                byte[] bytes = new byte[result.getPayload().remaining()];
                result.getPayload().get(bytes);
                String resultString = new String(bytes);
                return new AsyncTaskResult<String>(resultString);
            } catch (Exception e) {
                Log.e(UpdateShadowTask.class.getCanonicalName(), "updateShadowTask", e);
                return new AsyncTaskResult<String>(e);
            }
        }

        @Override
        protected void onPostExecute(AsyncTaskResult<String> result) {
            if (result.getError() == null) {
                Log.i(UpdateShadowTask.class.getCanonicalName(), result.getResult());
            } else {
                Log.e(UpdateShadowTask.class.getCanonicalName(), "Error in Update Shadow",
                        result.getError());
            }
        }
    }

    public void setSpeed(View view){
        AwesomeSpeedometer speedometer = (AwesomeSpeedometer) findViewById(R.id.awesomeSpeedometer);
        EditText curSpeed  = (EditText) findViewById(R.id.speed);
        int newSpeed = Integer.parseInt(curSpeed.getText().toString());
        if(newSpeed  > 100) newSpeed = 100;
        speedometer.speedTo(newSpeed);
        UpdateShadowTask updateShadowTask = new UpdateShadowTask();
        updateShadowTask.setThingName("CruiseControl");
        String newState = String.format("{\"state\":{\"desired\":{\"speed\":%d}}}", newSpeed);
        Log.i(LOG_TAG, newState);
        updateShadowTask.setState(newState);
        updateShadowTask.execute();
    }
}
