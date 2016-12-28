# Todo

- `design/`: describing the intended API (Websocket)

- `README` needs some pictures to make the page lighter to read
 
- mention in `README` what we expect of the implementations:

||persistent|replicated| 
|---|---|---|
|Kafka|Y|Y|
|DistributedLog|Y|Y|
|MongoDB|Y|Y|

Data content is opaque to us, but e.g. Three Sleeves Configuration API will use HOCON.


---
 
- Akka Streams interface

- plain in-memory implementation (Akka Streams)

- Websocket -> Akka Streams converter

- plain node.js implementation (Websocket)

- Akka Streams -> Websocket converter

---

- Kafka, DistributedLog and MongoDB implementations...

Note: The main focus of Asko (the original author) is to work on the interface. If you are more interested in the implementation, please say so and take lead on those.
