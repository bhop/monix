/*
 * Copyright (c) 2014-2018 by The Monix Project Developers.
 * See the project homepage at: https://monix.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package monix.execution.schedulers

import java.util.concurrent.TimeUnit

import cats.effect.IO
import minitest.SimpleTestSuite
import monix.execution.Cancelable
import monix.execution.ExecutionModel.{AlwaysAsyncExecution, SynchronousExecution}
import scala.concurrent.duration._
import scala.util.Success

object ReferenceSchedulerSuite extends SimpleTestSuite {
  class DummyScheduler(
    val underlying: TestScheduler = TestScheduler())
    extends ReferenceScheduler {

    def executionModel = monix.execution.ExecutionModel.Default
    def tick(time: FiniteDuration = Duration.Zero) = underlying.tick(time)
    def execute(runnable: Runnable): Unit = underlying.execute(runnable)
    def reportFailure(t: Throwable): Unit = underlying.reportFailure(t)
    def scheduleOnce(initialDelay: Long, unit: TimeUnit, r: Runnable): Cancelable =
      underlying.scheduleOnce(initialDelay, unit, r)
  }

  test("clockRealTime") {
    val s = new DummyScheduler
    val ws = s.withExecutionModel(SynchronousExecution)
    assert(ws.clockRealTime(MILLISECONDS) > 0)
  }

  test("clockMonotonic") {
    val s = new DummyScheduler
    val ws = s.withExecutionModel(SynchronousExecution)
    assert(ws.clockMonotonic(MILLISECONDS) > 0)
  }

  test("schedule with fixed delay") {
    val s = new DummyScheduler
    val ws = s.withExecutionModel(SynchronousExecution)
    var effect = 0

    val task = ws.scheduleWithFixedDelay(1.second, 2.seconds) { effect += 1 }

    s.tick(1.second)
    assertEquals(effect, 1)
    s.tick(2.seconds)
    assertEquals(effect, 2)
    s.tick(1.seconds)
    assertEquals(effect, 2)
    s.tick(1.seconds)
    assertEquals(effect, 3)
    s.tick(1.second)
    task.cancel()
    s.tick(1.second)
    assertEquals(effect, 3)
  }

  test("schedule at fixed rate") {
    val s = new DummyScheduler
    val ws = s.withExecutionModel(SynchronousExecution)

    var effect = 0
    val task = ws.scheduleAtFixedRate(1.second, 2.seconds) { effect += 1 }

    s.tick(1.second)
    assertEquals(effect, 1)
    s.tick(2.seconds)
    assertEquals(effect, 2)
    s.tick(1.seconds)
    assertEquals(effect, 2)
    s.tick(1.seconds)
    assertEquals(effect, 3)
    s.tick(1.second)
    task.cancel()
    s.tick(1.second)
    assertEquals(effect, 3)
  }

  test("change ExecutionModel") {
    val s = (new DummyScheduler).withExecutionModel(AlwaysAsyncExecution)
    assertEquals(s.executionModel, AlwaysAsyncExecution)
  }

  test("changed em triggers execution") {
    val s = new DummyScheduler
    val ws = s.withExecutionModel(AlwaysAsyncExecution)

    var effect = 0
    ws.executeAsync { () => effect += 1 }

    assertEquals(effect, 0)
    s.tick()
    assertEquals(effect, 1)
  }

  test("can change em multiple times") {
    val s = new DummyScheduler
    var ws = s.withExecutionModel(AlwaysAsyncExecution)
    for (_ <- 0 until 10000) ws = ws.withExecutionModel(AlwaysAsyncExecution)
    assertEquals(ws.executionModel, AlwaysAsyncExecution)
  }

  test("changed em triggers execution with delay") {
    val s = new DummyScheduler
    val ws = s.withExecutionModel(AlwaysAsyncExecution)

    var effect = 0
    ws.scheduleOnce(1.second) { effect += 1 }

    assertEquals(effect, 0)
    s.tick(1.second)
    assertEquals(effect, 1)
  }

  test("changed em error reporting") {
    val s = new DummyScheduler
    val ws = s.withExecutionModel(AlwaysAsyncExecution)

    val dummy = new RuntimeException("dummy")
    ws.executeAsync { () => throw dummy }

    s.tick()
    assertEquals(s.underlying.state.lastReportedError, dummy)
  }

  test("clock.monotonic") {
    val s = new DummyScheduler
    val clock = s.clock[IO]

    val clockMonotonic = clock.monotonic(MILLISECONDS).unsafeRunSync()
    assert(clockMonotonic > 0)
  }

  test("clock.realTime") {
    val s = new DummyScheduler
    val clock = s.clock[IO]

    val clockRealTime = clock.realTime(MILLISECONDS).unsafeRunSync()
    assert(clockRealTime > 0)
  }

  test("timer.sleep") {
    val s = new DummyScheduler
    val timer = s.timer[IO]

    val f = timer.sleep(10.seconds).unsafeToFuture()
    assertEquals(f.value, None)

    s.tick(5.seconds)
    assertEquals(f.value, None)

    s.tick(5.seconds)
    assertEquals(f.value, Some(Success(())))
  }

  test("contextShift.shift") {
    val s = new DummyScheduler
    val contextShift = s.contextShift[IO]

    val f = contextShift.shift.unsafeToFuture()
    assertEquals(f.value, None)

    s.tick()
    assertEquals(f.value, Some(Success(())))
  }

  test("contextShift.evalOn") {
    val s = new DummyScheduler
    val contextShift = s.contextShift[IO]
    val s2 = new DummyScheduler()

    val f = contextShift.evalOn(s2)(IO(1)).unsafeToFuture()
    assertEquals(f.value, None)

    s.tick()
    assertEquals(f.value, None)

    s2.tick()
    assertEquals(f.value, None)
    s.tick()
    assertEquals(f.value, Some(Success(1)))
  }
}