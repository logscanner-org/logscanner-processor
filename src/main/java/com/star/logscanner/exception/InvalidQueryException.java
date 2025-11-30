package com.star.logscanner.exception;

import lombok.Getter;

@Getter
public class InvalidQueryException extends RuntimeException {
    
    private final String field;
    private final Object invalidValue;
    
    public InvalidQueryException(String message) {
        super(message);
        this.field = null;
        this.invalidValue = null;
    }
    
    public InvalidQueryException(String message, Throwable cause) {
        super(message, cause);
        this.field = null;
        this.invalidValue = null;
    }
    
    public InvalidQueryException(String field, Object invalidValue, String message) {
        super(message);
        this.field = field;
        this.invalidValue = invalidValue;
    }

    public static InvalidQueryException invalidField(String field, Object value, String reason) {
        return new InvalidQueryException(
                field, 
                value, 
                String.format("Invalid value '%s' for field '%s': %s", value, field, reason)
        );
    }
    
    public static InvalidQueryException unsupportedField(String field) {
        return new InvalidQueryException(
                field, 
                null, 
                String.format("Field '%s' is not supported for this operation", field)
        );
    }
    
    public static InvalidQueryException invalidDateRange() {
        return new InvalidQueryException(
                "dateRange", 
                null, 
                "Start date must be before end date"
        );
    }
    
    public static InvalidQueryException invalidSortField(String field) {
        return new InvalidQueryException(
                "sortBy", 
                field, 
                String.format("Cannot sort by field '%s'. Supported fields: timestamp, lineNumber, level, logger", field)
        );
    }
}
