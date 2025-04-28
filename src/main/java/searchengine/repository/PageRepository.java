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

    boolean existsByPath(String path);

    Page findByPath(String path);

    List<Page> findAll();

    boolean existsBySite(Site siteUrl);
    
    Collection<Object> findBySite(Site siteUrl);

    @Query("SELECT l.page FROM Index l WHERE l.lemma = :lemma ")
    List<Page> findAllPageByLemma(@Param("lemma") Lemma lemma);

    List<Page> findAllPageBySite(Site  byUrl);

    boolean existsBySiteUrl(String url);

    @Query("SELECT l.page FROM Index l, Lemma l1 WHERE l.lemma =l1 AND l1.lemma =:lemmaName ")
    List<Page> findAllPageByLemmaName(String lemmaName);
}
