package com.patrickcremin.react;

import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.patrickcremin.react.RNSyncModule;
import com.cloudant.sync.documentstore.DocumentRevision;
import com.cloudant.sync.event.Subscribe;
import com.cloudant.sync.event.notifications.DocumentCreated;
import com.cloudant.sync.event.notifications.DocumentUpdated;
import com.cloudant.sync.event.notifications.DocumentDeleted;

import android.support.annotation.Nullable;
import android.util.Log;
import java.util.HashMap;


public class DocumentEventHandler {
    public static final String TAG = "DocumentEventHandler";
    private ReactApplicationContext reactContext;
    private String datastoreName;

    public DocumentEventHandler(ReactApplicationContext reactContext, String datastoreName) {
        this.reactContext = reactContext;
        this.datastoreName = datastoreName;
    }

    private void sendEvent(String eventName, DocumentRevision doc) {
        if (doc.getId().startsWith("_")) return; // We're not interested in documents that start with an underscore
        WritableMap params = RNSyncModule.createWritableMapFromHashMap(RNSyncModule.createDoc(doc));
        params.putString("datastoreName", datastoreName);
        reactContext
            .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
            .emit(eventName, params);
    }

    @Subscribe
    public void onDocumentCreated(DocumentCreated dc) {
        DocumentRevision doc = dc.newDocument;
        Log.d(TAG, String.format("onDocumentCreated: %s", doc.getId()));
        sendEvent("rnsyncDocumentCreated", doc);
    }

    @Subscribe
    public void onDocumentUpdated(DocumentUpdated dc) {
        DocumentRevision doc = dc.newDocument;
        Log.d(TAG, String.format("onDocumentUpdated: %s", doc.getId()));
        if (doc.isDeleted()) {
            // We may get a onDocumentUpdated event even though the "update" was to delete the doc
            sendEvent("rnsyncDocumentDeleted", doc);
        } else {
            sendEvent("rnsyncDocumentUpdated", doc);
        }
    }

    @Subscribe
    public void onDocumentDeleted(DocumentDeleted dc) {
        DocumentRevision doc = dc.prevDocument;
        Log.d(TAG, String.format("onDocumentDeleted: %s", doc.getId()));
        sendEvent("rnsyncDocumentDeleted", doc);
    }
}