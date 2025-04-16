package searchengine.dto.indexPage;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@Getter
@Setter
public class IndexPageResponce {

     private boolean result;
     private String error;

    public IndexPageResponce(boolean result ) {
        this.result = result;
    }
}
