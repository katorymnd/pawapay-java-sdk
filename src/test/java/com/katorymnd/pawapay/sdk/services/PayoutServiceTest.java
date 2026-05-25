// src/test/java/com/katorymnd/pawapay/sdk/services/PayoutServiceTest.java
package com.katorymnd.pawapay.sdk.services;

import com.katorymnd.pawapay.sdk.api.ApiClient;
import com.katorymnd.pawapay.sdk.exceptions.PawapayException;
import com.katorymnd.pawapay.sdk.models.PayoutRequest;
import com.katorymnd.pawapay.sdk.models.PayoutResponse;
import com.katorymnd.pawapay.sdk.models.TransactionStatus;
import com.katorymnd.pawapay.sdk.models.common.Money;
import com.katorymnd.pawapay.sdk.models.common.Party;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class PayoutServiceTest {

    @Mock
    private ApiClient mockApiClient;

    private PayoutService payoutService;

    @BeforeEach
    void setUp() {
        payoutService = new PayoutService(mockApiClient);
    }

    @Test
    @DisplayName("Should successfully initiate a payout (ACCEPTED) - V2")
    void testInitiatePayoutSuccess() throws PawapayException {
        // 1. Arrange - Mock V2 API response
        Map<String, Object> apiResponse = new HashMap<>();
        apiResponse.put("status", 202); // HTTP 202 Accepted
        
        Map<String, Object> responseData = new HashMap<>();
        responseData.put("payoutId", "7a3b9c12-e456-4f89-a012-345678901234");
        responseData.put("status", "ACCEPTED");
        responseData.put("nextStep", "FINAL_STATUS");
        apiResponse.put("response", responseData);

        // Mock initiatePayoutV2: (payoutId, amount, currency, msisdn, provider, description, metadata)
        Mockito.when(mockApiClient.initiatePayoutV2(
                any(), any(), any(), any(), any(), any(), any()))
               .thenReturn(CompletableFuture.completedFuture(apiResponse));

        // 2. Act - Build request and call service
        PayoutRequest request = new PayoutRequest();
        request.setAmount(new Money("5000.00", "UGX"));
        request.setRecipient(new Party("MMO", "256783456789", "MTN_MOMO_UGA"));
        request.setCustomerMessage("Java SDK Payout Test");
        request.setMetadata(createTestMetadata());

        PayoutResponse response = payoutService.initiatePayout(request);

        // 3. Assert
        assertNotNull(response, "Payout response should not be null");
        assertEquals("7a3b9c12-e456-4f89-a012-345678901234", response.getPayoutId());
        assertEquals("ACCEPTED", response.getStatus());
        assertEquals("FINAL_STATUS", response.getNextStep());
        
        Mockito.verify(mockApiClient, Mockito.times(1))
               .initiatePayoutV2(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Should successfully fetch the status of an existing payout (COMPLETED)")
    void testGetPayoutStatusSuccess() throws PawapayException {
        // 1. Arrange - Mock status response
        Map<String, Object> apiResponse = new HashMap<>();
        apiResponse.put("status", 200);
        
        Map<String, Object> responseData = new HashMap<>();
        responseData.put("payoutId", "7a3b9c12-e456-4f89-a012-345678901234");
        responseData.put("status", "COMPLETED");
        apiResponse.put("response", responseData);

        // Mock checkTransactionStatusAuto for payout type
        Mockito.when(mockApiClient.checkTransactionStatusAuto(
                eq("7a3b9c12-e456-4f89-a012-345678901234"), eq("payout")))
               .thenReturn(CompletableFuture.completedFuture(apiResponse));

        // 2. Act
        TransactionStatus status = payoutService.getPayoutStatus("7a3b9c12-e456-4f89-a012-345678901234");

        // 3. Assert
        assertNotNull(status);
        assertEquals("COMPLETED", status.getStatus());
        assertEquals("7a3b9c12-e456-4f89-a012-345678901234", status.getDepositId()); // Reuses depositId field
    }

    @Test
    @DisplayName("Should throw PawapayException when the API returns an error")
    void testInitiatePayoutThrowsException() {
        // 1. Arrange - Mock error response
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("status", 400);
        errorResponse.put("response", Collections.singletonMap("error", "Insufficient balance for payout"));

        Mockito.when(mockApiClient.initiatePayoutV2(
                any(), any(), any(), any(), any(), any(), any()))
               .thenReturn(CompletableFuture.completedFuture(errorResponse));

        //   Populate request to avoid NPE before mock is invoked
        PayoutRequest request = new PayoutRequest();
        request.setAmount(new Money("100", "UGX"));
        request.setRecipient(new Party("MMO", "256783456789", "MTN_MOMO_UGA"));
        request.setCustomerMessage("Test");

        // 2 & 3. Act & Assert
        PawapayException exception = assertThrows(PawapayException.class, () -> {
            payoutService.initiatePayout(request);
        });

        assertTrue(exception.getMessage().contains("Insufficient balance") || 
                   exception.getMessage().contains("failed") ||
                   exception.getMessage().contains("API error"));
    }

    // Helper to create test metadata 
    @SuppressWarnings("unchecked")
    private List<Object> createTestMetadata() {
        List<Object> metadata = new java.util.ArrayList<>();
        
        Map<String, Object> meta1 = new HashMap<>();
        meta1.put("fieldName", "orderId");
        meta1.put("fieldValue", "ORD-998877");
        metadata.add(meta1);
        
        Map<String, Object> meta2 = new HashMap<>();
        meta2.put("fieldName", "customerId");
        meta2.put("fieldValue", "CUST-001");
        metadata.add(meta2);
        
        return metadata;
    }
}