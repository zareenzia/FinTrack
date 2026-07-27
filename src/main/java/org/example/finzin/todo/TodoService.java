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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/** CRUD + reorder for the Folders -> Lists -> Items(+one level of sub-items) To-Do model.
 *  Mirrors org.example.finzin.purchaseplanner.PurchaseItemService's structure/conventions. */
@Service
public class TodoService {

    private final TodoFolderRepository folderRepository;
    private final TodoListRepository listRepository;
    private final TodoItemRepository itemRepository;
    private final DocumentIndexer documentIndexer;
    private final ApplicationEventPublisher eventPublisher;

    public TodoService(TodoFolderRepository folderRepository, TodoListRepository listRepository,
                        TodoItemRepository itemRepository, DocumentIndexer documentIndexer,
                        ApplicationEventPublisher eventPublisher) {
        this.folderRepository = folderRepository;
        this.listRepository = listRepository;
        this.itemRepository = itemRepository;
        this.documentIndexer = documentIndexer;
        this.eventPublisher = eventPublisher;
    }

    // ============== Folders ==============

    public Optional<TodoFolderEntity> findOwnedFolder(Long userId, Long id) {
        return folderRepository.findByIdAndUserId(id, userId);
    }

    public List<TodoFolderResponse> listFolders(Long userId) {
        return sortedByBoard(folderRepository.findByUserId(userId), TodoFolderEntity::getBoardPosition, TodoFolderEntity::getCreatedAt)
                .stream().map(this::toFolderResponse).collect(Collectors.toList());
    }

    public TodoFolderEntity createFolder(Long userId, TodoFolderRequest body) {
        if (body.name() == null || body.name().isBlank()) throw TodoException.badRequest("Folder name is required");
        TodoFolderEntity entity = new TodoFolderEntity();
        entity.setUserId(userId);
        entity.setName(body.name().trim());
        return folderRepository.save(entity);
    }

    public TodoFolderEntity renameFolder(TodoFolderEntity existing, String name) {
        if (name == null || name.isBlank()) throw TodoException.badRequest("Folder name is required");
        existing.setName(name.trim());
        return folderRepository.save(existing);
    }

    /** Un-parents member lists rather than cascading — folder membership is optional, so removing the
     *  grouping shouldn't destroy its lists. */
    @Transactional
    public void deleteFolder(TodoFolderEntity existing) {
        List<TodoListEntity> lists = listRepository.findByUserIdAndFolderId(existing.getUserId(), existing.getId());
        lists.forEach(l -> { l.setFolderId(null); l.setBoardPosition(null); });
        listRepository.saveAll(lists);
        folderRepository.delete(existing);
    }

    @Transactional
    public void reorderFolders(Long userId, List<Long> orderedIds) {
        if (orderedIds == null || orderedIds.isEmpty()) throw TodoException.badRequest("orderedIds must not be empty");
        Map<Long, TodoFolderEntity> byId = folderRepository.findAllById(orderedIds).stream()
                .collect(Collectors.toMap(TodoFolderEntity::getId, f -> f));
        List<TodoFolderEntity> ordered = new ArrayList<>();
        for (Long id : orderedIds) {
            TodoFolderEntity f = byId.get(id);
            if (f == null || !Objects.equals(f.getUserId(), userId)) {
                throw TodoException.badRequest("One or more folders couldn't be reordered — try refreshing.");
            }
            ordered.add(f);
        }
        for (int i = 0; i < ordered.size(); i++) ordered.get(i).setBoardPosition(i);
        folderRepository.saveAll(ordered);
    }

    // ============== Lists ==============

    public Optional<TodoListEntity> findOwnedList(Long userId, Long id) {
        return listRepository.findByIdAndUserId(id, userId);
    }

    public List<TodoListResponse> listLists(Long userId) {
        Map<Long, TodoItemRepository.TodoListItemCount> counts = itemRepository.countTopLevelItemsByList(userId).stream()
                .collect(Collectors.toMap(TodoItemRepository.TodoListItemCount::getListId, c -> c));
        return sortedByBoard(listRepository.findByUserId(userId), TodoListEntity::getBoardPosition, TodoListEntity::getCreatedAt)
                .stream().map(l -> toListResponse(l, counts.get(l.getId()))).collect(Collectors.toList());
    }

