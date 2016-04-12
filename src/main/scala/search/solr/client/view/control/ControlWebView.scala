package search.solr.client.view.control

import java.util.concurrent.atomic.AtomicBoolean
import javax.servlet.http.HttpServletRequest

import org.json4s.JValue
import search.solr.client.SolrClientConf
import search.solr.client.index.manager.impl.DefaultIndexManager
import search.solr.client.listener.{DelLastIndex, IndexTaskTraceListener}
import search.solr.client.queue.BlockQueue
import search.solr.client.redis.Redis
import search.solr.client.searchInterface.SearchInterface
import search.solr.client.util.{Util, Json4sHelp, Logging}
import search.solr.client.view.{PageUtil, WebView, WebViewPage}

import scala.collection.mutable
import scala.xml.Node
import search.solr.client.view.JettyUtil._


/**
  * Created by soledede on 2016/4/8.
  */
private[search] class ControlWebView(requestedPort: Int, conf: SolrClientConf) extends WebView(requestedPort, conf, name = "ControlWebView") with Logging {


  /** Initialize all components of the server. */
  override def initialize(): Unit = {
    attachHandler(createStaticHandler(WEB_STATIC_RESOURCE_DIR, "static"))
    val controlPage = new ControlWebPage()
    attachPage(controlPage)
    val listPage = new ListWebPage()
    attachPage(listPage)
  }

  initialize()
}

/*   <h1 style="color:red; text-align:center">welcome crawler!</h1>
         <svg width="100%" height="100%">
           <circle cx="500" cy="300" r="100" stroke="#ff0" stroke-width="5" fill="red"/>
           <polygon points="50 160 55 180 70 180 60 190 65 205 50 195 35 205 40 190 30 180 45 180"
                    stroke="green" fill="transparent" stroke-width="5"/>
           <text x="500" y="500" font-size="60" text-anchor="middle" fill="red">SVG</text>
           <svg width="5cm" height="4cm">
             <image xlink:href="http://s1.95171.cn/b/img/logo/95_logo_r.v731222600.png" x="0" y="0" height="100px" width="100px"/>
           </svg>
         </svg>*/

private[search] class ControlWebPage extends WebViewPage("solr") with PageUtil with Logging {


  override def render(request: HttpServletRequest): Seq[Node] = {
    val currentConsumerActiveCount = DefaultIndexManager.consumerManageThreadPool.getActiveCount
    val currentActiveCount = DefaultIndexManager.indexProcessThreadPool.getActiveCount
    val completeTaskCount = DefaultIndexManager.indexProcessThreadPool.getCompletedTaskCount
    val runConsumerTaskCount = DefaultIndexManager.consumerManageThreadPool.getQueue.size()
    val runTaskCount = DefaultIndexManager.indexProcessThreadPool.getQueue.size()

    //println("completeTaskCount:"+completeTaskCount+"\nrunTaskCount:"+runTaskCount+"\ncurrentActiveCount:"+currentActiveCount)

    if ((currentConsumerActiveCount>0 || currentActiveCount > 0) && completeTaskCount <= 5 && ( runTaskCount > 0||runConsumerTaskCount>0) ) DefaultIndexManager.bus.post(DelLastIndex())


    val queryString = request.getQueryString

    val cacheIndexDataQueue = ControlWebPage.currentIndexDatas

    try {
      if (queryString != null) {
        if (queryString.contains("switchCollection=")) {
          val isChoosenCollection = Util.regexExtract(queryString, "switchCollection=([a-zA-Z0-9]+)&?", 1)
          if (isChoosenCollection != null && !isChoosenCollection.toString.trim.equalsIgnoreCase(""))
            SearchInterface.switchCollection = isChoosenCollection.toString.toBoolean
        }
        if (SearchInterface.switchCollection) {
          if (queryString.contains("switchMg=")) {
            val mergeClouds = Util.regexExtract(queryString, "switchMg=([a-zA-Z0-9]+)&?", 1)
            if (mergeClouds != null && !mergeClouds.toString.trim.equalsIgnoreCase(""))
              SearchInterface.switchMg = mergeClouds.toString.trim
          }
          if (queryString.contains("switchSc=")) {
            val screenClouds = Util.regexExtract(queryString, "switchSc=([a-zA-Z0-9]+)&?", 1)
            if (screenClouds != null && !screenClouds.toString.trim.equalsIgnoreCase(""))
              SearchInterface.switchSc = screenClouds.toString.trim
          }
        }
      }
    } catch {
      case e: Exception => log.error("switch faield", e)
    }


    val showPage = {
      <div>
        <img src="http://www.ehsy.com/images/logo.png"/>{if (currentActiveCount > 0) {
        <h4 style="color:red">Index is Running...请刷新浏览器！</h4>
      } else {
        <h4 style="color:green">Index Finished</h4>
      }}{if (SearchInterface.switchMg != null && !"null".equalsIgnoreCase(SearchInterface.switchMg)) {
        <h4 style="color:yellow">current product collection:
          {SearchInterface.switchMg}
        </h4>
      }}<br/>{if (SearchInterface.switchSc != null && !"null".equalsIgnoreCase(SearchInterface.switchSc)) {
        <h4 style="color:yellow">current attribute collection:
          {SearchInterface.switchSc}
        </h4>
      }}<br/>{if (currentActiveCount > 0 && cacheIndexDataQueue.size() > 0) {
        <h3>正在索引或删除的sku/id.</h3>
          <h4>
            {cacheIndexDataQueue.poll()}
          </h4>
          <a href="list" target="_blank">查看本次索引历史列表.</a>
      }}<br/>{if (currentActiveCount <= 0) {
        <a href="list" target="_blank">点击查看上次索引更新列表.</a>
      }}<br/>
      </div>
    }
    assemblePage(showPage, "solr task trace")
  }

  override def renderJson(request: HttpServletRequest): JValue = Json4sHelp.writeTest
}

