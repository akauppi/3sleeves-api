package impl.calot

import akka.actor.ActorRef
import impl.calot.tools.RelPath

import scala.util.Try

/*
* Common base class for 'LogNode' and 'PathNode'.
*
* Helps the 'PathNodeActor' quite essentially, allowing it to deal with both children the same. The ground reason for
* this is to keep logs and subpaths from using the same name (akin to how one cannot have a directory and a file with
* the same name in common file systems, though for the computer there's no reason not to).
*/
abstract class AnyNode {

  //def ref: ActorRef
}
