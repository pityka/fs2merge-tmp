```
[info] Running TryFS2
Pure stream
acquire 1
pull stream 1: a
pull stream 1: c
pull stream 1: e
pull stream 1: g
acquire 2
pull stream 2: b
pull stream 2: d
pull stream 2: f
pull stream 2: h
emit a
emit b
emit c
emit d
emit e
emit f
emit g
release 1
emit h
release 2


Reading from file
pull stream 1: a
pull stream 1: c
pull stream 1: e
pull stream 2: b
pull stream 2: d
pull stream 2: f
emit a
emit b
emit c
emit d
emit e
finalize stream 2
pull stream 1: g
emit f
finalize stream 1
```
