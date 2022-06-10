package com.horizon.exchangeapi.route.organization

import com.horizon.exchangeapi.{ApiTime, ApiUtils, ChangeEntry, ExchangeApi, HttpCode, Password, ResourceChangesRequest, ResourceChangesRespObject, Role, TestDBConnection}
import com.horizon.exchangeapi.tables.{AgbotRow, AgbotsTQ, NodeRow, NodesTQ, OrgRow, OrgsTQ, ResourceChangeRow, ResourceChangesTQ, UserRow, UsersTQ}
import org.json4s.DefaultFormats
import org.json4s.jackson.JsonMethods
import org.json4s.native.Serialization
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import org.scalatest.funsuite.AnyFunSuite
import scalaj.http.{Http, HttpResponse}
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.Await
import scala.concurrent.duration.{Duration, DurationInt}

class TestPostOrgChangesRoute extends AnyFunSuite with BeforeAndAfterAll with BeforeAndAfterEach {

  private val ACCEPT = ("Accept","application/json")
  private val CONTENT: (String, String) = ("Content-Type", "application/json")
  private val AWAITDURATION: Duration = 15.seconds
  private val DBCONNECTION: TestDBConnection = new TestDBConnection
  private val URL = sys.env.getOrElse("EXCHANGE_URL_ROOT", "http://localhost:8080") + "/v1/orgs/"
  private val ROUTE = "/changes"

  private val EXCHANGEVERSION: String = ExchangeApi.adminVersion()

  private implicit val formats = DefaultFormats

  private val HUBADMINPASSWORD = "hubadminpassword"
  private val ORG1USERPASSWORD = "org1userpassword"
  private val ORG2USERPASSWORD = "org2userpassword"
  private val ORG1NODETOKEN = "org1nodetoken"
  private val ORG2NODETOKEN = "org2nodetoken"
  private val ORG1AGBOTTOKEN = "org1agbottoken"
  private val ORG2AGBOTTOKEN = "org2agbottoken"
  private val IBMAGBOTTOKEN = "ibmagbottoken"

  private val TESTORGS: Seq[OrgRow] =
    Seq(
      OrgRow(
        orgId              = "testPostOrgChangesRouteOrg1",
        orgType            = "",
        label              = "testPostOrgChangesRouteOrg",
        description        = "Test Organization 1",
        lastUpdated        = ApiTime.nowUTC,
        tags               = None,
        limits             = "",
        heartbeatIntervals = ""),
      OrgRow(
        orgId              = "testPostOrgChangesRouteOrg2",
        orgType            = "",
        label              = "testPostOrgChangesRouteOrg",
        description        = "Test Organization 2",
        lastUpdated        = ApiTime.nowUTC,
        tags               = None,
        limits             = "",
        heartbeatIntervals = ""
      )
    )

  private val TESTUSERS: Seq[UserRow] =
    Seq(
      UserRow(
        username    = "root/testPostOrgChangesRouteHubAdmin",
        orgid       = "root",
        hashedPw    = Password.hash(HUBADMINPASSWORD),
        admin       = false,
        hubAdmin    = true,
        email       = "testPostOrgChangesRouteHubAdmin@ibm.com",
        lastUpdated = ApiTime.nowUTC,
        updatedBy   = "root"
      ),
      UserRow(
        username    = TESTORGS(0).orgId + "/org1user",
        orgid       = TESTORGS(0).orgId,
        hashedPw    = Password.hash(ORG1USERPASSWORD),
        admin       = false,
        hubAdmin    = false,
        email       = "org1user@ibm.com",
        lastUpdated = ApiTime.nowUTC,
        updatedBy   = "root"
      ),
      UserRow(
        username    = TESTORGS(1).orgId + "/org2user",
        orgid       = TESTORGS(1).orgId,
        hashedPw    = Password.hash(ORG2USERPASSWORD),
        admin       = false,
        hubAdmin    = false,
        email       = "org2user@ibm.com",
        lastUpdated = ApiTime.nowUTC,
        updatedBy   = "root"
      )
    )

  private val TESTNODES: Seq[NodeRow] =
    Seq(
      NodeRow(
        arch               = "",
        id                 = TESTORGS(0).orgId + "/org1node",
        heartbeatIntervals = "",
        lastHeartbeat      = Some(ApiTime.nowUTC),
        lastUpdated        = ApiTime.nowUTC,
        msgEndPoint        = "",
        name               = "",
        nodeType           = "",
        orgid              = TESTORGS(0).orgId,
        owner              = TESTUSERS(1).username, //org 1 user
        pattern            = "",
        publicKey          = "",
        regServices        = "",
        softwareVersions   = "",
        token              = Password.hash(ORG1NODETOKEN),
        userInput          = ""
      ),
      NodeRow(
        arch               = "",
        id                 = TESTORGS(1).orgId + "/org2node",
        heartbeatIntervals = "",
        lastHeartbeat      = Some(ApiTime.nowUTC),
        lastUpdated        = ApiTime.nowUTC,
        msgEndPoint        = "",
        name               = "",
        nodeType           = "",
        orgid              = TESTORGS(1).orgId,
        owner              = TESTUSERS(2).username, //org 2 user
        pattern            = "",
        publicKey          = "",
        regServices        = "",
        softwareVersions   = "",
        token              = Password.hash(ORG2NODETOKEN),
        userInput          = ""
      )
    )

