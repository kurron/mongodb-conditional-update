# Overview
This project is an experiment to see if MongoDB can help with a particular scenario.
I have an application that processes events from a stream, transforming the events
into MongoDB documents.  The documents represent the current state of an object,
requiring that only the latest information be retained.  The events, for various
reasons, can arrive out of sequence.  The goal is to derive the correct state
regardless of the sequence the events are processed.  My solution is to introduce
a [fencing token](https://martin.kleppmann.com/2016/02/08/how-to-do-distributed-locking.html)
into the document and use that to prevent stale data from getting written to the
collection.

We'll be testing by using a simple document that is designed to be easily verified.
It'll be comprised of the following fields:

* id - the primary key to the document
* fencing token - a sequnce number that indicates the order of the events
* state - a simple field the represents the current state of the document
* access list - an array that holds the ids of the events that triggered an update
to the document.  This exists for verification purposes.

## Tested Scenarios

* properly sequenced processing
* completely reversed processing
* randomly shuffled events

## Technology Stack

* [MongoDB 3.4.6 SaaS](https://cloud.mongodb.com/)
* [Spring Data MongoDB 1.10.4](http://projects.spring.io/spring-data-mongodb/)
* [Groovy 2.4.12](http://groovy-lang.org/)
* [Spock 1.1-groovy-2.4](http://spockframework.org/)
* [Azul's Zulu JDK 1.8.0_131](http://zulu.org/)
* [Ubuntu 16.04](https://ubuntu.com/)
* [Gradle 4.0.1](https://gradle.org/)

# Results
As it turns out, you can meet the scenario requirements but, due to the nature of
`findAndModify`, you have to include at least an additional query.  Since events
can arrive out of sequence, the processor can't assume that the document is already
in the database and relies on upsert behavior.  This normally works great.  When
combined with a conditional update based on a fencing token, things get interesting.

Assume a clean database and the update says "find me a document with this id and a fencing token
value less than this".  The query fails, triggering the upsert ,and the document is added to
the database.  Perfect.  Lets say the current fencing token is 10 and the next event comes
in with a value of 5.  When the processor says "find me a document with this id and a fencing
token value less than 5", the find fails and the **upsert logic kicks in again**, causing
a duplicate key error.  The second time through, MongoDB thinks we want to insert another
document, which we don't.  The naive, but functional, solution is to trap the error and
assume that the duplicate key exception was because of the undesired insert and not some
other reason.  To be conservative, try again but this time *without upsert enabled*.  The find
will fail again but, since the upsert has been disabled, nothing gets written to the database
and no exceptions are thrown.  In short, conditional *updates* work as expected but conditional
*upserts* do not.

An optimization that can be made, at the expense of an additional trip to the database, is
to do a `findOne` prior to the update and use that information to appropriately set the
upsert flag.  If no document matches the id, upsert is on.  If a document using that id
already exists, upsert is off.  Considering that we making an indexed query, the lookup
time is negligible but the network round trip might be significant.

A further optimization that can be made, at the expense of some RAM, is to maintain a lookup table
that tracks whether or not upsert should be used.  In this scenario, only a
single additional  database hit is required -- that initial "is there a document already"
query.

In a concurrent environment, the upsert map will need to be thread-safe but that is easily
achieved.  In a distributed environment, where multiple processes are competing to update
the same document, the try/catch/retry model needs to be reintroduced.  Assuming a clean
database and two processes working on an event targetting the same document, Process A
and Process B both will see that Document 1 is not in the database and will enable upserting.
One of the two will "win". Lets assume A does.  B will get a duplicate key exception because
A has beat him to the punch and inserted the document.  B will need to trap that and try
again in update mode.

When replica sets are in play, the try/catch/retry form will also be required.  Due to the
replica lag, it is possible that a processor could ask the replica if the document exists
and get a "no it doesn't" response.  Meanwhile, the document *has* been inserted on the
primary node but the change has yet to arrived at the replica.  When the update is attempted,
with the upsert flag enabled, a duplicate key exception will be thrown because the document
exists.

As expected, the quickest scenario to process is where the events arrive in reverse order.
Since the most current event arrives first, the remaining events result in nothing being
modified in the database.

The in-order scenario was the slowest.  Since the most current event arrives last,
each event results in a database modification.  The conditional update does not
end up providing any benefit in this situation.

When event sequencing was randomized, the performance was much better.  I expected it to be
right in the middle of the previous scenarios. Instead, it turned out to take only
roughly one-third the time of the sequential processing scenario. This could be that
I didn't run enough tests to get more accurate results so take this with
a grain of salt.

In summary, if it is possible to know that a document already exists in the database,
conditional updates work great.  If you cannot assume the existence of the document, then
some orchestration is required.  The logic isn't complex but it does make things a little less
efficient.

# Guidebook
Details about this project are contained in the [guidebook](guidebook/guidebook.md)
and should be considered mandatory reading prior to contributing to this project.

# Prerequisites

# Building

# Installation

# Tips and Tricks

# Troubleshooting

# Contributing

# License and Credits
This project is licensed under the [Apache License Version 2.0, January 2004](http://www.apache.org/licenses/).

# List of Changes

