// src/test/java/com/katorymnd/pawapay/sdk/services/TransactionServiceTest.java
package com.katorymnd.pawapay.sdk.services;

import com.katorymnd.pawapay.sdk.api.ApiClient;
import com.katorymnd.pawapay.sdk.exceptions.PawapayException;
import com.katorymnd.pawapay.sdk.models.TransactionStatus;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class TransactionServiceTest {

    @Mock
    private ApiClient mockApiClient;

    private TransactionService transactionService;

    @BeforeEach
    void setUp() {
        transactionService = new TransactionService(mockApiClient);
    }

    @Test
    @DisplayName("Should successfully check deposit status (COMPLETED)")
    void testCheckDepositStatusSuccess() throws PawapayException {
        // 1. Arrange - Mock API response
        Map<String, Object> apiResponse = new HashMap<>();
        apiResponse.put("status", 200);
        
        Map<String, Object> responseData = new HashMap<>();
        responseData.put("depositId", "d-1a2b3c4d-e5f6-4789-b012-345678901def");
        responseData.put("status", "COMPLETED");
        responseData.put("type", "deposit");
        responseData.put("amount", "5000.00");
        responseData.put("currency", "UGX");
        apiResponse.put("response", responseData);

        Mockito.when(mockApiClient.checkTransactionStatusAuto(
                eq("d-1a2b3c4d-e5f6-4789-b012-345678901def"), eq("deposit")))
               .thenReturn(CompletableFuture.completedFuture(apiResponse));

        // 2. Act
        TransactionStatus status = transactionService.checkDepositStatus("d-1a2b3c4d-e5f6-4789-b012-345678901def");

        // 3. Assert
        assertNotNull(status);
        assertEquals("COMPLETED", status.getStatus());
        assertEquals("deposit", status.getType());
        assertEquals("5000.00", status.getAmount());
        assertEquals("UGX", status.getCurrency());
    }

    @Test
    @DisplayName("Should successfully check payout status (PENDING)")
    void testCheckPayoutStatusSuccess() throws PawapayException {
        // 1. Arrange
        Map<String, Object> apiResponse = new HashMap<>();
        apiResponse.put("status", 200);
        
        Map<String, Object> responseData = new HashMap<>();
        responseData.put("payoutId", "p-9f8e7d6c-5b4a-3210-fedc-ba9876543210");
        responseData.put("status", "PENDING");
        responseData.put("type", "payout");
        apiResponse.put("response", responseData);

        Mockito.when(mockApiClient.checkTransactionStatusAuto(
                eq("p-9f8e7d6c-5b4a-3210-fedc-ba9876543210"), eq("payout")))
               .thenReturn(CompletableFuture.completedFuture(apiResponse));

        // 2. Act
        TransactionStatus status = transactionService.checkPayoutStatus("p-9f8e7d6c-5b4a-3210-fedc-ba9876543210");

        // 3. Assert
        assertNotNull(status);
        assertEquals("PENDING", status.getStatus());
        assertEquals("payout", status.getType());
    }

    @Test
    @DisplayName("Should successfully check refund status (ACCEPTED)")
    void testCheckRefundStatusSuccess() throws PawapayException {
        // 1. Arrange
        Map<String, Object> apiResponse = new HashMap<>();
        apiResponse.put("status", 200);
        
        Map<String, Object> responseData = new HashMap<>();
        responseData.put("refundId", "r-abc123-def456-ghi789");
        responseData.put("status", "ACCEPTED");
        responseData.put("type", "refund");
        apiResponse.put("response", responseData);

        Mockito.when(mockApiClient.checkTransactionStatusAuto(
                eq("r-abc123-def456-ghi789"), eq("refund")))
               .thenReturn(CompletableFuture.completedFuture(apiResponse));

        // 2. Act
        TransactionStatus status = transactionService.checkRefundStatus("r-abc123-def456-ghi789");

        // 3. Assert
        assertNotNull(status);
        assertEquals("ACCEPTED", status.getStatus());
        assertEquals("refund", status.getType());
    }

    @ParameterizedTest
    @ValueSource(strings = {"deposit", "payout", "refund"})
    @DisplayName("Should check status using generic method for all transaction types")
    void testCheckStatusGeneric(String type) throws PawapayException {
        // 1. Arrange
        String txId = "tx-" + type + "-123";
        Map<String, Object> apiResponse = new HashMap<>();
        apiResponse.put("status", 200);
        
        Map<String, Object> responseData = new HashMap<>();
        responseData.put("status", "PROCESSING");
        responseData.put("type", type);
        apiResponse.put("response", responseData);

        Mockito.when(mockApiClient.checkTransactionStatusAuto(eq(txId), eq(type)))
               .thenReturn(CompletableFuture.completedFuture(apiResponse));

        // 2. Act
        TransactionStatus status = transactionService.checkStatus(txId, type);

        // 3. Assert
        assertNotNull(status);
        assertEquals("PROCESSING", status.getStatus());
        assertEquals(type, status.getType());
    }

    @Test
    @DisplayName("Should throw PawapayException with 404 when transaction not found")
    void testCheckStatusNotFound() {
        // 1. Arrange - Mock 404 response
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("status", 404);
        errorResponse.put("response", "Transaction not found");

        Mockito.when(mockApiClient.checkTransactionStatusAuto(
                eq("nonexistent-id"), eq("deposit")))
               .thenReturn(CompletableFuture.completedFuture(errorResponse));

        // 2 & 3. Act & Assert
        PawapayException exception = assertThrows(PawapayException.class, () -> {
            transactionService.checkDepositStatus("nonexistent-id");
        });

        assertTrue(exception.getMessage().contains("not found"));
        assertEquals(404, exception.getStatusCode());
    }

    @Test
    @DisplayName("Should throw PawapayException when API returns server error")
    void testCheckStatusServerError() {
        // 1. Arrange - Mock 500 response
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("status", 500);
        errorResponse.put("response", "Internal server error");

        Mockito.when(mockApiClient.checkTransactionStatusAuto(
                eq("tx-error"), eq("deposit")))
               .thenReturn(CompletableFuture.completedFuture(errorResponse));

        // 2 & 3. Act & Assert
        PawapayException exception = assertThrows(PawapayException.class, () -> {
            transactionService.checkDepositStatus("tx-error");
        });

        assertTrue(exception.getMessage().contains("failed") || exception.getMessage().contains("error"));
        assertEquals(500, exception.getStatusCode());
    }

    @Test
    @DisplayName("Should throw PawapayException for invalid transaction type")
    void testCheckStatusInvalidType() {
        // Act & Assert
        PawapayException exception = assertThrows(PawapayException.class, () -> {
            transactionService.checkStatus("tx-123", "invalid_type");
        });

        assertTrue(exception.getMessage().contains("Invalid transaction type"));
    }

    @Test
    @DisplayName("Should default to 'deposit' type when null is provided")
    void testCheckStatusNullTypeDefaultsToDeposit() throws PawapayException {
        // 1. Arrange
        Map<String, Object> apiResponse = new HashMap<>();
        apiResponse.put("status", 200);
        apiResponse.put("response", new HashMap<>());

        Mockito.when(mockApiClient.checkTransactionStatusAuto(
                eq("tx-default"), eq("deposit")))
               .thenReturn(CompletableFuture.completedFuture(apiResponse));

        // 2. Act - Pass null type, should default to "deposit"
        TransactionStatus status = transactionService.checkStatus("tx-default", null);

        // 3. Assert
        assertNotNull(status);
        Mockito.verify(mockApiClient).checkTransactionStatusAuto(eq("tx-default"), eq("deposit"));
    }
}