  private val TESTAGBOTS: Seq[AgbotRow] =
    Seq(AgbotRow(
      id            = TESTORGS(0).orgId + "/org1agbot",
      orgid         = TESTORGS(0).orgId,
      token         = Password.hash(ORG1AGBOTTOKEN),
      name          = "",
      owner         = TESTUSERS(1).username, //org 1 user
      msgEndPoint   = "",
      lastHeartbeat = ApiTime.nowUTC,
      publicKey     = ""
    ),
      AgbotRow(
        id            = TESTORGS(1).orgId + "/org2agbot",
        orgid         = TESTORGS(1).orgId,
        token         = Password.hash(ORG2AGBOTTOKEN),
        name          = "",
        owner         = TESTUSERS(2).username, //org 2 user
        msgEndPoint   = "",
        lastHeartbeat = ApiTime.nowUTC,
        publicKey     = ""
      ),
      AgbotRow(
        id            = "IBM/testPostOrgChangesRouteIBMAgbot",
        orgid         = "IBM",
        token         = Password.hash(IBMAGBOTTOKEN),
        name          = "",
        owner         = "root/root",
        msgEndPoint   = "",
        lastHeartbeat = ApiTime.nowUTC,
        publicKey     = ""
      )
    )

  private val ROOTAUTH = ("Authorization","Basic " + ApiUtils.encode(Role.superUser + ":" + sys.env.getOrElse("EXCHANGE_ROOTPW", "")))
  private val HUBADMINAUTH = ("Authorization", "Basic " + ApiUtils.encode(TESTUSERS(0).username + ":" + HUBADMINPASSWORD))
  private val ORG1USERAUTH = ("Authorization", "Basic " + ApiUtils.encode(TESTUSERS(1).username + ":" + ORG1USERPASSWORD))
  private val ORG2USERAUTH = ("Authorization", "Basic " + ApiUtils.encode(TESTUSERS(2).username + ":" + ORG2USERPASSWORD))
  private val ORG1NODEAUTH = ("Authorization", "Basic " + ApiUtils.encode(TESTNODES(0).id + ":" + ORG1NODETOKEN))
  private val ORG2NODEAUTH = ("Authorization", "Basic " + ApiUtils.encode(TESTNODES(1).id + ":" + ORG2NODETOKEN))
  private val ORG1AGBOTAUTH = ("Authorization", "Basic " + ApiUtils.encode(TESTAGBOTS(0).id + ":" + ORG1AGBOTTOKEN))
  private val ORG2AGBOTAUTH = ("Authorization", "Basic " + ApiUtils.encode(TESTAGBOTS(1).id + ":" + ORG2AGBOTTOKEN))
  private val IBMAGBOTAUTH = ("Authorization", "Basic " + ApiUtils.encode(TESTAGBOTS(2).id + ":" + IBMAGBOTTOKEN))

