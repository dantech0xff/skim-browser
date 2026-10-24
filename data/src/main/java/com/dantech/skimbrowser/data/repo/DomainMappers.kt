package com.dantech.skimbrowser.data.repo

import com.dantech.skimbrowser.data.db.entity.BookmarkEntity
import com.dantech.skimbrowser.data.db.entity.BookmarkFolderEntity
import com.dantech.skimbrowser.data.db.entity.HistoryEntryEntity
import com.dantech.skimbrowser.data.db.entity.ShortcutEntity
import com.dantech.skimbrowser.data.db.entity.SitePermissionEntity
import com.dantech.skimbrowser.data.db.entity.SkimChatEntity
import com.dantech.skimbrowser.data.db.entity.SkimMessageEntity
import com.dantech.skimbrowser.data.db.entity.TabEntity
import com.dantech.skimbrowser.data.db.entity.TabGroupEntity
import com.dantech.skimbrowser.data.db.entity.TabGroupingSnapshotEntity
import com.dantech.skimbrowser.data.db.dao.ChatListItem
import com.dantech.skimbrowser.domain.bookmark.Bookmark
import com.dantech.skimbrowser.domain.bookmark.BookmarkFolder
import com.dantech.skimbrowser.domain.chat.ChatSummary
import com.dantech.skimbrowser.domain.chat.DeletedChat
import com.dantech.skimbrowser.domain.chat.SavedChat
import com.dantech.skimbrowser.domain.chat.SavedMessage
import com.dantech.skimbrowser.domain.history.HistoryEntry
import com.dantech.skimbrowser.domain.permission.SitePermission
import com.dantech.skimbrowser.domain.shortcut.Shortcut
import com.dantech.skimbrowser.domain.tab.GroupingSnapshot
import com.dantech.skimbrowser.domain.tab.PersistedTab
import com.dantech.skimbrowser.domain.tab.PersistedTabGroup

internal fun HistoryEntryEntity.toDomain() = HistoryEntry(id, url, title, host, visitedAt)

internal fun HistoryEntry.toEntity() = HistoryEntryEntity(id, url, title, host, visitedAt)

internal fun ShortcutEntity.toDomain() = Shortcut(id, name, url, position)

internal fun Shortcut.toEntity() = ShortcutEntity(id, name, url, position)

internal fun BookmarkEntity.toDomain() = Bookmark(id, folderId, title, url, host, position, addedAt)

internal fun Bookmark.toEntity() = BookmarkEntity(id, folderId, title, url, host, position, addedAt)

internal fun BookmarkFolderEntity.toDomain() = BookmarkFolder(
    id = id,
    parentId = parentId,
    name = name,
    kind = kind,
    sourceFileName = sourceFileName,
    position = position,
    createdAt = createdAt,
)

internal fun SkimChatEntity.toDomain() = SavedChat(id, kind, url, host, title, tabCount, snippet, createdAt, updatedAt)

internal fun SavedChat.toEntity() = SkimChatEntity(id, kind, url, host, title, tabCount, snippet, createdAt, updatedAt)

internal fun SkimMessageEntity.toDomain() = SavedMessage(
    id, chatId, role, kind, text, citationsJson, feedback, status, suggestedQuestionsJson, createdAt,
)

internal fun SavedMessage.toEntity() = SkimMessageEntity(
    id, chatId, role, kind, text, citationsJson, feedback, status, suggestedQuestionsJson, createdAt,
)

internal fun ChatListItem.toDomain() = ChatSummary(chat.toDomain(), questionCount, hasSummary, answerCount)

internal fun deletedChat(chat: SkimChatEntity, messages: List<SkimMessageEntity>) =
    DeletedChat(chat.toDomain(), messages.map { it.toDomain() })

internal fun SitePermissionEntity.toDomain() = SitePermission(host, skimAi, location, camera, microphone, updatedAt)

internal fun TabEntity.toDomain() = PersistedTab(
    id, url, title, groupId, position, desktopSite, openedByExternalApp, closeOnBack, fromNewTabPage,
    chatId, createdAt, lastAccessedAt, closedAt,
)

fun PersistedTab.toEntity() = TabEntity(
    id, url, title, groupId, position, desktopSite, openedByExternalApp, closeOnBack, fromNewTabPage,
    chatId, createdAt, lastAccessedAt, closedAt,
)

internal fun TabGroupEntity.toDomain() = PersistedTabGroup(id, title, position, createdBySkim)

internal fun PersistedTabGroup.toEntity() = TabGroupEntity(id, title, position, createdBySkim)

internal fun TabGroupingSnapshotEntity.toDomain() = GroupingSnapshot(
    id, previousLayoutJson, tabSetKey, appliedLayoutVersion, bannerVisible, createdAt,
)

internal fun GroupingSnapshot.toEntity() = TabGroupingSnapshotEntity(
    id, previousLayoutJson, tabSetKey, appliedLayoutVersion, bannerVisible, createdAt,
)
