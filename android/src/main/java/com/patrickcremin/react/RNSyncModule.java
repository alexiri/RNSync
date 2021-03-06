package com.patrickcremin.react;

import android.content.Context;
import android.util.Base64;
import android.util.Log;

import com.cloudant.sync.documentstore.Attachment;
import com.cloudant.sync.documentstore.DocumentBodyFactory;
import com.cloudant.sync.documentstore.DocumentNotFoundException;
import com.cloudant.sync.documentstore.DocumentRevision;
import com.cloudant.sync.documentstore.DocumentStore;
import com.cloudant.sync.documentstore.DocumentStoreException;
import com.cloudant.sync.documentstore.DocumentStoreNotDeletedException;
import com.cloudant.sync.documentstore.DocumentStoreNotOpenedException;
import com.cloudant.sync.documentstore.UnsavedFileAttachment;
import com.cloudant.sync.query.FieldSort;
import com.cloudant.sync.query.QueryException;
import com.cloudant.sync.query.QueryResult;
import com.cloudant.sync.query.Tokenizer;
import com.cloudant.sync.replication.Replicator;
import com.cloudant.sync.replication.ReplicatorBuilder;

import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.ReadableMapKeySetIterator;
import com.facebook.react.bridge.ReadableNativeMap;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableNativeArray;
import com.facebook.react.bridge.WritableNativeMap;
import com.google.gson.Gson;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.FileOutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import android.os.PersistableBundle;
import android.content.ComponentName;
import android.content.Context;
import android.app.job.JobScheduler;
import android.app.job.JobInfo;
import android.app.job.JobInfo.Builder;
import android.content.res.AssetManager;

class Store {
    URI replicationUri;
    DocumentStore documentStore;
    DocumentEventHandler docListener;
    ReplicationEventHandler replListener;

    Store(URI replicationUri, DocumentStore documentStore) {
        this.replicationUri = replicationUri;
        this.documentStore = documentStore;
    }
}

public class RNSyncModule extends ReactContextBaseJavaModule {
    public static final String TAG = "RNSyncModule";

    private static final int MAX_THREADS = 5;
    private static ThreadPoolExecutor executor;
    private static final String datastoreDir = "datastores";

    static {
        executor = new ThreadPoolExecutor(
                MAX_THREADS, MAX_THREADS,
                1, TimeUnit.MINUTES,
                new ArrayBlockingQueue<Runnable>(MAX_THREADS, true)
        );
    }

    private HashMap<String, Store> stores = new HashMap<>();
    private DatabaseEventHandler dbListener;

    RNSyncModule(ReactApplicationContext reactContext) {
        super(reactContext);
    }

    @Override
    public String getName() {
        return "RNSync";
    }

    // TODO let them name the datastore
    @ReactMethod
    public void init(String databaseUrl, String datastoreName, Callback callback) {
        File path = super.getReactApplicationContext()
                .getApplicationContext()
                .getDir(datastoreDir, Context.MODE_PRIVATE);

        if (stores.size() == 0) {
            dbListener = new DatabaseEventHandler(super.getReactApplicationContext(), path.getAbsolutePath());
            DocumentStore.getEventBus().register(dbListener);
            Log.i(TAG, "Registered DB listener");
        }
        try{
            DocumentStore ds = DocumentStore.getInstance(new File(path, datastoreName));
            URI uri = new URI(databaseUrl);

            Store store = new Store(uri, ds);
            store.docListener = new DocumentEventHandler(super.getReactApplicationContext(), datastoreName);
            ds.database().getEventBus().register(store.docListener);

            stores.put(datastoreName, store);
        } catch (DocumentStoreNotOpenedException e) {
            callback.invoke(e.getMessage());
            return;
        } catch (URISyntaxException e) {
            callback.invoke(e.getMessage());
            return;
        }

        callback.invoke();
    }

