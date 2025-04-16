package searchengine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class Application {
    public static void main(String[] args) {
        try {
        SpringApplication.run(Application.class, args);
        } catch (Exception e) {

            if (e.getMessage().indexOf("The driver has not received any packets from the server")>0) {
                System.out.println("Сервер базы данных не доступен.");
            }
            if (e.getMessage().indexOf("Failed to load driver class org.postgresql.Driver")>0) {
                System.out.println("Не удается загрузить драйвер Postgresql.");
            }

            if (e.getMessage().indexOf("FATAL: password authentication failed for user \"root\"")>0) {
                System.out.println("Ошибка авторизации.");
            }


        }
    }
}
