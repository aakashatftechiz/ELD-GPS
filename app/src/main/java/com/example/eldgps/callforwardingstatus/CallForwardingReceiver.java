package com.example.eldgps.callforwardingstatus;

import static android.content.Context.TELEPHONY_SERVICE;

import static com.google.firebase.BuildConfig.DEBUG;


import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;

import com.eldgps.services.R;

import java.util.List;

public class CallForwardingReceiver extends BroadcastReceiver {
    final String TAG = "Receiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if ("de.kaiserdragon.callforwardingstatus.TOGGLE_CALL_FORWARDING".equals(intent.getAction())) {
            // DatabaseHelper databaseHelper = new DatabaseHelper(context);
            // String[] array = databaseHelper.getSelected();
            String phoneNumber = intent.getStringExtra("phoneNumber");
            Log.v(TAG, "Telephone_Number = " + phoneNumber);
            if (DEBUG) Log.v(TAG, "Number = " + phoneNumber);
            if (!phoneNumber.equals("")) {
                Toast.makeText(context, context.getString(R.string.setupCallForwarding), Toast.LENGTH_LONG).show();
                setCallForwarding(context, phoneNumber);
            } else
                Toast.makeText(context, context.getString(R.string.NoNumber), Toast.LENGTH_SHORT).show();
        }

    }

    public int getSavedSelectedSimId(Context context) {
        SubscriptionManager subscriptionManager = SubscriptionManager.from(context);


        // Get the list of active subscriptions (SIMs)
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(context,"you don't have permission",Toast.LENGTH_LONG).show();
            Log.e("NUMBER== 3 ", "you don't have permission");
            return -1;
        }
        List<SubscriptionInfo> subscriptionList = subscriptionManager.getActiveSubscriptionInfoList();
        if (subscriptionList != null && !subscriptionList.isEmpty()) {
            // Return the subscription ID for the first active SIM (SIM 1)
            SubscriptionInfo sim1Info = subscriptionList.get(0);
            Log.e("NUMBER== 4 ", "sub id : " + sim1Info.getSubscriptionId());
            return sim1Info.getSubscriptionId();
        }


            return -1; // -1 is a default value if the preference is not found
        }

        private void setCallForwarding (Context context, String phoneNumber){
            Log.v("NUMBER== 1 ", phoneNumber);
            TelephonyManager manager = (TelephonyManager) context.getSystemService(TELEPHONY_SERVICE);

            int defaultSubId = getSavedSelectedSimId(context);
            if (defaultSubId <= 0) {
                SubscriptionManager subscriptionManager = SubscriptionManager.from(context);
                // Determine the default subscription ID
                defaultSubId = SubscriptionManager.getDefaultSubscriptionId();
                // Check if the device supports multiple SIMs and retrieve active subscriptions
                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
                    List<SubscriptionInfo> activeSubscriptions = subscriptionManager.getActiveSubscriptionInfoList();
                    if (activeSubscriptions != null && !activeSubscriptions.isEmpty()) {
                        // Choose the first active subscription
                        SubscriptionInfo subscriptionInfo = activeSubscriptions.get(0);
                        defaultSubId = subscriptionInfo.getSubscriptionId();
                    }
                }

            }
            Log.v("NUMBER== ssd", String.valueOf(defaultSubId));

            Handler handler = new Handler();
            TelephonyManager.UssdResponseCallback responseCallback = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

                Log.e("reponse","in the response");

                responseCallback = new TelephonyManager.UssdResponseCallback() {
                    @Override
                    public void onReceiveUssdResponse(TelephonyManager telephonyManager, String request, CharSequence response) {
                        super.onReceiveUssdResponse(telephonyManager, request, response);
                        Log.d(TAG, "onReceiveUssdResponse: " + response);
                        Toast.makeText(context, response.toString(), Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onReceiveUssdResponseFailed(TelephonyManager telephonyManager, String request, int failureCode) {
                        super.onReceiveUssdResponseFailed(telephonyManager, request, failureCode);
                        Log.d(TAG, "onReceiveUssdResponseFailed: " + failureCode);
                        Toast.makeText(context, String.valueOf(failureCode), Toast.LENGTH_SHORT).show();
                    }
                };
            }
            TelephonyManager manager1;


                Log.e("MANAGER", "CALL_PHONE permission: " +
                        ActivityCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE));

                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                    String ussdRequest = "*21*" + phoneNumber + "#";
                    Log.v("MANAGER", ussdRequest);


                    // Set the subscription ID for call forwarding
                    manager1 = manager.createForSubscriptionId(defaultSubId);
                    Log.v("MANAGER", String.valueOf(defaultSubId));
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        manager1.sendUssdRequest(ussdRequest, responseCallback, handler);
                        Log.v("MANAGER", String.valueOf(manager1));

                    }
                }
             else {
                Log.v("MANAGER", "else");
                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                    // Set the subscription ID for call forwarding
                    manager1 = manager.createForSubscriptionId(defaultSubId);
                    Log.v("MANAGER", String.valueOf(manager1));
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        manager1.sendUssdRequest("#21#", responseCallback, handler);
                    }
                }
            }
        }
    }