    @ReactMethod
    public void initFromFile(String databaseUrl, String datastoreName, String dbDump, Callback callback) {
        File path = super.getReactApplicationContext()
                .getApplicationContext()
                .getDir(datastoreDir, Context.MODE_PRIVATE);

        File dbDir = new File(path, datastoreName);

        if (!dbDir.isDirectory()) {
            Log.i(TAG, "Database does not exist");
            AssetManager assetManager = super.getReactApplicationContext()
                .getApplicationContext()
                .getAssets();

            try {
                dbDir.mkdirs();
                try (InputStream in = assetManager.open(dbDump)) { //new FileInputStream(src)
                    try (OutputStream out = new FileOutputStream(new File(dbDir, "db.sync"))) {
                        // Transfer bytes from in to out
                        byte[] buf = new byte[1024];
                        int len;
                        while ((len = in.read(buf)) > 0) {
                            out.write(buf, 0, len);
                        }
                    }
                }
                Log.i(TAG, "Database created from file");
            } catch (IOException e) {
                Log.e(TAG, "Failed to copy file", e);
                callback.invoke(e.getMessage());
                return;
            }
        }

        init(databaseUrl, datastoreName, callback);
    }

    @ReactMethod
    public void close(String datastoreName, Callback callError) {
        Store store = stores.get(datastoreName);
        if (store == null) {
            callError.invoke("No datastore named " + datastoreName);
            return;
        }

        store.documentStore.close();
        if (store.docListener != null) {
            store.documentStore.database().getEventBus().unregister(store.docListener);
        }
        if (store.replListener != null) {
            RNSyncJobService.getEventBus().unregister(store.replListener);
        }
        stores.remove(datastoreName);

        if (stores.size() == 0) {
            DocumentStore.getEventBus().unregister(dbListener);
            dbListener = null;
            Log.i(TAG, "Unregistered DB listener");
        }

        callError.invoke();
    }

    @ReactMethod
    public void compact(String datastoreName, Callback callback) {
        Store store = stores.get(datastoreName);
        if (store == null) {
            callback.invoke("No datastore named " + datastoreName);
            return;
        }

        try {
            store.documentStore.database().compact();
        } catch (DocumentStoreException e) {
            callback.invoke(e.getMessage());
            return;
        }

        callback.invoke();
    }

    @ReactMethod
    public void readAll(String datastoreName, Callback callback) {
        Store store = stores.get(datastoreName);
        if (store == null) {
            callback.invoke("No datastore named " + datastoreName);
            return;
        }
        DocumentStore ds = store.documentStore;

        try {
            List<DocumentRevision> documentRevisions = ds.database().read(0, ds.database().getDocumentCount(), true);

            WritableArray docs = new WritableNativeArray();

            for (DocumentRevision revision : documentRevisions) {
                if (revision.getId().startsWith("_")) continue; // We're not interested in documents that start with an underscore

                String jsonString = new Gson().toJson(this.createDoc(revision));

                docs.pushString(jsonString);
            }

            callback.invoke(null, docs);
        } catch (Exception e) {
            callback.invoke(e.getMessage());
        }
    }

    @ReactMethod
    public void replicatePush(String datastoreName, int delaySeconds, Callback callback) {
        replicate(datastoreName, delaySeconds, false, true, callback);
    }

    @ReactMethod
    public void replicatePull(String datastoreName, int delaySeconds, Callback callback) {
        replicate(datastoreName, delaySeconds, true, false, callback);
    }

    @ReactMethod
    public void replicateSync(String datastoreName, int delaySeconds, Callback callback) {
        replicate(datastoreName, delaySeconds, true, true, callback);
    }

    private void replicate(String datastoreName, int minSeconds, Boolean pull, Boolean push, Callback callback) {
        Store store = stores.get(datastoreName);
        if (store == null) {
            callback.invoke("No datastore named " + datastoreName);
            return;
        }

        File path = super.getReactApplicationContext()
                    .getApplicationContext()
                    .getDir(datastoreDir, Context.MODE_PRIVATE);

        PersistableBundle bundle = new PersistableBundle();
        bundle.putString("datastoreName", datastoreName);
        bundle.putString("datastoreDir", path.getAbsolutePath());
        bundle.putString("replicationUri", store.replicationUri.toString());
        bundle.putBoolean("DIR_PULL", pull);
        bundle.putBoolean("DIR_PUSH", push);
        int MY_JOB_ID = 1;

        if (store.replListener == null) {
            store.replListener = new ReplicationEventHandler(super.getReactApplicationContext(), datastoreName);
            RNSyncJobService.getEventBus().register(store.replListener);
        }

        Context context = super.getReactApplicationContext().getApplicationContext();
        ComponentName jobServiceComponent = new ComponentName(context, RNSyncJobService.class);

        JobInfo.Builder builder = new JobInfo.Builder(MY_JOB_ID, jobServiceComponent);
        builder.setMinimumLatency(minSeconds * 1000);
        builder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY);
        builder.setExtras(bundle);

