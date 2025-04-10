package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import searchengine.enums.STATUS;
import searchengine.model.Site;
import java.util.List;
import java.util.Optional;

@Repository
public interface SiteRepository extends JpaRepository<Site, Integer> {

    // Метод для поиска сайта по названию
    Optional<Site> findByName(String name);

    // Метод для проверки существования сайта по названию
    boolean existsByName(String name);

    boolean existsByStatus(STATUS status);

    Optional<Site> findByUrl(String siteUrl);

    boolean existsByUrl(String siteUrl);

    List<Site> findALLByStatus(STATUS status);


}
