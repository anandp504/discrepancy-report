package com.collective.service

import java.io.File
import com.google.api.ads.dfp.lib.client.DfpSession
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.http.HttpTransport
import com.google.api.client.json.JsonFactory
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.common.collect.ImmutableList

/**
 * Created by anand on 04/12/14.
 */
object DfpSessionFactory {

  def getDfpSession(): DfpSession = {
    val httpTransport: HttpTransport = GoogleNetHttpTransport.newTrustedTransport()
    val jsonFactory: JsonFactory = JacksonFactory.getDefaultInstance()
    val secretFile = this.getClass.getClassLoader.getResource("key.p12").getPath

    val credential: GoogleCredential = new GoogleCredential.Builder().setTransport(httpTransport)
      .setJsonFactory(jsonFactory)
      .setServiceAccountId("825035362745-qf916vhhnntd44enqkcbhbrlb09o0e5o@developer.gserviceaccount.com")
      .setServiceAccountScopes(ImmutableList.of("https://www.googleapis.com/auth/dfp"))
      .setServiceAccountPrivateKeyFromP12File(new File(secretFile))
      .build()

    credential.refreshToken()
    val dfpSession: DfpSession = new DfpSession.Builder().fromFile().withOAuth2Credential(credential).build()
    dfpSession
  }
}