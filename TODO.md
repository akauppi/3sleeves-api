# Todo

- `README` needs some pictures to make the page lighter to read
 
- mention in `README` what we expect of the implementations:

||persistent|resilient|replicated| 
|---|---|---|---|
|Kafka|Y|Y|Y|
|DistributedLog|Y|Y|Y|
|MongoDB|Y|Y|Y|
|our mem-plain|N|N|N|
|our node.js|Y|N?|N|

Data content is opaque to us, but e.g. Three Sleeves Configuration API will use HOCON.


---
 
- Akka Streams interface
  - started

- plain in-memory implementation (Akka Streams)

- Websocket -> Akka Streams converter

- plain node.js implementation (Websocket)

- Akka Streams -> Websocket converter

---

- Kafka, DistributedLog and MongoDB implementations...

Note: The main focus of Asko (the original author) is to work on the interface. If you are more interested in the implementation, please say so and take lead on those.

## Open issues

Write may fail - how to communicate that over Websockets?

- it will fail if the storage space quota is exceeded. If we simply ignore this (and e.g. not have quotas) there won't be a problem. But quotas would be nice to allow multiple teams use a system, independently, within the agreed bounds.

- Any such quota system is likely to arise from the implementation: if the implementation supports quotas, we should be compatible with them. We cannot enforce quotas since we are not in charge of the retention, that may be used to free up space.
