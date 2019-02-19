package webmodelica.core

import webmodelica.constants.confDefault
import webmodelica.models.config._
import webmodelica.models._
import webmodelica.services.TokenGenerator
import org.slf4j.LoggerFactory;
import org.mongodb.scala._
import java.security.MessageDigest

trait ModuleLifecycle {
  def startup():Unit
  def shutdown():Unit
}

trait WMModule
    extends ModuleLifecycle
    with DocumentWriters {

  def environment:String
  def configFile:String
  lazy val log = LoggerFactory.getLogger("WMModule")

  lazy val config: WMConfig = {
    import com.typesafe.config.ConfigFactory
    import webmodelica.models.config.WMConfig
    import webmodelica.models.config.configReaders._
    import pureconfig.generic.auto._
    val rootConfig =
      if(configFile == confDefault) ConfigFactory.load("webmodelica.conf")
      else ConfigFactory.parseFile(new java.io.File(configFile))
    val conf = pureconfig.loadConfigOrThrow[WMConfig](rootConfig.getConfig(environment))
    log.info(s"config loaded: $conf")
    conf
  }

  def dbConfig:MongoDBConfig = config.mongodb
  def mopeConfig:MopeClientConfig = config.mope

  lazy val mongoClient:MongoClient = MongoClient(dbConfig.address)
  lazy val mongoDB:MongoDatabase =
    mongoClient.getDatabase(dbConfig.database).withCodecRegistry(codecRegistry)

  lazy val tokenGenerator: TokenGenerator = new TokenGenerator(config.secret)
  lazy val digestHasher: MessageDigest = MessageDigest.getInstance("SHA-256")

  override def startup():Unit = { val _ = mongoClient }
  override def shutdown():Unit = { mongoClient.close() }

}
