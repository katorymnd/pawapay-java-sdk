// src/main/java/com/katorymnd/pawapay/sdk/utils/Validator.java
package com.katorymnd.pawapay.sdk.utils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * A surgical, idiomatic Java translation of the original PHP Validator class
 * from Katorymnd.PawaPayIntegration.Utils.
 */
public class Validator {
    
    private Validator() {
        // Private constructor to prevent instantiation
    }
    
    /**
     * Validate that the input has only alphanumeric characters and spaces
     *
     * @param inputStr Input string to validate
     * @return inputStr if valid
     * @throws IllegalArgumentException with suggested correction when invalid characters are present
     */
    public static String validateAlphanumeric(String inputStr) {
        if (inputStr == null) {
            throw new IllegalArgumentException("Input must be a string.");
        }
        
        Pattern invalidCharPattern = Pattern.compile("[^a-zA-Z0-9 ]");
        Matcher matcher = invalidCharPattern.matcher(inputStr);
        
        if (matcher.find()) {
            String suggestedInput = matcher.replaceAll("");
            throw new IllegalArgumentException(
                String.format("The statement description contains invalid characters. " +
                    "Only alphanumeric characters and spaces are allowed. " +
                    "Suggested correction: '%s'", suggestedInput)
            );
        }
        
        return inputStr;
    }
    
    /**
     * Validate that the length of the input does not exceed the specified max length
     *
     * @param inputStr Input string to validate
     * @param maxLength Maximum allowed length
     * @return inputStr if valid
     * @throws IllegalArgumentException with suggested truncation when too long
     */
    public static String validateLength(String inputStr, int maxLength) {
        if (inputStr == null) {
            throw new IllegalArgumentException("Input must be a string.");
        }
        
        if (maxLength < 0) {
            throw new IllegalArgumentException("maxLength must be a non-negative integer.");
        }
        
        if (inputStr.length() > maxLength) {
            String suggestedInput = inputStr.substring(0, maxLength);
            throw new IllegalArgumentException(
                String.format("The statement description exceeds the allowed length of %d characters. " +
                    "Suggested correction: '%s'", maxLength, suggestedInput)
            );
        }
        
        return inputStr;
    }
    
    /**
     * Full validation function: length + alphanumeric characters
     *
     * @param inputStr Input string to validate
     * @param maxLength Maximum allowed length (default: 22 if using overloaded method)
     * @return inputStr if validation passes
     * @throws IllegalArgumentException if validation fails
     */
    public static String validateStatementDescription(String inputStr, int maxLength) {
        // Step 1: Ensure input has only alphanumeric characters and spaces
        validateAlphanumeric(inputStr);
        
        // Step 2: Ensure length doesn't exceed the limit
        validateLength(inputStr, maxLength);
        
        return inputStr;
    }
    
    /**
     * Full validation function with default max length of 22
     *
     * @param inputStr Input string to validate
     * @return inputStr if validation passes
     * @throws IllegalArgumentException if validation fails
     */
    public static String validateStatementDescription(String inputStr) {
        return validateStatementDescription(inputStr, 22);
    }
    
    /**
     * Combined regex and logical validation for amount.
     *
     * Mirrors the PHP behavior:
     * - First checks regex: /^([0]|([1-9][0-9]{0,17}))([.][0-9]{0,2})?$/
     *   (up to 18 digits before decimal, up to 2 decimal places)
     * - Then enforces "NotBlank" and "Positive" semantics: not blank, > 0
     *
     * @param amount Amount to validate (can be String, Number, etc.)
     * @return The original amount string (trimmed)
     * @throws IllegalArgumentException with descriptive message when invalid
     */
    public static String validateAmount(Object amount) {
        // Normalize input, preserve trimmed string for messages
        String amountStr = amount == null ? "" : amount.toString().trim();
        
        // pawaPay's pattern: up to 18 digits before decimal, optional decimal with up to 2 places
        Pattern pattern = Pattern.compile("^([0]|([1-9][0-9]{0,17}))([.][0-9]{0,2})?$");
        
        // Validate presence
        if (amountStr.isEmpty()) {
            throw new IllegalArgumentException("This value should not be blank.");
        }
        
        // Validate pattern
        Matcher matcher = pattern.matcher(amountStr);
        if (!matcher.matches()) {
            throw new IllegalArgumentException(
                String.format("The amount '%s' is invalid. " +
                    "The amount must be a number with up to 18 digits before the decimal point " +
                    "and up to 2 decimal places.", amountStr)
            );
        }
        
        // Convert to BigDecimal for accurate numeric validation
        BigDecimal numeric;
        try {
            numeric = new BigDecimal(amountStr);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                String.format("The amount '%s' is not a valid number.", amountStr)
            );
        }
        
        // Enforce Positive (disallows zero)
        if (numeric.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("This value should be positive.");
        }
        
