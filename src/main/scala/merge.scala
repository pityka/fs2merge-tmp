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
    val acquireA = Task.delay { println("acquire A"); () }
    val acquireB = Task.delay { println("acquire B"); () }
    val releaseA = Task.delay { println("release A"); () }
    val releaseB = Task.delay { println("release B"); () }
    val streamA =
      Stream.bracket(acquireA)(_ => Stream(1, 3, 5, 7, 9, 11, 12),
                               _ => releaseA)
    val streamB =
      Stream.bracket(acquireB)(_ => Stream(-1, 0, 2, 4, 6, 8), _ => releaseB)

    val flat =  mergeSorted2(implicitly[Ordering[Int]]).apply(streamA, streamB)
      .map { x =>
        println("emit " + x); x
      }
    assert(flat.runLog.unsafeRunSync == Right(Vector(-1,0,1,2,3,4,5,6,7,8,9,11,12)))
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
        println("read from file A: " + x); x
      }
      .onFinalize(Task.delay { println("finalize file A"); () })

    val stream2 = io.file
      .readAll[Task](file2, 4096)
      .through(text.utf8Decode)
      .through(text.lines)
      .map { x =>
        println("read from file B: " + x); x
      }
      .onFinalize(Task.delay { println("finalize file B"); () })

    val flat =
      mergeSorted2(implicitly[Ordering[String]])
        .apply(stream1, stream2)
        .map { x =>
          println("emit " + x); x
        }
    println(flat.runLog.unsafeRunSync)
  }


}
