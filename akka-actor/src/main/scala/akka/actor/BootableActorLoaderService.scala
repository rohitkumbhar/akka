/**
 * Copyright (C) 2009-2011 Typesafe Inc. <http://www.typesafe.com>
 */

package akka.actor

import java.io.File
import java.net.{ URL, URLClassLoader }
import java.util.jar.JarFile
import akka.util.Bootable
import akka.AkkaApplication

/**
 * Handles all modules in the deploy directory (load and unload)
 */
trait BootableActorLoaderService extends Bootable {

  def app: AkkaApplication

  val BOOT_CLASSES = app.AkkaConfig.BootClasses
  lazy val applicationLoader = createApplicationClassLoader()

  protected def createApplicationClassLoader(): Option[ClassLoader] = Some({
    if (app.AkkaConfig.Home.isDefined) {
      val DEPLOY = app.AkkaConfig.Home.get + "/deploy"
      val DEPLOY_DIR = new File(DEPLOY)
      if (!DEPLOY_DIR.exists) {
        System.exit(-1)
      }
      val filesToDeploy = DEPLOY_DIR.listFiles.toArray.toList
        .asInstanceOf[List[File]].filter(_.getName.endsWith(".jar"))
      var dependencyJars: List[URL] = Nil
      filesToDeploy.map { file ⇒
        val jarFile = new JarFile(file)
        val en = jarFile.entries
        while (en.hasMoreElements) {
          val name = en.nextElement.getName
          if (name.endsWith(".jar")) dependencyJars ::= new File(
            String.format("jar:file:%s!/%s", jarFile.getName, name)).toURI.toURL
        }
      }
      val toDeploy = filesToDeploy.map(_.toURI.toURL)
      val allJars = toDeploy ::: dependencyJars

      new URLClassLoader(allJars.toArray, Thread.currentThread.getContextClassLoader)
    } else Thread.currentThread.getContextClassLoader
  })

  abstract override def onLoad() = {
    super.onLoad()

    applicationLoader foreach Thread.currentThread.setContextClassLoader

    for (loader ← applicationLoader; clazz ← BOOT_CLASSES) {
      loader.loadClass(clazz).newInstance
    }
  }

  abstract override def onUnload() = {
    super.onUnload()
    app.registry.local.shutdownAll
  }
}

/**
 * Java API for the default JAX-RS/Mist Initializer
 */
class DefaultBootableActorLoaderService(val app: AkkaApplication) extends BootableActorLoaderService
