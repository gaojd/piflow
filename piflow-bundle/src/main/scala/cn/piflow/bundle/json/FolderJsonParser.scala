package cn.piflow.bundle.json


import java.net.URI

import cn.piflow.{JobContext, JobInputStream, JobOutputStream, ProcessContext}
import cn.piflow.conf.{ConfigurableStop, PortEnum, StopGroupEnum}
import cn.piflow.conf.bean.PropertyDescriptor
import cn.piflow.conf.util.{ImageUtil, MapUtil}
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.{FileStatus, FileSystem, FileUtil, Path}
import org.apache.spark.sql.{DataFrame, SparkSession}

import scala.collection.mutable.ArrayBuffer
import scala.util.control.Breaks.{break, breakable}

class FolderJsonParser extends ConfigurableStop{
  override val authorEmail: String = "yangqidong@cnic.cn"
  val inportList: List[String] = List(PortEnum.NonePort.toString)
  val outportList: List[String] = List(PortEnum.DefaultPort.toString)
  override val description: String ="parser json folder"


  var FolderPath:String = _
  var tag : String = _

  override def perform(in: JobInputStream, out: JobOutputStream, pec: JobContext): Unit = {
    val spark = pec.get[SparkSession]()
    val arrPath: ArrayBuffer[String] = getFileName(FolderPath)
    val FinalDF = getFinalDF(arrPath,spark)
    out.write(FinalDF)
  }


  def getDf(Path: String,ss:SparkSession): DataFrame ={
    val frame: DataFrame = ss.read.json(Path)
    frame
  }


  def getFinalDF(arrPath: ArrayBuffer[String],ss:SparkSession): DataFrame = {
    var index: Int = 0
    breakable {
      for (i <- 0 until arrPath.length) {
        if (getDf(arrPath(i), ss).count() != 0) {
          index = i
          break
        }
      }
    }

    val df01 = ss.read.option("multiline","true").json(arrPath(index))
    var df: DataFrame = df01.select(tag)
    df.printSchema()
    for(d <- index+1 until(arrPath.length)){
      if(getDf(arrPath(d),ss).count()!=0){
        val df1: DataFrame = ss.read.option("multiline","true").json(arrPath(d)).select(tag)
        df1.printSchema()
        val df2: DataFrame = df.union(df1).toDF()
        df=df2
      }
    }
    df
  }


  //获取.xml所有文件路径
  def getFileName(path:String):ArrayBuffer[String]={
    val conf: Configuration = new Configuration()
    val hdfs: FileSystem = FileSystem.get(URI.create(path),conf)
    val fs: Array[FileStatus] = hdfs.listStatus(new Path(path))
    val arrPath: Array[Path] = FileUtil.stat2Paths(fs)
    var arrBuffer:ArrayBuffer[String]=ArrayBuffer()
    for(eachPath<-arrPath){
      arrBuffer+=(FolderPath+eachPath.getName)
    }
    arrBuffer
  }



  override def setProperties(map: Map[String, Any]): Unit = {
    FolderPath = MapUtil.get(map,"FolderPath").asInstanceOf[String]
    tag = MapUtil.get(map,"tag").asInstanceOf[String]
  }



  override def getPropertyDescriptor(): List[PropertyDescriptor] = {
    var descriptor : List[PropertyDescriptor] = List()
    val FolderPath = new PropertyDescriptor().name("FolderPath").displayName("FolderPath").description("The path of the json folder").defaultValue("").required(true)
    descriptor = FolderPath :: descriptor
    val tag = new PropertyDescriptor().name("tag").displayName("tag").description("The tag you want to parse").defaultValue("").required(true)
    descriptor = tag :: descriptor
    descriptor
  }



  override def getIcon(): Array[Byte] = {
    ImageUtil.getImage("./src/main/resources/selectHiveQL.jpg")
  }

  override def getGroup(): List[String] = {
    List(StopGroupEnum.JsonGroup.toString)
  }

  override def initialize(ctx: ProcessContext): Unit = {

  }

}
