package webmodelica.services

import com.google.inject.Inject
import com.twitter.finatra.json.FinatraObjectMapper
import com.twitter.util.{Future, FuturePool, Time}
import com.twitter.finagle.stats.StatsReceiver
import webmodelica.UUIDStr
import webmodelica.models.config.WMConfig
import webmodelica.models.{Project, Session}

import scala.collection.concurrent

trait SessionRegistry extends com.twitter.util.Closable {
  def create(p:Project): Future[(SessionService, Session)]
  def get(id:UUIDStr): Future[Option[SessionService]]
  def killSession(id:UUIDStr): Future[Unit]
}

class SessionRegistryImpl @Inject()(conf:WMConfig,
  statsReceiver:StatsReceiver)
  extends SessionRegistry
    with com.twitter.inject.Logging
    with com.twitter.util.Closable {

  private val lock:java.util.concurrent.locks.Lock = new java.util.concurrent.locks.ReentrantLock()
  private val registry = concurrent.TrieMap[UUIDStr, SessionService]()

  private def sync[A](f: => A): A = {
    try {
      lock.lock()
      f
    } finally {
      lock.unlock()
    }
  }

  override def create(p:Project): Future[(SessionService, Session)] = FuturePool.unboundedPool { sync {
    val s = Session(p)
    info(s"creating session $s")
    val service = new SessionService(conf.mope, s, conf.redis, statsReceiver)
    registry += (s.idString -> service)
    (service, s)
  }}

  override def get(id:UUIDStr): Future[Option[SessionService]] = FuturePool.unboundedPool { sync{ registry.get(id) } }

  override def killSession(id:UUIDStr): Future[Unit] = {
    sync { registry.remove(id) } match {
      case Some(service) =>
        info(s"killing session $id")
        service.close(Time.fromSeconds(60))
      case None =>
        warn(s"session $id not found, we aren't killing it.")
        Future.value(())
    }
  }

  override def close(deadline:Time):Future[Unit] =
    Future.collect(this.registry.values.map(_.close(deadline)).toList).map(_ => ())
}