    public TodoListEntity createList(Long userId, TodoListRequest body) {
        if (body.name() == null || body.name().isBlank()) throw TodoException.badRequest("List name is required");
        if (body.folderId() != null && folderRepository.findByIdAndUserId(body.folderId(), userId).isEmpty()) {
            throw TodoException.badRequest("That folder doesn't exist or doesn't belong to you.");
        }
        TodoListEntity entity = new TodoListEntity();
        entity.setUserId(userId);
        entity.setName(body.name().trim());
        entity.setFolderId(body.folderId());
        return listRepository.save(entity);
    }

    public TodoListEntity renameList(TodoListEntity existing, String name) {
        if (name == null || name.isBlank()) throw TodoException.badRequest("List name is required");
        existing.setName(name.trim());
        return listRepository.save(existing);
    }

    public TodoListEntity moveListToFolder(TodoListEntity existing, Long folderId) {
        if (folderId != null && folderRepository.findByIdAndUserId(folderId, existing.getUserId()).isEmpty()) {
            throw TodoException.badRequest("That folder doesn't exist or doesn't belong to you.");
        }
        if (Objects.equals(existing.getFolderId(), folderId)) return existing;
        existing.setFolderId(folderId);
        existing.setBoardPosition(null); // manual order doesn't carry across buckets
        return listRepository.save(existing);
    }

    @Transactional
    public void deleteList(TodoListEntity existing) {
        List<TodoItemEntity> topLevel = itemRepository.findByUserIdAndListIdAndParentItemIdIsNull(existing.getUserId(), existing.getId());
        topLevel.forEach(i -> documentIndexer.deleteTodoItem(existing.getUserId(), i.getId()));
        itemRepository.deleteByListId(existing.getId());
        listRepository.delete(existing);
    }

    @Transactional
    public void reorderLists(Long userId, Long folderId, List<Long> orderedIds) {
        if (orderedIds == null || orderedIds.isEmpty()) throw TodoException.badRequest("orderedIds must not be empty");
        Map<Long, TodoListEntity> byId = listRepository.findAllById(orderedIds).stream()
                .collect(Collectors.toMap(TodoListEntity::getId, l -> l));
        List<TodoListEntity> ordered = new ArrayList<>();
        for (Long id : orderedIds) {
            TodoListEntity l = byId.get(id);
            if (l == null || !Objects.equals(l.getUserId(), userId) || !Objects.equals(l.getFolderId(), folderId)) {
                throw TodoException.badRequest("One or more lists couldn't be reordered — try refreshing.");
            }
            ordered.add(l);
        }
        for (int i = 0; i < ordered.size(); i++) ordered.get(i).setBoardPosition(i);
        listRepository.saveAll(ordered);
    }

    // ============== Items ==============

    public Optional<TodoItemEntity> findOwnedItem(Long userId, Long id) {
        return itemRepository.findByIdAndUserId(id, userId);
    }

    public List<TodoItemResponse> listTopLevelItems(Long userId, Long listId) {
        List<TodoItemEntity> topLevel = itemRepository.findByUserIdAndListIdAndParentItemIdIsNull(userId, listId);
        Map<Long, List<TodoItemEntity>> subItemsByParent = itemRepository.findByListId(listId).stream()
                .filter(i -> i.getParentItemId() != null)
                .collect(Collectors.groupingBy(TodoItemEntity::getParentItemId));
        return sortedByBoard(topLevel, TodoItemEntity::getBoardPosition, TodoItemEntity::getCreatedAt).stream()
                .map(i -> toItemResponse(i, subItemsByParent.getOrDefault(i.getId(), List.of())))
                .collect(Collectors.toList());
    }

    public TodoItemDetailResponse getItemDetail(TodoItemEntity item) {
        List<TodoItemEntity> subItems = sortedByBoard(itemRepository.findByParentItemId(item.getId()),
                TodoItemEntity::getBoardPosition, TodoItemEntity::getCreatedAt);
        List<TodoItemResponse> subResponses = subItems.stream().map(s -> toItemResponse(s, List.of())).collect(Collectors.toList());
        return new TodoItemDetailResponse(toItemResponse(item, subItems), subResponses);
    }

