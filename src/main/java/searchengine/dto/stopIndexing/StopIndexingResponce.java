package searchengine.dto.stopIndexing;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class StopIndexingResponce {
     private boolean result;
     private String error;

     public StopIndexingResponce(boolean result ) {
        this.result = result;
     }
}