  private val TESTRESOURCECHANGES: Seq[ResourceChangeRow] = Seq(
    ResourceChangeRow( //old -- 0
      changeId = 0L,
      orgId = TESTORGS(0).orgId,
      id = TESTNODES(0).id,
      category = "node",
      public = "true",
      resource = "org",
      operation = "created",
      lastUpdated = ApiTime.pastUTCTimestamp(300) //5 min ago
    ),
    ResourceChangeRow( //other org, not public -- 1
      changeId = 0L,
      orgId = TESTORGS(1).orgId,
      id = TESTNODES(0).id,
      category = "service",
      public = "false",
      resource = "agbot",
      operation = "created",
      lastUpdated = ApiTime.nowUTCTimestamp
    ),
    ResourceChangeRow( //node category, id of other node -- 2
      changeId = 0L,
      orgId = TESTORGS(0).orgId,
      id = TESTNODES(1).id,
      category = "node",
      public = "false",
      resource = "agbotagreements",
      operation = "deleted",
      lastUpdated = ApiTime.nowUTCTimestamp
    ),
    ResourceChangeRow( //mgmtpolicy -- 3
      changeId = 0L,
      orgId = TESTORGS(0).orgId,
      id = TESTNODES(1).id,
      category = "mgmtpolicy",
      public = "false",
      resource = "nodeagreements",
      operation = "deleted",
      lastUpdated = ApiTime.nowUTCTimestamp
    ),
    ResourceChangeRow( //other org, public -- 4
      changeId = 0L,
      orgId = TESTORGS(1).orgId,
      id = TESTNODES(1).id,
      category = "mgmtpolicy",
      public = "true",
      resource = "node",
      operation = "deleted",
      lastUpdated = ApiTime.nowUTCTimestamp
    ),
    ResourceChangeRow( //resource nodemsgs -- 5
      changeId = 0L,
      orgId = TESTORGS(1).orgId,
      id = TESTNODES(0).id,
      category = "service",
      public = "true",
      resource = "nodemsgs",
      operation = "deleted",
      lastUpdated = ApiTime.nowUTCTimestamp
    ),
    ResourceChangeRow(  //resource nodestatus ... 6
      changeId = 0L,
      orgId = TESTORGS(1).orgId,
      id = TESTNODES(0).id,
      category = "org",
      public = "false",
      resource = "nodestatus",
      operation = "deleted",
      lastUpdated = ApiTime.nowUTCTimestamp
    ),
    ResourceChangeRow(  //resource nodeagreements + op createdmodified -- 7
      changeId = 0L,
      orgId = TESTORGS(0).orgId,
      id = TESTNODES(0).id.split("/")(1), //want the id without the org part
      category = "node",
      public = "true",
      resource = "nodeagreements",
      operation = "created/modified",
      lastUpdated = ApiTime.nowUTCTimestamp
    ),
    ResourceChangeRow(  //resource agbotagreements + op createdmodified -- 8
      changeId = 0L,
      orgId = TESTORGS(0).orgId,
      id = TESTNODES(0).id,
      category = "mgmtpolicy",
      public = "true",
      resource = "agbotagreements",
      operation = "created/modified",
      lastUpdated = ApiTime.nowUTCTimestamp
    ),
    ResourceChangeRow( //agbot success -- 9
      changeId = 0L,
      orgId = TESTORGS(0).orgId,
      id = TESTNODES(0).id,
      category = "mgmtpolicy",
      public = "false",
      resource = "org",
      operation = "created",
      lastUpdated = ApiTime.nowUTCTimestamp
    )
  )

  var lastChangeId: Long = 0L //will be set in beforeAll()

  override def beforeAll(): Unit = {
    Await.ready(DBCONNECTION.getDb.run(
      (OrgsTQ ++= TESTORGS) andThen
        (UsersTQ ++= TESTUSERS) andThen
        (AgbotsTQ ++= TESTAGBOTS) andThen
        (NodesTQ ++= TESTNODES) andThen
        (ResourceChangesTQ ++= TESTRESOURCECHANGES)), AWAITDURATION
    )
    lastChangeId = Await.result(DBCONNECTION.getDb.run(ResourceChangesTQ //get changeId of last RC added to DB
      .filter(_.orgId startsWith "testPostOrgChangesRoute")
      .sortBy(_.changeId.desc)
      .take(1)
      .result), AWAITDURATION).head.changeId
  }

  override def afterAll(): Unit = {
    Await.ready(DBCONNECTION.getDb.run(ResourceChangesTQ.filter(_.orgId startsWith "testPostOrgChangesRoute").delete andThen
      OrgsTQ.filter(_.orgid startsWith "testPostOrgChangesRoute").delete andThen
      UsersTQ.filter(_.username startsWith "root/testPostOrgChangesRouteHubAdmin").delete andThen
      AgbotsTQ.filter(_.id startsWith "IBM/testPostOrgChangesRouteIBMAgbot").delete), AWAITDURATION)
    DBCONNECTION.getDb.close()
  }

  override def afterEach(): Unit = {
    //need to reset heartbeat each time to ensure it actually gets changed in each test
    Await.ready(DBCONNECTION.getDb.run(
      NodesTQ.update(TESTNODES(0)) andThen //can't do "updateAll", so do them individually
      NodesTQ.update(TESTNODES(1)) andThen
      AgbotsTQ.update(TESTAGBOTS(0)) andThen
      AgbotsTQ.update(TESTAGBOTS(1)) andThen
      AgbotsTQ.update(TESTAGBOTS(2))
    ), AWAITDURATION)
  }

  // Resource Changes that are dynamically needed, specific to the test case.
  def fixtureResourceChanges(testCode: Seq[ResourceChangeRow] => Any, testData: Seq[ResourceChangeRow]): Any = {
    try{
      Await.result(DBCONNECTION.getDb.run(ResourceChangesTQ ++= testData), AWAITDURATION)
      testCode(testData)
    }
    finally
      Await.result(DBCONNECTION.getDb.run(ResourceChangesTQ.filter(_.lastUpdated inSet testData.map(_.lastUpdated)).delete), AWAITDURATION) //hopefully lastUpdated is unique? because don't know change id
  }

  private val defaultIdRequest: ResourceChangesRequest = ResourceChangesRequest(
    changeId = lastChangeId, //will ensure that at least the final RC added will be returned
    lastUpdated = None,
    maxRecords = 100,
    orgList = None
  )

