package searchengine.dto.startIndexing;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@Getter
@Setter
public class StartIndexingResponce {

     private boolean result;
     private String error;

     public StartIndexingResponce(boolean result ) {
        this.result = result;
    }
}
