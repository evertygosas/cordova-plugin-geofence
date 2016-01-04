package com.cowbell.cordova.geofence;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;

import com.google.gson.annotations.Expose;

public class Webhook {
    private Context context;
    //private AssetUtil assets;

    @Expose public String url;
    @Expose public String authorization;

    public void setContext(Context context) {
        this.context = context;
        //this.assets = AssetUtil.getInstance(context);
    }

    public String getUrl() {
        return this.url;
    }

    public String getAuth() {
        return this.authorization;
    }

    public String toString() {
        return "Webhook url: " + getUrl();
    }
}
