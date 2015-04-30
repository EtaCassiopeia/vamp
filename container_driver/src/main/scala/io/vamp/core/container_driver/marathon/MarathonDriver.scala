package io.vamp.core.container_driver.marathon

import com.typesafe.scalalogging.Logger
import io.vamp.common.http.RestClient
import io.vamp.core.container_driver._
import io.vamp.core.container_driver.marathon.api._
import io.vamp.core.model.artifact._
import org.slf4j.LoggerFactory

import scala.concurrent.{ExecutionContext, Future}

class MarathonDriver(ec: ExecutionContext, url: String) extends AbstractContainerDriver(ec) {

  private val logger = Logger(LoggerFactory.getLogger(classOf[MarathonDriver]))

  def info: Future[ContainerInfo] = RestClient.request[Info](s"GET $url/v2/info").map {
    ContainerInfo("marathon", _)
  }

  def all: Future[List[ContainerService]] = {
    logger.debug(s"marathon get all")
    RestClient.request[Apps](s"GET $url/v2/apps?embed=apps.tasks").map(apps => apps.apps.filter(app => processable(app.id)).map(app => containerService(app)))
  }

  def deploy(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService, update: Boolean) = {
    val id = appId(deployment, service.breed)
    logger.info(s"marathon create app: $id")

    val app = CreateApp(id, CreateContainer(CreateDocker(service.breed.deployable.name, portMappings(deployment, cluster, service))), service.scale.get.instances, service.scale.get.cpu, service.scale.get.memory, environment(deployment, cluster, service))

    if (update)
      RestClient.request[Any](s"PUT $url/v2/apps/${app.id}", app)
    else
      RestClient.request[Any](s"POST $url/v2/apps", app)
  }

  def undeploy(deployment: Deployment, service: DeploymentService) = {
    val id = appId(deployment, service.breed)
    logger.info(s"marathon delete app: $id")
    RestClient.delete(s"$url/v2/apps/$id")
  }

  private def containerService(app: App): ContainerService =
    ContainerService(nameMatcher(app.id), DefaultScale("", app.cpus, app.mem, app.instances), app.tasks.map(task => ContainerServer(task.id, task.host, task.ports, task.startedAt.isDefined)))

}

