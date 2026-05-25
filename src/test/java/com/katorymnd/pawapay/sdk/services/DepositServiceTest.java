package com.katorymnd.pawapay.sdk.services;

import com.katorymnd.pawapay.sdk.api.ApiClient;
import com.katorymnd.pawapay.sdk.exceptions.PawapayException;
import com.katorymnd.pawapay.sdk.models.DepositRequest;
import com.katorymnd.pawapay.sdk.models.DepositResponse;
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
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT) // ← Relaxes stubbing strictness globally
public class DepositServiceTest {

    @Mock
    private ApiClient mockApiClient;

    private DepositService depositService;

    @BeforeEach
    void setUp() {
        depositService = new DepositService(mockApiClient);
    }

    @Test
    @DisplayName("Should successfully initiate a deposit (ACCEPTED)")
    void testInitiateDepositSuccess() throws PawapayException {
        // 1. Arrange
        Map<String, Object> apiResponse = new HashMap<>();
        apiResponse.put("status", 202);
        
        Map<String, Object> responseData = new HashMap<>();
        responseData.put("depositId", "9d57e6a2-d084-4917-bead-4583016b5701");
        responseData.put("status", "ACCEPTED");
        responseData.put("nextStep", "FINAL_STATUS");
        apiResponse.put("response", responseData);

        Mockito.when(mockApiClient.initiateDepositV2(
                any(), any(), any(), any(), any(), any(), any(), any(), any()))
               .thenReturn(CompletableFuture.completedFuture(apiResponse));

        // 2. Act
        DepositRequest request = new DepositRequest();
        request.setAmount(new Money("5000.00", "UGX"));
        request.setPayer(new Party("MMO", "256783456789", "MTN_MOMO_UGA"));
        request.setCustomerMessage("Java SDK Test");

        DepositResponse response = depositService.initiateDeposit(request);

        // 3. Assert
        assertNotNull(response, "Deposit response should not be null");
        assertEquals("9d57e6a2-d084-4917-bead-4583016b5701", response.getDepositId());
        assertEquals("ACCEPTED", response.getStatus());
        assertEquals("FINAL_STATUS", response.getNextStep());
        
        Mockito.verify(mockApiClient, Mockito.times(1))
               .initiateDepositV2(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Should successfully fetch the status of an existing deposit (COMPLETED)")
    void testGetDepositStatusSuccess() throws PawapayException {
        // 1. Arrange
        Map<String, Object> apiResponse = new HashMap<>();
        apiResponse.put("status", 200);
        
        Map<String, Object> responseData = new HashMap<>();
        responseData.put("depositId", "9d57e6a2-d084-4917-bead-4583016b5701");
        responseData.put("status", "COMPLETED");
        apiResponse.put("response", responseData);

        Mockito.when(mockApiClient.checkTransactionStatusAuto(
                eq("9d57e6a2-d084-4917-bead-4583016b5701"), eq("deposit")))
               .thenReturn(CompletableFuture.completedFuture(apiResponse));

        // 2. Act
        TransactionStatus status = depositService.getDepositStatus("9d57e6a2-d084-4917-bead-4583016b5701");

        // 3. Assert
        assertNotNull(status);
        assertEquals("COMPLETED", status.getStatus());
        assertEquals("9d57e6a2-d084-4917-bead-4583016b5701", status.getDepositId());
    }

    @Test
    @DisplayName("Should throw PawapayException when the API returns an error")
    void testInitiateDepositThrowsException() {
        // 1. Arrange - Mock error response
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("status", 400);
        errorResponse.put("response", Collections.singletonMap("error", "Insufficient funds"));

        Mockito.when(mockApiClient.initiateDepositV2(
                any(), any(), any(), any(), any(), any(), any(), any(), any()))
               .thenReturn(CompletableFuture.completedFuture(errorResponse));

        //  Populate request with valid data so DepositService doesn't NPE before calling API
        DepositRequest request = new DepositRequest();
        request.setAmount(new Money("100", "UGX"));              // ← Prevents NPE on getAmount()
        request.setPayer(new Party("MMO", "256783456789", "MTN_MOMO_UGA")); // ← Prevents NPE on getPayer()
        request.setCustomerMessage("Test");

        // 2 & 3. Act & Assert
        PawapayException exception = assertThrows(PawapayException.class, () -> {
            depositService.initiateDeposit(request);
        });

        assertTrue(exception.getMessage().contains("Insufficient funds") || 
                   exception.getMessage().contains("failed") ||
                   exception.getMessage().contains("API error"));
    }
}