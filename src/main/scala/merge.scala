import fs2._
import fs2.util._

object TryFS2 extends App {

  def mergeSorted2[F[_], T: Ordering]: Pipe2[F, T, T, T] = { (s1, s2) =>
  val ord = implicitly[Ordering[T]]
  def goB(h1: Handle[F, T], h2: Handle[F, T]): Pull[F, T, Unit] =
    h1.receive1Option {
      case Some((t1, h1)) =>
        h2.receive1Option {
          case Some((t2, h2)) if ord.lteq(t1, t2) =>
            Pull.output1(t1) >> goB(h1, h2.push1(t2))
          case Some((t2, h2)) =>
            Pull.output1(t2) >> goB(h1.push1(t1), h2)
          case None =>
            h1.push1(t1).echo
        }
      case None =>
        h2.echo
    }

    s1.pull2(s2)((x, y) => goB(x, y))
  }

  {
    println("Pure stream")
    val acquire1 = Task.delay { println("acquire 1"); () }
    val acquire2 = Task.delay { println("acquire 2"); () }
    val release1 = Task.delay { println("release 1"); () }
    val release2 = Task.delay { println("release 2"); () }
    val stream1 =
      Stream.bracket(acquire1)(_ => Stream("a", "c", "e", "g"),
                               _ => release1).map{x => println("pull stream 1: "+x);x}
    val stream2 =
      Stream.bracket(acquire2)(_ => Stream("b", "d", "f", "h"), _ => release2).map{x => println("pull stream 2: "+x);x}

    val flat =  mergeSorted2(implicitly[Ordering[String]]).apply(stream1 ,stream2)
      .map { x =>
        println("emit " + x); x
      }
    assert(flat.runLog.unsafeRunSync == Right(Vector("a","b","c","d","e","f","g","h")))
  }

  {
    println("\n\nReading from file")
    val file1 = java.nio.file.Paths.get("testA")
    val file2 = java.nio.file.Paths.get("testB")

    Stream("a", "c", "e", "g")
      .covary[Task]
      .intersperse("\n")
      .through(text.utf8Encode)
      .through(io.file.writeAll(file1))
      .run
      .unsafeRunSync

    Stream("b", "d", "f", "h")
      .covary[Task]
      .intersperse("\n")
      .through(text.utf8Encode)
      .through(io.file.writeAll(file2))
      .run
      .unsafeRunSync

    val stream1 = io.file
      .readAll[Task](file1, 4096)
      .through(text.utf8Decode)
      .through(text.lines)
      .map { x =>
        println("pull stream 1: " + x); x
      }
      .onFinalize(Task.delay { println("finalize stream 1"); () })

    val stream2 = io.file
      .readAll[Task](file2, 4096)
      .through(text.utf8Decode)
      .through(text.lines)
      .map { x =>
        println("pull stream 2: " + x); x
      }
      .onFinalize(Task.delay { println("finalize stream 2"); () })

    val flat =
      mergeSorted2(implicitly[Ordering[String]])
        .apply(stream1, stream2)
        .map { x =>
          println("emit " + x); x
        }
    println(flat.runLog.unsafeRunSync)
  }


}
