package vn.uth.itcomponentsecommerce.dto;

public class ApiFieldErrorResponse {

    private String fieldId;
    private String errorMessage;

    public ApiFieldErrorResponse() {
    }

    public ApiFieldErrorResponse(String fieldId, String errorMessage) {
        this.fieldId = fieldId;
        this.errorMessage = errorMessage;
    }

    public String getFieldId() { return fieldId; }
    public void setFieldId(String fieldId) { this.fieldId = fieldId; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}
