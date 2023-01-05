package org.blind.help.object.detection;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.speech.RecognizerIntent;

public class VoiceBroadcastReciever extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction().contains("android.speech.action.VOICE_SEARCH_RESULTS")) {
            String spokenText = intent.getStringExtra(RecognizerIntent.EXTRA_RESULTS);
            if (spokenText.contains("blind")) {
                // Open your app
                Intent appIntent = new Intent(context, MainActivity.class);
                appIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(appIntent);
            }
        }
    }
}
