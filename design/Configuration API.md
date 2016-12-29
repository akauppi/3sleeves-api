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


## Endpoint

```
wss://host:port/.conf/?from=/path[/...]/log[,...]
```

The Configuration API can be presented by a single endpoint that works as a proxy to one or more HOCON streams. These streams can use `include` to outsource parts of the overall configuration to other people, probably with different (lesser) scopes than the master configuration.

The output of the endpoint, as discussed above, is line-wise HOCON configuration strings.



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



