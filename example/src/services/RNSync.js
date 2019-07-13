import RNSync, { RNSyncStorage } from "rnsync";
import { Config } from "./config";

console.log("RNSync starting:", Config.COUCHDB_URL, Config.COUCHDB_DB);
RNSync.init(Config.COUCHDB_URL, Config.COUCHDB_DB)
  .then(result => {
    console.log("RNSync init successfully!");
  })
  .then(() => {
    RNSync.find(
      Config.COUCHDB_DB,
      { _id: { $exists: true } },
      [],
      (error, docs) => {
        if (error) {
          console.warn("RNSync find error:", error);
        } else {
          console.log(`RNSync found ${docs.length} docs`);
        }
      });
  })
  .catch(error => console.warn("RNSync init error", error));



export default RNSync;
