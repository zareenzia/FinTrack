package org.example.finzin.todo;

import org.example.finzin.ai.rag.DocumentIndexer;
import org.example.finzin.entity.TodoFolderEntity;
import org.example.finzin.entity.TodoItemEntity;
import org.example.finzin.entity.TodoListEntity;
import org.example.finzin.gamification.GamificationEvent;
import org.example.finzin.gamification.GamificationEventType;
import org.example.finzin.repository.TodoFolderRepository;
import org.example.finzin.repository.TodoItemRepository;
import org.example.finzin.repository.TodoListRepository;
import org.example.finzin.todo.dto.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Plain-Mockito unit test (matching BudgetPlanServiceTest's convention) for TodoService: the
 * Folders -> Lists -> Items(+one level of sub-items) CRUD/reorder model, its ownership checks,
 * and the document-indexing/gamification side effects each mutation triggers.
 */
@ExtendWith(MockitoExtension.class)
class TodoServiceTest {

    private static final Long USER_ID = 42L;

    @Mock private TodoFolderRepository folderRepository;
    @Mock private TodoListRepository listRepository;
    @Mock private TodoItemRepository itemRepository;
    @Mock private DocumentIndexer documentIndexer;
    @Mock private ApplicationEventPublisher eventPublisher;

    private TodoService service;

    @BeforeEach
    void setUp() {
        service = new TodoService(folderRepository, listRepository, itemRepository, documentIndexer, eventPublisher);
    }

    private TodoFolderEntity folder(Long id, String name, Integer boardPosition) {
        TodoFolderEntity f = new TodoFolderEntity();
        f.setId(id);
        f.setUserId(USER_ID);
        f.setName(name);
        f.setBoardPosition(boardPosition);
        f.setCreatedAt(LocalDateTime.now());
        return f;
    }

    private TodoListEntity list(Long id, String name, Long folderId, Integer boardPosition) {
        TodoListEntity l = new TodoListEntity();
        l.setId(id);
        l.setUserId(USER_ID);
        l.setName(name);
        l.setFolderId(folderId);
        l.setBoardPosition(boardPosition);
        l.setCreatedAt(LocalDateTime.now());
        return l;
    }

    private TodoItemEntity item(Long id, Long listId, Long parentItemId, String title, boolean completed) {
        TodoItemEntity i = new TodoItemEntity();
        i.setId(id);
        i.setUserId(USER_ID);
        i.setListId(listId);
        i.setParentItemId(parentItemId);
        i.setTitle(title);
        i.setCompleted(completed);
        i.setImportant(false);
        i.setCreatedAt(LocalDateTime.now());
        return i;
    }

    // ================================================================================
    // Folders
    // ================================================================================

    @Test
    void findOwnedFolderDelegatesToRepository() {
        TodoFolderEntity f = folder(1L, "Work", null);
        when(folderRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(f));

        assertSame(f, service.findOwnedFolder(USER_ID, 1L).get());
    }

    @Test
    void listFoldersSortsByBoardPositionThenMostRecentlyCreatedFirst() {
        TodoFolderEntity noPositionOld = folder(1L, "Old, no position", null);
        noPositionOld.setCreatedAt(LocalDateTime.now().minusDays(2));
        TodoFolderEntity noPositionNew = folder(2L, "New, no position", null);
        noPositionNew.setCreatedAt(LocalDateTime.now());
        TodoFolderEntity positioned = folder(3L, "Positioned", 0);
        when(folderRepository.findByUserId(USER_ID)).thenReturn(List.of(noPositionOld, noPositionNew, positioned));

        List<TodoFolderResponse> result = service.listFolders(USER_ID);

        assertEquals(List.of(3L, 2L, 1L), result.stream().map(TodoFolderResponse::id).toList(),
                "positioned folder must come first, then unpositioned ones newest-created-first");
    }

    @Test
    void createFolderRejectsBlankName() {
        TodoException ex = assertThrows(TodoException.class, () -> service.createFolder(USER_ID, new TodoFolderRequest("  ")));
        assertEquals("BAD_REQUEST", ex.getErrorTag());
        verifyNoInteractions(folderRepository);
    }

    @Test
    void createFolderTrimsNameAndSaves() {
        when(folderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TodoFolderEntity result = service.createFolder(USER_ID, new TodoFolderRequest("  Groceries  "));

        assertEquals("Groceries", result.getName());
        assertEquals(USER_ID, result.getUserId());
    }

    @Test
    void renameFolderRejectsBlankName() {
        TodoFolderEntity f = folder(1L, "Old", null);
        assertThrows(TodoException.class, () -> service.renameFolder(f, ""));
    }

    @Test
    void renameFolderTrimsAndSaves() {
        TodoFolderEntity f = folder(1L, "Old", null);
        when(folderRepository.save(f)).thenReturn(f);

        TodoFolderEntity result = service.renameFolder(f, "  New Name  ");

        assertEquals("New Name", result.getName());
    }

    @Test
    void deleteFolderUnparentsListsInsteadOfCascadingThenDeletesFolder() {
        TodoFolderEntity f = folder(1L, "Work", null);
        TodoListEntity l1 = list(10L, "List A", 1L, 0);
        TodoListEntity l2 = list(11L, "List B", 1L, 1);
        when(listRepository.findByUserIdAndFolderId(USER_ID, 1L)).thenReturn(new ArrayList<>(List.of(l1, l2)));

        service.deleteFolder(f);

        assertNull(l1.getFolderId());
        assertNull(l1.getBoardPosition());
        assertNull(l2.getFolderId());
        verify(listRepository).saveAll(List.of(l1, l2));
        verify(folderRepository).delete(f);
    }

    @Test
    void reorderFoldersRejectsEmptyOrderedIds() {
        assertThrows(TodoException.class, () -> service.reorderFolders(USER_ID, List.of()));
        assertThrows(TodoException.class, () -> service.reorderFolders(USER_ID, null));
    }

    @Test
    void reorderFoldersRejectsFolderNotOwnedByUser() {
        TodoFolderEntity other = folder(1L, "Not mine", null);
        other.setUserId(999L);
        when(folderRepository.findAllById(List.of(1L))).thenReturn(List.of(other));

        assertThrows(TodoException.class, () -> service.reorderFolders(USER_ID, List.of(1L)));
        verify(folderRepository, never()).saveAll(any());
    }

    @Test
    void reorderFoldersAssignsSequentialBoardPositionsInRequestedOrder() {
        TodoFolderEntity f1 = folder(1L, "A", null);
        TodoFolderEntity f2 = folder(2L, "B", null);
        when(folderRepository.findAllById(List.of(2L, 1L))).thenReturn(List.of(f1, f2));

        service.reorderFolders(USER_ID, List.of(2L, 1L));

        assertEquals(0, f2.getBoardPosition());
        assertEquals(1, f1.getBoardPosition());
        verify(folderRepository).saveAll(List.of(f2, f1));
    }

    // ================================================================================
    // Lists
    // ================================================================================

    @Test
    void createListRejectsBlankName() {
        assertThrows(TodoException.class, () -> service.createList(USER_ID, new TodoListRequest("", null)));
    }

    @Test
    void createListRejectsFolderNotOwnedByUser() {
        when(folderRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.empty());

        assertThrows(TodoException.class, () -> service.createList(USER_ID, new TodoListRequest("Tasks", 1L)));
    }

    @Test
    void createListSucceedsWithoutFolder() {
        when(listRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TodoListEntity result = service.createList(USER_ID, new TodoListRequest("Tasks", null));

        assertEquals("Tasks", result.getName());
        assertNull(result.getFolderId());
    }

    @Test
    void moveListToFolderIsNoOpWhenTargetFolderUnchanged() {
        TodoListEntity l = list(10L, "List", 1L, 0);
        when(folderRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(folder(1L, "Folder", null)));

        TodoListEntity result = service.moveListToFolder(l, 1L);

        assertSame(l, result);
        verify(listRepository, never()).save(any());
    }

    @Test
    void moveListToFolderRejectsFolderNotOwnedByUser() {
        TodoListEntity l = list(10L, "List", null, 0);
        when(folderRepository.findByIdAndUserId(2L, USER_ID)).thenReturn(Optional.empty());

        assertThrows(TodoException.class, () -> service.moveListToFolder(l, 2L));
    }

    @Test
    void moveListToFolderClearsBoardPositionWhenBucketChanges() {
        TodoListEntity l = list(10L, "List", null, 5);
        when(folderRepository.findByIdAndUserId(2L, USER_ID)).thenReturn(Optional.of(folder(2L, "Folder", null)));
        when(listRepository.save(l)).thenReturn(l);

        TodoListEntity result = service.moveListToFolder(l, 2L);

        assertEquals(2L, result.getFolderId());
        assertNull(result.getBoardPosition());
    }

    @Test
    void deleteListDeindexesTopLevelItemsThenDeletesItemsAndList() {
        TodoListEntity l = list(10L, "List", null, null);
        TodoItemEntity topLevel = item(100L, 10L, null, "Task", false);
        when(itemRepository.findByUserIdAndListIdAndParentItemIdIsNull(USER_ID, 10L)).thenReturn(List.of(topLevel));

        service.deleteList(l);

        verify(documentIndexer).deleteTodoItem(USER_ID, 100L);
        verify(itemRepository).deleteByListId(10L);
        verify(listRepository).delete(l);
    }

    @Test
    void reorderListsRejectsListFromDifferentFolder() {
        TodoListEntity l = list(10L, "List", 1L, null);
        when(listRepository.findAllById(List.of(10L))).thenReturn(List.of(l));

        assertThrows(TodoException.class, () -> service.reorderLists(USER_ID, 2L, List.of(10L)));
    }

    @Test
    void reorderListsAssignsSequentialPositionsWithinSameFolderBucket() {
        TodoListEntity l1 = list(10L, "A", 1L, null);
        TodoListEntity l2 = list(11L, "B", 1L, null);
        when(listRepository.findAllById(List.of(10L, 11L))).thenReturn(List.of(l1, l2));

        service.reorderLists(USER_ID, 1L, List.of(10L, 11L));

        assertEquals(0, l1.getBoardPosition());
        assertEquals(1, l2.getBoardPosition());
    }

    // ================================================================================
    // Items — create
    // ================================================================================

    @Test
    void createItemRejectsBlankTitle() {
        assertThrows(TodoException.class,
                () -> service.createItem(USER_ID, new TodoItemRequest("", null, null, 1L, null)));
    }

    @Test
    void createItemRejectsWhenBothListIdAndParentItemIdProvided() {
        assertThrows(TodoException.class,
                () -> service.createItem(USER_ID, new TodoItemRequest("Task", null, null, 1L, 2L)));
    }

    @Test
    void createItemRejectsWhenNeitherListIdNorParentItemIdProvided() {
        assertThrows(TodoException.class,
                () -> service.createItem(USER_ID, new TodoItemRequest("Task", null, null, null, null)));
    }

    @Test
    void createItemRejectsInvalidDueDateFormat() {
        assertThrows(TodoException.class,
                () -> service.createItem(USER_ID, new TodoItemRequest("Task", null, "not-a-date", 1L, null)));
    }

    @Test
    void createItemAsTopLevelIndexesTheItem() {
        TodoListEntity l = list(1L, "List", null, null);
        when(listRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(l));
        when(itemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TodoItemEntity result = service.createItem(USER_ID, new TodoItemRequest("Task", "notes", null, 1L, null));

        assertEquals(1L, result.getListId());
        verify(documentIndexer).indexTodoItem(result);
    }

    @Test
    void createItemAsSubItemCopiesParentListIdAndSkipsIndexing() {
        TodoItemEntity parent = item(5L, 1L, null, "Parent", false);
        when(itemRepository.findByIdAndUserId(5L, USER_ID)).thenReturn(Optional.of(parent));
        when(itemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TodoItemEntity result = service.createItem(USER_ID, new TodoItemRequest("Step 1", null, null, null, 5L));

        assertEquals(1L, result.getListId());
        assertEquals(5L, result.getParentItemId());
        verifyNoInteractions(documentIndexer);
    }

    @Test
    void createItemRejectsNestingUnderAnExistingSubItem() {
        TodoItemEntity grandparentSubItem = item(6L, 1L, 5L, "Already a sub-item", false);
        when(itemRepository.findByIdAndUserId(6L, USER_ID)).thenReturn(Optional.of(grandparentSubItem));

        TodoException ex = assertThrows(TodoException.class,
                () -> service.createItem(USER_ID, new TodoItemRequest("Too deep", null, null, null, 6L)));
        assertEquals("NESTING_TOO_DEEP", ex.getErrorTag());
    }

    @Test
    void createItemRejectsParentNotOwnedByUser() {
        when(itemRepository.findByIdAndUserId(6L, USER_ID)).thenReturn(Optional.empty());

        assertThrows(TodoException.class,
                () -> service.createItem(USER_ID, new TodoItemRequest("Step", null, null, null, 6L)));
    }

    @Test
    void createItemRejectsListNotOwnedByUser() {
        when(listRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.empty());

        assertThrows(TodoException.class,
                () -> service.createItem(USER_ID, new TodoItemRequest("Task", null, null, 1L, null)));
    }

    @Test
    void createQuickItemReusesExistingTasksListWhenPresent() {
        TodoListEntity tasksList = list(9L, "Tasks", null, null);
        when(listRepository.findFirstByUserIdAndNameIgnoreCase(USER_ID, "Tasks")).thenReturn(Optional.of(tasksList));
        when(itemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TodoItemEntity result = service.createQuickItem(USER_ID, new QuickItemRequest("Buy milk", null));

        assertEquals(9L, result.getListId());
        verify(listRepository, never()).save(any());
        verify(documentIndexer).indexTodoItem(result);
    }

    @Test
    void createQuickItemCreatesTasksListWhenNoneExists() {
        when(listRepository.findFirstByUserIdAndNameIgnoreCase(USER_ID, "Tasks")).thenReturn(Optional.empty());
        when(listRepository.save(any())).thenAnswer(inv -> {
            TodoListEntity l = inv.getArgument(0);
            l.setId(20L);
            return l;
        });
        when(itemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TodoItemEntity result = service.createQuickItem(USER_ID, new QuickItemRequest("Buy milk", null));

        assertEquals(20L, result.getListId());
        verify(listRepository).save(any());
    }

    @Test
    void createQuickItemRejectsBlankTitle() {
        assertThrows(TodoException.class, () -> service.createQuickItem(USER_ID, new QuickItemRequest(" ", null)));
    }

    // ================================================================================
    // Items — update / complete / important
    // ================================================================================

    @Test
    void updateItemAppliesOnlyProvidedFieldsAndReindexesTopLevelItem() {
        TodoItemEntity existing = item(1L, 10L, null, "Old title", false);
        existing.setNotes("Old notes");
        when(itemRepository.save(existing)).thenReturn(existing);

        TodoItemEntity result = service.updateItem(existing, new TodoItemUpdateRequest("New title", null, null));

        assertEquals("New title", result.getTitle());
        assertEquals("Old notes", result.getNotes(), "null fields in the update request must leave existing values untouched");
        verify(documentIndexer).indexTodoItem(existing);
    }

    @Test
    void updateItemRejectsBlankTitleWhenTitleFieldIsProvided() {
        TodoItemEntity existing = item(1L, 10L, null, "Old", false);
        assertThrows(TodoException.class, () -> service.updateItem(existing, new TodoItemUpdateRequest("  ", null, null)));
    }

    @Test
    void updateItemSkipsIndexingForSubItems() {
        TodoItemEntity subItem = item(2L, 10L, 1L, "Step", false);
        when(itemRepository.save(subItem)).thenReturn(subItem);

        service.updateItem(subItem, new TodoItemUpdateRequest("New step title", null, null));

        verifyNoInteractions(documentIndexer);
    }

    @Test
    void setCompletedIsNoOpWhenStateAlreadyMatches() {
        TodoItemEntity existing = item(1L, 10L, null, "Task", false);

        TodoItemEntity result = service.setCompleted(existing, false);

        assertSame(existing, result);
        verify(itemRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void setCompletedTrueSetsTimestampAndPublishesGamificationEvent() {
        TodoItemEntity existing = item(1L, 10L, null, "Task", false);
        when(itemRepository.save(existing)).thenReturn(existing);

        service.setCompleted(existing, true);

        assertTrue(existing.getCompleted());
        assertTrue(existing.getCompletedAt() != null);
        ArgumentCaptor<GamificationEvent> captor = ArgumentCaptor.forClass(GamificationEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertEquals(GamificationEventType.TODO_COMPLETED, captor.getValue().type());
        assertEquals(USER_ID, captor.getValue().userId());
    }

    @Test
    void setCompletedFalseClearsTimestampWithoutPublishingEvent() {
        TodoItemEntity existing = item(1L, 10L, null, "Task", true);
        existing.setCompletedAt(LocalDateTime.now());
        when(itemRepository.save(existing)).thenReturn(existing);

        service.setCompleted(existing, false);

        assertFalse(existing.getCompleted());
        assertNull(existing.getCompletedAt());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void setImportantTogglesAndSaves() {
        TodoItemEntity existing = item(1L, 10L, null, "Task", false);
        when(itemRepository.save(existing)).thenReturn(existing);

        TodoItemEntity result = service.setImportant(existing, true);

        assertTrue(result.getImportant());
    }

    // ================================================================================
    // Items — delete / reorder / starred
    // ================================================================================

    @Test
    void deleteItemForTopLevelItemDeletesSubItemsAndDeindexes() {
        TodoItemEntity topLevel = item(1L, 10L, null, "Task", false);

        service.deleteItem(topLevel);

        verify(itemRepository).deleteByParentItemId(1L);
        verify(documentIndexer).deleteTodoItem(USER_ID, 1L);
        verify(itemRepository).delete(topLevel);
    }

    @Test
    void deleteItemForSubItemDoesNotCascadeOrDeindex() {
        TodoItemEntity subItem = item(2L, 10L, 1L, "Step", false);

        service.deleteItem(subItem);

        verify(itemRepository, never()).deleteByParentItemId(any());
        verifyNoInteractions(documentIndexer);
        verify(itemRepository).delete(subItem);
    }

    @Test
    void reorderItemsRejectsItemFromDifferentListBucket() {
        TodoItemEntity wrongList = item(1L, 99L, null, "Task", false);
        when(itemRepository.findAllById(List.of(1L))).thenReturn(List.of(wrongList));

        assertThrows(TodoException.class, () -> service.reorderItems(USER_ID, 10L, null, List.of(1L)));
    }

    @Test
    void reorderItemsAssignsSequentialPositionsAmongTopLevelItemsOfSameList() {
        TodoItemEntity i1 = item(1L, 10L, null, "A", false);
        TodoItemEntity i2 = item(2L, 10L, null, "B", false);
        when(itemRepository.findAllById(List.of(2L, 1L))).thenReturn(List.of(i1, i2));

        service.reorderItems(USER_ID, 10L, null, List.of(2L, 1L));

        assertEquals(0, i2.getBoardPosition());
        assertEquals(1, i1.getBoardPosition());
    }

    @Test
    void reorderItemsAssignsSequentialPositionsAmongSubItemsOfSameParent() {
        TodoItemEntity s1 = item(1L, 10L, 5L, "Step A", false);
        TodoItemEntity s2 = item(2L, 10L, 5L, "Step B", false);
        when(itemRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of(s1, s2));

        service.reorderItems(USER_ID, 10L, 5L, List.of(1L, 2L));

        assertEquals(0, s1.getBoardPosition());
        assertEquals(1, s2.getBoardPosition());
    }

    @Test
    void listStarredItemsDelegatesToRepositoryQuery() {
        TodoItemEntity starred = item(1L, 10L, null, "Important", false);
        starred.setImportant(true);
        when(itemRepository.findByUserIdAndImportantTrueAndCompletedFalseOrderByCreatedAtDesc(USER_ID))
                .thenReturn(List.of(starred));

        List<TodoItemResponse> result = service.listStarredItems(USER_ID);

        assertEquals(1, result.size());
        assertEquals("Important", result.get(0).title());
    }
}
