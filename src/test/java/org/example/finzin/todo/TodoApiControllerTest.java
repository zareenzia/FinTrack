package org.example.finzin.todo;

import org.example.finzin.entity.TodoFolderEntity;
import org.example.finzin.entity.TodoItemEntity;
import org.example.finzin.entity.TodoListEntity;
import org.example.finzin.service.JwtTokenProvider;
import org.example.finzin.todo.dto.TodoFolderResponse;
import org.example.finzin.todo.dto.TodoItemDetailResponse;
import org.example.finzin.todo.dto.TodoItemResponse;
import org.example.finzin.todo.dto.TodoListResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Slice test for {@link TodoApiController}. */
@WebMvcTest(TodoApiController.class)
class TodoApiControllerTest {

    private static final Long USER_ID = 42L;

    @Autowired private MockMvc mockMvc;

    @MockitoBean private JwtTokenProvider jwtTokenProvider; // only needed so JwtAuthFilter can be constructed
    @MockitoBean private TodoService todoService;

    private TodoFolderEntity folder(Long id) {
        TodoFolderEntity f = new TodoFolderEntity();
        f.setId(id);
        f.setUserId(USER_ID);
        f.setName("Work");
        return f;
    }

    private TodoListEntity list(Long id) {
        TodoListEntity l = new TodoListEntity();
        l.setId(id);
        l.setUserId(USER_ID);
        l.setName("Groceries");
        return l;
    }

    private TodoItemEntity item(Long id) {
        TodoItemEntity i = new TodoItemEntity();
        i.setId(id);
        i.setUserId(USER_ID);
        i.setListId(1L);
        i.setTitle("Buy milk");
        return i;
    }

    // ============== Folders ==============