  private val defaultTimeRequest: ResourceChangesRequest = ResourceChangesRequest(
    changeId = 0L, // <- 0 means don't use
    lastUpdated = Some(ApiTime.pastUTC(60)), //should get everything added with ApiTime.nowUTC
    maxRecords = 100,
    orgList = None
  )

  //I assume this returns empty list because maxRecords = 0?
  test("POST /orgs/" + TESTORGS(0).orgId + ROUTE + " -- empty body -- success, empty return") {
    val response: HttpResponse[String] = Http(URL + TESTORGS(0).orgId + ROUTE).postData("{}").headers(ACCEPT).headers(CONTENT).headers(ROOTAUTH).asString
    info("Code: " + response.code)
    info("Body: " + response.body)
    assert(response.code === HttpCode.POST_OK.intValue)
    val responseObj: ResourceChangesRespObject = JsonMethods.parse(response.body).extract[ResourceChangesRespObject]
    assert(responseObj.changes.isEmpty)
    assert(responseObj.exchangeVersion === EXCHANGEVERSION)
    assert(!responseObj.hitMaxRecords)
  }

  test("POST /orgs/" + TESTORGS(0).orgId + ROUTE + " -- invalid body -- 400 bad input") {
    val response: HttpResponse[String] = Http(URL + TESTORGS(0).orgId + ROUTE).postData("{\"invalidKey\":\"invalidValue\"}").headers(ACCEPT).headers(CONTENT).headers(ROOTAUTH).asString
    info("Code: " + response.code)
    info("Body: " + response.body)
    assert(response.code === HttpCode.BAD_INPUT.intValue)
  }


  /* disabling until issue 619 bug fix
  test("POST /orgs/" + TESTORGS(0).orgId + ROUTE + " -- improperly formatted time -- 502 bad gateway") {
    val request: ResourceChangesRequest = ResourceChangesRequest(
      changeId = 0, //shouldn't actually be used so value doesn't matter
      lastUpdated = Some("asdf"), //invalid
      maxRecords = 100,
      orgList = None
    )
    val response: HttpResponse[String] = Http(URL + TESTORGS(0).orgId + ROUTE).postData(Serialization.write(request)).headers(ACCEPT).headers(CONTENT).headers(ROOTAUTH).asString
    info("Code: " + response.code)
    info("Body: " + response.body)
    assert(response.code === HttpCode.BAD_INPUT.intValue)
  }
  */

  test("POST /orgs/" + TESTORGS(0).orgId + ROUTE + " -- specify changeId -- success, returns last TESTRC") {
    val response: HttpResponse[String] = Http(URL + TESTORGS(0).orgId + ROUTE).postData(Serialization.write(defaultIdRequest)).headers(ACCEPT).headers(CONTENT).headers(ROOTAUTH).asString
    info("Code: " + response.code)
    info("Body: " + response.body)
    assert(response.code === HttpCode.POST_OK.intValue)
    val responseObj: ResourceChangesRespObject = JsonMethods.parse(response.body).extract[ResourceChangesRespObject]
    assert(responseObj.changes.nonEmpty)
    assert(responseObj.changes.filter(_.resourceChanges.filter(_.changeId === lastChangeId).nonEmpty).nonEmpty) //check if RC with lastChangeId is in response
    assert(responseObj.exchangeVersion === EXCHANGEVERSION)
  }

  test("POST /orgs/" + TESTORGS(0).orgId + ROUTE + " -- both changeId and lastUpdated provided -- success, uses lastUpdated") {
    val request: ResourceChangesRequest = ResourceChangesRequest(
      changeId = lastChangeId, //will ensure that at least the final RC added will be returned
      lastUpdated = Some(ApiTime.futureUTC(60)), //if this were to be used, none of the TESTRCs would be included
      maxRecords = 100,
      orgList = None
    )
    val response: HttpResponse[String] = Http(URL + TESTORGS(0).orgId + ROUTE).postData(Serialization.write(request)).headers(ACCEPT).headers(CONTENT).headers(ROOTAUTH).asString
    info("Code: " + response.code)
    info("Body: " + response.body)
    assert(response.code === HttpCode.POST_OK.intValue)
    val responseObj: ResourceChangesRespObject = JsonMethods.parse(response.body).extract[ResourceChangesRespObject]
    assert(responseObj.changes.nonEmpty)
    assert(responseObj.changes.filter(_.resourceChanges.filter(_.changeId === lastChangeId).nonEmpty).nonEmpty) //check if RC with lastChangeId is in response
    assert(responseObj.exchangeVersion === EXCHANGEVERSION)
  }

