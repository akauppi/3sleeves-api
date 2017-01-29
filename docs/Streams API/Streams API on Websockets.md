# Streams API on Websockets

The Streams API allows persistent message buses to be used almost as if they are append-only filesystems.

We are provided:

- access rights to certain namespaces
  - settings per those namespaces
  - e.g. retention period (or lack thereof) is part of these settings

Changes to these things need to be done "behind the curtains" - the API does not provide means for doing them. This is partly to keep things simple, partly because such things vary a lot, partly in order to duck security concerns.

We support:

- creating new streams within pre-configured namespace that we have access rights to
- publishing (append one or more records)
- subscribing 
- subscribing to new streams within a namespace
- sealing (closing) a stream

Each of the above has its own access rights. It seems a good assumption that *we* might end up handling access rights altogether; let's see where this leads.

We don't support:

- deletion of a stream

A single stream can have multiple (or no) publishers and multiple (or no) subscribers.

At publishing, the API allows the client to provide a key for each record, that can be used in systems like Kafka that support stream compaction. However, any such use or lack thereof is up to the implementation and its configuration.

The Configuration API goes well beyond key/value use, but builds on top of the lower level Stream API. More about that separately.

Note: Within this file, we're using `&` consistently to mark URL parameters. In reality, the first one is preceded by `?` and the latter ones by `&` but that would be unnecessary finess and hard to make consistent when parameters can be optional. Forgive.


## Endpoints

### Creating new log or a path

```
POST /path[/...]/[log]
```

Creates either a path (node) or a log (leaf). Any upper, non-existing paths are also created.

Access requirements:

- `.create` scope for the path or log

Possible responses:

- `201` CREATED: the creation succeeded
- `200` OK: the node/log already existed
- `401` UNAUTHORIZED: not allowed to create

In the case of error, a Problem JSON object is returned, explaining the problem and suggesting how to remedy it.

Note that the difference between `200` and `201` return codes can be used by clients as an ownership claiming mechanism: each node can try to create their own log with a random name, and try again if the log existed.
 

### Publishing

We only support a Websocket interface, for simplicity (i.e. no REST API for publishing individual or a batch of records).

Any number of publishers may hold open websocket connections to a certain log, at any time, and records from such will be interlaced to the log.

