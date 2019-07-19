var rnsyncModule = require( 'react-native' ).NativeModules.RNSync;
import { Platform } from 'react-native';

const noop = () =>
{
};

export class RNSyncStorage {

    constructor(datastoreName) {
        this.datastoreName = datastoreName
    }

    setItem ( key, value, callback )
    {
        callback = callback || noop;

        // value is a string, but we need a data blob
        let body = { value }

        rnsyncModule.retrieve( this.datastoreName, key, ( error, doc ) =>
        {
            if ( error )     // should be 404
            {
                rnsyncModule.create( this.datastoreName, body, key, callback );
            }
            else
            {
                rnsyncModule.update( this.datastoreName, doc.id, doc.key, body, callback );
            }
        } );
    }

    getItem( key, callback )
    {
        callback = callback || noop;

        rnsyncModule.retrieve( this.datastoreName, key, ( error, doc ) =>
        {
            let item = error ? null : doc.body.value;

            callback( error, item );
        } );

    }

    removeItem( key, callback )
    {
        callback = callback || noop;

        rnsyncModule.delete( this.datastoreName, key, callback );
    }

    getAllKeys( callback )
    {
        callback = callback || noop;

        // using _id as the field isn't right (since the body doesn't contain an _id) but
        // it keeps the body from returning since the field doesn't exist
        // TODO try ' '?
        rnsyncModule.find( this.datastoreName, {'_id': {'$exists': true } }, ['_id'], ( error, docs ) =>
        {
            if ( error )
            {
                callback( error );
                return;
            }

            if ( Platform.OS === "android" )
            {
                docs = docs.map( doc => JSON.parse( doc ) )
            }

            let keys = docs.map( doc =>
            {
                return doc.id
            } )

            callback( null, keys );
        } );
    }

    deleteAllKeys( callback )
    {
        this.getAllKeys( ( error, keys ) =>
        {
            if ( error )
            {
                callback( error )
            }
            else
            {
                for ( let i = 0; i < keys.length; i++ )
                {
                    let key = keys[ i ];
                    this.removeItem( key )
                }

                callback( null )
            }

        } )
    }
}

export class RNSync
{
    init ( cloudantServerUrl, datastoreName, callback )
    {
        this.databaseUrl = cloudantServerUrl + '/' + datastoreName
        this.databaseName = datastoreName
    }

    init( callback )
    {
        return new Promise( ( resolve, reject ) =>
        {
            const databaseUrl = cloudantServerUrl + '/' + datastoreName;

            rnsyncModule.init( databaseUrl, datastoreName, error =>
            {
                callback( error );
                if(error) reject(error);
                else resolve(null)
            } );
        } )
    }

    initFromFile( cloudantUrl, databaseName, dbDump)
    {
        this.databaseUrl = cloudantUrl + '/' + databaseName
        this.databaseName = databaseName

        return new Promise( ( resolve, reject ) =>
        {
            rnsyncModule.initFromFile( this.databaseUrl, this.databaseName, dbDump, error =>
            {
                error == null ? resolve() : reject( error )
            } )
        } )
    }

    close( datastoreName, callback )
    {
        return new Promise( ( resolve, reject ) =>
        {
            rnsyncModule.close(datastoreName, error =>
            {
                if (callback) callback( error );
                if(error) reject(error);
                else resolve(null)
            } );
        } )
    }

    compact( datastoreName, callback )
    {
        return new Promise( ( resolve, reject ) =>
        {
            rnsyncModule.compact(datastoreName, error =>
            {
                if (callback) callback( error );
                if(error) reject(error);
                else resolve(null)
            } );
        } )
    }

    create ( datastoreName, body, id, callback )
    {
        callback = callback || noop;

        if ( typeof(body) === 'string' && typeof(id) === 'function' )
        {
            callback = id;

            id = body;

            body = null;
        }
        else if ( typeof(body) === 'function' )
        {
            callback = body;

            body = id = null;
        }

        if ( typeof(id) === 'function' )
        {
            callback = id;

            id = null;
        }

        return new Promise( ( resolve, reject ) =>
        {
            rnsyncModule.create( datastoreName, body, id, ( error, doc ) =>
            {
                callback( error, doc );
                error == null ? resolve( doc ) : reject( error )
            } );
        } )
    }

    retrieve ( datastoreName, id, callback )
    {
        callback = callback || noop;

        return new Promise( ( resolve, reject ) =>
        {
            rnsyncModule.retrieve( datastoreName, id, ( error, doc ) =>
            {
                callback( error, doc );

                error == null ? resolve( doc ) : reject( error )
            } );
        } )
    }

    // The callback success value is an object where the keys are the attachment names,
    // and the values are the base64 encoded attachments
    retrieveAttachments ( datastoreName, id, callback )
    {
        callback = callback || noop;

        return new Promise( (resolve, reject) =>
        {
            rnsyncModule.retrieveAttachments( datastoreName, id, ( error, attachments ) =>
            {
                callback( error, attachments );
                if(error) reject(error);
                else resolve(attachments)
            } );
        })
    }