  test("POST /orgs/doesNotExist" + ROUTE + " -- all RCs returned are public") {
    val request: ResourceChangesRequest = ResourceChangesRequest(
      changeId = lastChangeId - 5, //hopefully some of the TESTRCs will be returned. if not, no big deal, we should still get some public RCs
      lastUpdated = None,
      maxRecords = 5, //only take 5 for speed's sake
      orgList = None
    )
    val response: HttpResponse[String] = Http(URL + "doesNotExist" + ROUTE).postData(Serialization.write(defaultIdRequest)).headers(ACCEPT).headers(CONTENT).headers(ROOTAUTH).asString
    info("Code: " + response.code)
    info("Body: " + response.body)
    assert(response.code === HttpCode.POST_OK.intValue)
    val responseObj: ResourceChangesRespObject = JsonMethods.parse(response.body).extract[ResourceChangesRespObject]
    assert(responseObj.changes.nonEmpty)
    for (change <- responseObj.changes) {
      for (rc <- change.resourceChanges) { //double-nested for loop, but there will only be a max of 5 rcs to loop through
        assert(Await.result(DBCONNECTION.getDb.run(ResourceChangesTQ.filter(_.changeId === rc.changeId).result), AWAITDURATION).head.public === "true")
      }
    }
    assert(responseObj.exchangeVersion === EXCHANGEVERSION)
  }

  /* disabling until issue 620 bug fix
  test("POST /orgs/" + TESTORGS(1).orgId + ROUTE + " -- " + TESTORGS(1).orgId + " not in orgList, should be autmatically added -- success") {
    val request: ResourceChangesRequest = ResourceChangesRequest(
      changeId = 0,
      lastUpdated = Some(ApiTime.pastUTC(60)), //should get most TESTRCs
      maxRecords = 100,
      orgList = Some(List(TESTORGS(0).orgId))
    )
    val response: HttpResponse[String] = Http(URL + TESTORGS(1).orgId + ROUTE).postData(Serialization.write(request)).headers(ACCEPT).headers(CONTENT).headers(IBMAGBOTAUTH).asString
    info("Code: " + response.code)
    info("Body: " + response.body)
    assert(response.code === HttpCode.POST_OK.intValue)
    val responseObj: ResourceChangesRespObject = JsonMethods.parse(response.body).extract[ResourceChangesRespObject]
    assert(responseObj.changes.nonEmpty)
    assert(responseObj.changes.filter(_.resourceChanges.filter(_.lastUpdated === TESTRESOURCECHANGES(6).lastUpdated.toString).nonEmpty).nonEmpty) //lastUpdated time should be unique to that specific RC
    assert(responseObj.exchangeVersion === EXCHANGEVERSION)
    //TODO: insure heartbeat changed
  }
  */

  //TODO: add more tests for multitenant agbot after issue 620 bug fix

  test("POST /orgs/" + TESTORGS(0).orgId + ROUTE + " -- as agbot in org -- success") {
    val response: HttpResponse[String] = Http(URL + TESTORGS(0).orgId + ROUTE).postData(Serialization.write(defaultTimeRequest)).headers(ACCEPT).headers(CONTENT).headers(ORG1AGBOTAUTH).asString
    info("Code: " + response.code)
    info("Body: " + response.body)
    assert(response.code === HttpCode.POST_OK.intValue)
    val responseObj: ResourceChangesRespObject = JsonMethods.parse(response.body).extract[ResourceChangesRespObject]
    assert(responseObj.changes.size >= 4) //can't check for exact num because it may pick up some public RCs from other tests
    //check that the lastUpdated values match for the RCs that should be returned for this user
    assert(responseObj.changes.filter(_.resourceChanges.filter(_.lastUpdated === ApiTime.fixFormatting(TESTRESOURCECHANGES(2).lastUpdated.toString)).nonEmpty).nonEmpty)
    assert(responseObj.changes.filter(_.resourceChanges.filter(_.lastUpdated === ApiTime.fixFormatting(TESTRESOURCECHANGES(3).lastUpdated.toString)).nonEmpty).nonEmpty)
    assert(responseObj.changes.filter(_.resourceChanges.filter(_.lastUpdated === ApiTime.fixFormatting(TESTRESOURCECHANGES(4).lastUpdated.toString)).nonEmpty).nonEmpty)
    assert(responseObj.changes.filter(_.resourceChanges.filter(_.lastUpdated === ApiTime.fixFormatting(TESTRESOURCECHANGES(9).lastUpdated.toString)).nonEmpty).nonEmpty)
    assert(responseObj.exchangeVersion === EXCHANGEVERSION)
    //check that heartbeat of caller was updated
    assert(Await.result(DBCONNECTION.getDb.run(AgbotsTQ.filter(_.id === TESTAGBOTS(0).id).result), AWAITDURATION).head.lastHeartbeat > TESTAGBOTS(0).lastHeartbeat)
  }

