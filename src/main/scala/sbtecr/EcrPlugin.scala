package sbtecr

import com.amazonaws.regions.Region
import com.typesafe.sbt.SbtNativePackager.autoImport.packageName
import com.typesafe.sbt.SbtNativePackager.{autoImport => docker}
import com.typesafe.sbt.packager.docker.DockerPlugin
import sbt.Keys._
import sbt._
import sbtecr.Commands._

import scala.language.postfixOps

object EcrPlugin extends AutoPlugin {

  object autoImport {
      lazy val Ecr = config("ecr")

      lazy val region           = settingKey[Region]("Amazon EC2 region.")
      lazy val repositoryName   = settingKey[String]("Amazon ECR repository name.")
      lazy val localDockerImage = settingKey[String]("Local Docker image.")
      lazy val repositoryTags   = settingKey[Seq[String]]("Tags managed in the Amazon ECR repository.")

      lazy val createRepository = taskKey[Unit]("Create a repository in Amazon ECR.")
      lazy val login            = taskKey[Unit]("Login to Amazon ECR.")
      lazy val push             = taskKey[Unit]("Push a Docker image to Amazon ECR.")
  }

  import autoImport._

  override def requires = DockerPlugin

  override lazy val projectSettings = inConfig(Ecr)(defaultSettings ++ tasks ++ dockerSettings)

  import DockerPlugin.autoImport.Docker
  lazy val packagerKeys = com.typesafe.sbt.packager.Keys
  lazy val deploy = ""

  lazy val defaultSettings: Seq[Def.Setting[_]] = Seq(
    repositoryTags   := {deploy match {
                            case "prod" => Seq("prod-" + version.value)
                            case "acc"  => Seq("acc-"  + version.value)
                            case _      => Seq("stg-"  + version.value)
                         }},
    localDockerImage := s"${(packageName in Docker).value}:${(version in Docker).value}",
    repositoryName   := {deploy match {
                            case "prod" => (packageName in Docker).value
                            case "acc"  => (packageName in Docker).value
                            case _      => (packageName in Docker).value + "-stg"
                        }}
  )
  lazy val dockerSettings: Seq[Def.Setting[_]] = Seq(
    packagerKeys.maintainer         := "ml infra <ml-infra@nextbeat.net>",
    packagerKeys.dockerBaseImage    := "openjdk:8-jre",
    packagerKeys.dockerExposedPorts := Seq(9000, 9000),
    version in Docker               := "latest",
  )

  lazy val tasks: Seq[Def.Setting[_]] = Seq(
    createRepository := {
      implicit val logger = streams.value.log
      AwsEcr.createRepository(region.value, repositoryName.value)
    },
    login := {
      implicit val logger = streams.value.log
      val accountId = AwsSts.accountId(region.value)
      val (user, pass) = AwsEcr.dockerCredentials(region.value)
      val cmd = s"docker login -u ${user} -p ${pass} https://${AwsEcr.domain(region.value, accountId)}"
      exec(cmd) match {
        case 0 =>
        case _ =>
          sys.error(s"Login failed. Command: $cmd")
      }
    },
    push := {
      login.value
      implicit val logger = streams.value.log
      val accountId = AwsSts.accountId(region.value)

      val src = localDockerImage.value
      def destination(tag: String) = s"${AwsEcr.domain(region.value, accountId)}/${repositoryName.value}:$tag"

      repositoryTags.value.foreach { tag =>
        val dst = destination(tag)
        exec(s"docker tag ${src} ${dst}") match {
          case 0 =>
            exec(s"docker push ${dst}") match {
              case 0 =>
              case _ =>
                sys.error(s"Pushing failed. Target image: ${dst}")
            }
          case _ =>
            sys.error(s"Tagging failed. Source image: ${src} Target image: ${dst}")
        }
      }
    }
  )
}
