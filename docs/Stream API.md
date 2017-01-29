# Stream API

## Design 

Keyless and keyed streams are conceptually separated, (even though some implementations such as Kafka would treat them rather alike), since their usage patterns are different.

Keyless streams may support retention time / space limits in the back-end. 

Keyed streams may support compaction in the back-end.

## Akka Streams API

## Websockets API