  test("POST /orgs/" + TESTORGS(0).orgId + ROUTE + " -- as agbot in other org -- 403 access denied") {
    val response: HttpResponse[String] = Http(URL + TESTORGS(0).orgId + ROUTE).postData(Serialization.write(defaultTimeRequest)).headers(ACCEPT).headers(CONTENT).headers(ORG2AGBOTAUTH).asString
    info("Code: " + response.code)
    info("Body: " + response.body)
    assert(response.code === HttpCode.ACCESS_DENIED.intValue)
    //insure heartbeat hasn't changed
    assert(Await.result(DBCONNECTION.getDb.run(AgbotsTQ.filter(_.id === TESTAGBOTS(1).id).result), AWAITDURATION).head.lastHeartbeat === TESTAGBOTS(1).lastHeartbeat)
  }

  test("POST /orgs/" + TESTORGS(0).orgId + ROUTE + " -- as node in org -- success") {
    val response: HttpResponse[String] = Http(URL + TESTORGS(0).orgId + ROUTE).postData(Serialization.write(defaultTimeRequest)).headers(ACCEPT).headers(CONTENT).headers(ORG1NODEAUTH).asString
    info("Code: " + response.code)
    info("Body: " + response.body)
    assert(response.code === HttpCode.POST_OK.intValue)
    val responseObj: ResourceChangesRespObject = JsonMethods.parse(response.body).extract[ResourceChangesRespObject]
    assert(responseObj.changes.size >= 6) //can't check for exact num because it may pick up some public RCs from other tests
    //check that the lastUpdated values match for the RCs that should be returned for this user
    assert(responseObj.changes.filter(_.resourceChanges.filter(_.lastUpdated === ApiTime.fixFormatting(TESTRESOURCECHANGES(3).lastUpdated.toString)).nonEmpty).nonEmpty)
    assert(responseObj.changes.filter(_.resourceChanges.filter(_.lastUpdated === ApiTime.fixFormatting(TESTRESOURCECHANGES(4).lastUpdated.toString)).nonEmpty).nonEmpty)
    assert(responseObj.changes.filter(_.resourceChanges.filter(_.lastUpdated === ApiTime.fixFormatting(TESTRESOURCECHANGES(5).lastUpdated.toString)).nonEmpty).nonEmpty)
    assert(responseObj.changes.filter(_.resourceChanges.filter(_.lastUpdated === ApiTime.fixFormatting(TESTRESOURCECHANGES(7).lastUpdated.toString)).nonEmpty).nonEmpty)
    assert(responseObj.changes.filter(_.resourceChanges.filter(_.lastUpdated === ApiTime.fixFormatting(TESTRESOURCECHANGES(8).lastUpdated.toString)).nonEmpty).nonEmpty)
    assert(responseObj.changes.filter(_.resourceChanges.filter(_.lastUpdated === ApiTime.fixFormatting(TESTRESOURCECHANGES(9).lastUpdated.toString)).nonEmpty).nonEmpty)
    assert(responseObj.exchangeVersion === EXCHANGEVERSION)
    //check that heartbeat of caller was updated
    assert(Await.result(DBCONNECTION.getDb.run(NodesTQ.filter(_.id === TESTNODES(0).id).result), AWAITDURATION).head.lastHeartbeat.get > TESTNODES(0).lastHeartbeat.get)
  }

  test("POST /orgs/" + TESTORGS(0).orgId + ROUTE + " -- as node in other org -- 403 access denied") {
    val response: HttpResponse[String] = Http(URL + TESTORGS(0).orgId + ROUTE).postData(Serialization.write(defaultTimeRequest)).headers(ACCEPT).headers(CONTENT).headers(ORG2NODEAUTH).asString
    info("Code: " + response.code)
    info("Body: " + response.body)
    assert(response.code === HttpCode.ACCESS_DENIED.intValue)
    //insure heartbeat hasn't changed
    assert(Await.result(DBCONNECTION.getDb.run(NodesTQ.filter(_.id === TESTNODES(1).id).result), AWAITDURATION).head.lastHeartbeat === TESTNODES(1).lastHeartbeat)
  }

