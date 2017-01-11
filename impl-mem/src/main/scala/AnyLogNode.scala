package impl.calot

import java.time.Instant

import threeSleeves.StreamsAPI.UID

/*
* Common features of 'KeylessLogNode' and 'KeyedLogNode'
*/
abstract class AnyLogNode[R](created: Tuple2[UID,Instant]) extends AnyNode(created) {

  type Marshaller = R => Array[Byte]
  type Unmarshaller = Array[Byte] => R
}
