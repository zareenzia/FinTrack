package org.example.finzin.todo;

import jakarta.servlet.http.HttpServletRequest;
import org.example.finzin.entity.TodoFolderEntity;
import org.example.finzin.entity.TodoItemEntity;
import org.example.finzin.entity.TodoListEntity;
import org.example.finzin.todo.dto.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

@RestController
@RequestMapping("/api/todo")
public class TodoApiController {

    private final TodoService todoService;

    public TodoApiController(TodoService todoService) {
        this.todoService = todoService;
    }

    private Long getUserId(HttpServletRequest request) {
        Object userId = request.getAttribute("userId");
        return userId != null ? (Long) userId : 1L;
    }

    // ============== Folders ==============

    @GetMapping("/folders")
    public List<TodoFolderResponse> listFolders(HttpServletRequest request) {
        return todoService.listFolders(getUserId(request));
    }

    @PostMapping("/folders")
    public ResponseEntity<?> createFolder(HttpServletRequest request, @RequestBody TodoFolderRequest body) {
        try {
            TodoFolderEntity saved = todoService.createFolder(getUserId(request), body);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", saved.getId()));
        } catch (TodoException e) {
            return mapException(e);
        }
    }

    @PutMapping("/folders/{id}")
    public ResponseEntity<?> renameFolder(HttpServletRequest request, @PathVariable Long id, @RequestBody TodoFolderRequest body) {
        return withOwnedFolder(request, id, existing -> {
            todoService.renameFolder(existing, body.name());
            return Map.of("success", true);
        });
    }

    @DeleteMapping("/folders/{id}")
    public ResponseEntity<?> deleteFolder(HttpServletRequest request, @PathVariable Long id) {
        return withOwnedFolder(request, id, existing -> {
            todoService.deleteFolder(existing);
            return Map.of("success", true);
        });
    }

    @PatchMapping("/folders/reorder")
    public ResponseEntity<?> reorderFolders(HttpServletRequest request, @RequestBody OrderedIdsRequest body) {
        try {
            todoService.reorderFolders(getUserId(request), body.orderedIds());
            return ResponseEntity.ok(Map.of("success", true));
        } catch (TodoException e) {
            return mapException(e);
        }
    }

    // ============== Lists ==============

    @GetMapping("/lists")
    public List<TodoListResponse> listLists(HttpServletRequest request) {
        return todoService.listLists(getUserId(request));
    }

    @PostMapping("/lists")
    public ResponseEntity<?> createList(HttpServletRequest request, @RequestBody TodoListRequest body) {
        try {
            TodoListEntity saved = todoService.createList(getUserId(request), body);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", saved.getId()));
        } catch (TodoException e) {
            return mapException(e);
        }
    }

    @PutMapping("/lists/{id}")
    public ResponseEntity<?> renameList(HttpServletRequest request, @PathVariable Long id, @RequestBody TodoListRequest body) {
        return withOwnedList(request, id, existing -> {
            todoService.renameList(existing, body.name());
            return Map.of("success", true);
        });
    }

    @PatchMapping("/lists/{id}/folder")
    public ResponseEntity<?> moveListToFolder(HttpServletRequest request, @PathVariable Long id, @RequestBody FolderMoveRequest body) {
        return withOwnedList(request, id, existing -> {
            todoService.moveListToFolder(existing, body.folderId());
            return Map.of("success", true);
        });
    }

    @DeleteMapping("/lists/{id}")
    public ResponseEntity<?> deleteList(HttpServletRequest request, @PathVariable Long id) {
        return withOwnedList(request, id, existing -> {
            todoService.deleteList(existing);
            return Map.of("success", true);
        });
    }

    @PatchMapping("/lists/reorder")
    public ResponseEntity<?> reorderLists(HttpServletRequest request, @RequestBody ListReorderRequest body) {
        try {
            todoService.reorderLists(getUserId(request), body.folderId(), body.orderedIds());
            return ResponseEntity.ok(Map.of("success", true));
        } catch (TodoException e) {
            return mapException(e);
        }
    }

    // ============== Items ==============

    @GetMapping("/lists/{listId}/items")
    public List<TodoItemResponse> listTopLevelItems(HttpServletRequest request, @PathVariable Long listId) {
        return todoService.listTopLevelItems(getUserId(request), listId);
    }