<!-- disabled (we don't need atomic grouping, or do we? If we do, make the counter in websocket API handle it.

We'll need some way to tie records together, to be dealt with atomically. Maybe, for this reason, it makes sense to have a REST API interface that places all records in the log atomically.

```
PATCH /path[/...]/log

- Adds the body to the leaf log
- multi-form MIME type can be used for appending multiple entries
```
-->

```
wss://host:port/path[/...]/log?write[&autoCreate]
```

- author is provided by authentication scope `uid`
- in: original timestamp, payload, non-decreasing counter (optional)
- out: non-decreasing counter values that have been persisted (reached quorum in the back end)
  - not every counter value is necessarily echoed back, just the ones where persistence to quorum has happened

The non-decreasing counter is particular to the Websocket connection, and is not stored in the log.

<!-- disabled
It can be used for finding out which data is permanently stored, but also to group records together, atomically:

If adjacent records have the same counter (it does not increment), they are considered the same "step" in data and stored in the log atomically, without records from possible other producers interlacing them. 
-->

Access requirements:

- `.write` scope for the log

Possible responses:

- Websocket gets created
- `401` UNAUTHORIZED: not allowed
- `404` NOT FOUND: no such stream (and `autoCreate` was not given) (would have had access)


### Reading a log

```
wss://host:port/path[/...]/log[&read][&at=<offset|0|-1>]
```

Parameters:

- `at` provides an offset from which (inclusive) to start reading. If the offset is not available (the log has been pruned), an error will be provided. `-1` starts from the earliest existing data, without producing an error. The default is to start listening for new entries only.

Note: Not sure what should be done about the time aspect. Introducing a `since` parameter should refer to the origin time (because that's what matters to data), but origin times don't necessarily grow in order. Storage time would, but is likely of no importance to the consumer. 

It may be best to simply allow a consumer to start from "earliest available" and filter the data itself.

For each record, the following information is provided:

- offset (>=0, non-decreasing)
- origin timestamp
- author
- payload

Access requirements:

- `.read` scope for the log

Possible responses:

- Websocket gets created
- `401` UNAUTHORIZED: not allowed
- `404` NOT FOUND: no such stream (would have had access)

For errors, Problem JSON error object is provided in the body.


### Status

Getting a snapshot of the status of a log or path:

```
GET /path[/...]/[log][&filter=...[,...]]
```

For a log, this will give:

- smallest available offset (or next offset if log is empty)
- next offset
- who created the stream
- creation time
- retention time for records (can be infinite)
- retention space for records (can be infinite)
- who sealed the stream (if sealed)
- sealing time (if sealed)

Throughput and other such stuff is left for the metrics interface (later).

A `filter` parameter is useful to make the client explicitly state, which fields it wishes to receive. This way, we can return an error or behave in backwards compatible manner if the set of fields were to change. The default is to provide all current (non-deprecated) fields.

For a path, one gets:

- logs within the path (just their names)
- sub-paths
- who created the path
- creation time
- who sealed and when (if sealed)
- overall quota (if any); how much is used of it

Note: For application level metadata, we can easily use a normal stream for that, e.g. ".info" to tell who to contact etc. about the logs in the path. Such things don't need to be formulated within the Streams API.

Access requirements:

- `.status` scope for the log or path

Possible responses:

- `200` OK: existed, data provided
- `401` UNAUTHORIZED: not allowed
- `404` NOT FOUND: no such path or log (but would have been authorized)


### Watching a path

```
wss://host:port/path[/...]/[&at=<offset>|0][&filter=logs|paths]
```

The stream will carry information about the logs and sub-paths created under this path (not recursively).

The `at` parameter (similar to the one used in Status `GET`) allows to "rewind" the creation "stream": 0 lists all the logs from the oldest to the most recent (and continues streaming newcomers). The order is always the same, and the logs never get deleted (part of our design criteria; we need to think what to do if someone deletes them on the implementation side).

Without such a parameter one could get a directory listing, or otherwise "know" that there are 11 logs. Then, listening "from now on" could miss logs created between those two instances (listing and subscribing). By using `at=11` the client can be assured that it gets all announcements.

---
Note: A third way would be to *always* rewind from the beginning, i.e. always list all the entries. This might be the simplest way for implementing, and there's no real down side to it, except that applications only interested in new entries would need to skip the initial batch (we can provide it in one batch, and provide an empty batch if the directory is currently empty, so all they need to do is skip the first batch).

The information per log entry / sub-path can be:

- name

If the client wants more info, they should use the `status` call.

Access requirements:

- `.watch` scope for the path

Possible responses:

- Websocket opened: good
- `404` NOT FOUND: no such path
- `401` UNAUTHORIZED: not allowed

Again, `401` precedes over `404` (see above).


### Sealing

Sealing a log means no more writes are allowed to it. Possible readers of the log (the websocket connections) are closed.

Sealing a path means no new logs (or sub-paths) are allowed to be created within it. Possible watchers of the path (the websocket connections) are closed.

Note: Sealing is analogous to closing a file without being able to re-open it for writing.

```
PUT /path[/...]/[log]&seal
```

Note: The `PUT` makes sense because it is idempotent. A log or a path can be sealed multiple times, and the result is the same. `PATCH`, another method that could be used, is not idempotent by HTTP definitions.

Access requirements:

- `.seal` scope for the log or path

Possible responses:

- `200` OK: sealed (or was already sealed)
- `401` UNAUTHORIZED: not allowed
- `404` NOT FOUND: no such path (but would have been authorized)

Again, `401` precedes over `404` (see above).


## Access Scopes

Providing a unified authentication and access control mechanism as part of the Streams API is of huge value to the applications.

Currently, a reasonable amount of the boilerplate of creating a microservice comes from handling access control. If we place the access control closer to the data (in between the service and the data), this boilerplate largely vanishes. It's not the access to a service that really should be guarded - it's the access to the data that the service handles.

Furthermore, such a system should allow granular ability to provide or deny access, similar to how file systems.

My naive thinking on this (authentication and authorization are NOT my strongholds so comments and collaboration IS appreciated) is to use OAauth 2 and bearer tokens for the task.

( Btw, the state of authorization on the existing persistent message buses is *very* varying. Kafka has access control since version 0.9. DistributedLog does not seem to have any (yet). MongoDB obviously has. Handling the access control as part of the Streams API in a consistent manner seems crucial for getting a usable system created. Even after this, the Three Sleeves API and the back end can use another authentication mechanism to guarantee their roles, but the stream-using clients will not see this and assignment of client access rights is not likely happening there. )

We've mentioned the following scopes:

|Scope|Allows|
|---|---|
|something.*:create|creation of logs or sub-paths|
|something.*:write|writing to a log|
|something.*:read|reading from a log|
|something.*:status|getting information about a log or a path|
|something.*:watch|detecting new entries in a path|
|something.*:seal|sealing a log or a path|

The beginning of the scopes would be a pattern (maybe globs like in filenames), describing the affected paths and logs.

NOTE: It would be good to discuss this more in detail, with people who have practical experience of using authentication. Is bearer token (OAuth 2) a suitable approach, or should it be built on top of something else?

If the authentication approach fails, the overall Three Sleeves API has little appeal. Also this needs to hit right home.

### Managing access rights

Initial access rights can be provided by the Three Sleeves service's configuration. From there, it could incorporate Three Sleeves Configuration API (to be discussed separately, and building on top of this Streams API), to allow clients (who have access) change access rights, using the logging system itself as the storage.

This should work.

There is a certain recursive feeling to it, since the Configuration API would end up being both the user of the Streams API as well as a building block for managing its own access, but if it works, it might be elegant.

We need an actual test platform to see the practicalities.


## Deletion

The Streams API intentionally omits any deletion capabilities. We're seeing the message bus as immutable, except for deprecation time / size limit (optional).

<!-- disabled
Another approach could be to add an API for removing a path or a log, but not give anyone access rights to that (in an organization that does not want things to suddenly disappear).
-->

Traditional admin directly on the underlying back-end can be used to completely remove paths and logs. Doing so should be made relatively easy, e.g. with suitable scripts, if one has the access rights to deal with the engine directly.

Within the Stream API we do need to consider deletion of paths and/or logs from "underneath" of the system.

- write and read Websockets to streams that got deleted should be closed with an error
- watch Websocket could be used to inform of the deletions (if we can sniff it ourselves). Its `at` offset should not be strictly tied to the number of existing paths and logs, it's more important (and in style with the offset log approach) that each entry retains the same offset it once had. In a way, deletion of entries is slightly analogous to the compaction of Kafka logs (though here information is actually lost).


## Metrics

Metrics are not provided as a part of the API. Instead, they are collected and separately available in some monitoring system like Grafana.

Logging could be done on the console, or anything that suits the operational needs of the organization running the Streams API.

The main benefit of not using our own logging infrastructure for metrics and logging is that in case something is wrong, we'd never know, right?

For logs, we can track:

- number of kept records
- space taken by the records
- write bandwidth (records/sec)
- read bandwidth (records/sec)
 
For paths, we can track:

- used cumulative space
- quota (if any)


## Changing stream settings (retention time or size)

Retention time (or size) changes can be regarded as admin things, at least in the beginning, and done similarly under-the-hood as entry deletion.

Implications to the API is that status APIs should not cache this information but fetch it separately from the back-end each time needed. This shouldn't be a problem since normal operation (reading and writing streams) does not need to know about retention configuration.

Maybe, eventually, we could employ the Configuration API also in setting these characteristics by the users, themselves. For streams were this is not to be allowed, the configuration stream could be simply sealed.


## Using as (compacted) key/value store

Kafka has a "compaction" mode that allows entries in the queue that get later overwritten (based on their key value) to be skipped, thus allowing the queue to be seen as a key/value store.

This is a rather powerful feature and Kafka seems to plan to expose this dual characteristics (message bus, and key/value store) further in the future.

We can play to this feature, but also go way beyond it with the Configuration API, which can be implemented on top of the Streams API and is described in another document.