        JobScheduler jobScheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        jobScheduler.schedule(builder.build());

        callback.invoke();
    }

    @ReactMethod
    public void create(String datastoreName, ReadableMap body, String id, Callback callback) {
        Store store = stores.get(datastoreName);
        if (store == null) {
            callback.invoke("No datastore named " + datastoreName);
            return;
        }
        DocumentStore ds = store.documentStore;

        ReadableNativeMap nativeBody = (ReadableNativeMap) body;

        DocumentRevision revision;

        if (id != null && !id.isEmpty()) {
            revision = new DocumentRevision(id);
        } else {
            revision = new DocumentRevision();
        }

        if (body == null) {
            revision.setBody(DocumentBodyFactory.create(new HashMap<String, Object>()));
        } else {
            revision.setBody(DocumentBodyFactory.create(nativeBody.toHashMap()));
        }

        try {
            DocumentRevision saved = ds.database().create(revision);

            WritableMap doc = RNSyncModule.createWritableMapFromHashMap(this.createDoc(saved));

            callback.invoke(null, doc);
        } catch (Exception e) {
            callback.invoke(e.getMessage());
        }
    }

    // TODO need ability to update and remove attachments
    @ReactMethod
    public void addAttachment(String datastoreName, String id, String name, String path, String type, Callback callback) {
        Store store = stores.get(datastoreName);
        if (store == null) {
            callback.invoke("No datastore named " + datastoreName);
            return;
        }
        DocumentStore ds = store.documentStore;

        try {
            DocumentRevision revision = ds.database().read(id);

            // Add an attachment -- binary data like a JPEG
            File f = new File(path);
            UnsavedFileAttachment att1 =
                    new UnsavedFileAttachment(f, type);

            revision.getAttachments().put(f.getName(), att1);
            DocumentRevision updated = ds.database().update(revision);

            WritableMap doc = RNSyncModule.createWritableMapFromHashMap(this.createDoc(updated));

            callback.invoke(null, doc);
        } catch (Exception e) {
            callback.invoke(e.getMessage());
        }
    }

    @ReactMethod
    public void retrieve(String datastoreName, String id, Callback callback) {
        Store store = stores.get(datastoreName);
        if (store == null) {
            callback.invoke("No datastore named " + datastoreName);
            return;
        }
        DocumentStore ds = store.documentStore;

        try {
            DocumentRevision revision = ds.database().read(id);

            WritableMap doc = RNSyncModule.createWritableMapFromHashMap(this.createDoc(revision));

            callback.invoke(null, doc);
        } catch (Exception e) {
            callback.invoke(e.getMessage());
        }
    }

    @ReactMethod
    public void retrieveAttachments(String datastoreName, String id, Callback callback) {
        Store store = stores.get(datastoreName);
        if (store == null) {
            callback.invoke("No datastore named " + datastoreName);
            return;
        }
        DocumentStore ds = store.documentStore;

        try {
            DocumentRevision revision = ds.database().read(id);
            Map<String, Attachment> attachments = revision.getAttachments();
            HashMap<String, Object> dataBlobs = new HashMap<>();
            for (Map.Entry<String, Attachment> attachment : attachments.entrySet()) {
                Attachment att = attachment.getValue();

                if(att.encoding == Attachment.Encoding.Plain){
                    InputStream inputStream = att.getInputStream();

                    byte[] imageBytes = new byte[(int)inputStream.available()];
                    inputStream.read(imageBytes, 0, imageBytes.length);
                    inputStream.close();

                    String encodedString = Base64.encodeToString(imageBytes, Base64.CRLF);

                    dataBlobs.put(attachment.getKey(), "data:"+att.type+";base64,"+encodedString);
                }
            }
            callback.invoke(null, RNSyncModule.createWritableMapFromHashMap(dataBlobs));
        }
        catch (DocumentNotFoundException e) {
            callback.invoke(e.getMessage());
            return;
        }
        catch (DocumentStoreException e) {
            callback.invoke(e.getMessage());
            return;
        }
        catch (IOException e) {
            callback.invoke(e.getMessage());
            return;
        }
    }

    @ReactMethod
    public void update(String datastoreName, String id, String rev, ReadableMap body, Callback callback) {
        Store store = stores.get(datastoreName);
        if (store == null) {
            callback.invoke("No datastore named " + datastoreName);
            return;
        }
        DocumentStore ds = store.documentStore;

        try {
            DocumentRevision revision = ds.database().read(id);

            ReadableNativeMap nativeBody = (ReadableNativeMap) body;

            revision.setBody(DocumentBodyFactory.create(nativeBody.toHashMap()));

            DocumentRevision updated = ds.database().update(revision);

            WritableMap doc = RNSyncModule.createWritableMapFromHashMap(this.createDoc(updated));

            callback.invoke(null, doc);
        } catch (Exception e) {
            callback.invoke(e.getMessage());
        }
    }

    @ReactMethod
    public void delete(String datastoreName, String id, Callback callback) {
        Store store = stores.get(datastoreName);
        if (store == null) {
            callback.invoke("No datastore named " + datastoreName);
            return;
        }
        DocumentStore ds = store.documentStore;

        try {
            DocumentRevision revision = ds.database().read(id);

            ds.database().delete(revision);

            callback.invoke();
        } catch (Exception e) {
            callback.invoke(e.getMessage());
        }
    }

    @ReactMethod
    public void find(String datastoreName, ReadableMap query, ReadableArray fields, Callback callback) {
        Store store = stores.get(datastoreName);
        if (store == null) {
            callback.invoke("No datastore named " + datastoreName);
            return;
        }
        DocumentStore ds = store.documentStore;

        try {
            ReadableNativeMap nativeQuery = (ReadableNativeMap) query;

            QueryResult result;

            if (fields == null) {
                result = ds.query().find(nativeQuery.toHashMap(), 0, 0, null, null);
            } else {
                List<String> fieldsList = new ArrayList<>();
                for (int i = 0; i < fields.size(); i++) {
                    fieldsList.add(fields.getString(i));
                }

                result = ds.query().find(nativeQuery.toHashMap(), 0, 0, fieldsList, null);
            }

            WritableArray docs = new WritableNativeArray();

            for (DocumentRevision revision : result) {

                String jsonString = new Gson().toJson(this.createDoc(revision));

                docs.pushString(jsonString);
            }

            callback.invoke(null, docs);
        } catch (QueryException e) {
            callback.invoke(e.getMessage());
        }
    }

    public static HashMap<String, Object> createDoc(DocumentRevision revision) {
        HashMap<String, Object> doc = new HashMap<>();
        doc.put("id", revision.getId());
        doc.put("rev", revision.getRevision());
        doc.put("body", revision.getBody().asMap());


        // TODO map attachments
//        WritableArray attachments = new WritableNativeArray();
//        Iterator it = revision.getAttachments().entrySet().iterator();
//        while (it.hasNext()) {
//            Map.Entry pair = (Map.Entry)it.next();
//            System.out.println(pair.getKey() + " = " + pair.getValue());
//            String key = (String)pair.getKey();
//            //attachments.put((String)pair.getKey(), pair.getValue());
//            attachments.pushString(pair.getValue().toString());
//            it.remove(); // avoids a ConcurrentModificationException
//        }
//        doc.put("attachments", attachments);

        return doc;
    }

    /**
     * @param indexes  e.g. {"TEXT":{"textNames":["Common_name","Botanical_name"]},"JSON":{"jsonNames":["Common_name","Botanical_name"]}}
     * @param callback
     */
    @ReactMethod
    public void createIndexes(final String datastoreName, final ReadableMap indexes, final Callback callback) {
        Log.i(TAG, "createIndexes: " + indexes.toString());

        Store store = stores.get(datastoreName);
        if (store == null) {
            callback.invoke("No datastore named " + datastoreName);
            return;
        }
        final DocumentStore ds = store.documentStore;

        final ReadableMap jsonIndexes = indexes.getMap("JSON");
        final ReadableMap textIndexes = indexes.getMap("TEXT");

        // Run on a background thread - creating indexes can take a while
        RNSyncModule.executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    if (jsonIndexes != null) {
                        ReadableMapKeySetIterator iterator = jsonIndexes.keySetIterator();
                        while (iterator.hasNextKey()) {
                            String key = iterator.nextKey();

                            ReadableArray array = jsonIndexes.getArray(key);
                            List<FieldSort> fields = new ArrayList<>();
                            for (int i = 0; i < array.size(); i++) {
                                fields.add(new FieldSort(array.getString(i)));
                            }

                            ds.query().createJsonIndex(fields, key);
                        }
                    }

                    if (textIndexes != null) {
                        ReadableMapKeySetIterator iterator = textIndexes.keySetIterator();
                        while (iterator.hasNextKey()) {
                            String key = iterator.nextKey();

                            ReadableArray array = textIndexes.getArray(key);
                            List<FieldSort> fields = new ArrayList<>();
                            for (int i = 0; i < array.size(); i++) {
                                fields.add(new FieldSort(array.getString(i)));
                            }

                            ds.query().createTextIndex(fields, key, new Tokenizer("porter unicode61"));
                        }
                    }

                    ds.query().refreshAllIndexes();

                    callback.invoke();
                } catch (QueryException e) {
                    callback.invoke(e.getMessage());
                }
            }
        });
    }

    @ReactMethod
    public void deleteStore(String datastoreName, Callback callback) {
        Store store = stores.get(datastoreName);
        if (store == null) {
            callback.invoke("No datastore named " + datastoreName);
            return;
        }
        DocumentStore ds = store.documentStore;

        try {
            ds.delete();
            callback.invoke();
        }
        catch (DocumentStoreNotDeletedException e) {
            callback.invoke(e.getMessage());
            return;
        }
    }

    private static WritableArray convertJsonToArray(JSONArray jsonArray) throws JSONException {
        WritableArray array = new WritableNativeArray();

        for (int i = 0; i < jsonArray.length(); i++) {
            Object value = jsonArray.get(i);
            if (value instanceof JSONObject) {
                array.pushMap(convertJsonToMap((JSONObject) value));
            } else if (value instanceof JSONArray) {
                array.pushArray(convertJsonToArray((JSONArray) value));
            } else if (value instanceof Boolean) {
                array.pushBoolean((Boolean) value);
            } else if (value instanceof Integer) {
                array.pushInt((Integer) value);
            } else if (value instanceof Double) {
                array.pushDouble((Double) value);
            } else if (value instanceof String)  {
                array.pushString((String) value);
            } else {
                array.pushString(value.toString());
            }
        }
        return array;
    }

    private static WritableMap convertJsonToMap(JSONObject jsonObject) throws JSONException {
        WritableMap map = new WritableNativeMap();

        Iterator<String> iterator = jsonObject.keys();
        while (iterator.hasNext()) {
            String key = iterator.next();
            Object value = jsonObject.get(key);
            if (value instanceof JSONObject) {
                map.putMap(key, convertJsonToMap((JSONObject) value));
            } else if (value instanceof JSONArray) {
                map.putArray(key, convertJsonToArray((JSONArray) value));
            } else if (value instanceof Boolean) {
                map.putBoolean(key, (Boolean) value);
            } else if (value instanceof Integer) {
                map.putInt(key, (Integer) value);
            } else if (value instanceof Double) {
                map.putDouble(key, (Double) value);
            } else if (value instanceof String)  {
                map.putString(key, (String) value);
            } else {
                map.putString(key, value.toString());
            }
        }

        return map;
    }

    public static WritableMap createWritableMapFromHashMap(HashMap<String, Object> doc) {
        try {
            JSONObject jsonObject = new JSONObject(new Gson().toJson(doc));

            return convertJsonToMap(jsonObject);
        } catch (JSONException e) {
            Log.e(TAG, "Failed to create WriteableMap from document");

            throw new RuntimeException(e);
        }
    }
}