    public TodoItemEntity createItem(Long userId, TodoItemRequest body) {
        if (body.title() == null || body.title().isBlank()) throw TodoException.badRequest("Title is required");
        boolean hasList = body.listId() != null;
        boolean hasParent = body.parentItemId() != null;
        if (hasList == hasParent) {
            throw TodoException.badRequest("Provide exactly one of listId (top-level item) or parentItemId (sub-item)");
        }

        TodoItemEntity entity = new TodoItemEntity();
        entity.setUserId(userId);
        entity.setTitle(body.title().trim());
        entity.setNotes(body.notes());
        entity.setDueDate(parseDate(body.dueDate()));

        if (hasParent) {
            TodoItemEntity parent = itemRepository.findByIdAndUserId(body.parentItemId(), userId)
                    .orElseThrow(() -> TodoException.badRequest("That item doesn't exist or doesn't belong to you."));
            if (parent.getParentItemId() != null) throw TodoException.nestingTooDeep();
            entity.setListId(parent.getListId());
            entity.setParentItemId(parent.getId());
        } else {
            TodoListEntity list = listRepository.findByIdAndUserId(body.listId(), userId)
                    .orElseThrow(() -> TodoException.badRequest("That list doesn't exist or doesn't belong to you."));
            entity.setListId(list.getId());
        }

        TodoItemEntity saved = itemRepository.save(entity);
        if (saved.getParentItemId() == null) documentIndexer.indexTodoItem(saved);
        return saved;
    }

    /** Voice-assistant entry point — no list to choose, so resolves/creates a default "Tasks" list. */
    public TodoItemEntity createQuickItem(Long userId, QuickItemRequest body) {
        if (body.title() == null || body.title().isBlank()) throw TodoException.badRequest("Title is required");
        TodoListEntity tasksList = listRepository.findFirstByUserIdAndNameIgnoreCase(userId, "Tasks")
                .orElseGet(() -> createList(userId, new TodoListRequest("Tasks", null)));

        TodoItemEntity entity = new TodoItemEntity();
        entity.setUserId(userId);
        entity.setTitle(body.title().trim());
        entity.setDueDate(parseDate(body.dueDate()));
        entity.setListId(tasksList.getId());

        TodoItemEntity saved = itemRepository.save(entity);
        documentIndexer.indexTodoItem(saved);
        return saved;
    }

    public TodoItemEntity updateItem(TodoItemEntity existing, TodoItemUpdateRequest body) {
        if (body.title() != null) {
            if (body.title().isBlank()) throw TodoException.badRequest("Title is required");
            existing.setTitle(body.title().trim());
        }
        if (body.notes() != null) existing.setNotes(body.notes());
        if (body.dueDate() != null) existing.setDueDate(parseDate(body.dueDate()));
        TodoItemEntity saved = itemRepository.save(existing);
        if (saved.getParentItemId() == null) documentIndexer.indexTodoItem(saved);
        return saved;
    }

    public TodoItemEntity setCompleted(TodoItemEntity existing, boolean completed) {
        boolean was = Boolean.TRUE.equals(existing.getCompleted());
        if (was == completed) return existing;
        existing.setCompleted(completed);
        existing.setCompletedAt(completed ? LocalDateTime.now() : null);
        TodoItemEntity saved = itemRepository.save(existing);
        if (!was && completed) {
            eventPublisher.publishEvent(new GamificationEvent(saved.getUserId(), GamificationEventType.TODO_COMPLETED,
                    Map.of("todoId", saved.getId())));
        }
        return saved;
    }

    public TodoItemEntity setImportant(TodoItemEntity existing, boolean important) {
        existing.setImportant(important);
        return itemRepository.save(existing);
    }

    @Transactional
    public void deleteItem(TodoItemEntity existing) {
        if (existing.getParentItemId() == null) {
            itemRepository.deleteByParentItemId(existing.getId());
            documentIndexer.deleteTodoItem(existing.getUserId(), existing.getId());
        }
        itemRepository.delete(existing);
    }

