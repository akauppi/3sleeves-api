# Three Sleeves API

This repo is a proposal and construction site for a common API for dealing with persistent message buses.

## But Why?

Persistent message buses are a rising trend. Currently (late 2016), they are still seen as high bandwidth solutions maybe because "that's where the money is". But there's more to them..

You can use the same abstraction, in very small scale, to remake the way we build microservices. This is only natural - the same kind of data binding thing happened in the desktop environments and is happening in the way we code web front ends. It's simply taking "functional programming" (streams, immutable data, mapping and so forth) to the empty space between the services.

### Implementations and interfaces

At least the following products have a feature set that can fulfill our requirements, and serve as implementation of the Three Sleeves API:

- [Kafka](https://kafka.apache.org/) 
- [DistributedLog](http://distributedlog.incubator.apache.org/)
- [MongoDB](https://www.mongodb.com/)
- ...

MongoDB is primarily a database, but its features are capable of being used as a persistent message bus for our purposes. So might some other products (Amazon SQS, RabbitMq, HornetQ) mentioned in Adam Warski's [blog post](http://www.warski.org/blog/2014/07/evaluating-persistent-replicated-message-queues/) from 2014.

One of the benefits of making such a common wrapping around these systems is to show which features, which abstractions, really matter, and if succesful the project can provide feedback to the implementations on what they can concentrate on; what use cases matter for people.


## Okay, how?

The initial purpose of this repo is to be a discussion site and playground for building the interface, and tests for it.

Subprojects that may be needed early on:

- Three Sleeves API in Akka Streams (interface, as a trait)
- Three Sleeves API in Websockets (documentation)
- Akka Streams -> Websockets conversion
- Websockets -> Akka Streams conversion
- In-memory implementation (no persistence, no replication)
- node.js Websockets implementation (persistence in local file system, no replication)

At this stage, the usefulness of the API should be demonstratable, and adapters to different underlying backbones (Kafka et.al.) can be crafted.

The outcome of this would be a proxy server, one that can be described as a combination of REST API and Websockets. 

Alternatively, applications can use the Akka Streams API directly (no proxies) and benefit from having the same API no matter which underlying system they choose to use. This is not unlike database abstraction layers we know and love and hate.

### Access rights

The different implementations (Kafka et.al.) are very different in their approaches, optimized use cases and naturally their APIs.

One thing where they differ is access control. Kafka provides built-in access control (since `0.9.0`) whereas DistributedLog has no such thing (in `0.4.0-incubator-SNAPSHOT`). MongoDB obviously has access control.

So it's not only a matter of APIs, it's also a matter of harmonizing the access controls. Because having meaningful, application oriented authentication is an essential part of the abstraction we want to create.

Who sent what? Do you have write access to a stream or just read? By keeping authentication close to data - from the microservice's point of view, *with* the data - we further simplify the service building, and can maybe help reduce the amount of time spent in security auditing application level code.


## The interface

Finally - what shall the interface itself look like?

See separate [design](design/README.md) folder.


## Is there more..?

Yes, there is. :)

Once we have the above interfaces, we can start looking at familiar problems that used to be difficult and simplify then.

Take distributed configuration. You have a microservice and it has a configuration. You want some people to be able to tune some parts of the configuration, but not all. You may want to also limit, who can read the existing config. You want to keep the trace of config changes (who, when and why) for traceability reasons. And you want to change configuration on-the-fly, without needing to restart the system.

I think we can do all of the above, with the Three Sleeves API. It's a common, reoccurring use case that by itself is worth solving.


## Contributing

All help, design insight and information spreading within your organization is welcome.

See [CONTRIBUTE.md](CONTRIBUTE.md) and [TODO.md](TODO.md).

### Background of Authors

- [Asko](https://twitter.com/askokauppi) works at Zalando Helsinki, assisting 10+ teams to get better at their work and to have less of it. He's programmed since the age of 12, and is focusing on Scala as the primary language since 2012. He first learned about persistent message buses in late 2014 and was immediately hooked. He's planning to change the world by introducing low-flying rail-bound electric public transportation. And houses that live for 1000 years or so. But that's after the Three Sleeves API is in production.

- (your name here)


## What's with the name...?

Three Sleeves is a reference to Season 1, Episode 11 of [A Very Secret Service](https://www.netflix.com/title/80097771), where a French agent gets a bathrobe with three sleeves. The redundancy saves his life.


## References

- [Evaluating persistent, replicated message queues (updated w/ Kafka)](http://www.warski.org/blog/2014/07/evaluating-persistent-replicated-message-queues/) (Blog, Jul 2014)