    @GetMapping("/items/starred")
    public List<TodoItemResponse> listStarredItems(HttpServletRequest request) {
        return todoService.listStarredItems(getUserId(request));
    }

    @GetMapping("/items/{id}")
    public ResponseEntity<?> getItemDetail(HttpServletRequest request, @PathVariable Long id) {
        Long userId = getUserId(request);
        return todoService.findOwnedItem(userId, id)
                .<ResponseEntity<?>>map(item -> ResponseEntity.ok(todoService.getItemDetail(item)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/items")
    public ResponseEntity<?> createItem(HttpServletRequest request, @RequestBody TodoItemRequest body) {
        try {
            TodoItemEntity saved = todoService.createItem(getUserId(request), body);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", saved.getId()));
        } catch (TodoException e) {
            return mapException(e);
        }
    }

    @PostMapping("/items/quick")
    public ResponseEntity<?> createQuickItem(HttpServletRequest request, @RequestBody QuickItemRequest body) {
        try {
            TodoItemEntity saved = todoService.createQuickItem(getUserId(request), body);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", saved.getId()));
        } catch (TodoException e) {
            return mapException(e);
        }
    }

    @PutMapping("/items/{id}")
    public ResponseEntity<?> updateItem(HttpServletRequest request, @PathVariable Long id, @RequestBody TodoItemUpdateRequest body) {
        return withOwnedItem(request, id, existing -> {
            todoService.updateItem(existing, body);
            return Map.of("success", true);
        });
    }

    @PatchMapping("/items/{id}/complete")
    public ResponseEntity<?> setCompleted(HttpServletRequest request, @PathVariable Long id, @RequestBody CompletedRequest body) {
        return withOwnedItem(request, id, existing -> {
            todoService.setCompleted(existing, Boolean.TRUE.equals(body.completed()));
            return Map.of("success", true);
        });
    }

    @PatchMapping("/items/{id}/important")
    public ResponseEntity<?> setImportant(HttpServletRequest request, @PathVariable Long id, @RequestBody ImportantRequest body) {
        return withOwnedItem(request, id, existing -> {
            todoService.setImportant(existing, Boolean.TRUE.equals(body.important()));
            return Map.of("success", true);
        });
    }

    @DeleteMapping("/items/{id}")
    public ResponseEntity<?> deleteItem(HttpServletRequest request, @PathVariable Long id) {
        return withOwnedItem(request, id, existing -> {
            todoService.deleteItem(existing);
            return Map.of("success", true);
        });
    }

    @PatchMapping("/items/reorder")
    public ResponseEntity<?> reorderItems(HttpServletRequest request, @RequestBody ItemReorderRequest body) {
        try {
            todoService.reorderItems(getUserId(request), body.listId(), body.parentItemId(), body.orderedIds());
            return ResponseEntity.ok(Map.of("success", true));
        } catch (TodoException e) {
            return mapException(e);
        }
    }

    // ============== Shared helpers ==============

    private ResponseEntity<?> withOwnedFolder(HttpServletRequest request, Long id, Function<TodoFolderEntity, Object> mutation) {
        Long userId = getUserId(request);
        return todoService.findOwnedFolder(userId, id)
                .<ResponseEntity<?>>map(existing -> {
                    try { return ResponseEntity.ok(mutation.apply(existing)); }
                    catch (TodoException e) { return mapException(e); }
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private ResponseEntity<?> withOwnedList(HttpServletRequest request, Long id, Function<TodoListEntity, Object> mutation) {
        Long userId = getUserId(request);
        return todoService.findOwnedList(userId, id)
                .<ResponseEntity<?>>map(existing -> {
                    try { return ResponseEntity.ok(mutation.apply(existing)); }
                    catch (TodoException e) { return mapException(e); }
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private ResponseEntity<?> withOwnedItem(HttpServletRequest request, Long id, Function<TodoItemEntity, Object> mutation) {
        Long userId = getUserId(request);
        return todoService.findOwnedItem(userId, id)
                .<ResponseEntity<?>>map(existing -> {
                    try { return ResponseEntity.ok(mutation.apply(existing)); }
                    catch (TodoException e) { return mapException(e); }
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private ResponseEntity<?> mapException(TodoException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getUserMessage()));
    }
}
