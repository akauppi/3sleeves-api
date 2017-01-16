package impl.calot

import java.time.Instant

import threeSleeves.StreamsAPI.UID

/*
* Common features of 'KeylessLogNode' and 'KeyedLogNode'
*/
abstract class AnyLogNode(created: Tuple2[UID,Instant]) extends AnyNode(created)

object AnyLogNode {
  abstract class Status extends AnyNode.Status
}
