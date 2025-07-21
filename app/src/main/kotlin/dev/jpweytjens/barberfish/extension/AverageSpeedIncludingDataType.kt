/**
 * Copyright (c) 2024 SRAM LLC.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package dev.jpweytjens.barberfish.extension

import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.models.DataPoint
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.StreamState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import timber.log.Timber

class AverageSpeedIncludingDataType(
        private val karooSystem: KarooSystemService,
        extension: String,
) : DataTypeImpl(extension, "avg-speed-inc") {

    override fun startStream(emitter: Emitter<StreamState>) {
        Timber.d("start average speed (inc) stream")
        val job =
                CoroutineScope(Dispatchers.IO).launch {
                    val distanceFlow = karooSystem.streamDataFlow(DataType.Type.DISTANCE)
                    val rideTimeFlow = karooSystem.streamDataFlow(DataType.Type.RIDE_TIME)
                    combine(
                                    distanceFlow,
                                    rideTimeFlow,
                            ) { distanceState, totalTimeState ->
                        when {
                            distanceState is StreamState.Streaming &&
                                    totalTimeState is StreamState.Streaming -> {
                                val distance = distanceState.dataPoint.singleValue ?: 0.0
                                val totalTime = totalTimeState.dataPoint.singleValue ?: 0.0

                                val averageSpeed =
                                        if (totalTime > 0.0) {
                                            distance /
                                                    (totalTime /
                                                            3600.0) // Convert seconds to hours for
                                            // speed calculation
                                        } else {
                                            0.0
                                        }

                                StreamState.Streaming(
                                        DataPoint(
                                                dataTypeId,
                                                values =
                                                        mapOf(
                                                                DataType.Field.SINGLE to
                                                                        averageSpeed
                                                        ),
                                        )
                                )
                            }
                            else -> StreamState.NotAvailable
                        }
                    }
                            .collect { emitter.onNext(it) }
                }
        emitter.setCancellable { job.cancel() }
    }
}
