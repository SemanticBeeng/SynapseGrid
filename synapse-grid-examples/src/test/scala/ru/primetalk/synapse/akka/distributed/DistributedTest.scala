///////////////////////////////////////////////////////////////
// © ООО «Праймтолк», 2014                                   //
// Все права принадлежат компании ООО «Праймтолк».           //
///////////////////////////////////////////////////////////////
/**
 * SynapseGrid
 * © Primetalk Ltd., 2014.
 * All rights reserved.
 * Authors: A.Zhizhelev
 *
 * Created: 19.05.14, zhizhelev
 */
package ru.primetalk.synapse.akka.distributed

import akka.actor.{ActorSystem, Props}
import akka.serialization.SerializationExtension
import com.typesafe.config.ConfigFactory
import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner
import org.slf4j.LoggerFactory
import ru.primetalk.synapse.core._
import ru.primetalk.synapse.akka._
import ru.primetalk.synapse.core.components.ComponentWithInternalStructure

@RunWith(classOf[JUnitRunner])
class DistributedTest extends FunSuite {

  /** The simple system that does nothing.
    */
  class NoOpSystem extends BaseTypedSystem("child") {
    val inChild = input[String]("inChild")
    val outChild = output[String]("outChild")

    protected override
    def defineSystem(implicit sb: SystemBuilder) {
      inChild.foreach(n => println("child.in: " + n))
      inChild.map("Hello, " + _) >> outChild
    }
  }

  /** The parent system contains NoOpSystem and does nothing except transferring data to
    * the child and getting the signal back. */
  class ParentSystem extends BaseTypedSystem {
    val in = input[String]("in")
    val out = output[String]("out")
    val child = new NoOpSystem

    protected override
    def defineSystem(implicit sb: SystemBuilder) {
      sb.addComponent(child.toActorComponent())
//      new ActorSystemBuilderOps().addActorSubsystem(child)
      in.foreach(n => println("parent.in: " + n))
      in >> child.inChild
      child.outChild >> out //possibly send answer output to sender of the original signal (or design rx-java interoperability). If send to sender - don't forget to store original sender.
      child.outChild.foreach(n => println("Answer: " + n))
    }
  }

  /** The simple channel has input contact that will be placed in one ActorSystem
    * and the output contact that will be in another.
    */
  class SimpleChannel extends BaseTypedSystem {
    val in = input[String]("in")
    val out = output[String]("out")

    protected override
    def defineSystem(implicit sb: SystemBuilder) {
      in >> out
    }

  }

  val parentSystem = new ParentSystem
  private val system = parentSystem.toStaticSystem
  val root: ComponentWithInternalStructure = new ActorComponent(system, defaultSupervisorStrategy)

  root.toDot().saveTo("root.dot")

  /**
   * 1. Construct grid with two subsystems (or, at least - two contacts).
   * 2. Construct two hosting ActorSystems.
   * 3. Create deployment descriptor.
   * 4. Deploy grid over two ActorSystems.
   * 5. Process a signal that should flow from one Actor system to another.
   * 6. Assert that a signal appear on output within timeout.
   */
  test("Two actor systems test") {
    val log = LoggerFactory.getLogger(getClass)
    val hostname = "127.0.0.1"

    val port1 = 2013
    val port2 = port1 + 2000

    val h1 = HostId(hostname, port1, "synapse1")
    val h2 = HostId(hostname, port2, "synapse2")

    val config = ConfigFactory.parseString(
      s"""
        |akka1 {
        |    akka.remote.netty.tcp.port=$port1
        |}
        |
        |akka2 {
        |    akka.remote.netty.tcp.port=$port2
        |}
        |
        |akka {
        |    actor {
        |        provider = "akka.remote.RemoteActorRefProvider"
        |        serializers {
        |            java = "akka.serialization.JavaSerializer"
        |            contacts = "ru.primetalk.synapse.akka.distributed.ContactsSerializer"
        |        }
        |        serialization-bindings {
        |            "java.lang.Object" = contacts
        |            #"ru.primetalk.synapse.core.Signal" = contacts
        |        }
        |    }
        |    remote {
        |        enabled-transports = ["akka.remote.netty.tcp"]
        |        netty.tcp {
        |            hostname = "127.0.0.1"
        |        }
        |    }
        |}
      """.stripMargin)

    val akka1 = ActorSystem.create(h1.actorSystemName, config.getConfig("akka1").withFallback(config))
    val akka2 = ActorSystem.create(h2.actorSystemName, config.getConfig("akka2").withFallback(config))

    ContactsMapExtension(akka1).setSystem(system)
    ContactsMapExtension(akka2).setSystem(system)
    val serialization1 = SerializationExtension(akka1)
    val serializer1 = serialization1.findSerializerFor(parentSystem.in)
    serializer1.toBinary(parentSystem.in)

    try {

      val realm = RealmDescriptor(root, Vector(
        List(List("ParentSystem")) -> h1.toActorPath,
        List(List("ParentSystem", "child")) -> h2.toActorPath,
        List(List()) -> h1.toActorPath
      ))

      val escalate = ru.primetalk.synapse.akka.defaultSupervisorStrategy
      //      val testing1 = akka1.actorOf(Props(new ClientTestingActor()), "client")
      // h1 contains root app system.

//      val parent1 =
        akka1.actorOf(Props(new HostActor(h1, realm, escalate, None)), h1.hostActorName)
//      val parent2 =
        akka2.actorOf(Props(new HostActor(h2, realm, escalate, None)), h2.hostActorName)

      log.info("start")
      Thread.sleep(1000)
      val selection = akka1.actorSelection(realm.getRouterPath(List("ParentSystem")))
      selection ! Signal(parentSystem.in, "World")
      Thread.sleep(4000)
      log.info("end")
    } finally {
      akka1.shutdown()
      akka2.shutdown()
    }
  }
}
