package searchengine.dto.stopIndexing;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@Setter
@Getter
public class StopIndexingResponce {
     private boolean result;
     private String error;

     public StopIndexingResponce(boolean result ) {
        this.result = result;
     }
}
