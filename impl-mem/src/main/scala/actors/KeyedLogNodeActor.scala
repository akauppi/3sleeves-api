package impl.calot.actors

import threeSleeves.StreamsAPI.UID

/*
* Log actor with keyed data
*
* - no size or time-wise retention
* - can support compaction (though we don't)
*
* Note: We allow reading from any position (for auditing needs, for example), but it only makes sense to read from
*     beginning, aka earliest kept position.
*/
private [calot]
class KeyedLogNodeActor(creator: UID) extends LogNodeActor[KeyedLogNodeActor.Record](creator)

private [calot]
object KeyedLogNodeActor {

  // Record for reading
  //
  class Record(key: String, data: Array[Byte])
}
