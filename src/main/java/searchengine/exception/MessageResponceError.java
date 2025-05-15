package searchengine.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class MessageResponceError {
    private boolean result;
    private String error;
}