    findOrCreate ( datastoreName, id, callback )
    {
        callback = callback || noop;

        return new Promise( ( resolve, reject ) =>
        {
            rnsyncModule.retrieve( datastoreName, id,  ( error, doc ) =>
            {
                if ( error === 404 )
                {
                    this.create( datastoreName, id, ( error, doc ) =>
                    {
                        callback( error, doc );

                        error == null ? resolve( doc ) : reject( error )
                    } )
                }
                else
                {
                    callback( error, doc );

                    error == null ? resolve( doc ) : reject( error )
                }
            } );
        } )
    }

    update ( datastoreName, id, rev, body, callback )
    {
        callback = callback || noop;

        if ( typeof(id) === 'object' )
        {
            var doc = id;
            id = doc.id;
            rev = doc.rev;
            body = doc.body;
        }

        return new Promise( ( resolve, reject ) =>
        {
            rnsyncModule.update( datastoreName, id, rev, body, ( error, doc ) =>
            {
                callback( error, doc );

                error == null ? resolve( doc ) : reject( error )
            } );
        } )
    }

    delete ( datastoreName, id, callback )
    {
        callback = callback || noop;

        if ( typeof(id) === 'object' )
        {
            id = id.id; // doc.id
        }

        return new Promise( ( resolve, reject ) =>
        {
            rnsyncModule.delete( datastoreName, id, ( error ) =>
            {
                callback( error );
                error == null ? resolve() : reject( error )
            } );
        } );

    }

    replicateSync ( datastoreName, delaySeconds, callback )
    {
        callback = callback || noop;

        return new Promise( ( resolve, reject ) =>
        {
            rnsyncModule.replicateSync( datastoreName, delaySeconds, (error) =>
            {
                if(error) reject(error);
                else resolve();
            })
        });
    }

    replicatePush ( datastoreName, delaySeconds, callback )
    {
        callback = callback || noop;

        return new Promise( ( resolve, reject ) =>
        {
            rnsyncModule.replicatePush( datastoreName, delaySeconds, (error) =>
            {
                if(error) reject(error);
                else resolve();
            })
        });
    }

    replicatePull ( datastoreName, delaySeconds, callback )
    {
        callback = callback || noop;

        return new Promise( ( resolve, reject ) =>
        {
            rnsyncModule.replicatePull( datastoreName, delaySeconds, (error) =>
            {
                if(error) reject(error);
                else resolve();
            })
        });
    }

    replicateIos ( datastoreName, callback )
    {
        callback = callback || noop;

        return new Promise( ( resolve, reject ) =>
        {
            rnsyncModule.replicatePull( datastoreName, (error, msg) =>
            {
                callback( error, msg );
                if(error) reject(error);
                else resolve(msg)
            })
        });
    }


    readAll ( datastoreName, callback )
    {
        callback = callback || noop;

        return new Promise( ( resolve, reject ) =>
        {
            rnsyncModule.readAll( datastoreName, ( error, docs ) =>
            {
                if ( !error && Platform.OS === "android" )
                {
                    docs = docs.map( doc => JSON.parse( doc ) )
                }

                callback( error, docs );

                error == null ? resolve( docs ) : reject( error )
            } );

        } );
    }

    // For how to create a query: https://github.com/cloudant/CDTDatastore/blob/master/doc/query.md
    // The 'fields' argument is for projection. It's an array of fields that you want returned when you do not want the entire doc
    find ( datastoreName, query, fields, callback )
    {
        callback = callback || noop;

        if ( typeof(fields) === 'function' )
        {
            callback = fields;
            fields = null;
        }

        return new Promise( ( resolve, reject ) =>
        {
            rnsyncModule.find( datastoreName, query, fields, ( error, docs ) =>
            {
                if ( !error && Platform.OS === "android" )
                {
                    docs = docs.map( doc => JSON.parse( doc ) )
                }

                callback( error, docs );

                error == null ? resolve( docs ) : reject( error )
            } );

        } );
    }
}


// TODO This class exist only for the purpose of not screwing up backawards compat.  Should go away in next major release
class RNSyncWrapper extends RNSync
{
    constructor()
    {
        super()
    }

    init( cloudantUrl, databaseName, callback )
    {
        this.databaseUrl = cloudantUrl + '/' + databaseName
        this.databaseName = databaseName

        callback = callback || noop;

        return new Promise( ( resolve, reject ) =>
        {
            rnsyncModule.init( this.databaseUrl, this.databaseName, error =>
            {
                callback( error )

                error == null ? resolve() : reject( error )
            } )
        } )
    }

    /*
     * Indexes is of the form:
     * {"TEXT":{"textNames":["Common_name","Botanical_name"]},"JSON":{"jsonNames":["Common_name","Botanical_name"]}}
     */
    createIndexes ( datastoreName, indexes, callback )
    {
        callback = callback || noop;

        return new Promise( (resolve, reject) =>
        {
            rnsyncModule.createIndexes( datastoreName, indexes, ( error ) =>
            {
                callback( error );
                if(error) reject(error);
                else resolve()
            } );
        });
    }

    deleteStore ( datastoreName, callback )
    {
        callback = callback || noop;

        return new Promise( (resolve, reject) =>
        {
            rnsyncModule.deleteStore( datastoreName, (error) =>
            {
                callback( error );
                if(error) reject(error);
                else resolve()
            } );
        });
    }
}

export default new RNSyncWrapper();
