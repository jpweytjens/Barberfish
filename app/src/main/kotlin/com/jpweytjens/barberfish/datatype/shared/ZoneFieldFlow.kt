package com.jpweytjens.barberfish.datatype.shared

import com.jpweytjens.barberfish.extension.ZoneConfig
import com.jpweytjens.barberfish.extension.streamDataFlow
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.UserProfile
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

// Shared liveFlow/previewFlow scaffold for single-stream zone fields. Each field
// combines its config + user profile + zone config, then maps a single SDK stream
// (live) or cycles preview states. `toState` receives the whole config so fields
// with extra params (e.g. zone displayMode) can pull what they need.

@OptIn(ExperimentalCoroutinesApi::class)
internal fun <Cfg> zoneFieldLiveFlow(
    configFlow: Flow<Cfg>,
    profile: Flow<UserProfile>,
    zones: Flow<ZoneConfig>,
    sdkType: String,
    karooSystem: KarooSystemService,
    toState: (StreamState, UserProfile, ZoneConfig, Cfg) -> FieldState,
): Flow<FieldState> =
    combine(configFlow, profile, zones) { cfg, prof, z -> Triple(cfg, prof, z) }
        .flatMapLatest { (cfg, prof, z) ->
            karooSystem.streamDataFlow(sdkType).map { state -> toState(state, prof, z, cfg) }
        }

@OptIn(ExperimentalCoroutinesApi::class)
internal fun <Cfg> zoneFieldPreviewFlow(
    configFlow: Flow<Cfg>,
    profile: Flow<UserProfile>,
    zones: Flow<ZoneConfig>,
    toPreviews: (Cfg, UserProfile, ZoneConfig) -> List<FieldState>,
): Flow<FieldState> =
    combine(configFlow, profile, zones) { cfg, prof, z -> Triple(cfg, prof, z) }
        .flatMapLatest { (cfg, prof, z) -> cyclePreview(toPreviews(cfg, prof, z)) }
