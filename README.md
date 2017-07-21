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

