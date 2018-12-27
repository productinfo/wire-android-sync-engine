/*
 * Wire
 * Copyright (C) 2016 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.waz.sync.client

import java.io.ByteArrayInputStream

import com.waz.{AuthenticationConfig, ZIntegrationSpec}
import com.waz.ZLog.ImplicitTag._
import com.waz.ZLog.verbose
import com.waz.api.impl.ErrorResponse
import com.waz.model.{AssetId, Mime, Sha256}
import com.waz.service.assets2.{Asset, BlobDetails, NoEncryption}
import com.waz.sync.client.AssetClient.FileWithSha
import com.waz.sync.client.AssetClient2.{AssetContent, Metadata, Retention, UploadResponse2}
import com.waz.utils.returning

import scala.concurrent.Future
import scala.util.Random

//TODO Think about tests resources cleanup
class AssetClient2Spec extends ZIntegrationSpec with AuthenticationConfig {

  private lazy val assetClient = new AssetClient2Impl()
  private val testAssetContent = returning(Array.ofDim[Byte](1024))(Random.nextBytes)
  private val testAssetMetadata = Metadata(retention = Retention.Volatile)
  private val testAssetMime = Mime.Default
  private val testRawAsset = AssetContent(testAssetMime, () => Future.successful(new ByteArrayInputStream(testAssetContent)), Some(testAssetContent.length))

  private def createBlobAsset(response: UploadResponse2): Asset[BlobDetails.type] = {
    Asset(
      id = AssetId(response.key.str),
      token = response.token,
      sha = Sha256.calculate(testAssetContent),
      mime = Mime.Default,
      encryption = NoEncryption,
      localSource = None,
      preview = None,
      name = "test_content",
      size = testAssetContent.length,
      details = BlobDetails,
      convId = None
    )
  }

  feature("Assets http requests") {

    scenario("upload asset") {
      for {
        result <- assetClient.uploadAsset(testAssetMetadata, testRawAsset, callback = None)
        _ = verbose(s"Uploading asset result: $result")
      } yield result shouldBe an[Right[ErrorResponse, UploadResponse2]]
    }

    scenario("upload and load asset") {
      for {
        uploadResult <- assetClient.uploadAsset(testAssetMetadata, testRawAsset, callback = None)
        _ = verbose(s"Uploading asset result: $uploadResult")
        uploadResponse = uploadResult.right.get
        asset = createBlobAsset(uploadResponse)
        loadResult <- assetClient.loadAssetContent(asset, callback = None)
      } yield {
        loadResult shouldBe an[Right[ErrorResponse, FileWithSha]]
        loadResult.right.get.sha256 shouldBe asset.sha
      }
    }

    scenario("upload and delete asset") {
      for {
        uploadResult <- assetClient.uploadAsset(testAssetMetadata, testRawAsset, callback = None)
        _ = verbose(s"Uploading asset result: $uploadResult")
        uploadResponse = uploadResult.right.get
        asset = createBlobAsset(uploadResponse)
        deleteResult <- assetClient.deleteAsset(asset.id)
        _ = verbose(s"Deleting asset result: $deleteResult")
      } yield {
        deleteResult shouldBe an[Right[ErrorResponse, Boolean]]
        deleteResult.right.get shouldBe true
      }
    }

    scenario("delete not existed asset") {
      for {
        deleteResult <- assetClient.deleteAsset(AssetId("3-3-cff20a61-3dd5-4fcf-b612-dc650a9ca245"))
        _ = verbose(s"Deleting asset result: $deleteResult")
      } yield {
        deleteResult shouldBe an[Right[ErrorResponse, Boolean]]
        deleteResult.right.get shouldBe false
      }
    }

  }

}
