package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import searchengine.model.Lemma;
import searchengine.model.Site;
import java.util.List;
import java.util.Optional;

@Repository
public interface LemmaRepository  extends JpaRepository<Lemma, Integer> {

    @Query("SELECT l FROM Lemma l WHERE l.lemma = :lemma AND l.site = :site")
    Lemma findByLemmaAndSite(@Param("lemma") String lemma, @Param("site") Site site);

    List<Lemma> findAllLemmaBySite(Optional<Site> byUrl);

    boolean existsBySiteUrl(String url);


    Lemma findByLemma(String originalWord);

}
