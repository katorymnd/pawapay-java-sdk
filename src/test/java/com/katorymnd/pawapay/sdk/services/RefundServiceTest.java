// src/test/java/com/katorymnd/pawapay/sdk/services/RefundServiceTest.java
package com.katorymnd.pawapay.sdk.services;

import com.katorymnd.pawapay.sdk.api.ApiClient;
import com.katorymnd.pawapay.sdk.exceptions.PawapayException;
import com.katorymnd.pawapay.sdk.models.RefundRequest;
import com.katorymnd.pawapay.sdk.models.RefundResponse;
import com.katorymnd.pawapay.sdk.models.TransactionStatus;

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
public class RefundServiceTest {

    @Mock
    private ApiClient mockApiClient;

    private RefundService refundService;

    @BeforeEach
    void setUp() {
        refundService = new RefundService(mockApiClient);
    }

    @Test
    @DisplayName("Should successfully initiate a refund (ACCEPTED) - V2")
    void testInitiateRefundSuccess() throws PawapayException {
        // 1. Arrange - Mock V2 API response
        Map<String, Object> apiResponse = new HashMap<>();
        apiResponse.put("status", 202); // HTTP 202 Accepted
        
        Map<String, Object> responseData = new HashMap<>();
        responseData.put("refundId", "r-8f2a1b3c-d4e5-4f67-a890-123456789abc");
        responseData.put("depositId", "d-1a2b3c4d-e5f6-4789-b012-345678901def");
        responseData.put("status", "ACCEPTED");
        responseData.put("nextStep", "FINAL_STATUS");
        apiResponse.put("response", responseData);

        // Mock initiateRefundV2: (refundId, depositId, amount, currency, metadata)
        Mockito.when(mockApiClient.initiateRefundV2(
                any(), any(), any(), any(), any()))
               .thenReturn(CompletableFuture.completedFuture(apiResponse));

        // 2. Act - Build request and call service
        RefundRequest request = new RefundRequest();
        request.setRefundId("r-8f2a1b3c-d4e5-4f67-a890-123456789abc");
        request.setDepositId("d-1a2b3c4d-e5f6-4789-b012-345678901def");
        request.setAmount("1000");
        request.setCurrency("UGX");
        request.setMetadata(createTestMetadata());

        RefundResponse response = refundService.initiateRefund(request);

        // 3. Assert
        assertNotNull(response, "Refund response should not be null");
        assertEquals("r-8f2a1b3c-d4e5-4f67-a890-123456789abc", response.getRefundId());
        assertEquals("d-1a2b3c4d-e5f6-4789-b012-345678901def", response.getDepositId());
        assertEquals("ACCEPTED", response.getStatus());
        assertEquals("FINAL_STATUS", response.getNextStep());
        
        Mockito.verify(mockApiClient, Mockito.times(1))
               .initiateRefundV2(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Should successfully fetch the status of an existing refund (COMPLETED)")
    void testGetRefundStatusSuccess() throws PawapayException {
        // 1. Arrange - Mock status response
        Map<String, Object> apiResponse = new HashMap<>();
        apiResponse.put("status", 200);
        
        Map<String, Object> responseData = new HashMap<>();
        responseData.put("refundId", "r-8f2a1b3c-d4e5-4f67-a890-123456789abc");
        responseData.put("status", "COMPLETED");
        apiResponse.put("response", responseData);

        // Mock checkTransactionStatusAuto for refund type
        Mockito.when(mockApiClient.checkTransactionStatusAuto(
                eq("r-8f2a1b3c-d4e5-4f67-a890-123456789abc"), eq("refund")))
               .thenReturn(CompletableFuture.completedFuture(apiResponse));

        // 2. Act
        TransactionStatus status = refundService.getRefundStatus("r-8f2a1b3c-d4e5-4f67-a890-123456789abc");

        // 3. Assert
        assertNotNull(status);
        assertEquals("COMPLETED", status.getStatus());
        assertEquals("r-8f2a1b3c-d4e5-4f67-a890-123456789abc", status.getDepositId());
    }

    @Test
    @DisplayName("Should throw PawapayException when the API returns an error")
    void testInitiateRefundThrowsException() {
        // 1. Arrange - Mock error response
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("status", 400);
        errorResponse.put("response", Collections.singletonMap("error", "Refund amount exceeds original deposit"));

        Mockito.when(mockApiClient.initiateRefundV2(
                any(), any(), any(), any(), any()))
               .thenReturn(CompletableFuture.completedFuture(errorResponse));

        //  CRITICAL: Populate request to avoid NPE before mock is invoked
        RefundRequest request = new RefundRequest();
        request.setRefundId("r-test");
        request.setDepositId("d-original");
        request.setAmount("1000");
        request.setCurrency("UGX");

        // 2 & 3. Act & Assert
        PawapayException exception = assertThrows(PawapayException.class, () -> {
            refundService.initiateRefund(request);
        });

        assertTrue(exception.getMessage().contains("Refund amount") || 
                   exception.getMessage().contains("failed") ||
                   exception.getMessage().contains("API error"));
    }

    // Helper to create test metadata matching TestRefund.java format
    @SuppressWarnings("unchecked")
    private List<Object> createTestMetadata() {
        List<Object> metadata = new java.util.ArrayList<>();
        
        Map<String, Object> meta1 = new HashMap<>();
        meta1.put("fieldName", "reason");
        meta1.put("fieldValue", "Customer request");
        metadata.add(meta1);
        
        Map<String, Object> meta2 = new HashMap<>();
        meta2.put("fieldName", "adminUser");
        meta2.put("fieldValue", "Admin-01");
        metadata.add(meta2);
        
        return metadata;
    }
}