  test("POST /orgs/" + TESTORGS(0).orgId + ROUTE + " -- as root -- success") {
    val response: HttpResponse[String] = Http(URL + TESTORGS(0).orgId + ROUTE).postData(Serialization.write(defaultTimeRequest)).headers(ACCEPT).headers(CONTENT).headers(ROOTAUTH).asString
    info("Code: " + response.code)
    info("Body: " + response.body)
    assert(response.code === HttpCode.POST_OK.intValue)
    val responseObj: ResourceChangesRespObject = JsonMethods.parse(response.body).extract[ResourceChangesRespObject]
    assert(responseObj.changes.size >= 7) //can't check for exact num because it may pick up some public RCs from other tests
    //check that the lastUpdated values match for the RCs that should be returned for this user
    assert(responseObj.changes.filter(_.resourceChanges.filter(_.lastUpdated === ApiTime.fixFormatting(TESTRESOURCECHANGES(2).lastUpdated.toString)).nonEmpty).nonEmpty)
    assert(responseObj.changes.filter(_.resourceChanges.filter(_.lastUpdated === ApiTime.fixFormatting(TESTRESOURCECHANGES(3).lastUpdated.toString)).nonEmpty).nonEmpty)
    assert(responseObj.changes.filter(_.resourceChanges.filter(_.lastUpdated === ApiTime.fixFormatting(TESTRESOURCECHANGES(4).lastUpdated.toString)).nonEmpty).nonEmpty)
    assert(responseObj.changes.filter(_.resourceChanges.filter(_.lastUpdated === ApiTime.fixFormatting(TESTRESOURCECHANGES(5).lastUpdated.toString)).nonEmpty).nonEmpty)
    assert(responseObj.changes.filter(_.resourceChanges.filter(_.lastUpdated === ApiTime.fixFormatting(TESTRESOURCECHANGES(7).lastUpdated.toString)).nonEmpty).nonEmpty)
    assert(responseObj.changes.filter(_.resourceChanges.filter(_.lastUpdated === ApiTime.fixFormatting(TESTRESOURCECHANGES(8).lastUpdated.toString)).nonEmpty).nonEmpty)
    assert(responseObj.changes.filter(_.resourceChanges.filter(_.lastUpdated === ApiTime.fixFormatting(TESTRESOURCECHANGES(9).lastUpdated.toString)).nonEmpty).nonEmpty)
    assert(responseObj.exchangeVersion === EXCHANGEVERSION)
  }

  test("POST /orgs/" + TESTORGS(0).orgId + ROUTE + " -- as hub admin -- success") {
    val response: HttpResponse[String] = Http(URL + TESTORGS(0).orgId + ROUTE).postData(Serialization.write(defaultTimeRequest)).headers(ACCEPT).headers(CONTENT).headers(HUBADMINAUTH).asString
    info("Code: " + response.code)
    info("Body: " + response.body)
    assert(response.code === HttpCode.POST_OK.intValue)
    val responseObj: ResourceChangesRespObject = JsonMethods.parse(response.body).extract[ResourceChangesRespObject]
    assert(responseObj.changes.size >= 7) //can't check for exact num because it may pick up some public RCs from other tests
    //check that the lastUpdated values match for the RCs that should be returned for this user
    assert(responseObj.changes.filter(_.resourceChanges.filter(_.lastUpdated === ApiTime.fixFormatting(TESTRESOURCECHANGES(2).lastUpdated.toString)).nonEmpty).nonEmpty)
    assert(responseObj.changes.filter(_.resourceChanges.filter(_.lastUpdated === ApiTime.fixFormatting(TESTRESOURCECHANGES(3).lastUpdated.toString)).nonEmpty).nonEmpty)
    assert(responseObj.changes.filter(_.resourceChanges.filter(_.lastUpdated === ApiTime.fixFormatting(TESTRESOURCECHANGES(4).lastUpdated.toString)).nonEmpty).nonEmpty)
    assert(responseObj.changes.filter(_.resourceChanges.filter(_.lastUpdated === ApiTime.fixFormatting(TESTRESOURCECHANGES(5).lastUpdated.toString)).nonEmpty).nonEmpty)
    assert(responseObj.changes.filter(_.resourceChanges.filter(_.lastUpdated === ApiTime.fixFormatting(TESTRESOURCECHANGES(7).lastUpdated.toString)).nonEmpty).nonEmpty)
    assert(responseObj.changes.filter(_.resourceChanges.filter(_.lastUpdated === ApiTime.fixFormatting(TESTRESOURCECHANGES(8).lastUpdated.toString)).nonEmpty).nonEmpty)
    assert(responseObj.changes.filter(_.resourceChanges.filter(_.lastUpdated === ApiTime.fixFormatting(TESTRESOURCECHANGES(9).lastUpdated.toString)).nonEmpty).nonEmpty)
    assert(responseObj.exchangeVersion === EXCHANGEVERSION)
  }

