package com.horizon.exchangeapi.route.admin

import org.junit.runner.RunWith
import org.scalatest.funsuite.AnyFunSuite
import org.scalatestplus.junit.JUnitRunner

import com.horizon.exchangeapi.AdminDropdbTokenResponse

@RunWith(classOf[JUnitRunner])
class TestAdminDropdbTokenResponse extends AnyFunSuite {
  test("Constructor") {
    val testResponse  = AdminDropdbTokenResponse(token = "")
    
    assert(testResponse.isInstanceOf[AdminDropdbTokenResponse])
    
    assert(testResponse.token === "")
  }
  
  test("Equality") {
    class TestClass(variable1:String = "")
    
    val testResponse  = AdminDropdbTokenResponse(token = "")
    val testResponse2 = AdminDropdbTokenResponse(token = "")
    val testResponse3 = AdminDropdbTokenResponse(token = " ")
    val testResponse4 = testResponse
    
    assert(!testResponse.equals(AnyRef))
    assert(!testResponse.equals(TestClass))
    
    assert(testResponse.equals(testResponse2))
    assert(!testResponse.equals(testResponse3))
    assert(testResponse2.equals(testResponse))
    assert(!testResponse2.equals(testResponse3))
    assert(testResponse.equals(testResponse4))
    
    assert(testResponse.hashCode() === testResponse2.hashCode())
    assert(testResponse.hashCode() !== testResponse3.hashCode())
    assert(testResponse.hashCode() === testResponse4.hashCode())
    
    assert(testResponse === testResponse2)
    assert(testResponse !== testResponse3)
    assert(testResponse === testResponse4)
    
    assert(testResponse ne testResponse2)
    assert(testResponse ne testResponse3)
    assert(testResponse2 ne testResponse3)
    assert(testResponse eq testResponse4)
  }
  
  test("Copy") {
    val testResponse  = AdminDropdbTokenResponse(token = "")
    val testResponse2 = testResponse.copy()
    val testResponse3 = testResponse.copy(token = "a")
    
    assert(testResponse2.token === "")
    assert(testResponse3.token === "a")
    
    assert(testResponse.equals(testResponse2))
    assert(!testResponse.equals(testResponse3))
    
    assert(testResponse.hashCode() === testResponse2.hashCode())
    assert(testResponse.hashCode() !== testResponse3.hashCode())
    
    assert(testResponse === testResponse2)
    assert(testResponse !== testResponse3)
    
    assert(testResponse ne testResponse2)
    assert(testResponse ne testResponse3)
  }
  
  test("toString()") {
    var testResponse = AdminDropdbTokenResponse(token = "abc")
    
    assert(testResponse.toString() === "AdminDropdbTokenResponse(abc)")
  }
}