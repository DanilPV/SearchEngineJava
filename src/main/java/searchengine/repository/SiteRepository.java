package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import searchengine.enums.Status;
import searchengine.model.Site;
import java.util.List;
import java.util.Optional;

@Repository
public interface SiteRepository extends JpaRepository<Site, Integer> {

    boolean existsByStatus(Status status);

    Optional<Site> findByUrl(String siteUrl);

    boolean existsByUrl(String siteUrl);

    List<Site> findALLByStatus(Status status);

}
