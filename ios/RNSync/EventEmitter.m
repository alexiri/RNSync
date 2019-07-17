#import "EventEmitter.h"

@implementation EventEmitter
{
  bool hasListeners;
}

RCT_EXPORT_MODULE();

// Will be called when this module's first listener is added.
-(void)startObserving {
    hasListeners = YES;
    // Set up any upstream listeners or background tasks as necessary
}

// Will be called when this module's last listener is removed, or on dealloc.
-(void)stopObserving {
    hasListeners = NO;
    // Remove upstream listeners, stop unnecessary background tasks
}

// List of events that JS expects.
- (NSArray<NSString *> *)supportedEvents
{
  return @[@"rnsyncDocumentCreated", @"rnsyncDocumentUpdated", @"rnsyncDocumentDeleted",
        @"rnsyncDatabaseOpened", @"rnsyncDatabaseClosed", @"rnsyncDatabaseCreated", @"rnsyncDatabaseDeleted",
        @"rnsyncReplicationCompleted", @"rnsyncReplicationFailed"
  ];
}

- (void)documentCreated:(NSNotification *)notification
{
  NSString *eventName = notification.userInfo[@"name"];
  if (hasListeners) { // Only send events if anyone is listening
    [self sendEventWithName:@"rnsyncDocumentCreated" body:@{@"name": eventName}];
  }
}

@end