        return amountStr;
    }
    
    /**
     * Constraint types for Joi validation
     */
    public static class Constraint {
        public static final String NOT_BLANK = "NotBlank";
        public static final String POSITIVE = "Positive";
        public static final String LENGTH = "Length";
        public static final String REGEX = "Regex";
        
        private Constraint() {}
    }
    
    /**
     * General lightweight validator to mimic Symfony behavior for simple constraints.
     *
     * The function throws the first encountered violation as an IllegalArgumentException.
     *
     * @param data Data to validate
     * @param constraints List of constraint maps
     * @return data when valid
     * @throws IllegalArgumentException with first violation message
     */
    public static Object joiValidate(Object data, List<Map<String, Object>> constraints) {
        for (Map<String, Object> constraint : constraints) {
            if (constraint == null || !constraint.containsKey("type")) {
                continue;
            }
            
            String constraintType = (String) constraint.get("type");
            
            switch (constraintType) {
                case Constraint.NOT_BLANK:
                    if (data == null || 
                        (data instanceof String && ((String) data).trim().isEmpty())) {
                        String message = constraint.containsKey("message") ? 
                            (String) constraint.get("message") : "This value should not be blank.";
                        throw new IllegalArgumentException(message);
                    }
                    break;
                    
                case Constraint.POSITIVE:
                    try {
                        double numeric;
                        if (data instanceof Number) {
                            numeric = ((Number) data).doubleValue();
                        } else {
                            numeric = Double.parseDouble(data.toString());
                        }
                        if (numeric <= 0) {
                            String message = constraint.containsKey("message") ? 
                                (String) constraint.get("message") : "This value should be positive.";
                            throw new IllegalArgumentException(message);
                        }
                    } catch (NumberFormatException e) {
                        String message = constraint.containsKey("message") ? 
                            (String) constraint.get("message") : "This value should be positive.";
                        throw new IllegalArgumentException(message);
                    }
                    break;
                    
                case Constraint.LENGTH:
                    if (data instanceof String) {
                        String strData = (String) data;
                        if (constraint.containsKey("max") && constraint.get("max") != null) {
                            int max = ((Number) constraint.get("max")).intValue();
                            if (strData.length() > max) {
                                String msg = constraint.containsKey("max_message") ? 
                                    (String) constraint.get("max_message") : 
                                    "This value is too long. It should have {{ limit }} characters or less.";
                                msg = msg.replace("{{ limit }}", String.valueOf(max));
                                throw new IllegalArgumentException(msg);
                            }
                        }
                        
                        if (constraint.containsKey("min") && constraint.get("min") != null) {
                            int min = ((Number) constraint.get("min")).intValue();
                            if (strData.length() < min) {
                                String msg = constraint.containsKey("min_message") ? 
                                    (String) constraint.get("min_message") : 
                                    "This value is too short. It should have {{ limit }} characters or more.";
                                msg = msg.replace("{{ limit }}", String.valueOf(min));
                                throw new IllegalArgumentException(msg);
                            }
                        }
                    }
                    break;
                    
                case Constraint.REGEX:
                    if (constraint.containsKey("pattern")) {
                        Object patternObj = constraint.get("pattern");
                        Pattern regex;
                        if (patternObj instanceof Pattern) {
                            regex = (Pattern) patternObj;
                        } else if (patternObj instanceof String) {
                            regex = Pattern.compile((String) patternObj);
                        } else {
                            continue;
                        }
                        
                        if (!(data instanceof String) || !regex.matcher((String) data).matches()) {
                            String message = constraint.containsKey("message") ? 
                                (String) constraint.get("message") : "This value is not valid.";
                            throw new IllegalArgumentException(message);
                        }
                    }
                    break;
            }
        }
        
        return data;
    }
    
    /**
     * Validate that the number of metadata items does not exceed 10
     *
     * @param metadata Metadata list to validate
     * @return metadata if valid
     * @throws IllegalArgumentException when count > 10
     */
    public static List<?> validateMetadataItemCount(List<?> metadata) {
        if (metadata == null) {
            throw new IllegalArgumentException("Metadata must be a list.");
        }
        
        if (metadata.size() > 10) {
            throw new IllegalArgumentException(
                String.format("Number of metadata items must not be more than 10. " +
                    "You provided %d items.", metadata.size())
            );
        }
        
        return metadata;
    }
    
    /**
     * Validate individual metadata fields: field_name and field_value
     *
     * @param fieldName Metadata field name
     * @param fieldValue Metadata field value
     * @return Map with validated field_name and field_value
     * @throws IllegalArgumentException if validation fails
     */
    public static Map<String, String> validateMetadataField(String fieldName, String fieldValue) {
        // Define constraints for field_name
        List<Map<String, Object>> fieldNameConstraints = List.of(
            Map.of("type", Constraint.NOT_BLANK, "message", "Metadata field name cannot be blank."),
            Map.of("type", Constraint.LENGTH, "max", 50,
                "max_message", "Metadata field name cannot exceed {{ limit }} characters."),
            Map.of("type", Constraint.REGEX, "pattern", "^[a-zA-Z0-9_ ]+$",
                "message", "Metadata field name can only contain alphanumeric characters, underscores, and spaces.")
        );
        
        // Define constraints for field_value
        List<Map<String, Object>> fieldValueConstraints = List.of(
            Map.of("type", Constraint.NOT_BLANK, "message", "Metadata field value cannot be blank."),
            Map.of("type", Constraint.LENGTH, "max", 100,
                "max_message", "Metadata field value cannot exceed {{ limit }} characters."),
            Map.of("type", Constraint.REGEX, "pattern", "^[a-zA-Z0-9_\\-., ]+$",
                "message", "Metadata field value can only contain alphanumeric characters, " +
                    "underscores, hyphens, periods, commas, and spaces.")
        );
        
        // Validate field_name
        joiValidate(fieldName, fieldNameConstraints);
        
        // Validate field_value
        joiValidate(fieldValue, fieldValueConstraints);
        
        Map<String, String> result = new HashMap<>();
        result.put("field_name", fieldName);
        result.put("field_value", fieldValue);
        return result;
    }
}