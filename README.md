# RNSync

[![CI Status](http://img.shields.io/travis/Patrick Cremin/RNSync.svg?style=flat)](https://travis-ci.org/Patrick Cremin/RNSync)
[![Version](https://img.shields.io/cocoapods/v/RNSync.svg?style=flat)](http://cocoapods.org/pods/RNSync)
[![License](https://img.shields.io/cocoapods/l/RNSync.svg?style=flat)](http://cocoapods.org/pods/RNSync)
[![Platform](https://img.shields.io/cocoapods/p/RNSync.svg?style=flat)](http://cocoapods.org/pods/RNSync)

## Installation

Install with npm
```ruby
npm install --save rnsync
```

Edit your Podfile
```ruby
pod 'rnsync', :path => '../node_modules/rnsync'
```

Pod install
```ruby
pod install
```

## Usage

#### Connect
```javascript
var rnsync = require('rnsync');

// connect to your cloudant server
var dbUrl = "https://xxxxx";
var dbName = "name_xxxx";

rnsync.init(dbUrl, dbName, function(error)
{
  console.log(error);
}
```

#### Create

Both the object and the id are optional.  If you leave out the object, it will create a new doc that is empty.  If you leave
out the id that will be autogenerated for you.
```javascript
var object = {x:10};
var id = "whatever";

rnsync.create(object, id, function(error, docs)
{
  console.log(docs[0].id);
}
```

#### Retrieve

```javascript

var id = "whatever";
rnsync.retrieve(id, function(error, docs)
{
  console.log(JSON.stringify(docs[0].body));
}
```

#### Update

```javascript

doc.body.somechange = "hi mom";

rnsync.update(doc.id, doc.rev, doc.body, function(error, docs)
{
  console.log(JSON.stringify(docs[0].body));
}
```

#### Delete

```javascript

rnsync.update(doc.id, function(error)
{
  console.log(error);
}

## Author

Patrick Cremin, pwcremin@gmail.com

## License

RNSync is available under the MIT license. See the LICENSE file for more info.
