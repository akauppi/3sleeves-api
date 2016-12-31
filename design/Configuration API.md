# Configuration API

Without going much into the "why", let's just state that microservices need dynamic configuration that can be changed on-the-fly, without bringing such services unnecessarily down.

Current systems don't exhibit good solutions to this. Often, databases are used but they are rather heavy and lack tracking and cascading access rights (who's allowed to tune which parameter). Dedicated configuration services like `etcd` probably fall in this same category. Nice, but kind of flat.

What we are after is that, using the [Streams API](Streams API.md) and its access control, we can create a cascading, dynamic, immutable (traceable) configuration service that is exposed to applications as a stream of changing configuration.

Interested? Let's read on.


## Format and precedence

I like the [HOCON](https://github.com/typesafehub/config/blob/master/HOCON.md) format a lot, and would like to build on top of it, allowing it to get a temporal dimension.

Likely the format becomes a subset of HOCON, since e.g. environment variables are meaningless in a distributed scenario.

They way things would work (below is a proper use case):

1. One creates a log that is the master. Write 1 or more HOCON snippets in there. These form the configuration as if they occurred sequentially in a traditional configuration file.

For example:

```
a { b: 42 }
a.c = 30
```

This is the same as the configuration:

```
a { b: 42, c: 30 }
```

&#50;. One can use `include` to include other HOCON logs within the master:

```
a.more = include "/path/to/more.conf"
```

Now, the `a.more` contents start living a life all of their own, and the people having write access to `more.conf` HOCON stream can fully decorate it to their liking. However, they don't get access outside of their `a.more` domain.

The master can "prime" such configs by first giving default values, but what happens if the master adds values later? Whose values will win?

- others add `f: 7` within `more.conf`
- master adds `a.more.f: 10`

The use of `include` shows trust in the other party, and that shall prevail. The value in effect shall be `a.more.f: 7`, but if the other party subsequently removes their setting for `f` (by setting it to `null`), the last master value, `10`, takes effect.

So, in cascade:

- includes first, no matter when their values got updated
- after that, fallbacks from the including config

&#51;. How does the application see this?

To the application, configuration is passed as a stream of key, value pairs, like one-line HOCON.

e.g. the above steps (including the creation and removal of `f: 7`) would cause the following records:

```
a.b: 42
a.c: 30
a.more.f: 7
// master sets a.more.f: 10 here, but it does not cause an event
a.more.f: 10
```

The application only needs to parse values one by one. The right side of the colon is normal JSON types, plus HOCON duration and size, but no objects. Removal of a value is indicated by a `null`.

One can easily split such a stream by the key, and provide to each application entry that needs to react on the changed config just the stream of events that matter to it. Voíla!

Note that if the configuration setup is regarded sacred (so that not even the original author should change it), its stream can be sealed. The parts that are included can still be changed, but the structure and maybe some initial values are fixed. How much this is needed in practise remains to be seen, since it's about dynamic configuration, after all, but it's good to see the basic structures of Streams API being useful here.


## Endpoints

```
wss://host:port/.conf/?from=/path[/...]/log
```

The Configuration API can be presented by a single endpoint that works as a proxy to one or more HOCON streams. These streams can use `include` to outsource parts of the overall configuration to other people, probably with different (lesser) scopes than the master configuration.

The output of the endpoint, as discussed above, is line-wise HOCON configuration strings.

Multiple configuration strings must be deliverable as a single batch (details on how to do this are still open), since configuration entries may have interdependencies (think of e.g. changing a host and port separately - we don't want the application to have a state where it's using the old host but with new port).

In the beginning of consumption, the *whole* existing state shall be provided to the client *compacted*, i.e. only the currently applicable values listed, and each of them only once. This shall be done as a single batch. From there on, changes come in further batches.

If all the streams contributing to the configuration have been sealed, also the config Websocket connection will close (since there will be no more new values). In practise, this is likely not the case since the point is in the values changing, but we should test for those cases, nonetheless.

### No need for an 'at' parameter?

While the source streams used for a configuration can be rewound from any offset (they are expected not to be head-chopped (*)), rewinding a configuration stream is not supported. You would need to provide offsets of all the involved streams, in order to do that.

If we ever support `since` rewinding in normal streams, that can be supported also by configuration streams, to get back to historic configurations at certain times.

* (\*) There must be an English term for that? What's truncation that happens at the head, not the tail?


## Format particulars

- no environment variable expansion (behave the same as if those env.vars don't exist)
- comments (`//` and `#`) stipped as in HOCON (in the incoming entries)
- probably not going to allow newline (`\n`) so much in entries as HOCON does (use comma instead)
- like in HOCON, references in `include`d streams are done in two scopes:

> Substitutions in included files are looked up at two different paths; first, relative to the root of the included file; second, relative to the root of the including configuration.

This may be slightly unnecessary for us, but it's good to avoid surprises and follow the HOCON conventions as close as we can.

- unlike HOCON, we could provide an error if an included stream does not exist. This is likely a typo and good to detect fast (HOCON treats missing files as empty objects). This means we would not need the special `required` construct, either.

- like HOCON, we would not allow an unquoted include parameter. That is because then we would introduce possibility to dynamically change the whole stream, where includes are read from. HOCON does not, currently, do this. If there is a compelling reason to do otherwise, technically that is doable.
 
- as with HOCON, we can allow `include` params to be either relative (to the parent of the stream that was including them) or absolute. We naturally won't support classPath, or file includes, only streams.

- `includes` are limited to the one server. No global `wss://` links, not URL reading. 
 
We should be able to use the Typesafe Config machinery itself, in evaluating the configurations. This way, we don't have to duplicate their logic, and any corner cases would get processed the same way.


## Use as access control for Streams API

Let's take a practical example from assigning access rights to Streams API paths and logs.

### master.conf

```
/**/: 
```




<!-- disabled

## Use case

tbd. Make a nice, ice cream -based sample case where the owner of the company owns the master config and kiosks own their own.

Show some cross referencing and overrides, whatever feels catchy when reading the HOCON spec, again.


/myapp/master.conf:

```
kiosks: {
	"kamppi": include "/myapp/kamppi.conf"
	"stockmann": include "/myapp/stockmann.conf"
	}
prices: {
	"Fudgy": 5.60,
	"Smooth": 4.20
	}	
}
```

/myapp/kamppi.conf:

```
seats: 10
products: [
	"Fudgy",
	"Something else"
}
```

/myapp/stockmann.conf:

```
```
-->



---

## Extras

- When opening 