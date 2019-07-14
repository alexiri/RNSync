package com.patrickcremin.react;

import android.util.Log;
import android.app.job.JobService;
import android.app.job.JobParameters;
import android.os.AsyncTask;
import android.os.PersistableBundle;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.CountDownLatch;

import java.io.File;
import java.lang.Override;

import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.Arguments;

import com.cloudant.sync.event.EventBus;
import com.cloudant.sync.event.Subscribe;
import com.cloudant.sync.event.notifications.ReplicationCompleted;
import com.cloudant.sync.event.notifications.ReplicationErrored;
import com.cloudant.sync.replication.ReplicationPolicyManager;
import com.cloudant.sync.replication.Replicator;
import com.cloudant.sync.replication.ReplicatorBuilder;
import com.cloudant.sync.documentstore.DocumentStore;
import com.cloudant.sync.documentstore.DocumentStoreNotOpenedException;


public class RNSyncJobService extends JobService {
    public static final String TAG = "RNSyncJobService";

    public static int PUSH_REPLICATION_ID = 0;
    public static int PULL_REPLICATION_ID = 1;

    private static String datastoreDir = "datastores";

    private JobParameters mJobParameters;
    private ReplicationTask mReplicationTask;
    private static EventBus sEventBus = new EventBus();

    // Create a simple listener that can be attached to the replications so that we can wait
    // for all replications to complete.
    public class Listener {

        private final CountDownLatch latch;

        Listener(CountDownLatch latch) {
            this.latch = latch;
        }

        @Subscribe
        public void complete(ReplicationCompleted event) {
            latch.countDown();
            sEventBus.post(event);
        }

        @Subscribe
        public void error(ReplicationErrored event) {
            latch.countDown();
            sEventBus.post(event);
        }
    }

    // Use an AsyncTask to run the replication(s) off the main thread.
    private class ReplicationTask extends AsyncTask<JobParameters, Void, JobParameters> {

        private final JobService jobService;
        private Replicator pullReplicator;
        private Replicator pushReplicator;

        public ReplicationTask(JobService jobService) {
            this.jobService = jobService;
        }

        //@Override
        protected JobParameters doInBackground(JobParameters... params) {

            PersistableBundle extras = params[0].getExtras();
            String datastoreName = extras.getString("datastoreName");
            String datastoreDir = extras.getString("datastoreDir");
            String replicationUri = extras.getString("replicationUri");
            boolean DIR_PULL = extras.getBoolean("DIR_PULL", false);
            boolean DIR_PUSH = extras.getBoolean("DIR_PUSH", false);

            try {
                DocumentStore ds = null;
                try {
                    ds = DocumentStore.getInstance(new File(datastoreDir, datastoreName));
                } catch (DocumentStoreNotOpenedException dsnoe) {
                    Log.e(TAG,  String.format("Unable to open DocumentStore %s: %s", datastoreName, dsnoe));
                }

                // Setup the CountDownLatch for our replication(s).
                int numLatches = 1;
                if (DIR_PULL && DIR_PUSH) {
                    numLatches = 2;
                }
                CountDownLatch latch = new CountDownLatch(numLatches);
                Listener listener = new Listener(latch);

                Log.i(TAG, "Running ReplicationTask...");
                if (DIR_PULL) {
                    this.pullReplicator = ReplicatorBuilder.pull()
                        .from(new URI(replicationUri))
                        .to(ds)
                        .withId(PULL_REPLICATION_ID)
                        .build();
                    this.pullReplicator.getEventBus().register(listener);
                    this.pullReplicator.start();
                }
                if (DIR_PUSH) {
                    this.pushReplicator = ReplicatorBuilder.push()
                        .to(new URI(replicationUri))
                        .from(ds)
                        .withId(PUSH_REPLICATION_ID)
                        .build();
                    this.pushReplicator.getEventBus().register(listener);
                    this.pushReplicator.start();
                }

                // Wait for the replication(s) to complete.
                latch.await();
                if (DIR_PULL) { this.pullReplicator.getEventBus().unregister(listener); }
                if (DIR_PUSH) { this.pushReplicator.getEventBus().unregister(listener); }
            } catch (URISyntaxException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                Log.i(TAG, "ReplicationTask has been cancelled");
                e.printStackTrace();
            }
            return params[0];
        }

        @Override
        protected void onPostExecute(JobParameters params) {
            // Job finished successfully, but what about the replication(s)?
            boolean PULL_OK;
            boolean PUSH_OK;
            if (pullReplicator == null || pullReplicator.getState() == Replicator.State.COMPLETE) {
                PULL_OK = true;
            } else {
                PULL_OK = false;
            }
            if (pushReplicator == null || pushReplicator.getState() == Replicator.State.COMPLETE) {
                PUSH_OK = true;
            } else {
                PUSH_OK = false;
            }

            if (PULL_OK && PUSH_OK) {
                // All replications ok!
                Log.i(TAG, "Finished ReplicationTask.");
                jobFinished(mJobParameters, false);
            } else {
                // Something failed :(
                Log.w(TAG, "Job failed, retrying...");
                jobFinished(mJobParameters, true);
            }

        }

        //@Override
        protected void onCancelled(Void aVoid) {
            // Replications were cancelled. Request rescheduling.
            jobFinished(mJobParameters, true);
        }
    }

    @Override
    public boolean onStartJob(JobParameters jobParameters) {
        mJobParameters = jobParameters;

        Log.i(TAG, "Running onStartJob...");

        if (!jobParameters.isOverrideDeadlineExpired()) {
            mReplicationTask = new ReplicationTask(this);
            mReplicationTask.execute(mJobParameters);
        } else {
            // An undocumented feature of the JobScheduler is that for a periodic job it
            // will call onStartJob at the end of the period regardless of whether
            // the other conditions for the job are met. However, when it does it
            // for this reason, jobParameters.isOverrideDeadlineExpired() will
            // be true. Since we only want to replicate if all the conditions for
            // the job are true, we just ignore this case and jobFinished().
            jobFinished(mJobParameters, false);
        }

        // Work is being done on a separate thread.
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters jobParameters) {
        mReplicationTask.cancel(true);

        // We want the job rescheduled next time the conditions for execution are met.
        return true;
    }

    public static EventBus getEventBus() {
        return sEventBus;
    }

}