# Multi Channel Voice over IP (CS354)

Alexis Vincent - 18680666

## Introduction / Overview
The goal of this project was to implement a voip system that performed well across the local network (minimal lag / echo, with good voice
quality).

## Unimplemented Features
None

## Program Description
I chose to implement this voip system in Clojure (JVM based, modern dialect of LISP), as an exercise to learn the language.
Using Clojure lent itself to a simple reactive model where the server stored all state in a single map, and events 
(client connections / messages etc.), were used to reduce the state to something new. `(state, event) -> newState`.
Whenever a new state object was derived, the diff would be propagated to all clients. Since at all times, all clients
know the complete state of the system, there is no need to query information from the server, leading to a simpler design.

I had no separation between private calls and conference calling. My implementation simply had channels, with voice multiplexed
to all channel members. Thus a 'private' call was simply a channel with just two people in it.

I had a lot of fun building stream abstractions for voice on top of the clojure's `manifold` stream library. To start with,
each tcp endpoint was abstracted away as a duplex stream of discrete quantities of bytes. I then spliced these on to streams
that converted unserialized data structures into byte streams and vice versa. This was acheived using two libraries. A
serialization / deserialization library called `nippy`, and a byte protocol library called `gloss` (to deal with the fact
that tcp emits a stream of bytes, not discrete quantities). I now had the ability to send and receive arbitrary data structures 
over a stream API. 

Next I constructed various `manifold` stream abstractions over the Java Voice API. Including the ability to create `mic` and `playback`
streams that could be spliced together to get instant playback. Since all these streams were `manifold` streams, they could also be
split and mapped / filtered over. Clojure also has a sequence abstraction (lazy lists), and `manifold` streams are also valid `seqs`.
Thus recording a stream was as simple as reifying it into a list.

When a client constructed a local representation of a channel, it got back a duplex stream.
Where it could `put` in voice data from its own mic (simply by splicing on the `mic` capture stream), and that spat out streams,
which in turn spat out voice data. Thus it was a stream of voice streams. This was convenient as when a new client
joined the channel, the channel stream would simply spit out a new stream representing its `audio` stream. These individual `audio` 
streams were then spliced on to the `playback` stream as they arrived. 

As you can see, this turned out to be a very powerful abstraction to use, and made implementing conference calling simple.

Network communication was again as simple as splicing the audio streams on to the tcp stream, which handled the (de)serialization.

Since the server state was automatically replicated to all clients whenever it changed, messaging (including voice notes), was implemented
by simply adding the message to a list of messages in the channel on the server. The voice notes remained valid `manifold` streams 
through the serialization process.

## Additional Features Implemented
- Written in Clojure
- 90% pure functions

## Description of files
- natbox.core.`kernal`         - Bootstraps and wraps the state of the client and the server
- natbox.core.`server`         - Contains the server state and server specific handlers / functions
- natbox.core.`client`         - Contains the client state and client specific handlers / functions
- natbox.core.`util`           - Utility functions including prompt macro and stream abstractions
- natbox.core.`peer`           - Represents a peer (client), as a serializable data structure
- natbox.core.`cli`            - Simple function to enable a cli
- natbox.core.`state`          - Factory function to construct server state and for working with it
- natbox.core.`comms`          - Utilities to construct and interpret internal messages
- natbox.core.`audio`          - Functions for working with audio streams

## Experiments
There were no experiments to be performed, all functionality was extensively tested at a function level.

## Issues encountered
Before introducing additional threads, I struggled to get voice data to play simultaneously, as it turns out `Mixer`
lines are thread blocking.

## Significant Data Structures
In clojure you only have data structures and functions (No class abstractions etc.). This project was 
completely implemented using the following data structures:

- streams
- maps
- lists

## Compilation
run `lein uberjar`

## Libraries
- manifold - a compatibility layer for event-driven abstractions
- aleph - asynchronous communication (tcp), provided as manifold streams
- com.taoensso/nippy - high-performance serialization library
- byte-streams - seamlessly convert between different byte formats
- pandect - fast and easy-to-use Message Digest, Checksum and HMAC
- kovacnica/clojure.network.ip - network manipulation
- org.clojure/core.match - an optimized pattern matching library

## Execution
Start server on port 8000

`java -jar voip.jar server 8000`

Start client with hostname "alexisvincent" and connect to server at localhost:8000, 
use port 8001 to communicate with other peers

`java -jar voip.jar client localhost 8000 8001 alexisvincent`

