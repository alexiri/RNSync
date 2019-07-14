import RNSync, { RNSyncStorage } from "rnsync";
import { DeviceEventEmitter } from 'react-native';
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

this.docCreated = DeviceEventEmitter.addListener('rnsyncDocumentCreated', (e) => {
  console.log("doc created", e);
});
this.docUpdated = DeviceEventEmitter.addListener('rnsyncDocumentUpdated', (e) => {
  console.log("doc updated", e);
});
this.docDeleted = DeviceEventEmitter.addListener('rnsyncDocumentDeleted', (e) => {
  console.log("doc deleted", e);
});

this.docCreated = DeviceEventEmitter.addListener('rnsyncDatabaseOpened', (e) => {
  console.log("store opened", e);
});
this.docUpdated = DeviceEventEmitter.addListener('rnsyncDatabaseClosed', (e) => {
  console.log("store closed", e);
});
this.docDeleted = DeviceEventEmitter.addListener('rnsyncDatabaseCreated', (e) => {
  console.log("store created", e);
});
this.docDeleted = DeviceEventEmitter.addListener('rnsyncDatabaseDeleted', (e) => {
  console.log("store deleted", e);
});

this.docDeleted = DeviceEventEmitter.addListener('rnsyncReplicationCompleted', (e) => {
  console.log(new Date(), "replication completed", e);
  console.log(new Date(), "starting another one");
  RNSync.replicatePull(Config.COUCHDB_DB, 10);
});
this.docDeleted = DeviceEventEmitter.addListener('rnsyncReplicationFailed', (e) => {
  console.log("replication failed", e);
});

RNSync.init(Config.COUCHDB_URL, Config.COUCHDB_DB)
  .then(result => {
    console.log("RNSync init successfully!");
  })
  .then(() => countDocs())
  .then(() => {
    console.log(new Date(), "RNSync going to replicate");

    RNSync.replicatePull(Config.COUCHDB_DB, 1);
  })
  .catch(error => console.warn("RNSync init error", error));



export default RNSync;
