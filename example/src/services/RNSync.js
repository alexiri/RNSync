import RNSync, { RNSyncStorage } from "rnsync";
import { Config } from "./config";

console.log("RNSync starting:", Config.COUCHDB_URL, Config.COUCHDB_DB);
RNSync.init(Config.COUCHDB_URL, Config.COUCHDB_DB)
  .then(result => {
    console.log("RNSync init:", result);
  })
  .catch(error => console.warn("rnsync init error", error));



export default RNSync;
