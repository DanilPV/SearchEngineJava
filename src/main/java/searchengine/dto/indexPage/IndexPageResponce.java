package searchengine.dto.indexPage;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class IndexPageResponce {

     private boolean result;
     private String error;

    public IndexPageResponce(boolean result ) {
        this.result = result;
    }
}
