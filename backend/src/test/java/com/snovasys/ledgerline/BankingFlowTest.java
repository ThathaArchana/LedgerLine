package com.snovasys.ledgerline;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "ledgerline.admin.email=admin-test@example.com",
        "ledgerline.admin.password=admin-test-password-123"
})
@AutoConfigureMockMvc
class BankingFlowTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void customerCanCreateAccountDepositOnceAndCannotOverdraw() throws Exception {
        String email = "banking-" + UUID.randomUUID() + "@example.com";
        MvcResult signupResult = mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Banking Test\",\"email\":\"" + email
                                + "\",\"password\":\"password123\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn();
        String adminToken = adminToken();
        approveUser(email, adminToken);
        String token = login(email);

        mockMvc.perform(get("/api/accounts"))
                .andExpect(status().isUnauthorized());

        MvcResult accountResult = mockMvc.perform(post("/api/accounts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currency\":\"INR\"}"))
                .andExpect(status().isOk())
                .andReturn();
        long accountId = objectMapper.readTree(accountResult.getResponse().getContentAsString())
                .get("id").asLong();

        String movement = "{\"accountId\":" + accountId + ",\"amount\":100.00,\"description\":\"Initial deposit\"}";
        String key = UUID.randomUUID().toString();
        MvcResult firstDeposit = mockMvc.perform(post("/api/transactions/deposit")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(movement))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ledgerEntries.length()").value(2))
                .andReturn();
        String transactionId = objectMapper.readTree(firstDeposit.getResponse().getContentAsString())
                .get("id").asText();

        mockMvc.perform(post("/api/transactions/deposit")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(movement))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(transactionId));

        mockMvc.perform(get("/api/accounts/" + accountId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(100.0));

        String otherEmail = "other-" + UUID.randomUUID() + "@example.com";
        MvcResult otherSignup = mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Other Customer\",\"email\":\"" + otherEmail
                                + "\",\"password\":\"password123\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        approveUser(otherEmail, adminToken);
        String otherToken = login(otherEmail);
        mockMvc.perform(get("/api/accounts/" + accountId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/transactions/withdraw")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountId\":" + accountId + ",\"amount\":101.00}"))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/accounts")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].ownerEmail").value(email));

        mockMvc.perform(get("/api/admin/transactions")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].ownerEmail").value(email))
                .andExpect(jsonPath("$[0].accountHolder").value("Banking Test"))
                .andExpect(jsonPath("$[0].transactionType").value("DEPOSIT"))
                .andExpect(jsonPath("$[0].amount").value(100.0));

        mockMvc.perform(get("/api/admin/accounts")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/transactions")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    private String adminToken() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin-test@example.com\",\"password\":\"admin-test-password-123\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }

    private String login(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }

    private void approveUser(String email, String adminToken) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode users = objectMapper.readTree(result.getResponse().getContentAsString());
        long userId = -1;
        for (JsonNode user : users) {
            if (email.equals(user.get("email").asText())) {
                userId = user.get("id").asLong();
                break;
            }
        }
        if (userId < 0) {
            throw new AssertionError("Signup request was not listed for admin approval.");
        }
        mockMvc.perform(post("/api/admin/users/" + userId + "/approve")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }
}