package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.model.Site;
import java.util.Collection;
import java.util.List;

@Repository
public interface PageRepository  extends JpaRepository<Page, Integer> {

    // Метод для проверки существования сайта по названию
    boolean existsByPath(String path);

    // Найти страницу по path (учитывая, что path уникален)
    Page findByPath(String path);

    List<Page> findAll();

    boolean existsBySite(Site siteUrl);
    Collection<Object> findBySite(Site siteUrl);


    @Query("SELECT l.page FROM Index l WHERE l.lemma = :lemma ")
    List<Page> findAllPageByLemma(@Param("lemma") Lemma lemma);

    List<Page> findAllPageBySite(Site  byUrl);
}
