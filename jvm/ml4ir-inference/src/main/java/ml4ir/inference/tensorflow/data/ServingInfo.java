package ml4ir.inference.tensorflow.data;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ServingInfo {
    @JsonProperty("name")
    private String name;

    @JsonProperty("required")
    private boolean isRequired;

    public String getName() {
        return name;
    }
}