object ControlWebPage {
  val currentIndexDatas = BlockQueue[String](new SolrClientConf(), "indexing")
  val isAlive = new AtomicBoolean(false)

  private val listenerThread = new Thread("check activity for thread") {
    setDaemon(true)

    override def run(): Unit = {
      while (true) {
        val currentActiveCount = DefaultIndexManager.consumerManageThreadPool.getActiveCount
        val completeTaskCount = DefaultIndexManager.consumerManageThreadPool.getCompletedTaskCount
        val runTaskCount = DefaultIndexManager.consumerManageThreadPool.getQueue.size()

        //println("completeTaskCount:"+completeTaskCount+"\nrunTaskCount:"+runTaskCount+"\ncurrentActiveCount:"+currentActiveCount)

        if (currentActiveCount > 0 && completeTaskCount <= 1 && runTaskCount > 0) DefaultIndexManager.bus.post(DelLastIndex())
        /*if (currentActiveCount > 0)
          ControlWebPage.isAlive.compareAndSet(false, true)
        else ControlWebPage.isAlive.compareAndSet(true, false)*/
        Thread.sleep(30 * 1000)
      }
    }
  }
  // listenerThread.start()
}


private[search] class ListWebPage extends WebViewPage("solr/list") with PageUtil with Logging {
  val redis = Redis()

  override def render(request: HttpServletRequest): Seq[Node] = {

    val currentConsumerActiveCount = DefaultIndexManager.consumerManageThreadPool.getActiveCount
    val currentActiveCount = DefaultIndexManager.indexProcessThreadPool.getActiveCount
    val completeTaskCount = DefaultIndexManager.indexProcessThreadPool.getCompletedTaskCount
    val runConsumerTaskCount = DefaultIndexManager.consumerManageThreadPool.getQueue.size()
    val runTaskCount = DefaultIndexManager.indexProcessThreadPool.getQueue.size()

    //println("completeTaskCount:"+completeTaskCount+"\nrunTaskCount:"+runTaskCount+"\ncurrentActiveCount:"+currentActiveCount)

    if ((currentConsumerActiveCount>0 || currentActiveCount > 0) && completeTaskCount <= 5 && ( runTaskCount > 0||runConsumerTaskCount>0) ) DefaultIndexManager.bus.post(DelLastIndex())

    val listSkus = redis.getAllFromSetByKey[String](IndexTaskTraceListener.SET_KEY)

    val showPage = {
      <div>
        <img src="http://www.ehsy.com/images/logo.png"/>
        <h1 style="color:red">索引列表,总数(大约)：
          {listSkus.size}
          条记录!请刷新浏览器！</h1>{if (listSkus.size > 0) {
        listSkus.map { s =>
          <h6>
            {s}
          </h6>
        }
      }}
      </div>
    }
    assemblePage(showPage, "solr list show")
  }

  override def renderJson(request: HttpServletRequest): JValue = Json4sHelp.writeTest
}

