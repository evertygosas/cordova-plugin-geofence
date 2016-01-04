package com.cowbell.cordova.geofence;

import android.app.IntentService;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.os.AsyncTask;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;

import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingEvent;

import java.util.ArrayList;
import java.util.List;
import java.io.IOException;


public class ReceiveTransitionsIntentService extends IntentService {
    protected static final String GeofenceTransitionIntent = "com.cowbell.cordova.geofence.TRANSITION";
    protected BeepHelper beepHelper;
    protected GeoNotificationNotifier notifier;
    protected GeoNotificationStore store;

    /**
     * Sets an identifier for the service
     */
    public ReceiveTransitionsIntentService() {
        super("ReceiveTransitionsIntentService");
        beepHelper = new BeepHelper();
        store = new GeoNotificationStore(this);
        Logger.setLogger(new Logger(GeofencePlugin.TAG, this, false));
    }

    /**
     * Handles incoming intents
     *
     * @param intent
     *            The Intent sent by Location Services. This Intent is provided
     *            to Location Services (inside a PendingIntent) when you call
     *            addGeofences()
     */
    @Override
    protected void onHandleIntent(Intent intent) {
        Logger logger = Logger.getLogger();
        logger.log(Log.DEBUG, "ReceiveTransitionsIntentService - onHandleIntent");
        Intent broadcastIntent = new Intent(GeofenceTransitionIntent);
        notifier = new GeoNotificationNotifier(
                (NotificationManager) this.getSystemService(Context.NOTIFICATION_SERVICE),
                this
        );

        // First check for errors
        GeofencingEvent geofencingEvent = GeofencingEvent.fromIntent(intent);
        if (geofencingEvent.hasError()) {
            // Get the error code with a static method
            int errorCode = geofencingEvent.getErrorCode();
            String error = "Location Services error: " + Integer.toString(errorCode);
            // Log the error
            logger.log(Log.ERROR, error);
            broadcastIntent.putExtra("error", error);
        } else {
            // Get the type of transition (entry or exit)
            int transitionType = geofencingEvent.getGeofenceTransition();
            if ((transitionType == Geofence.GEOFENCE_TRANSITION_ENTER)
                    || (transitionType == Geofence.GEOFENCE_TRANSITION_EXIT)) {
                logger.log(Log.DEBUG, "Geofence transition detected");
                List<Geofence> triggerList = geofencingEvent.getTriggeringGeofences();
                List<GeoNotification> geoNotifications = new ArrayList<GeoNotification>();
                for (Geofence fence : triggerList) {
                    String fenceId = fence.getRequestId();
                    GeoNotification geoNotification = store
                            .getGeoNotification(fenceId);

                    if (geoNotification != null) {
                        // Notification
                        if (geoNotification.notification != null) {
                            notifier.notify(geoNotification.notification);
                        }
                        // Server track
                        if (geoNotification.webhook != null) {
                            new NotifyServerAsyncTask().execute(geoNotification, new Integer(transitionType));
                        }
                        geoNotification.transitionType = transitionType;
                        geoNotifications.add(geoNotification);
                    }
                }

                if (geoNotifications.size() > 0) {
                    broadcastIntent.putExtra("transitionData", Gson.get().toJson(geoNotifications));
                    GeofencePlugin.onTransitionReceived(geoNotifications);
                }
            } else {
                String error = "Geofence transition error: " + transitionType;
                logger.log(Log.ERROR, error);
                broadcastIntent.putExtra("error", error);
            }
        }
        sendBroadcast(broadcastIntent);
    }

    protected void notifyServer(GeoNotification geo, Integer transition) {
        Logger logger = Logger.getLogger();

        HttpClient httpclient = new DefaultHttpClient();
      	HttpPost httppost = new HttpPost(geo.webhook.getUrl());
        logger.log(Log.DEBUG, "GEOSERVER Sending server track to : " + geo.webhook.getUrl());
      	try {
            List nameValuePairs = new ArrayList();
            // Geofence transition informations
            nameValuePairs.add(new BasicNameValuePair("id", geo.id));
            nameValuePairs.add(new BasicNameValuePair("action", (transition == Geofence.GEOFENCE_TRANSITION_ENTER) ? "enter" : "exit"));
            nameValuePairs.add(new BasicNameValuePair("timestamp", String.valueOf(System.currentTimeMillis())));
            httppost.setEntity(new UrlEncodedFormEntity(nameValuePairs));
            // Authorization header
            if (geo.webhook.getAuth() != null) {
              httppost.setHeader("Authorization", geo.webhook.getAuth());
            }
            // send the variable and value, in other words post, to the URL
      		  HttpResponse response = httpclient.execute(httppost);
      	} catch (ClientProtocolException e) {
            logger.log(Log.ERROR, "Error sending track. ClientProtocol Error");
      		  // process execption
      	} catch (IOException e) {
      		  // process execption
      		  logger.log(Log.ERROR, "Error sending track. IOException");
      	}
    }


    private class NotifyServerAsyncTask extends AsyncTask<Object, Integer, Double>{
        @Override
        protected Double doInBackground(Object... params) {
            notifyServer((GeoNotification)params[0], (Integer)params[1]);
            return null;
        }
    }
}
