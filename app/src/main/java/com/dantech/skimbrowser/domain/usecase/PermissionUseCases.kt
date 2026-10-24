package com.dantech.skimbrowser.domain.usecase

import com.dantech.skimbrowser.domain.permission.PermissionState
import com.dantech.skimbrowser.domain.permission.SitePermissionKind
import com.dantech.skimbrowser.domain.permission.SitePermissionRepository
import com.dantech.skimbrowser.domain.permission.SkimAiState

class SetSkimAi(private val sites: SitePermissionRepository) {
    suspend operator fun invoke(host: String, state: SkimAiState) = sites.setSkimAi(host, state)
}

class SetSitePermission(private val sites: SitePermissionRepository) {
    suspend operator fun invoke(host: String, kind: SitePermissionKind, state: PermissionState) =
        sites.setPermission(host, kind, state)
}

class ReadSkimAiStates(private val sites: SitePermissionRepository) {
    suspend operator fun invoke(hosts: Set<String>): Map<String, SkimAiState> = sites.skimAiStates(hosts)
}
