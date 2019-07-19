import RNSync, { RNSyncStorage } from "rnsync";
import { NativeEventEmitter, NativeModules } from 'react-native';
import { Config } from "./config";


function countDocs() {
  return new Promise( ( resolve, reject ) =>
    RNSync.readAll(Config.COUCHDB_DB, (error, docs) => {
        if (error) {
          console.warn("RNSync readAll error:", error);
          reject(error);
        } else {
          console.log(`RNSync read ${docs.length} docs`);
          resolve(docs.length);
        }
      })
  );
}
console.log("RNSync starting:", Config.COUCHDB_URL, Config.COUCHDB_DB);

const eventEmitter = new NativeEventEmitter(NativeModules.RNSync);

this.docCreated = eventEmitter.addListener('rnsyncDocumentCreated', (e) => {
  console.log("doc created", e);
});
this.docUpdated = eventEmitter.addListener('rnsyncDocumentUpdated', (e) => {
  console.log("doc updated", e);
});
this.docDeleted = eventEmitter.addListener('rnsyncDocumentDeleted', (e) => {
  console.log("doc deleted", e);
});

this.docCreated = eventEmitter.addListener('rnsyncDatabaseOpened', (e) => {
  console.log("store opened", e);
});
this.docUpdated = eventEmitter.addListener('rnsyncDatabaseClosed', (e) => {
  console.log("store closed", e);
});
this.docDeleted = eventEmitter.addListener('rnsyncDatabaseCreated', (e) => {
  console.log("store created", e);
});
this.docDeleted = eventEmitter.addListener('rnsyncDatabaseDeleted', (e) => {
  console.log("store deleted", e);
});

this.docDeleted = eventEmitter.addListener('rnsyncReplicationCompleted', (e) => {
  console.log(new Date(), "replication completed", e);
  console.log(new Date(), "starting another one");
  //RNSync.replicatePull(Config.COUCHDB_DB, 10);
});
this.docDeleted = eventEmitter.addListener('rnsyncReplicationFailed', (e) => {
  console.log("replication failed", e);
});

export function replicateIos() {
  return RNSync
    .replicateIos(Config.COUCHDB_DB)
    .then(result => {
      console.log("RNSync replicatePull:", result);
      if (result !== undefined) {
        const nums = result.match(/\d+/g).map(Number);
        if (nums[0] == 0) return; // No new documents replicated
      }
      countDocs();
    })
    .catch(error => {
      console.log("RNSync replicatePull error:", error);
    });
}


RNSync.initFromFile(Config.COUCHDB_URL, Config.COUCHDB_DB, "data/db.sync")
  .then(result => {
    console.log("RNSync init successfully!");
    RNSync.compact(Config.COUCHDB_DB)
      .then(() => console.log("Compaction executed"))
      .catch(error => console.warn("Compaction error", error));
  })
  .then(() => countDocs())
  .then(() => {
    console.log(new Date(), "RNSync going to replicate");

    RNSync.replicatePull(Config.COUCHDB_DB, 1);
    //replicateIos();
  })
  .catch(error => console.warn("RNSync init error", error));



export default RNSync;
