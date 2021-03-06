/*
 * Tencent is pleased to support the open source community by making Angel available.
 *
 * Copyright (C) 2017 THL A29 Limited, a Tencent company. All rights reserved.
 *
 * Licensed under the BSD 3-Clause License (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 *
 * https://opensource.org/licenses/BSD-3-Clause
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 *
 */

package com.tencent.angel.ml.classification.lr

import com.tencent.angel.ml.conf.MLConf
import com.tencent.angel.ml.feature.LabeledData
import com.tencent.angel.ml.math.vector.DenseDoubleVector
import com.tencent.angel.ml.model.{MLModel, PSModel}
import com.tencent.angel.ml.predict.PredictResult
import com.tencent.angel.ml.utils.MathUtils
import com.tencent.angel.worker.storage.{DataBlock, MemoryDataBlock}
import com.tencent.angel.worker.task.TaskContext
import org.apache.commons.logging.LogFactory
import org.apache.hadoop.conf.Configuration

/**
  * LR model
  *
  */

object LRModel{
  def apply(conf: Configuration) = {
    new LRModel(conf)
  }

  def apply(ctx:TaskContext, conf: Configuration) = {
    new LRModel(conf, ctx)
  }
}

class LRModel(conf: Configuration, _ctx: TaskContext = null) extends MLModel(conf, _ctx) {
  private val LOG = LogFactory.getLog(classOf[LRModel])

  val LR_WEIGHT_MAT = "lr_weight"
  val LR_INTERCEPT = "lr_intercept"

  val feaNum = conf.getInt(MLConf.ML_FEATURE_NUM, MLConf.DEFAULT_ML_FEATURE_NUM)
  // The feature weight vector, stored on PS
  val weight = PSModel[DenseDoubleVector](LR_WEIGHT_MAT, 1, feaNum).setAverage(true)
  val intercept_ = PSModel[DenseDoubleVector](LR_INTERCEPT, 1, 1).setAverage(true)
  val intercept =
      if (conf.getBoolean(MLConf.LR_USE_INTERCEPT, MLConf.DEFAULT_LR_USE_INTERCEPT)) {
        Some(intercept_)
       } else {
         None
   }
   addPSModel(LR_WEIGHT_MAT, weight)
   addPSModel(LR_INTERCEPT, intercept_)
   
   setSavePath(conf)
   setLoadPath(conf)


  /**
    *
    * @param dataSet
    * @return
    */
  override
  def predict(dataSet: DataBlock[LabeledData]): DataBlock[PredictResult] = {
    val start = System.currentTimeMillis()
    val wVector = weight.getRow(0)
    val b = intercept.map(_.getRow(0).get(0)).getOrElse(0.0)
    val cost = System.currentTimeMillis() - start
    LOG.info(s"pull LR Model from PS cost $cost ms." )

    val predict = new MemoryDataBlock[PredictResult](-1)

    dataSet.resetReadIndex()
    for (idx: Int <- 0 until dataSet.size) {
      val instance = dataSet.read
      val id = instance.getY
      val dot = wVector.dot(instance.getX)
      val sig = MathUtils.sigmoid(dot)
      predict.put(new SparseLRPredictResult(id, dot, sig))
    }
    predict
  }
}

class SparseLRPredictResult(id: Double, dot: Double, sig: Double) extends PredictResult {
  override def getText():String = {
    (id + separator + format.format(dot) + separator + format.format(sig))
  }

}

