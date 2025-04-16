package searchengine.exception;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class ExceptionHandlerControllers {

    @ExceptionHandler(RestException.class)
    public ResponseEntity<MessageResponceError> Exception(RestException e) {

        return ResponseEntity
                .status(e.getHttpStatus())
                .body(new MessageResponceError(e.getResult(),e.getMessage()));

    }


}
