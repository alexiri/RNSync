package com.patrickcremin.react;

import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import com.cloudant.sync.event.Subscribe;
import com.cloudant.sync.event.notifications.DocumentStoreOpened;
import com.cloudant.sync.event.notifications.DocumentStoreClosed;
import com.cloudant.sync.event.notifications.DocumentStoreCreated;
import com.cloudant.sync.event.notifications.DocumentStoreDeleted;

import android.util.Log;


public class DatabaseEventHandler {
    public static final String TAG = "DatabaseEventHandler";
    private ReactApplicationContext reactContext;
    private String path;

    public DatabaseEventHandler(ReactApplicationContext reactContext, String path) {
        this.reactContext = reactContext;
        this.path = path + "/";
    }

    private void sendEvent(String eventName, String datastoreName) {
        WritableMap params = Arguments.createMap();
        params.putString("datastoreName", datastoreName.replace(path, ""));
        reactContext
            .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
            .emit(eventName, params);
    }

    @Subscribe
    public void onDatabaseOpened(DocumentStoreOpened dc) {
        Log.d(TAG, String.format("onDatabaseOpened: %s", dc.dbName));
        sendEvent("rnsyncDatabaseOpened", dc.dbName);
    }

    @Subscribe
    public void onDatabaseClosed(DocumentStoreClosed dc) {
        Log.d(TAG, String.format("onDatabaseClosed: %s", dc.dbName));
        sendEvent("rnsyncDatabaseClosed", dc.dbName);
    }

    @Subscribe
    public void onDatabaseCreated(DocumentStoreCreated dc) {
        Log.d(TAG, String.format("onDatabaseCreated: %s", dc.dbName));
        sendEvent("rnsyncDatabaseCreated", dc.dbName);
    }

    @Subscribe
    public void onDatabaseDeleted(DocumentStoreDeleted dc) {
        Log.d(TAG, String.format("onDatabaseDeleted: %s", dc.dbName));
        sendEvent("rnsyncDatabaseDeleted", dc.dbName);
    }
}