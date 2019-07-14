package com.patrickcremin.react;

import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import com.cloudant.sync.event.Subscribe;
import com.cloudant.sync.event.notifications.ReplicationCompleted;
import com.cloudant.sync.event.notifications.ReplicationErrored;

import android.util.Log;


public class ReplicationEventHandler {
    public static final String TAG = "ReplicationEventHandler";
    private ReactApplicationContext reactContext;
    private String datastoreName;

    public ReplicationEventHandler(ReactApplicationContext reactContext, String datastoreName) {
        this.reactContext = reactContext;
        this.datastoreName = datastoreName;
    }

    private void sendEvent(String eventName, WritableMap params) {
        params.putString("datastoreName", datastoreName);
        reactContext
            .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
            .emit(eventName, params);
    }

    @Subscribe
    public void complete(ReplicationCompleted event) {
        Log.d(TAG, String.format("replication complete: %s docs modified", event.documentsReplicated));
        WritableMap params = Arguments.createMap();
        params.putInt("replicatorID", event.replicator.getId());
        params.putInt("documentsReplicated", event.documentsReplicated);
        params.putInt("batchesReplicated", event.batchesReplicated);
        sendEvent("rnsyncReplicationCompleted", params);
    }

    @Subscribe
    public void error(ReplicationErrored event) {
        WritableMap params = Arguments.createMap();
        params.putInt("replicatorID", event.replicator.getId());
        params.putString("error", event.errorInfo.getMessage());
        sendEvent("rnsyncReplicationFailed", params);
    }
}