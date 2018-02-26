# RNSync
[![npm version](https://badge.fury.io/js/rnsync.svg)](https://badge.fury.io/js/rnsync)

## About

RNSync is a React Native module that allows you to work with your Cloudant or CouchDB database locally (on the mobile device) and replicate to the remote database when needed.

RNSync is a wrapper for [Cloudant Sync](https://github.com/cloudant/CDTDatastore), which simplifies large-scale mobile development by enabling you to create a single database for every user; you simply replicate and sync the copy of this database in Cloudant with a local copy on their phone or tablet. This can reduce round-trip database requests with the server. If there’s no network connection, the app runs off the database on the device; when the network connection is restored, Cloudant re-syncs the device and server.

You can get an instance of [Cloudant](http://bit.ly/2eH8lbY) by creating an account on [IBM Bluemix](http://bit.ly/2fYtrCz).

RNSync only supports ReactNative > 0.40

RNSync works with [Redux Persist](https://github.com/rt2zz/redux-persist).  Please read the [RNSyncStorage doc](RNSyncStorage.md) for more info.  You may also prefer the simplified API.


## Installation

Install with npm
```ruby
npm install --save rnsync
```

### iOS

Add CDTDatastore to your Podfile

```ruby
pod 'CDTDatastore'
```

Link and Pod install

```ruby
react-native link rnsync
cd ios;pod install
```
### Android

```ruby
react-native link rnsync
```


## Usage

#### Init

The below example exposes your credentials on every device, and the database must already exist, but it is fine for testing the package.

To avoid exposing credentials create a web service to authenticate users and set up databases for client devices. This web service needs to:

- Handle sign in/sign up for users.
- Create a new remote database for a new user.
- Grant access to the new database for the new device (e.g., via [API keys on Cloudant](https://cloudant.com/for-developers/faq/auth/) or the _users database in CouchDB).
- Return the database URL and credentials to the device.

You can use the [rnsync_key_generator](https://github.com/pwcremin/rnsync_key_generator) package with your Express server to easily handle database and credentials creation. Also refer to [cloudantApiKeyGenerator](cloudantApiKeyGenerator) for an example of adding this functionality to your Express server if you do not wish to use rnsync_key_generator.

```javascript
import rnsync from 'rnsync';

// init with your cloudant or couchDB database
var dbUrl = "https://user:pass@xxxxx";
var dbName = "name_xxxx";

rnsync.init(dbUrl, dbName, function(error)
{
  console.log(error);
});
```

#### Delete Store

```javascript
import rnsync from 'rnsync';

rnsync.deleteStore(function(error)
{
  console.log(error);
});
```

#### Create

Both the object and the id are optional.  If you leave out the object it will create a new doc that is empty.  If you leave
out the id that will be autogenerated for you.
```javascript
var object = {x:10};
var id = "whatever";

rnsync.create(object, id, function(error, doc)
{
  console.log(doc.id);
});

rnsync.create({name: 'jon'},  function(error, doc)
{
  console.log(doc.id);
});

// note: create will return an error if the id already exist
rnsync.create('user',  function(error, doc)
{
  console.log(doc.id);
});

```

#### Find or Create

Returns the doc with the specified id.  It will create the doc if it does not already exist.

```javascript
rnsync.findOrCreate('user',  function(error, doc)
{
  console.log(doc.id);
});
```

#### Retrieve

Returns the doc with the specified id.

```javascript

var id = "whatever";

rnsync.retrieve(id, function(error, doc)
{
  console.log(JSON.stringify(doc.body));
});
```

#### Update

When doing an update to a doc, you must include the revision.

```javascript

doc.body.somechange = "hi mom";

rnsync.update(doc.id, doc.rev, doc.body, function(error, doc)
{
  console.log(JSON.stringify(doc.body));
});
```

#### Delete

```javascript

rnsync.delete(doc.id, function(error)
{
  console.log(error);
});
```

#### Replicate

All of the CRUD functions only affect the local database.  To push your changes to the remote server you must replicate.  For more details see the [replication docs](https://github.com/cloudant/CDTDatastore/blob/master/doc/replication.md)

Push your local changes to the remote database
```javascript
rnsync.replicatePush( error => console.log(error) );
```

Pull changes from the remote database to your local
```javascript
rnsync.replicatePull( error => console.log(error) );
```

Do both a push and a pull
```javascript
rnsync.replicateSync( error => console.log(error) );
```
#### Find

Query for documents.  For more details on the query semantics please see the [Cloudant query documentation](https://github.com/cloudant/CDTDatastore/blob/master/doc/query.md)

```javascript
var query = {name: 'John', age: { '$gt': 25 }};

rnsync.find(query, function(error, docs)
{
  console.log('found ' + docs.length);
});
```

#### Create Indexes

```javascript
var indexes = {
  "TEXT":{"textNames":["Common_name","Botanical_name"]},
  "JSON":{"jsonNames":["Common_name","Botanical_name"]}
};

rnsync.createIndexes(indexes, function(error)
{
  console.log(error);
});
```

## Usage with redux-persist

```javascript
import { createStore } from 'redux'
import reducer from './redux/reducers/index'


import {persistStore, autoRehydrate} from 'redux-persist'
import rnsync, {rnsyncStorage} from 'rnsync'


let dbUrl = "https://xxx:xxx-bluemix.cloudant.com";
let dbName = "rnsync";

rnsync.init(dbUrl, dbName, error => console.log(error) );

const store = createStore(reducer, undefined, autoRehydrate());

persistStore(store, {storage: rnsyncStorage});
```

If you want to do replication before loading the store then:
```javascript
rnsync.replicateSync().then(() => persistStore(store, {storage: rnsyncStorage}));
```

It is up to you to decide when and where to do replication.  Later I will add the ability automatically do a replication push when data changes (from a whitelist you pass to rnsyncStorage.)

## Author

Patrick Cremin, pwcremin@gmail.com

## License

RNSync is available under the MIT license. See the LICENSE file for more info.