    @Transactional
    public void reorderItems(Long userId, Long listId, Long parentItemId, List<Long> orderedIds) {
        if (orderedIds == null || orderedIds.isEmpty()) throw TodoException.badRequest("orderedIds must not be empty");
        Map<Long, TodoItemEntity> byId = itemRepository.findAllById(orderedIds).stream()
                .collect(Collectors.toMap(TodoItemEntity::getId, i -> i));
        List<TodoItemEntity> ordered = new ArrayList<>();
        for (Long id : orderedIds) {
            TodoItemEntity item = byId.get(id);
            boolean sameBucket = item != null && Objects.equals(item.getUserId(), userId)
                    && Objects.equals(item.getParentItemId(), parentItemId)
                    && (parentItemId != null || Objects.equals(item.getListId(), listId));
            if (!sameBucket) throw TodoException.badRequest("One or more items couldn't be reordered — try refreshing.");
            ordered.add(item);
        }
        for (int i = 0; i < ordered.size(); i++) ordered.get(i).setBoardPosition(i);
        itemRepository.saveAll(ordered);
    }

    /** Dashboard widget — direct replacement for the old flat TodoEntity's "pinned" query. */
    public List<TodoItemResponse> listStarredItems(Long userId) {
        return itemRepository.findByUserIdAndImportantTrueAndCompletedFalseOrderByCreatedAtDesc(userId).stream()
                .map(i -> toItemResponse(i, List.of())).collect(Collectors.toList());
    }

    // ============== Helpers ==============

    private static LocalDate parseDate(String s) {
        if (s == null || s.isBlank()) return null;
        try { return LocalDate.parse(s); } catch (Exception e) { throw TodoException.badRequest("dueDate must be in yyyy-MM-dd format"); }
    }

    private static <T> List<T> sortedByBoard(List<T> items, Function<T, Integer> posFn, Function<T, LocalDateTime> createdFn) {
        List<T> copy = new ArrayList<>(items);
        copy.sort((a, b) -> {
            int pa = posFn.apply(a) == null ? Integer.MAX_VALUE : posFn.apply(a);
            int pb = posFn.apply(b) == null ? Integer.MAX_VALUE : posFn.apply(b);
            if (pa != pb) return Integer.compare(pa, pb);
            return createdFn.apply(b).compareTo(createdFn.apply(a));
        });
        return copy;
    }

    private TodoFolderResponse toFolderResponse(TodoFolderEntity f) {
        return new TodoFolderResponse(f.getId(), f.getName(), f.getBoardPosition(),
                f.getCreatedAt() != null ? f.getCreatedAt().toString() : null,
                f.getUpdatedAt() != null ? f.getUpdatedAt().toString() : null);
    }

    private TodoListResponse toListResponse(TodoListEntity l, TodoItemRepository.TodoListItemCount count) {
        return new TodoListResponse(l.getId(), l.getName(), l.getFolderId(), l.getBoardPosition(),
                count != null ? count.getTotal() : 0L, count != null ? count.getCompletedCount() : 0L,
                l.getCreatedAt() != null ? l.getCreatedAt().toString() : null,
                l.getUpdatedAt() != null ? l.getUpdatedAt().toString() : null);
    }

    private TodoItemResponse toItemResponse(TodoItemEntity i, List<TodoItemEntity> subItems) {
        int subCount = subItems.size();
        int subCompleted = (int) subItems.stream().filter(s -> Boolean.TRUE.equals(s.getCompleted())).count();
        return new TodoItemResponse(i.getId(), i.getTitle(), i.getNotes(), i.getCompleted(), i.getImportant(),
                i.getDueDate() != null ? i.getDueDate().toString() : null, i.getListId(), i.getParentItemId(), i.getBoardPosition(),
                subCount, subCompleted,
                i.getCompletedAt() != null ? i.getCompletedAt().toString() : null,
                i.getCreatedAt() != null ? i.getCreatedAt().toString() : null,
                i.getUpdatedAt() != null ? i.getUpdatedAt().toString() : null);
    }
}
