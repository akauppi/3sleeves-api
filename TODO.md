# Todo

- `README` needs some pictures to make the page lighter to read

Make the below into a table in `README`:

```
3Sleeves websocket client:
   Akka Streams (JVM) interface   | Akka Streams Config (JVM) interface
   -------------- (network hop)
3Sleeves websocket server:
   websocket Stream APIs (optional)  | websocket Config API (optional)
Config API:
                                   Akka Streams Config (JVM) interface
Streams API:
   Akka Streams (JVM) interfaces
impl-*:
   back-end implementations (implementing an interface)
   -------------- (network hop)
   back-end system
```

Testing can happen both on the Akka Streams (for implementations) as well as on the Websockets level, and further in the Akka Streams client level. 

Applications can choose any of these levels for their operation.

- mention in `README` what we expect of the implementations:

||persistent|resilient|replicated| 
|---|---|---|---|
|Kafka|Y|Y|Y|
|DistributedLog|Y|Y|Y|
|MongoDB|Y|Y|Y|
|our mem-plain|N|N|N|
|our node.js|Y|N?|N|

Data content is opaque to us, but e.g. Three Sleeves Configuration API will use HOCON.

- Revise docs with the separation to "keyed" and "keyless"

---
 
- plain in-memory implementation (Akka Streams)
  - started
 
- Websocket -> Akka Streams converter

- plain node.js implementation (Websocket)

- Akka Streams -> Websocket converter

---

- Kafka, DistributedLog and MongoDB implementations...

Note: The main focus of Asko (the original author) is to work on the interface. If you are more interested in the implementation, please say so and take lead on those.

## Design

- Mark somewhere (Websocket project?) about user access
  - That we want to provide access control, and do it in a uniform manner

## Dependencies

- Track [Akka Typed](http://doc.akka.io/docs/akka/current/scala/typed.html#typed-scala) and start using it once it's no longer experimental (i.e. when the chances of interface changes are small enough).


## Design / ideas

- could emulate something like symlinks. If we do the directory layer essentially as configuration, it would be merely a thing in there.


## Implementations

- [etcd v3](https://coreos.com/etcd/) by CoreOS
  - maybe just the Config API

- [Cherami](https://eng.uber.com/cherami/) by Uber

- [DistributedLog](http://distributedlog.incubator.apache.org/) by Twitter

- [Kafka](https://kafka.apache.org/) by Confluent (originally from LinkedIn)

## Open issues

Write may fail - how to communicate that over Websockets?

- it will fail if the storage space quota is exceeded. If we simply ignore this (and e.g. not have quotas) there won't be a problem. But quotas would be nice to allow multiple teams use a system, independently, within the agreed bounds.

- Any such quota system is likely to arise from the implementation: if the implementation supports quotas, we should be compatible with them. We cannot enforce quotas since we are not in charge of the retention, that may be used to free up space.
