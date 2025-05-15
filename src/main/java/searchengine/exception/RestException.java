package searchengine.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class RestException extends RuntimeException {

    private final HttpStatus httpStatus;
    private final Boolean result;

    public RestException(Boolean result, String error, HttpStatus status) {

        super(error);
        httpStatus = status;
        this.result = result;

    }
}