    @Test
    void listFolders_returnsMappedList() throws Exception {
        given(todoService.listFolders(USER_ID)).willReturn(List.of(
                new TodoFolderResponse(1L, "Work", 0, "2026-01-01T00:00:00", "2026-01-01T00:00:00")));

        mockMvc.perform(get("/api/todo/folders").requestAttr("userId", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("Work"));
    }

    @Test
    void listFolders_defaultsToUserOne_whenUnauthenticated() throws Exception {
        given(todoService.listFolders(1L)).willReturn(List.of());

        mockMvc.perform(get("/api/todo/folders"))
                .andExpect(status().isOk());

        verify(todoService).listFolders(1L);
    }

    @Test
    void createFolder_returnsCreatedId() throws Exception {
        given(todoService.createFolder(eq(USER_ID), any())).willReturn(folder(5L));

        mockMvc.perform(post("/api/todo/folders").requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Work\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(5));
    }

    @Test
    void createFolder_returnsBadRequest_whenNameBlank() throws Exception {
        given(todoService.createFolder(eq(USER_ID), any())).willThrow(TodoException.badRequest("Folder name is required"));

        mockMvc.perform(post("/api/todo/folders").requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Folder name is required"));
    }

    @Test
    void renameFolder_returnsNotFound_whenNotOwned() throws Exception {
        given(todoService.findOwnedFolder(USER_ID, 1L)).willReturn(Optional.empty());

        mockMvc.perform(put("/api/todo/folders/1").requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Renamed\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void renameFolder_returnsSuccess_onSuccess() throws Exception {
        TodoFolderEntity existing = folder(1L);
        given(todoService.findOwnedFolder(USER_ID, 1L)).willReturn(Optional.of(existing));
        given(todoService.renameFolder(existing, "Renamed")).willReturn(existing);

        mockMvc.perform(put("/api/todo/folders/1").requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Renamed\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void deleteFolder_returnsSuccess_onSuccess() throws Exception {
        TodoFolderEntity existing = folder(1L);
        given(todoService.findOwnedFolder(USER_ID, 1L)).willReturn(Optional.of(existing));

        mockMvc.perform(delete("/api/todo/folders/1").requestAttr("userId", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(todoService).deleteFolder(existing);
    }

    @Test
    void reorderFolders_returnsSuccess_onSuccess() throws Exception {
        mockMvc.perform(patch("/api/todo/folders/reorder").requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderedIds\":[1,2,3]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(todoService).reorderFolders(USER_ID, List.of(1L, 2L, 3L));
    }

    @Test
    void reorderFolders_returnsBadRequest_whenServiceRejects() throws Exception {
        org.mockito.Mockito.doThrow(TodoException.badRequest("orderedIds must not be empty"))
                .when(todoService).reorderFolders(eq(USER_ID), any());

        mockMvc.perform(patch("/api/todo/folders/reorder").requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderedIds\":[]}"))
                .andExpect(status().isBadRequest());
    }

    // ============== Lists ==============

    @Test
    void listLists_returnsMappedList() throws Exception {
        given(todoService.listLists(USER_ID)).willReturn(List.of(
                new TodoListResponse(1L, "Groceries", null, 0, 3L, 1L, "2026-01-01T00:00:00", "2026-01-01T00:00:00")));

        mockMvc.perform(get("/api/todo/lists").requestAttr("userId", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Groceries"))
                .andExpect(jsonPath("$[0].itemCount").value(3));
    }

    @Test
    void createList_returnsCreatedId() throws Exception {
        given(todoService.createList(eq(USER_ID), any())).willReturn(list(7L));

        mockMvc.perform(post("/api/todo/lists").requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Groceries\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(7));
    }

    @Test
    void renameList_returnsNotFound_whenNotOwned() throws Exception {
        given(todoService.findOwnedList(USER_ID, 1L)).willReturn(Optional.empty());

        mockMvc.perform(put("/api/todo/lists/1").requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Renamed\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void moveListToFolder_returnsSuccess_onSuccess() throws Exception {
        TodoListEntity existing = list(1L);
        given(todoService.findOwnedList(USER_ID, 1L)).willReturn(Optional.of(existing));
        given(todoService.moveListToFolder(existing, 9L)).willReturn(existing);

        mockMvc.perform(patch("/api/todo/lists/1/folder").requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"folderId\":9}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void deleteList_returnsSuccess_onSuccess() throws Exception {
        TodoListEntity existing = list(1L);
        given(todoService.findOwnedList(USER_ID, 1L)).willReturn(Optional.of(existing));

        mockMvc.perform(delete("/api/todo/lists/1").requestAttr("userId", USER_ID))
                .andExpect(status().isOk());

        verify(todoService).deleteList(existing);
    }

    @Test
    void reorderLists_returnsSuccess_onSuccess() throws Exception {
        mockMvc.perform(patch("/api/todo/lists/reorder").requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"folderId\":null,\"orderedIds\":[1,2]}"))
                .andExpect(status().isOk());

        verify(todoService).reorderLists(USER_ID, null, List.of(1L, 2L));
    }

    // ============== Items ==============

    @Test
    void listTopLevelItems_returnsMappedList() throws Exception {
        given(todoService.listTopLevelItems(USER_ID, 1L)).willReturn(List.of(
                new TodoItemResponse(1L, "Buy milk", null, false, false, null, 1L, null, 0, 0, 0, null, "2026-01-01T00:00:00", "2026-01-01T00:00:00")));

        mockMvc.perform(get("/api/todo/lists/1/items").requestAttr("userId", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Buy milk"));
    }

    @Test
    void listStarredItems_returnsMappedList() throws Exception {
        given(todoService.listStarredItems(USER_ID)).willReturn(List.of(
                new TodoItemResponse(2L, "Important task", null, false, true, null, 1L, null, 0, 0, 0, null, "2026-01-01T00:00:00", "2026-01-01T00:00:00")));

        mockMvc.perform(get("/api/todo/items/starred").requestAttr("userId", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].important").value(true));
    }

    @Test
    void getItemDetail_returnsNotFound_whenNotOwned() throws Exception {
        given(todoService.findOwnedItem(USER_ID, 1L)).willReturn(Optional.empty());

        mockMvc.perform(get("/api/todo/items/1").requestAttr("userId", USER_ID))
                .andExpect(status().isNotFound());
    }

    @Test
    void getItemDetail_returnsDetail_onSuccess() throws Exception {
        TodoItemEntity existing = item(1L);
        given(todoService.findOwnedItem(USER_ID, 1L)).willReturn(Optional.of(existing));
        TodoItemResponse itemResponse = new TodoItemResponse(1L, "Buy milk", null, false, false, null, 1L, null, 0, 0, 0, null,
                "2026-01-01T00:00:00", "2026-01-01T00:00:00");
        given(todoService.getItemDetail(existing)).willReturn(new TodoItemDetailResponse(itemResponse, List.of()));

        mockMvc.perform(get("/api/todo/items/1").requestAttr("userId", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.item.title").value("Buy milk"));
    }

    @Test
    void createItem_returnsCreatedId() throws Exception {
        given(todoService.createItem(eq(USER_ID), any())).willReturn(item(3L));

        mockMvc.perform(post("/api/todo/items").requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Buy milk\",\"listId\":1}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(3));
    }

    @Test
    void createItem_returnsBadRequest_whenNeitherListNorParentProvided() throws Exception {
        given(todoService.createItem(eq(USER_ID), any())).willThrow(
                TodoException.badRequest("Provide exactly one of listId (top-level item) or parentItemId (sub-item)"));

        mockMvc.perform(post("/api/todo/items").requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Buy milk\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createQuickItem_returnsCreatedId() throws Exception {
        given(todoService.createQuickItem(eq(USER_ID), any())).willReturn(item(4L));

        mockMvc.perform(post("/api/todo/items/quick").requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Call mom\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(4));
    }

    @Test
    void updateItem_returnsNotFound_whenNotOwned() throws Exception {
        given(todoService.findOwnedItem(USER_ID, 1L)).willReturn(Optional.empty());

        mockMvc.perform(put("/api/todo/items/1").requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Updated\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateItem_returnsSuccess_onSuccess() throws Exception {
        TodoItemEntity existing = item(1L);
        given(todoService.findOwnedItem(USER_ID, 1L)).willReturn(Optional.of(existing));
        given(todoService.updateItem(eq(existing), any())).willReturn(existing);

        mockMvc.perform(put("/api/todo/items/1").requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Updated\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void setCompleted_returnsSuccess_onSuccess() throws Exception {
        TodoItemEntity existing = item(1L);
        given(todoService.findOwnedItem(USER_ID, 1L)).willReturn(Optional.of(existing));
        given(todoService.setCompleted(existing, true)).willReturn(existing);

        mockMvc.perform(patch("/api/todo/items/1/complete").requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"completed\":true}"))
                .andExpect(status().isOk());

        verify(todoService).setCompleted(existing, true);
    }

    @Test
    void setImportant_returnsSuccess_onSuccess() throws Exception {
        TodoItemEntity existing = item(1L);
        given(todoService.findOwnedItem(USER_ID, 1L)).willReturn(Optional.of(existing));
        given(todoService.setImportant(existing, true)).willReturn(existing);

        mockMvc.perform(patch("/api/todo/items/1/important").requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"important\":true}"))
                .andExpect(status().isOk());

        verify(todoService).setImportant(existing, true);
    }

    @Test
    void deleteItem_returnsNotFound_whenNotOwned() throws Exception {
        given(todoService.findOwnedItem(USER_ID, 1L)).willReturn(Optional.empty());

        mockMvc.perform(delete("/api/todo/items/1").requestAttr("userId", USER_ID))
                .andExpect(status().isNotFound());

        verify(todoService, never()).deleteItem(any());
    }

    @Test
    void deleteItem_returnsSuccess_onSuccess() throws Exception {
        TodoItemEntity existing = item(1L);
        given(todoService.findOwnedItem(USER_ID, 1L)).willReturn(Optional.of(existing));

        mockMvc.perform(delete("/api/todo/items/1").requestAttr("userId", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(todoService).deleteItem(existing);
    }

    @Test
    void reorderItems_returnsSuccess_onSuccess() throws Exception {
        mockMvc.perform(patch("/api/todo/items/reorder").requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"listId\":1,\"parentItemId\":null,\"orderedIds\":[1,2,3]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(todoService).reorderItems(USER_ID, 1L, null, List.of(1L, 2L, 3L));
    }
}
