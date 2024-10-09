package com.example.eldgps.callforwardingstatus.sms;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootCompletedReceiver extends BroadcastReceiver {
    @SuppressLint("UnsafeProtectedBroadcastReceiver")
    @Override
    public void onReceive(Context context, Intent argIntent) {
        Intent intent = new Intent(context, SmsReceiverService.class);
        context.startForegroundService(intent);
    }
}
