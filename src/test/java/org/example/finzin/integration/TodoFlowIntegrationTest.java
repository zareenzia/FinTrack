package org.example.finzin.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Real HTTP flow through TodoApiController + TodoService + real Postgres: create a folder, a list
 * inside it, and an item inside that list, then complete and star the item and confirm every
 * change is visible through the real GET endpoints.
 */
class TodoFlowIntegrationTest extends AbstractApiIntegrationTest {

    @Test
    void creatingAndCompletingATodoItemIsReflectedAcrossEndpoints() throws Exception {
        RegisteredUser user = registerUser("todoflow");

        Map<String, Object> folderBody = Map.of("name", "Personal " + uniqueSuffix());
        MvcResult folderResult = mockMvc.perform(post("/api/todo/folders")
                        .header("Authorization", user.authHeader())
                        .contentType(JSON)
                        .content(objectMapper.writeValueAsString(folderBody)))
                .andExpect(status().isCreated())
                .andReturn();
        long folderId = bodyOf(folderResult).get("id").asLong();

        Map<String, Object> listBody = new LinkedHashMap<>();
        listBody.put("name", "Groceries " + uniqueSuffix());
        listBody.put("folderId", folderId);
        MvcResult listResult = mockMvc.perform(post("/api/todo/lists")
                        .header("Authorization", user.authHeader())
                        .contentType(JSON)
                        .content(objectMapper.writeValueAsString(listBody)))
                .andExpect(status().isCreated())
                .andReturn();
        long listId = bodyOf(listResult).get("id").asLong();

        Map<String, Object> itemBody = new LinkedHashMap<>();
        itemBody.put("title", "Buy milk");
        itemBody.put("notes", "2% please");
        itemBody.put("listId", listId);
        MvcResult itemResult = mockMvc.perform(post("/api/todo/items")
                        .header("Authorization", user.authHeader())
                        .contentType(JSON)
                        .content(objectMapper.writeValueAsString(itemBody)))
                .andExpect(status().isCreated())
                .andReturn();
        long itemId = bodyOf(itemResult).get("id").asLong();

        MvcResult topLevelResult = mockMvc.perform(get("/api/todo/lists/" + listId + "/items")
                        .header("Authorization", user.authHeader()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode topLevel = bodyOf(topLevelResult);
        assertEquals(1, topLevel.size());
        assertEquals("Buy milk", topLevel.get(0).get("title").asText());
        assertFalse(topLevel.get(0).get("completed").asBoolean());

        Map<String, Object> completeBody = Map.of("completed", true);
        mockMvc.perform(patch("/api/todo/items/" + itemId + "/complete")
                        .header("Authorization", user.authHeader())
                        .contentType(JSON)
                        .content(objectMapper.writeValueAsString(completeBody)))
                .andExpect(status().isOk());

        Map<String, Object> importantBody = Map.of("important", true);
        mockMvc.perform(patch("/api/todo/items/" + itemId + "/important")
                        .header("Authorization", user.authHeader())
                        .contentType(JSON)
                        .content(objectMapper.writeValueAsString(importantBody)))
                .andExpect(status().isOk());

        MvcResult detailResult = mockMvc.perform(get("/api/todo/items/" + itemId)
                        .header("Authorization", user.authHeader()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode detail = bodyOf(detailResult);
        JsonNode item = detail.get("item");
        assertTrue(item.get("completed").asBoolean());
        assertTrue(item.get("important").asBoolean());
        assertTrue(item.has("completedAt") && !item.get("completedAt").isNull());

        MvcResult listsResult = mockMvc.perform(get("/api/todo/lists")
                        .header("Authorization", user.authHeader()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode lists = bodyOf(listsResult);
        JsonNode ourList = null;
        for (JsonNode l : lists) {
            if (l.get("id").asLong() == listId) {
                ourList = l;
                break;
            }
        }
        assertTrue(ourList != null, "Expected to find the created list in /api/todo/lists");
        assertEquals(1, ourList.get("itemCount").asLong());
        assertEquals(1, ourList.get("completedItemCount").asLong());
    }
}
