package searchengine.dto.startIndexing;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class StartIndexingResponce {

     private boolean result;
     private String error;

     public StartIndexingResponce(boolean result ) {
        this.result = result;
    }
}