  test("POST /orgs/" + TESTORGS(0).orgId + ROUTE + " -- as user in org -- success") {
    val response: HttpResponse[String] = Http(URL + TESTORGS(0).orgId + ROUTE).postData(Serialization.write(defaultTimeRequest)).headers(ACCEPT).headers(CONTENT).headers(ORG1USERAUTH).asString
    info("Code: " + response.code)
    info("Body: " + response.body)
    assert(response.code === HttpCode.POST_OK.intValue)
    val responseObj: ResourceChangesRespObject = JsonMethods.parse(response.body).extract[ResourceChangesRespObject]
    assert(responseObj.changes.size >= 7) //can't check for exact num because it may pick up some public RCs from other tests
    //check that the lastUpdated values match for the RCs that should be returned for this user
    assert(responseObj.changes.filter(_.resourceChanges.filter(_.lastUpdated === ApiTime.fixFormatting(TESTRESOURCECHANGES(2).lastUpdated.toString)).nonEmpty).nonEmpty)
    assert(responseObj.changes.filter(_.resourceChanges.filter(_.lastUpdated === ApiTime.fixFormatting(TESTRESOURCECHANGES(3).lastUpdated.toString)).nonEmpty).nonEmpty)
    assert(responseObj.changes.filter(_.resourceChanges.filter(_.lastUpdated === ApiTime.fixFormatting(TESTRESOURCECHANGES(4).lastUpdated.toString)).nonEmpty).nonEmpty)
    assert(responseObj.changes.filter(_.resourceChanges.filter(_.lastUpdated === ApiTime.fixFormatting(TESTRESOURCECHANGES(5).lastUpdated.toString)).nonEmpty).nonEmpty)
    assert(responseObj.changes.filter(_.resourceChanges.filter(_.lastUpdated === ApiTime.fixFormatting(TESTRESOURCECHANGES(7).lastUpdated.toString)).nonEmpty).nonEmpty)
    assert(responseObj.changes.filter(_.resourceChanges.filter(_.lastUpdated === ApiTime.fixFormatting(TESTRESOURCECHANGES(8).lastUpdated.toString)).nonEmpty).nonEmpty)
    assert(responseObj.changes.filter(_.resourceChanges.filter(_.lastUpdated === ApiTime.fixFormatting(TESTRESOURCECHANGES(9).lastUpdated.toString)).nonEmpty).nonEmpty)
    assert(responseObj.exchangeVersion === EXCHANGEVERSION)
  }

  test("POST /orgs/" + TESTORGS(0).orgId + ROUTE + " -- as user in other org -- 403 access denied") {
    val response: HttpResponse[String] = Http(URL + TESTORGS(0).orgId + ROUTE).postData(Serialization.write(defaultTimeRequest)).headers(ACCEPT).headers(CONTENT).headers(ORG2USERAUTH).asString
    info("Code: " + response.code)
    info("Body: " + response.body)
    assert(response.code === HttpCode.ACCESS_DENIED.intValue)
  }

  test("POST /orgs/" + TESTORGS(0).orgId + ROUTE + " -- insure changing maxRecords cuts down return size -- success") {
    val request: ResourceChangesRequest = ResourceChangesRequest(
      changeId = 0L, // <- 0 means don't use
      lastUpdated = Some(ApiTime.pastUTC(60)), //should get everything added with ApiTime.nowUTC
      maxRecords = 2,
      orgList = None
    )
    val response: HttpResponse[String] = Http(URL + TESTORGS(0).orgId + ROUTE).postData(Serialization.write(request)).headers(ACCEPT).headers(CONTENT).headers(ROOTAUTH).asString
    info("Code: " + response.code)
    info("Body: " + response.body)
    assert(response.code === HttpCode.POST_OK.intValue)
    val responseObj: ResourceChangesRespObject = JsonMethods.parse(response.body).extract[ResourceChangesRespObject]
    assert(responseObj.changes.size === 2)
    assert(responseObj.exchangeVersion === EXCHANGEVERSION)
  }

  //possibly: add test where maxRecords is > 10,000 and ensure that there are only 10,000 returns... would involve adding > 10,000 RCs to the DB... not sure if that's worth testing

  test("POST /orgs/" + TESTORGS(0).orgId + ROUTE + " -- insure 2 RCs are added under same change -- success") {
    val newRCs: Seq[ResourceChangeRow] = Seq(ResourceChangeRow( //very similar to 9 -- 10
      changeId = 0L,
      orgId = TESTORGS(0).orgId,
      id = TESTNODES(0).id,
      category = "agbot",
      public = "true",
      resource = "org",
      operation = "deleted",
      lastUpdated = ApiTime.nowUTCTimestamp
    ))
    fixtureResourceChanges(
      _ =>{
        val response: HttpResponse[String] = Http(URL + TESTORGS(0).orgId + ROUTE).postData(Serialization.write(defaultTimeRequest)).headers(ACCEPT).headers(CONTENT).headers(ROOTAUTH).asString
        info("Code: " + response.code)
        info("Body: " + response.body)
        assert(response.code === HttpCode.POST_OK.intValue)
        val responseObj: ResourceChangesRespObject = JsonMethods.parse(response.body).extract[ResourceChangesRespObject]
        val change: ChangeEntry = responseObj.changes.filter(_.orgId === TESTRESOURCECHANGES(9).orgId).filter(_.resource === TESTRESOURCECHANGES(9).resource).filter(_.id === TESTRESOURCECHANGES(9).id).head
        assert(change.operation === newRCs.head.operation)
        assert(change.resourceChanges.length === 2)
        assert(change.resourceChanges.filter(_.lastUpdated === ApiTime.fixFormatting(TESTRESOURCECHANGES(9).lastUpdated.toString)).nonEmpty)
        assert(change.resourceChanges.filter(_.lastUpdated === ApiTime.fixFormatting(newRCs.head.lastUpdated.toString)).nonEmpty)
        assert(responseObj.exchangeVersion === EXCHANGEVERSION)
      }, newRCs)
  }

}
